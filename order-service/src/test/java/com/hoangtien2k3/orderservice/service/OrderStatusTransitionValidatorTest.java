package com.hoangtien2k3.orderservice.service;

import com.hoangtien2k3.orderservice.entity.OrderStatus;
import com.hoangtien2k3.orderservice.exception.wrapper.InvalidOrderStateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderStatusTransitionValidatorTest {

    private OrderStatusTransitionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new OrderStatusTransitionValidator();
    }

    @Test
    void testValidTransitionFromPendingToPaymentConfirmed() {
        assertDoesNotThrow(() -> 
            validator.validateTransition(OrderStatus.PENDING, OrderStatus.PAYMENT_CONFIRMED)
        );
        assertTrue(validator.isValidTransition(OrderStatus.PENDING, OrderStatus.PAYMENT_CONFIRMED));
    }

    @Test
    void testValidTransitionFromPendingToCancelled() {
        assertDoesNotThrow(() -> 
            validator.validateTransition(OrderStatus.PENDING, OrderStatus.CANCELLED)
        );
        assertTrue(validator.isValidTransition(OrderStatus.PENDING, OrderStatus.CANCELLED));
    }

    @Test
    void testValidTransitionFromPaymentConfirmedToProcessing() {
        assertDoesNotThrow(() -> 
            validator.validateTransition(OrderStatus.PAYMENT_CONFIRMED, OrderStatus.PROCESSING)
        );
        assertTrue(validator.isValidTransition(OrderStatus.PAYMENT_CONFIRMED, OrderStatus.PROCESSING));
    }

    @Test
    void testValidTransitionFromProcessingToShipped() {
        assertDoesNotThrow(() -> 
            validator.validateTransition(OrderStatus.PROCESSING, OrderStatus.SHIPPED)
        );
        assertTrue(validator.isValidTransition(OrderStatus.PROCESSING, OrderStatus.SHIPPED));
    }

    @Test
    void testValidTransitionFromShippedToDelivered() {
        assertDoesNotThrow(() -> 
            validator.validateTransition(OrderStatus.SHIPPED, OrderStatus.DELIVERED)
        );
        assertTrue(validator.isValidTransition(OrderStatus.SHIPPED, OrderStatus.DELIVERED));
    }

    @Test
    void testInvalidTransitionFromPendingToShipped() {
        InvalidOrderStateException exception = assertThrows(
            InvalidOrderStateException.class,
            () -> validator.validateTransition(OrderStatus.PENDING, OrderStatus.SHIPPED)
        );
        assertTrue(exception.getMessage().contains("Invalid state transition from PENDING to SHIPPED"));
        assertFalse(validator.isValidTransition(OrderStatus.PENDING, OrderStatus.SHIPPED));
    }

    @Test
    void testInvalidTransitionFromDeliveredToCancelled() {
        InvalidOrderStateException exception = assertThrows(
            InvalidOrderStateException.class,
            () -> validator.validateTransition(OrderStatus.DELIVERED, OrderStatus.CANCELLED)
        );
        assertTrue(exception.getMessage().contains("Invalid state transition from DELIVERED to CANCELLED"));
        assertFalse(validator.isValidTransition(OrderStatus.DELIVERED, OrderStatus.CANCELLED));
    }

    @Test
    void testInvalidTransitionFromCancelledToAnyState() {
        InvalidOrderStateException exception = assertThrows(
            InvalidOrderStateException.class,
            () -> validator.validateTransition(OrderStatus.CANCELLED, OrderStatus.PENDING)
        );
        assertTrue(exception.getMessage().contains("Invalid state transition from CANCELLED"));
        assertFalse(validator.isValidTransition(OrderStatus.CANCELLED, OrderStatus.PENDING));
    }

    @Test
    void testSameStateTransition() {
        // Same state should be allowed (no-op)
        assertDoesNotThrow(() -> 
            validator.validateTransition(OrderStatus.PENDING, OrderStatus.PENDING)
        );
        assertTrue(validator.isValidTransition(OrderStatus.PENDING, OrderStatus.PENDING));
    }

    @Test
    void testValidTransitionChain() {
        // Test a complete valid transition chain
        assertDoesNotThrow(() -> {
            validator.validateTransition(OrderStatus.PENDING, OrderStatus.PAYMENT_CONFIRMED);
            validator.validateTransition(OrderStatus.PAYMENT_CONFIRMED, OrderStatus.PROCESSING);
            validator.validateTransition(OrderStatus.PROCESSING, OrderStatus.SHIPPED);
            validator.validateTransition(OrderStatus.SHIPPED, OrderStatus.DELIVERED);
        });
    }

    @Test
    void testCancellationFromMultipleStates() {
        // Cancellation should be valid from multiple states
        assertDoesNotThrow(() -> {
            validator.validateTransition(OrderStatus.PENDING, OrderStatus.CANCELLED);
            validator.validateTransition(OrderStatus.PAYMENT_CONFIRMED, OrderStatus.CANCELLED);
            validator.validateTransition(OrderStatus.PROCESSING, OrderStatus.CANCELLED);
            validator.validateTransition(OrderStatus.SHIPPED, OrderStatus.CANCELLED);
        });
    }

}
