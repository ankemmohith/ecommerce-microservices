package com.hoangtien2k3.orderservice;

import com.hoangtien2k3.orderservice.entity.OrderStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class OrderStatusTransitionTest {

    @Test
    void pendingCanTransitionToPaymentConfirmed() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.PAYMENT_CONFIRMED)).isTrue();
    }

    @Test
    void pendingCanTransitionToPaymentFailed() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.PAYMENT_FAILED)).isTrue();
    }

    @Test
    void pendingCanTransitionToCancelled() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
    }

    @Test
    void pendingCannotTransitionDirectlyToShipped() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.SHIPPED)).isFalse();
    }

    @Test
    void pendingCannotTransitionDirectlyToDelivered() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.DELIVERED)).isFalse();
    }

    @Test
    void paymentConfirmedCanTransitionToShipped() {
        assertThat(OrderStatus.PAYMENT_CONFIRMED.canTransitionTo(OrderStatus.SHIPPED)).isTrue();
    }

    @Test
    void paymentConfirmedCanTransitionToCancelled() {
        assertThat(OrderStatus.PAYMENT_CONFIRMED.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
    }

    @Test
    void paymentConfirmedCannotTransitionToPending() {
        assertThat(OrderStatus.PAYMENT_CONFIRMED.canTransitionTo(OrderStatus.PENDING)).isFalse();
    }

    @Test
    void paymentFailedCanOnlyTransitionToCancelled() {
        assertThat(OrderStatus.PAYMENT_FAILED.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.PAYMENT_FAILED.canTransitionTo(OrderStatus.PENDING)).isFalse();
        assertThat(OrderStatus.PAYMENT_FAILED.canTransitionTo(OrderStatus.SHIPPED)).isFalse();
    }

    @Test
    void shippedCanTransitionToDelivered() {
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.DELIVERED)).isTrue();
    }

    @Test
    void shippedCannotTransitionToCancelled() {
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
    }

    @Test
    void deliveredCanTransitionToRefunded() {
        assertThat(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.REFUNDED)).isTrue();
    }

    @Test
    void cancelledIsTerminalState() {
        assertThat(OrderStatus.CANCELLED.validTransitions()).isEmpty();
        assertThat(OrderStatus.CANCELLED.canTransitionTo(OrderStatus.PENDING)).isFalse();
    }

    @Test
    void refundedIsTerminalState() {
        assertThat(OrderStatus.REFUNDED.validTransitions()).isEmpty();
        assertThat(OrderStatus.REFUNDED.canTransitionTo(OrderStatus.PENDING)).isFalse();
    }

    @Test
    void allStatusesHaveValidTransitionsDefined() {
        for (OrderStatus status : OrderStatus.values()) {
            assertThat(status.validTransitions()).isNotNull();
        }
    }
}
