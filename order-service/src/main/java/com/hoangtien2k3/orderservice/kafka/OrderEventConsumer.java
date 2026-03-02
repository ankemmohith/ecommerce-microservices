package com.hoangtien2k3.orderservice.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoangtien2k3.orderservice.service.OrderSagaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final OrderSagaService orderSagaService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "inventory.reserved", groupId = "${order.kafka.consumer-group-id:order-service-group}")
    public void handleInventoryReserved(String message) {
        log.info("Received inventory.reserved: {}", message);
        try {
            JsonNode node = objectMapper.readTree(message);
            String sagaId = node.path("sagaId").asText();
            String messageId = node.path("messageId").asText(sagaId + "-inv-reserved");
            orderSagaService.handleInventoryReserved(sagaId, messageId);
        } catch (Exception e) {
            log.error("Error handling inventory.reserved: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "inventory.failed", groupId = "${order.kafka.consumer-group-id:order-service-group}")
    public void handleInventoryFailed(String message) {
        log.info("Received inventory.failed: {}", message);
        try {
            JsonNode node = objectMapper.readTree(message);
            String sagaId = node.path("sagaId").asText();
            String messageId = node.path("messageId").asText(sagaId + "-inv-failed");
            String reason = node.path("reason").asText("Inventory reservation failed");
            orderSagaService.handleInventoryFailed(sagaId, messageId, reason);
        } catch (Exception e) {
            log.error("Error handling inventory.failed: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "payment.completed", groupId = "${order.kafka.consumer-group-id:order-service-group}")
    public void handlePaymentCompleted(String message) {
        log.info("Received payment.completed: {}", message);
        try {
            JsonNode node = objectMapper.readTree(message);
            String sagaId = node.path("sagaId").asText();
            String messageId = node.path("messageId").asText(sagaId + "-pay-completed");
            orderSagaService.handlePaymentCompleted(sagaId, messageId);
        } catch (Exception e) {
            log.error("Error handling payment.completed: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "payment.failed", groupId = "${order.kafka.consumer-group-id:order-service-group}")
    public void handlePaymentFailed(String message) {
        log.info("Received payment.failed: {}", message);
        try {
            JsonNode node = objectMapper.readTree(message);
            String sagaId = node.path("sagaId").asText();
            String messageId = node.path("messageId").asText(sagaId + "-pay-failed");
            String reason = node.path("reason").asText("Payment processing failed");
            orderSagaService.handlePaymentFailed(sagaId, messageId, reason);
        } catch (Exception e) {
            log.error("Error handling payment.failed: {}", e.getMessage(), e);
        }
    }
}
