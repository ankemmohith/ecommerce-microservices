package com.hoangtien2k3.orderservice.entity;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum OrderStatus {

    PENDING,
    PAYMENT_CONFIRMED,
    PAYMENT_FAILED,
    INVENTORY_RESERVED,
    INVENTORY_FAILED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS;

    static {
        Map<OrderStatus, Set<OrderStatus>> transitions = new EnumMap<>(OrderStatus.class);
        transitions.put(PENDING, EnumSet.of(INVENTORY_RESERVED, INVENTORY_FAILED, CANCELLED));
        transitions.put(INVENTORY_RESERVED, EnumSet.of(PAYMENT_CONFIRMED, PAYMENT_FAILED, CANCELLED));
        transitions.put(PAYMENT_CONFIRMED, EnumSet.of(PROCESSING, CANCELLED));
        transitions.put(PAYMENT_FAILED, EnumSet.of(CANCELLED));
        transitions.put(INVENTORY_FAILED, EnumSet.of(CANCELLED));
        transitions.put(PROCESSING, EnumSet.of(SHIPPED, CANCELLED));
        transitions.put(SHIPPED, EnumSet.of(DELIVERED));
        transitions.put(DELIVERED, Collections.emptySet());
        transitions.put(CANCELLED, Collections.emptySet());
        VALID_TRANSITIONS = Collections.unmodifiableMap(transitions);
    }

    public boolean canTransitionTo(OrderStatus newStatus) {
        return VALID_TRANSITIONS.getOrDefault(this, Collections.emptySet()).contains(newStatus);
    }

    public Set<OrderStatus> allowedTransitions() {
        return VALID_TRANSITIONS.getOrDefault(this, Collections.emptySet());
    }
}
