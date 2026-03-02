package com.hoangtien2k3.orderservice.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoangtien2k3.orderservice.kafka.dto.OrderEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishEvent(String topic, OrderEventMessage event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            String key = event.getSagaId() != null ? event.getSagaId() : String.valueOf(event.getOrderId());
            kafkaTemplate.send(topic, key, message);
            log.info("Published event to topic {}: sagaId={}, orderId={}, eventType={}",
                    topic, event.getSagaId(), event.getOrderId(), event.getEventType());
        } catch (Exception e) {
            log.error("Failed to publish event to topic {}: {}", topic, e.getMessage(), e);
        }
    }
}
