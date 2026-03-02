package com.hoangtien2k3.orderservice.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoangtien2k3.orderservice.kafka.KafkaTopicConstants;
import com.hoangtien2k3.orderservice.kafka.dto.OrderEventMessage;
import com.hoangtien2k3.orderservice.service.SagaOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final SagaOrchestrationService sagaOrchestrationService;
    private final ObjectMapper objectMapper;
    private final Set<String> processedMessageIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @KafkaListener(topics = KafkaTopicConstants.INVENTORY_RESERVED,
            groupId = "${order.kafka.consumer-group-id:order-service-group}")
    public void handleInventoryReserved(String message) {
        processEvent(message, KafkaTopicConstants.INVENTORY_RESERVED,
                sagaOrchestrationService::handleInventoryReserved);
    }

    @KafkaListener(topics = KafkaTopicConstants.INVENTORY_FAILED,
            groupId = "${order.kafka.consumer-group-id:order-service-group}")
    public void handleInventoryFailed(String message) {
        processEvent(message, KafkaTopicConstants.INVENTORY_FAILED,
                sagaOrchestrationService::handleInventoryFailed);
    }

    @KafkaListener(topics = KafkaTopicConstants.PAYMENT_COMPLETED,
            groupId = "${order.kafka.consumer-group-id:order-service-group}")
    public void handlePaymentCompleted(String message) {
        processEvent(message, KafkaTopicConstants.PAYMENT_COMPLETED,
                sagaOrchestrationService::handlePaymentCompleted);
    }

    @KafkaListener(topics = KafkaTopicConstants.PAYMENT_FAILED,
            groupId = "${order.kafka.consumer-group-id:order-service-group}")
    public void handlePaymentFailed(String message) {
        processEvent(message, KafkaTopicConstants.PAYMENT_FAILED,
                sagaOrchestrationService::handlePaymentFailed);
    }

    private void processEvent(String message, String eventType, Consumer<OrderEventMessage> handler) {
        try {
            OrderEventMessage event = objectMapper.readValue(message, OrderEventMessage.class);
            String messageId = event.getMessageId();

            if (messageId != null && processedMessageIds.contains(messageId)) {
                log.info("Duplicate message {} for event {}, skipping", messageId, eventType);
                return;
            }
            if (messageId != null) {
                processedMessageIds.add(messageId);
            }

            log.info("Processing {} event: sagaId={}, orderId={}", eventType,
                    event.getSagaId(), event.getOrderId());
            handler.accept(event);
        } catch (Exception e) {
            log.error("Error processing {} event: {}", eventType, e.getMessage(), e);
        }
    }
}
