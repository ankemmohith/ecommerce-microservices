package com.hoangtien2k3.orderservice.saga;

import com.hoangtien2k3.orderservice.entity.*;
import com.hoangtien2k3.orderservice.exception.wrapper.InvalidStateTransitionException;
import com.hoangtien2k3.orderservice.exception.wrapper.OrderNotFoundException;
import com.hoangtien2k3.orderservice.kafka.dto.OrderEventMessage;
import com.hoangtien2k3.orderservice.kafka.producer.OrderEventProducer;
import com.hoangtien2k3.orderservice.repository.OrderRepository;
import com.hoangtien2k3.orderservice.repository.OrderSagaRepository;
import com.hoangtien2k3.orderservice.repository.OrderStatusHistoryRepository;
import com.hoangtien2k3.orderservice.service.impl.SagaOrchestrationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SagaOrchestrationServiceTest {

    @Mock
    private OrderSagaRepository orderSagaRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Mock
    private OrderEventProducer eventProducer;

    @InjectMocks
    private SagaOrchestrationServiceImpl sagaService;

    private Order sampleOrder;
    private OrderSaga sampleSaga;

    @BeforeEach
    void setUp() {
        sampleOrder = Order.builder()
                .orderId(1)
                .orderDesc("Test Order")
                .orderFee(99.99)
                .status(OrderStatus.PENDING)
                .cart(Cart.builder().cartId(1).userId(1L).build())
                .build();

        sampleSaga = OrderSaga.builder()
                .sagaId("test-saga-id")
                .orderId(1)
                .currentStep("RESERVE_INVENTORY")
                .status(SagaStatus.IN_PROGRESS)
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ---- startSaga ----

    @Test
    void startSaga_createsAndSavesSagaEntity() {
        when(orderRepository.findById(1)).thenReturn(Optional.of(sampleOrder));
        when(orderSagaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderSaga result = sagaService.startSaga(1);

        assertThat(result.getOrderId()).isEqualTo(1);
        assertThat(result.getStatus()).isEqualTo(SagaStatus.IN_PROGRESS);
        assertThat(result.getCurrentStep()).isEqualTo("RESERVE_INVENTORY");
        assertThat(result.getRetryCount()).isEqualTo(0);
        assertThat(result.getSagaId()).isNotNull();
    }

    @Test
    void startSaga_publishesOrderCreatedAndInventoryReserveEvents() {
        when(orderRepository.findById(1)).thenReturn(Optional.of(sampleOrder));
        when(orderSagaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sagaService.startSaga(1);

        verify(eventProducer, times(2)).publishEvent(anyString(), any(OrderEventMessage.class));
    }

    @Test
    void startSaga_throwsOrderNotFoundException_whenOrderNotFound() {
        when(orderRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sagaService.startSaga(99))
                .isInstanceOf(OrderNotFoundException.class);
    }

    // ---- handleInventoryReserved ----

    @Test
    void handleInventoryReserved_advancesSagaToProcessPayment() {
        when(orderSagaRepository.findById("test-saga-id")).thenReturn(Optional.of(sampleSaga));
        when(orderRepository.findById(1)).thenReturn(Optional.of(sampleOrder));
        when(orderSagaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderEventMessage event = buildEvent("test-saga-id", 1, "INVENTORY_RESERVED");
        sagaService.handleInventoryReserved(event);

        ArgumentCaptor<OrderSaga> sagaCaptor = ArgumentCaptor.forClass(OrderSaga.class);
        verify(orderSagaRepository).save(sagaCaptor.capture());
        assertThat(sagaCaptor.getValue().getCurrentStep()).isEqualTo("PROCESS_PAYMENT");
    }

    @Test
    void handleInventoryReserved_updatesOrderStatusToInventoryReserved() {
        when(orderSagaRepository.findById("test-saga-id")).thenReturn(Optional.of(sampleSaga));
        when(orderRepository.findById(1)).thenReturn(Optional.of(sampleOrder));
        when(orderSagaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderEventMessage event = buildEvent("test-saga-id", 1, "INVENTORY_RESERVED");
        sagaService.handleInventoryReserved(event);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.INVENTORY_RESERVED);
    }

    @Test
    void handleInventoryReserved_publishesPaymentRequestedEvent() {
        when(orderSagaRepository.findById("test-saga-id")).thenReturn(Optional.of(sampleSaga));
        when(orderRepository.findById(1)).thenReturn(Optional.of(sampleOrder));
        when(orderSagaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderEventMessage event = buildEvent("test-saga-id", 1, "INVENTORY_RESERVED");
        sagaService.handleInventoryReserved(event);

        verify(eventProducer).publishEvent(eq("payment.requested"), any(OrderEventMessage.class));
    }

    // ---- handleInventoryFailed ----

    @Test
    void handleInventoryFailed_marksSagaAsFailed() {
        when(orderSagaRepository.findById("test-saga-id")).thenReturn(Optional.of(sampleSaga));
        when(orderRepository.findById(1)).thenReturn(Optional.of(sampleOrder));
        when(orderSagaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderEventMessage event = buildEvent("test-saga-id", 1, "INVENTORY_FAILED");
        sagaService.handleInventoryFailed(event);

        ArgumentCaptor<OrderSaga> sagaCaptor = ArgumentCaptor.forClass(OrderSaga.class);
        verify(orderSagaRepository, atLeastOnce()).save(sagaCaptor.capture());
        List<OrderSaga> savedSagas = sagaCaptor.getAllValues();
        assertThat(savedSagas).anyMatch(s -> s.getStatus() == SagaStatus.FAILED);
    }

    @Test
    void handleInventoryFailed_cancelsOrder() {
        when(orderSagaRepository.findById("test-saga-id")).thenReturn(Optional.of(sampleSaga));
        when(orderRepository.findById(1)).thenReturn(Optional.of(sampleOrder));
        when(orderSagaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderEventMessage event = buildEvent("test-saga-id", 1, "INVENTORY_FAILED");
        sagaService.handleInventoryFailed(event);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, atLeastOnce()).save(orderCaptor.capture());
        List<Order> savedOrders = orderCaptor.getAllValues();
        assertThat(savedOrders).anyMatch(o -> o.getStatus() == OrderStatus.CANCELLED);
    }

    // ---- handlePaymentCompleted ----

    @Test
    void handlePaymentCompleted_marksSagaAsCompleted() {
        sampleOrder.setStatus(OrderStatus.INVENTORY_RESERVED);
        when(orderSagaRepository.findById("test-saga-id")).thenReturn(Optional.of(sampleSaga));
        when(orderRepository.findById(1)).thenReturn(Optional.of(sampleOrder));
        when(orderSagaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderEventMessage event = buildEvent("test-saga-id", 1, "PAYMENT_COMPLETED");
        sagaService.handlePaymentCompleted(event);

        ArgumentCaptor<OrderSaga> sagaCaptor = ArgumentCaptor.forClass(OrderSaga.class);
        verify(orderSagaRepository, atLeastOnce()).save(sagaCaptor.capture());
        List<OrderSaga> savedSagas = sagaCaptor.getAllValues();
        assertThat(savedSagas).anyMatch(s -> s.getStatus() == SagaStatus.COMPLETED);
    }

    @Test
    void handlePaymentCompleted_publishesNotificationEvent() {
        sampleOrder.setStatus(OrderStatus.INVENTORY_RESERVED);
        when(orderSagaRepository.findById("test-saga-id")).thenReturn(Optional.of(sampleSaga));
        when(orderRepository.findById(1)).thenReturn(Optional.of(sampleOrder));
        when(orderSagaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderEventMessage event = buildEvent("test-saga-id", 1, "PAYMENT_COMPLETED");
        sagaService.handlePaymentCompleted(event);

        verify(eventProducer).publishEvent(eq("order.notification.requested"), any());
    }

    // ---- handlePaymentFailed (compensation) ----

    @Test
    void handlePaymentFailed_startCompensation_publishesInventoryRelease() {
        sampleOrder.setStatus(OrderStatus.INVENTORY_RESERVED);
        when(orderSagaRepository.findById("test-saga-id")).thenReturn(Optional.of(sampleSaga));
        when(orderRepository.findById(1)).thenReturn(Optional.of(sampleOrder));
        when(orderSagaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderEventMessage event = buildEvent("test-saga-id", 1, "PAYMENT_FAILED");
        sagaService.handlePaymentFailed(event);

        verify(eventProducer).publishEvent(eq("inventory.release.requested"), any());
    }

    @Test
    void handlePaymentFailed_marksSagaAsFailed_afterCompensation() {
        sampleOrder.setStatus(OrderStatus.INVENTORY_RESERVED);
        when(orderSagaRepository.findById("test-saga-id")).thenReturn(Optional.of(sampleSaga));
        when(orderRepository.findById(1)).thenReturn(Optional.of(sampleOrder));
        when(orderSagaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderEventMessage event = buildEvent("test-saga-id", 1, "PAYMENT_FAILED");
        sagaService.handlePaymentFailed(event);

        ArgumentCaptor<OrderSaga> sagaCaptor = ArgumentCaptor.forClass(OrderSaga.class);
        verify(orderSagaRepository, atLeastOnce()).save(sagaCaptor.capture());
        List<OrderSaga> savedSagas = sagaCaptor.getAllValues();
        assertThat(savedSagas).anyMatch(s -> s.getStatus() == SagaStatus.FAILED);
    }

    // ---- retrySaga ----

    @Test
    void retrySaga_incrementsRetryCount_andResetsStatus() {
        sampleSaga.setStatus(SagaStatus.FAILED);
        sampleOrder.setStatus(OrderStatus.CANCELLED);
        when(orderSagaRepository.findTopByOrderIdOrderByCreatedAtDesc(1))
                .thenReturn(Optional.of(sampleSaga));
        when(orderRepository.findById(1)).thenReturn(Optional.of(sampleOrder));
        when(orderSagaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderSaga result = sagaService.retrySaga(1);

        assertThat(result.getStatus()).isEqualTo(SagaStatus.IN_PROGRESS);
        assertThat(result.getRetryCount()).isEqualTo(1);
    }

    @Test
    void retrySaga_throws_whenSagaNotInFailedState() {
        sampleSaga.setStatus(SagaStatus.COMPLETED);
        when(orderSagaRepository.findTopByOrderIdOrderByCreatedAtDesc(1))
                .thenReturn(Optional.of(sampleSaga));

        assertThatThrownBy(() -> sagaService.retrySaga(1))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---- state transition validation in updateOrderStatus ----

    @Test
    void handleInventoryReserved_throwsInvalidStateTransition_whenOrderInWrongState() {
        sampleOrder.setStatus(OrderStatus.SHIPPED); // already shipped, cannot go to INVENTORY_RESERVED
        when(orderSagaRepository.findById("test-saga-id")).thenReturn(Optional.of(sampleSaga));
        when(orderRepository.findById(1)).thenReturn(Optional.of(sampleOrder));
        when(orderSagaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderEventMessage event = buildEvent("test-saga-id", 1, "INVENTORY_RESERVED");

        assertThatThrownBy(() -> sagaService.handleInventoryReserved(event))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    // ---- getStatusHistory ----

    @Test
    void getStatusHistory_returnsHistoryFromRepository() {
        List<OrderStatusHistory> history = List.of(
                OrderStatusHistory.builder().orderId(1).toStatus(OrderStatus.PENDING).build(),
                OrderStatusHistory.builder().orderId(1).fromStatus(OrderStatus.PENDING)
                        .toStatus(OrderStatus.INVENTORY_RESERVED).build());
        when(orderStatusHistoryRepository.findByOrderIdOrderByChangedAtAsc(1)).thenReturn(history);

        List<OrderStatusHistory> result = sagaService.getStatusHistory(1);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getToStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.get(1).getToStatus()).isEqualTo(OrderStatus.INVENTORY_RESERVED);
    }

    // ---- helper ----

    private OrderEventMessage buildEvent(String sagaId, Integer orderId, String eventType) {
        return OrderEventMessage.builder()
                .messageId("msg-" + System.nanoTime())
                .sagaId(sagaId)
                .orderId(orderId)
                .eventType(eventType)
                .build();
    }
}
