package com.hoangtien2k3.orderservice.kafka;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class KafkaTopicConstants {

    // Topics published by order-service
    public static final String ORDER_CREATED = "order.created";
    public static final String INVENTORY_RESERVE_REQUESTED = "inventory.reserve.requested";
    public static final String INVENTORY_RELEASE_REQUESTED = "inventory.release.requested";
    public static final String PAYMENT_REQUESTED = "payment.requested";
    public static final String ORDER_NOTIFICATION_REQUESTED = "order.notification.requested";

    // Topics consumed by order-service
    public static final String INVENTORY_RESERVED = "inventory.reserved";
    public static final String INVENTORY_FAILED = "inventory.failed";
    public static final String PAYMENT_COMPLETED = "payment.completed";
    public static final String PAYMENT_FAILED = "payment.failed";
}
