package com.hoangtien2k3.orderservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderEventProducer {

    public static final String TOPIC_ORDER_CREATED = "order.created";
    public static final String TOPIC_INVENTORY_RESERVE_REQUESTED = "inventory.reserve.requested";
    public static final String TOPIC_PAYMENT_REQUESTED = "payment.requested";
    public static final String TOPIC_INVENTORY_RELEASE_REQUESTED = "inventory.release.requested";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public void publishOrderCreated(String message) {
        log.info("Publishing to {}: {}", TOPIC_ORDER_CREATED, message);
        kafkaTemplate.send(TOPIC_ORDER_CREATED, message);
    }

    public void publishInventoryReserveRequested(String message) {
        log.info("Publishing to {}: {}", TOPIC_INVENTORY_RESERVE_REQUESTED, message);
        kafkaTemplate.send(TOPIC_INVENTORY_RESERVE_REQUESTED, message);
    }

    public void publishPaymentRequested(String message) {
        log.info("Publishing to {}: {}", TOPIC_PAYMENT_REQUESTED, message);
        kafkaTemplate.send(TOPIC_PAYMENT_REQUESTED, message);
    }

    public void publishInventoryReleaseRequested(String message) {
        log.info("Publishing compensation to {}: {}", TOPIC_INVENTORY_RELEASE_REQUESTED, message);
        kafkaTemplate.send(TOPIC_INVENTORY_RELEASE_REQUESTED, message);
    }
}
