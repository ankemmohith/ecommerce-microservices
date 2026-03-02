package com.hoangtien2k3.orderservice.service;

import com.hoangtien2k3.orderservice.entity.OrderStatus;
import com.hoangtien2k3.orderservice.exception.wrapper.InvalidOrderStateException;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Service
public class OrderStatusTransitionValidator {

    private final Map<OrderStatus, Set<OrderStatus>> validTransitions;

    public OrderStatusTransitionValidator() {
        validTransitions = new EnumMap<>(OrderStatus.class);

        // Define valid state transitions
        validTransitions.put(OrderStatus.PENDING, EnumSet.of(
                OrderStatus.PAYMENT_CONFIRMED,
                OrderStatus.CANCELLED
        ));

        validTransitions.put(OrderStatus.PAYMENT_CONFIRMED, EnumSet.of(
                OrderStatus.PROCESSING,
                OrderStatus.CANCELLED
        ));

        validTransitions.put(OrderStatus.PROCESSING, EnumSet.of(
                OrderStatus.SHIPPED,
                OrderStatus.CANCELLED
        ));

        validTransitions.put(OrderStatus.SHIPPED, EnumSet.of(
                OrderStatus.DELIVERED,
                OrderStatus.CANCELLED
        ));

        validTransitions.put(OrderStatus.DELIVERED, EnumSet.noneOf(OrderStatus.class));
        validTransitions.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    public void validateTransition(OrderStatus currentStatus, OrderStatus newStatus) {
        if (currentStatus == newStatus) {
            return; // No transition needed
        }

        Set<OrderStatus> allowedTransitions = validTransitions.get(currentStatus);
        if (allowedTransitions == null || !allowedTransitions.contains(newStatus)) {
            throw new InvalidOrderStateException(
                    String.format("Invalid state transition from %s to %s", currentStatus, newStatus)
            );
        }
    }

    public boolean isValidTransition(OrderStatus currentStatus, OrderStatus newStatus) {
        if (currentStatus == newStatus) {
            return true;
        }
        Set<OrderStatus> allowedTransitions = validTransitions.get(currentStatus);
        return allowedTransitions != null && allowedTransitions.contains(newStatus);
    }

}
