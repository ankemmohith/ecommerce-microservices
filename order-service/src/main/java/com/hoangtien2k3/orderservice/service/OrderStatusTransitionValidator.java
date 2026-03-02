package com.hoangtien2k3.orderservice.service;

import com.hoangtien2k3.orderservice.entity.OrderStatus;
import com.hoangtien2k3.orderservice.exception.wrapper.InvalidOrderStateException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class OrderStatusTransitionValidator {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS =
            new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(OrderStatus.PENDING,
                EnumSet.of(OrderStatus.PAYMENT_CONFIRMED, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.PAYMENT_CONFIRMED,
                EnumSet.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.SHIPPED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED_TRANSITIONS.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    public void validateTransition(OrderStatus currentStatus, OrderStatus nextStatus) {
        if (currentStatus == null) {
            if (nextStatus != OrderStatus.PENDING) {
                throw new InvalidOrderStateException(
                        "Invalid order status transition from null to " + nextStatus);
            }
            return;
        }
        if (currentStatus == nextStatus) {
            return;
        }
        Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, EnumSet.noneOf(OrderStatus.class));
        if (!allowed.contains(nextStatus)) {
            throw new InvalidOrderStateException(
                    "Invalid order status transition from " + currentStatus + " to " + nextStatus);
        }
    }
}
