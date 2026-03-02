package com.hoangtien2k3.orderservice.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoangtien2k3.orderservice.dto.saga.OrderSagaDto;
import com.hoangtien2k3.orderservice.entity.*;
import com.hoangtien2k3.orderservice.exception.wrapper.InvalidOrderStateException;
import com.hoangtien2k3.orderservice.exception.wrapper.OrderNotFoundException;
import com.hoangtien2k3.orderservice.kafka.OrderEventProducer;
import com.hoangtien2k3.orderservice.repository.OrderRepository;
import com.hoangtien2k3.orderservice.repository.OrderSagaRepository;
import com.hoangtien2k3.orderservice.repository.OrderStatusHistoryRepository;
import com.hoangtien2k3.orderservice.service.OrderSagaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSagaServiceImpl implements OrderSagaService {

    private final OrderSagaRepository orderSagaRepository;
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OrderEventProducer orderEventProducer;
    private final ObjectMapper objectMapper;

    // Idempotency: track processed message IDs to prevent duplicate processing.
    // NOTE: This in-memory set does not survive service restarts. For production,
    // replace with a persistent store (e.g., database table) for durable idempotency.
    private final Set<String> processedMessageIds = ConcurrentHashMap.newKeySet();

    @Override
    @Transactional
    public OrderSagaDto startSaga(Integer orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        String sagaId = UUID.randomUUID().toString();
        OrderSaga saga = OrderSaga.builder()
                .sagaId(sagaId)
                .orderId(orderId)
                .currentStep(SagaStep.RESERVE_INVENTORY)
                .status(SagaStatus.IN_PROGRESS)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        orderSagaRepository.save(saga);

        // Publish order.created event
        String orderCreatedMsg = buildMessage(sagaId, orderId, "ORDER_CREATED");
        orderEventProducer.publishOrderCreated(orderCreatedMsg);

        // Start first saga step: reserve inventory
        String inventoryMsg = buildMessage(sagaId, orderId, "RESERVE_INVENTORY");
        orderEventProducer.publishInventoryReserveRequested(inventoryMsg);

        log.info("Saga started: sagaId={}, orderId={}", sagaId, orderId);
        return toDto(saga);
    }

    @Override
    public OrderSagaDto getSaga(String sagaId) {
        OrderSaga saga = orderSagaRepository.findById(sagaId)
                .orElseThrow(() -> new OrderNotFoundException("Saga not found: " + sagaId));
        return toDto(saga);
    }

    @Override
    @Transactional
    public void handleInventoryReserved(String sagaId, String messageId) {
        if (!processedMessageIds.add(messageId)) {
            log.info("Duplicate message ignored: {}", messageId);
            return;
        }

        OrderSaga saga = orderSagaRepository.findById(sagaId)
                .orElseThrow(() -> new OrderNotFoundException("Saga not found: " + sagaId));

        if (saga.getStatus() != SagaStatus.IN_PROGRESS) {
            log.warn("Saga {} is not IN_PROGRESS, ignoring inventory.reserved", sagaId);
            return;
        }

        saga.setCurrentStep(SagaStep.PROCESS_PAYMENT);
        saga.setUpdatedAt(Instant.now());
        orderSagaRepository.save(saga);

        // Trigger next step: process payment
        String paymentMsg = buildMessage(sagaId, saga.getOrderId(), "PROCESS_PAYMENT");
        orderEventProducer.publishPaymentRequested(paymentMsg);
        log.info("Saga {}: inventory reserved, triggering payment", sagaId);
    }

    @Override
    @Transactional
    public void handleInventoryFailed(String sagaId, String messageId, String reason) {
        if (!processedMessageIds.add(messageId)) {
            log.info("Duplicate message ignored: {}", messageId);
            return;
        }

        OrderSaga saga = orderSagaRepository.findById(sagaId)
                .orElseThrow(() -> new OrderNotFoundException("Saga not found: " + sagaId));

        saga.setStatus(SagaStatus.FAILED);
        saga.setErrorMessage("Inventory failed: " + reason);
        saga.setUpdatedAt(Instant.now());
        orderSagaRepository.save(saga);

        // Update order status to CANCELLED (inventory was never reserved, so cancel the order)
        updateOrderStatus(saga.getOrderId(), OrderStatus.CANCELLED, "SAGA", "Inventory reservation failed: " + reason);
        log.info("Saga {}: inventory failed, saga marked as FAILED", sagaId);
    }

    @Override
    @Transactional
    public void handlePaymentCompleted(String sagaId, String messageId) {
        if (!processedMessageIds.add(messageId)) {
            log.info("Duplicate message ignored: {}", messageId);
            return;
        }

        OrderSaga saga = orderSagaRepository.findById(sagaId)
                .orElseThrow(() -> new OrderNotFoundException("Saga not found: " + sagaId));

        if (saga.getStatus() != SagaStatus.IN_PROGRESS) {
            log.warn("Saga {} is not IN_PROGRESS, ignoring payment.completed", sagaId);
            return;
        }

        saga.setCurrentStep(SagaStep.UPDATE_ORDER_STATUS);
        saga.setStatus(SagaStatus.COMPLETED);
        saga.setUpdatedAt(Instant.now());
        orderSagaRepository.save(saga);

        // Update order status to PAYMENT_CONFIRMED
        updateOrderStatus(saga.getOrderId(), OrderStatus.PAYMENT_CONFIRMED, "SAGA", "Payment completed successfully");
        log.info("Saga {}: payment completed, order confirmed", sagaId);
    }

    @Override
    @Transactional
    public void handlePaymentFailed(String sagaId, String messageId, String reason) {
        if (!processedMessageIds.add(messageId)) {
            log.info("Duplicate message ignored: {}", messageId);
            return;
        }

        OrderSaga saga = orderSagaRepository.findById(sagaId)
                .orElseThrow(() -> new OrderNotFoundException("Saga not found: " + sagaId));

        saga.setStatus(SagaStatus.COMPENSATING);
        saga.setErrorMessage("Payment failed: " + reason);
        saga.setUpdatedAt(Instant.now());
        orderSagaRepository.save(saga);

        // Compensation: release inventory (reverse of step 1)
        String releaseMsg = buildMessage(sagaId, saga.getOrderId(), "RELEASE_INVENTORY");
        orderEventProducer.publishInventoryReleaseRequested(releaseMsg);

        // Update order status
        updateOrderStatus(saga.getOrderId(), OrderStatus.PAYMENT_FAILED, "SAGA", "Payment failed: " + reason);

        saga.setStatus(SagaStatus.FAILED);
        orderSagaRepository.save(saga);
        log.info("Saga {}: payment failed, compensation triggered", sagaId);
    }

    @Override
    @Transactional
    public OrderSagaDto retrySaga(Integer orderId) {
        OrderSaga existingSaga = orderSagaRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException("No saga found for order: " + orderId));

        if (existingSaga.getStatus() != SagaStatus.FAILED) {
            throw new InvalidOrderStateException(
                    "Cannot retry saga for order " + orderId + ": saga is not in FAILED status");
        }

        existingSaga.setStatus(SagaStatus.IN_PROGRESS);
        existingSaga.setCurrentStep(SagaStep.RESERVE_INVENTORY);
        existingSaga.setRetryCount(existingSaga.getRetryCount() + 1);
        existingSaga.setErrorMessage(null);
        existingSaga.setUpdatedAt(Instant.now());
        orderSagaRepository.save(existingSaga);

        // Reset order to PENDING if it was PAYMENT_FAILED
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        if (order.getStatus() == OrderStatus.PAYMENT_FAILED) {
            updateOrderStatus(orderId, OrderStatus.PENDING, "RETRY", "Retrying saga");
        }

        // Restart from step 1
        String inventoryMsg = buildMessage(existingSaga.getSagaId(), orderId, "RESERVE_INVENTORY");
        orderEventProducer.publishInventoryReserveRequested(inventoryMsg);

        log.info("Saga retried: sagaId={}, orderId={}, attempt={}", existingSaga.getSagaId(), orderId, existingSaga.getRetryCount());
        return toDto(existingSaga);
    }

    private void updateOrderStatus(Integer orderId, OrderStatus newStatus, String changedBy, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        OrderStatus currentStatus = order.getStatus();
        if (!currentStatus.canTransitionTo(newStatus)) {
            log.warn("Cannot transition order {} from {} to {}", orderId, currentStatus, newStatus);
            return;
        }

        OrderStatusHistory history = OrderStatusHistory.builder()
                .orderId(orderId)
                .fromStatus(currentStatus)
                .toStatus(newStatus)
                .changedAt(Instant.now())
                .changedBy(changedBy)
                .reason(reason)
                .build();
        orderStatusHistoryRepository.save(history);

        order.setStatus(newStatus);
        orderRepository.save(order);
        log.info("Order {} status updated: {} -> {}", orderId, currentStatus, newStatus);
    }

    private String buildMessage(String sagaId, Integer orderId, String action) {
        try {
            Map<String, Object> msg = new HashMap<>();
            msg.put("sagaId", sagaId);
            msg.put("orderId", orderId);
            msg.put("action", action);
            msg.put("messageId", UUID.randomUUID().toString());
            return objectMapper.writeValueAsString(msg);
        } catch (Exception e) {
            log.error("Error building message", e);
            // Fallback with a unique messageId to maintain idempotency
            String messageId = UUID.randomUUID().toString();
            return "{\"sagaId\":\"" + sagaId + "\",\"orderId\":" + orderId
                    + ",\"action\":\"" + action + "\",\"messageId\":\"" + messageId + "\"}";
        }
    }

    private OrderSagaDto toDto(OrderSaga saga) {
        return OrderSagaDto.builder()
                .sagaId(saga.getSagaId())
                .orderId(saga.getOrderId())
                .currentStep(saga.getCurrentStep())
                .status(saga.getStatus())
                .stepsData(saga.getStepsData())
                .retryCount(saga.getRetryCount())
                .errorMessage(saga.getErrorMessage())
                .createdAt(saga.getCreatedAt())
                .updatedAt(saga.getUpdatedAt())
                .build();
    }
}
