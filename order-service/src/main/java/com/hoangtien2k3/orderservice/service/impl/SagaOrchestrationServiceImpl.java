package com.hoangtien2k3.orderservice.service.impl;

import com.hoangtien2k3.orderservice.entity.*;
import com.hoangtien2k3.orderservice.exception.wrapper.InvalidStateTransitionException;
import com.hoangtien2k3.orderservice.exception.wrapper.OrderNotFoundException;
import com.hoangtien2k3.orderservice.kafka.KafkaTopicConstants;
import com.hoangtien2k3.orderservice.kafka.dto.OrderEventMessage;
import com.hoangtien2k3.orderservice.kafka.producer.OrderEventProducer;
import com.hoangtien2k3.orderservice.repository.OrderRepository;
import com.hoangtien2k3.orderservice.repository.OrderSagaRepository;
import com.hoangtien2k3.orderservice.repository.OrderStatusHistoryRepository;
import com.hoangtien2k3.orderservice.service.SagaOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
@Transactional
public class SagaOrchestrationServiceImpl implements SagaOrchestrationService {

    private final OrderSagaRepository orderSagaRepository;
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OrderEventProducer eventProducer;

    @Override
    public OrderSaga startSaga(Integer orderId) {
        orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        String sagaId = UUID.randomUUID().toString();
        OrderSaga saga = OrderSaga.builder()
                .sagaId(sagaId)
                .orderId(orderId)
                .currentStep("RESERVE_INVENTORY")
                .status(SagaStatus.IN_PROGRESS)
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        orderSagaRepository.save(saga);

        // Publish order.created event
        eventProducer.publishEvent(KafkaTopicConstants.ORDER_CREATED,
                buildEvent(sagaId, orderId, "ORDER_CREATED", null));

        // Start first saga step: reserve inventory
        eventProducer.publishEvent(KafkaTopicConstants.INVENTORY_RESERVE_REQUESTED,
                buildEvent(sagaId, orderId, "RESERVE_INVENTORY", null));

        log.info("Started saga {} for order {}", sagaId, orderId);
        return saga;
    }

    @Override
    public void handleInventoryReserved(OrderEventMessage event) {
        String sagaId = event.getSagaId();
        OrderSaga saga = findSaga(sagaId);
        if (saga == null) return;

        saga.setCurrentStep("PROCESS_PAYMENT");
        saga.setUpdatedAt(LocalDateTime.now());
        orderSagaRepository.save(saga);

        updateOrderStatus(event.getOrderId(), OrderStatus.INVENTORY_RESERVED, "Inventory reserved");

        // Proceed to next step: process payment
        eventProducer.publishEvent(KafkaTopicConstants.PAYMENT_REQUESTED,
                buildEvent(sagaId, event.getOrderId(), "PROCESS_PAYMENT", null));

        log.info("Saga {}: inventory reserved, proceeding to payment", sagaId);
    }

    @Override
    public void handleInventoryFailed(OrderEventMessage event) {
        String sagaId = event.getSagaId();
        OrderSaga saga = findSaga(sagaId);
        if (saga == null) return;

        saga.setStatus(SagaStatus.FAILED);
        saga.setErrorMessage("Inventory reservation failed: " + event.getPayload());
        saga.setCurrentStep("RESERVE_INVENTORY_FAILED");
        saga.setUpdatedAt(LocalDateTime.now());
        orderSagaRepository.save(saga);

        updateOrderStatus(event.getOrderId(), OrderStatus.INVENTORY_FAILED, "Inventory reservation failed");
        cancelOrder(event.getOrderId(), "Inventory reservation failed");

        log.info("Saga {}: inventory failed, saga marked as FAILED", sagaId);
    }

    @Override
    public void handlePaymentCompleted(OrderEventMessage event) {
        String sagaId = event.getSagaId();
        OrderSaga saga = findSaga(sagaId);
        if (saga == null) return;

        updateOrderStatus(event.getOrderId(), OrderStatus.PAYMENT_CONFIRMED, "Payment completed");
        updateOrderStatus(event.getOrderId(), OrderStatus.PROCESSING, "Order processing started");

        saga.setCurrentStep("NOTIFY_CUSTOMER");
        saga.setStatus(SagaStatus.COMPLETED);
        saga.setUpdatedAt(LocalDateTime.now());
        orderSagaRepository.save(saga);

        // Final step: notify customer
        eventProducer.publishEvent(KafkaTopicConstants.ORDER_NOTIFICATION_REQUESTED,
                buildEvent(sagaId, event.getOrderId(), "NOTIFY_CUSTOMER", null));

        log.info("Saga {}: payment completed, saga COMPLETED", sagaId);
    }

