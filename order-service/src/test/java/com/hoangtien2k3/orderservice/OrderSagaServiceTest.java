package com.hoangtien2k3.orderservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoangtien2k3.orderservice.entity.*;
import com.hoangtien2k3.orderservice.exception.wrapper.InvalidOrderStateException;
import com.hoangtien2k3.orderservice.exception.wrapper.OrderNotFoundException;
import com.hoangtien2k3.orderservice.kafka.OrderEventProducer;
import com.hoangtien2k3.orderservice.repository.OrderRepository;
import com.hoangtien2k3.orderservice.repository.OrderSagaRepository;
import com.hoangtien2k3.orderservice.repository.OrderStatusHistoryRepository;
import com.hoangtien2k3.orderservice.service.impl.OrderSagaServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderSagaServiceTest {

    @Mock
    private OrderSagaRepository orderSagaRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Mock
    private OrderEventProducer orderEventProducer;

    @InjectMocks
    private OrderSagaServiceImpl orderSagaService;

    private Order testOrder;

    @BeforeEach
    void setUp() throws Exception {
        // Inject ObjectMapper via reflection since @InjectMocks doesn't handle it
        var objectMapperField = OrderSagaServiceImpl.class.getDeclaredField("objectMapper");
        objectMapperField.setAccessible(true);
        objectMapperField.set(orderSagaService, new ObjectMapper());

        testOrder = Order.builder()
                .orderId(1)
                .orderDesc("Test order")
                .orderFee(100.0)
                .productId(10)
                .status(OrderStatus.PENDING)
                .cart(Cart.builder().cartId(1).userId(1L).build())
                .build();
    }

    @Test
    void startSaga_shouldCreateSagaAndPublishEvents() {
        when(orderRepository.findById(1)).thenReturn(Optional.of(testOrder));
        when(orderSagaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = orderSagaService.startSaga(1);

        assertThat(result).isNotNull();
        assertThat(result.getSagaId()).isNotNull();
        assertThat(result.getOrderId()).isEqualTo(1);
        assertThat(result.getStatus()).isEqualTo(SagaStatus.IN_PROGRESS);
        assertThat(result.getCurrentStep()).isEqualTo(SagaStep.RESERVE_INVENTORY);

        verify(orderEventProducer).publishOrderCreated(anyString());
        verify(orderEventProducer).publishInventoryReserveRequested(anyString());
    }

    @Test
    void startSaga_shouldThrowWhenOrderNotFound() {
        when(orderRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderSagaService.startSaga(999))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void handleInventoryReserved_shouldAdvanceSagaToPaymentStep() {
        OrderSaga saga = createInProgressSaga("saga-1", 1, SagaStep.RESERVE_INVENTORY);
        when(orderSagaRepository.findById("saga-1")).thenReturn(Optional.of(saga));
        when(orderSagaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        orderSagaService.handleInventoryReserved("saga-1", "msg-1");

        ArgumentCaptor<OrderSaga> sagaCaptor = ArgumentCaptor.forClass(OrderSaga.class);
        verify(orderSagaRepository).save(sagaCaptor.capture());
        assertThat(sagaCaptor.getValue().getCurrentStep()).isEqualTo(SagaStep.PROCESS_PAYMENT);

        verify(orderEventProducer).publishPaymentRequested(anyString());
    }

    @Test
    void handleInventoryReserved_shouldIgnoreDuplicateMessages() {
        OrderSaga saga = createInProgressSaga("saga-2", 1, SagaStep.RESERVE_INVENTORY);
        when(orderSagaRepository.findById("saga-2")).thenReturn(Optional.of(saga));
        when(orderSagaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        orderSagaService.handleInventoryReserved("saga-2", "duplicate-msg");
        orderSagaService.handleInventoryReserved("saga-2", "duplicate-msg"); // duplicate

        // Should only process once
        verify(orderSagaRepository, times(1)).save(any());
        verify(orderEventProducer, times(1)).publishPaymentRequested(anyString());
    }

    @Test
    void handlePaymentCompleted_happyPath_shouldCompleteOrderAndSaga() {
        OrderSaga saga = createInProgressSaga("saga-3", 1, SagaStep.PROCESS_PAYMENT);
        when(orderSagaRepository.findById("saga-3")).thenReturn(Optional.of(saga));
        when(orderSagaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.findById(1)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(orderStatusHistoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        orderSagaService.handlePaymentCompleted("saga-3", "msg-pay-1");

        ArgumentCaptor<OrderSaga> sagaCaptor = ArgumentCaptor.forClass(OrderSaga.class);
        verify(orderSagaRepository).save(sagaCaptor.capture());
        assertThat(sagaCaptor.getValue().getStatus()).isEqualTo(SagaStatus.COMPLETED);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.PAYMENT_CONFIRMED);
    }

    @Test
    void handlePaymentFailed_compensationScenario_shouldTriggerInventoryRelease() {
        OrderSaga saga = createInProgressSaga("saga-4", 1, SagaStep.PROCESS_PAYMENT);
        when(orderSagaRepository.findById("saga-4")).thenReturn(Optional.of(saga));
        when(orderSagaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.findById(1)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(orderStatusHistoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        orderSagaService.handlePaymentFailed("saga-4", "msg-pay-failed", "Insufficient funds");

        // Verify compensation: inventory release was triggered
        verify(orderEventProducer).publishInventoryReleaseRequested(anyString());

        // Verify saga is marked as FAILED
        ArgumentCaptor<OrderSaga> sagaCaptor = ArgumentCaptor.forClass(OrderSaga.class);
        verify(orderSagaRepository, times(2)).save(sagaCaptor.capture()); // COMPENSATING then FAILED
        assertThat(sagaCaptor.getAllValues().get(1).getStatus()).isEqualTo(SagaStatus.FAILED);
        assertThat(sagaCaptor.getAllValues().get(1).getErrorMessage()).contains("Insufficient funds");

        // Verify order status updated to PAYMENT_FAILED
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
    }

    @Test
    void handleInventoryFailed_shouldMarkSagaAsFailedWithoutCompensation() {
        OrderSaga saga = createInProgressSaga("saga-5", 1, SagaStep.RESERVE_INVENTORY);
        when(orderSagaRepository.findById("saga-5")).thenReturn(Optional.of(saga));
        when(orderSagaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.findById(1)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(orderStatusHistoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        orderSagaService.handleInventoryFailed("saga-5", "msg-inv-fail", "Out of stock");

        // No compensation event for inventory failure (nothing was reserved)
        verify(orderEventProducer, never()).publishInventoryReleaseRequested(anyString());

        // Saga is FAILED
        ArgumentCaptor<OrderSaga> sagaCaptor = ArgumentCaptor.forClass(OrderSaga.class);
        verify(orderSagaRepository).save(sagaCaptor.capture());
        assertThat(sagaCaptor.getValue().getStatus()).isEqualTo(SagaStatus.FAILED);
    }

    @Test
    void retrySaga_shouldResetAndRestartFailedSaga() {
        OrderSaga failedSaga = createSaga("saga-6", 1, SagaStep.PROCESS_PAYMENT, SagaStatus.FAILED);
        failedSaga.setRetryCount(0);

        Order failedOrder = Order.builder()
                .orderId(1).status(OrderStatus.PAYMENT_FAILED)
                .cart(Cart.builder().cartId(1).userId(1L).build()).build();

        when(orderSagaRepository.findByOrderId(1)).thenReturn(Optional.of(failedSaga));
        when(orderSagaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.findById(1)).thenReturn(Optional.of(failedOrder));

        var result = orderSagaService.retrySaga(1);

        assertThat(result.getStatus()).isEqualTo(SagaStatus.IN_PROGRESS);
        assertThat(result.getCurrentStep()).isEqualTo(SagaStep.RESERVE_INVENTORY);
        assertThat(result.getRetryCount()).isEqualTo(1);

        verify(orderEventProducer).publishInventoryReserveRequested(anyString());
    }

    @Test
    void retrySaga_shouldThrowWhenSagaNotInFailedStatus() {
        OrderSaga completedSaga = createSaga("saga-7", 1, SagaStep.NOTIFY_CUSTOMER, SagaStatus.COMPLETED);
        when(orderSagaRepository.findByOrderId(1)).thenReturn(Optional.of(completedSaga));

        assertThatThrownBy(() -> orderSagaService.retrySaga(1))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("not in FAILED status");
    }

    @Test
    void getSaga_shouldReturnSagaDetails() {
        OrderSaga saga = createInProgressSaga("saga-8", 1, SagaStep.RESERVE_INVENTORY);
        when(orderSagaRepository.findById("saga-8")).thenReturn(Optional.of(saga));

        var result = orderSagaService.getSaga("saga-8");

        assertThat(result.getSagaId()).isEqualTo("saga-8");
        assertThat(result.getOrderId()).isEqualTo(1);
        assertThat(result.getStatus()).isEqualTo(SagaStatus.IN_PROGRESS);
    }

    @Test
    void getSaga_shouldThrowWhenNotFound() {
        when(orderSagaRepository.findById("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderSagaService.getSaga("unknown"))
                .isInstanceOf(OrderNotFoundException.class);
    }

    private OrderSaga createInProgressSaga(String sagaId, Integer orderId, SagaStep step) {
        return createSaga(sagaId, orderId, step, SagaStatus.IN_PROGRESS);
    }

    private OrderSaga createSaga(String sagaId, Integer orderId, SagaStep step, SagaStatus status) {
        return OrderSaga.builder()
                .sagaId(sagaId)
                .orderId(orderId)
                .currentStep(step)
                .status(status)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
