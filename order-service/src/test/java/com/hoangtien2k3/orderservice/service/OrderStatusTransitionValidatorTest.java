package com.hoangtien2k3.orderservice.service;

import com.hoangtien2k3.orderservice.entity.OrderStatus;
import com.hoangtien2k3.orderservice.exception.wrapper.InvalidOrderStateException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderStatusTransitionValidatorTest {

    private final OrderStatusTransitionValidator validator = new OrderStatusTransitionValidator();

    @Test
    void validateTransition_allowsValidTransitions() {
        assertDoesNotThrow(() ->
                validator.validateTransition(OrderStatus.PENDING, OrderStatus.PAYMENT_CONFIRMED));
        assertDoesNotThrow(() ->
                validator.validateTransition(OrderStatus.PAYMENT_CONFIRMED, OrderStatus.SHIPPED));
        assertDoesNotThrow(() ->
                validator.validateTransition(OrderStatus.PAYMENT_CONFIRMED, OrderStatus.CANCELLED));
    }

    @Test
    void validateTransition_rejectsInvalidTransitions() {
        assertThrows(InvalidOrderStateException.class, () ->
                validator.validateTransition(OrderStatus.SHIPPED, OrderStatus.PAYMENT_CONFIRMED));
        assertThrows(InvalidOrderStateException.class, () ->
                validator.validateTransition(OrderStatus.CANCELLED, OrderStatus.SHIPPED));
    }
}