    @Override
    public void handlePaymentFailed(OrderEventMessage event) {
        String sagaId = event.getSagaId();
        OrderSaga saga = findSaga(sagaId);
        if (saga == null) return;

        saga.setStatus(SagaStatus.COMPENSATING);
        saga.setErrorMessage("Payment failed: " + event.getPayload());
        saga.setCurrentStep("COMPENSATE_INVENTORY");
        saga.setUpdatedAt(LocalDateTime.now());
        orderSagaRepository.save(saga);

        updateOrderStatus(event.getOrderId(), OrderStatus.PAYMENT_FAILED, "Payment failed");

        // Compensation: release inventory (reverse order)
        eventProducer.publishEvent(KafkaTopicConstants.INVENTORY_RELEASE_REQUESTED,
                buildEvent(sagaId, event.getOrderId(), "RELEASE_INVENTORY", null));

        cancelOrder(event.getOrderId(), "Payment failed - compensation executed");

        saga.setStatus(SagaStatus.FAILED);
        saga.setCurrentStep("COMPENSATED");
        saga.setUpdatedAt(LocalDateTime.now());
        orderSagaRepository.save(saga);

        log.info("Saga {}: payment failed, compensation initiated and saga marked FAILED", sagaId);
    }

    @Override
    public OrderSaga retrySaga(Integer orderId) {
        OrderSaga existingSaga = orderSagaRepository.findTopByOrderIdOrderByCreatedAtDesc(orderId)
                .orElseThrow(() -> new OrderNotFoundException("No saga found for order " + orderId));

        if (existingSaga.getStatus() != SagaStatus.FAILED) {
            throw new IllegalStateException(
                    "Can only retry sagas in FAILED state. Current state: " + existingSaga.getStatus());
        }

        existingSaga.setStatus(SagaStatus.IN_PROGRESS);
        existingSaga.setRetryCount(existingSaga.getRetryCount() + 1);
        existingSaga.setErrorMessage(null);
        existingSaga.setCurrentStep("RESERVE_INVENTORY");
        existingSaga.setUpdatedAt(LocalDateTime.now());
        orderSagaRepository.save(existingSaga);

        // Reset order status to PENDING for retry
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        order.setStatus(OrderStatus.PENDING);
        orderRepository.save(order);

        eventProducer.publishEvent(KafkaTopicConstants.INVENTORY_RESERVE_REQUESTED,
                buildEvent(existingSaga.getSagaId(), orderId, "RESERVE_INVENTORY", null));

        log.info("Retrying saga {} for order {}, retry count: {}",
                existingSaga.getSagaId(), orderId, existingSaga.getRetryCount());
        return existingSaga;
    }

    @Override
    @Transactional(readOnly = true)
    public OrderSaga getSagaBySagaId(String sagaId) {
        return orderSagaRepository.findById(sagaId)
                .orElseThrow(() -> new OrderNotFoundException("Saga not found: " + sagaId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderStatusHistory> getStatusHistory(Integer orderId) {
        return orderStatusHistoryRepository.findByOrderIdOrderByChangedAtAsc(orderId);
    }

    // ---- helpers ----

    private OrderSaga findSaga(String sagaId) {
        return orderSagaRepository.findById(sagaId).orElse(null);
    }

    private void updateOrderStatus(Integer orderId, OrderStatus newStatus, String reason) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("Order {} not found when trying to update status to {}", orderId, newStatus);
            return;
        }

        OrderStatus fromStatus = order.getStatus();
        if (fromStatus != null && !fromStatus.canTransitionTo(newStatus)) {
            throw new InvalidStateTransitionException(
                    String.format("Cannot transition order %d from %s to %s", orderId, fromStatus, newStatus));
        }

        order.setStatus(newStatus);
        orderRepository.save(order);

        orderStatusHistoryRepository.save(OrderStatusHistory.builder()
                .orderId(orderId)
                .fromStatus(fromStatus)
                .toStatus(newStatus)
                .changedAt(LocalDateTime.now())
                .changedBy("SAGA")
                .reason(reason)
                .build());
    }

    private void cancelOrder(Integer orderId, String reason) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) return;

        OrderStatus fromStatus = order.getStatus();
        if (fromStatus == null || fromStatus.canTransitionTo(OrderStatus.CANCELLED)) {
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);

            orderStatusHistoryRepository.save(OrderStatusHistory.builder()
                    .orderId(orderId)
                    .fromStatus(fromStatus)
                    .toStatus(OrderStatus.CANCELLED)
                    .changedAt(LocalDateTime.now())
                    .changedBy("SAGA")
                    .reason(reason)
                    .build());
        } else {
            log.warn("Cannot cancel order {} from status {}", orderId, fromStatus);
        }
    }

    private OrderEventMessage buildEvent(String sagaId, Integer orderId, String eventType, String payload) {
        return OrderEventMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .sagaId(sagaId)
                .orderId(orderId)
                .eventType(eventType)
                .payload(payload)
                .timestamp(LocalDateTime.now().toString())
                .build();
    }
}
