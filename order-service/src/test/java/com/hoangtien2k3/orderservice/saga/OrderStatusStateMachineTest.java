package com.hoangtien2k3.orderservice.saga;

import com.hoangtien2k3.orderservice.entity.OrderStatus;
import com.hoangtien2k3.orderservice.exception.wrapper.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class OrderStatusStateMachineTest {

    // ---- valid transitions ----

    @Test
    void pending_can_transition_to_inventoryReserved() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.INVENTORY_RESERVED)).isTrue();
    }

    @Test
    void pending_can_transition_to_inventoryFailed() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.INVENTORY_FAILED)).isTrue();
    }

    @Test
    void pending_can_transition_to_cancelled() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
    }

    @Test
    void inventoryReserved_can_transition_to_paymentConfirmed() {
        assertThat(OrderStatus.INVENTORY_RESERVED.canTransitionTo(OrderStatus.PAYMENT_CONFIRMED)).isTrue();
    }

    @Test
    void inventoryReserved_can_transition_to_paymentFailed() {
        assertThat(OrderStatus.INVENTORY_RESERVED.canTransitionTo(OrderStatus.PAYMENT_FAILED)).isTrue();
    }

    @Test
    void inventoryReserved_can_transition_to_cancelled() {
        assertThat(OrderStatus.INVENTORY_RESERVED.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
    }

    @Test
    void paymentConfirmed_can_transition_to_processing() {
        assertThat(OrderStatus.PAYMENT_CONFIRMED.canTransitionTo(OrderStatus.PROCESSING)).isTrue();
    }

    @Test
    void paymentConfirmed_can_transition_to_cancelled() {
        assertThat(OrderStatus.PAYMENT_CONFIRMED.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
    }

    @Test
    void processing_can_transition_to_shipped() {
        assertThat(OrderStatus.PROCESSING.canTransitionTo(OrderStatus.SHIPPED)).isTrue();
    }

    @Test
    void processing_can_transition_to_cancelled() {
        assertThat(OrderStatus.PROCESSING.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
    }

    @Test
    void shipped_can_transition_to_delivered() {
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.DELIVERED)).isTrue();
    }

    @Test
    void paymentFailed_can_transition_to_cancelled() {
        assertThat(OrderStatus.PAYMENT_FAILED.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
    }

    @Test
    void inventoryFailed_can_transition_to_cancelled() {
        assertThat(OrderStatus.INVENTORY_FAILED.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
    }

    // ---- invalid transitions ----

    @Test
    void pending_cannot_transition_directly_to_shipped() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.SHIPPED)).isFalse();
    }

    @Test
    void pending_cannot_transition_directly_to_delivered() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.DELIVERED)).isFalse();
    }

    @Test
    void pending_cannot_transition_directly_to_processing() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.PROCESSING)).isFalse();
    }

    @Test
    void pending_cannot_transition_directly_to_paymentConfirmed() {
        // Payment happens after inventory reservation in the saga flow
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.PAYMENT_CONFIRMED)).isFalse();
    }

    @Test
    void delivered_is_terminal_no_transitions_allowed() {
        for (OrderStatus target : OrderStatus.values()) {
            assertThat(OrderStatus.DELIVERED.canTransitionTo(target))
                    .as("DELIVERED should not transition to " + target)
                    .isFalse();
        }
    }

    @Test
    void cancelled_is_terminal_no_transitions_allowed() {
        for (OrderStatus target : OrderStatus.values()) {
            assertThat(OrderStatus.CANCELLED.canTransitionTo(target))
                    .as("CANCELLED should not transition to " + target)
                    .isFalse();
        }
    }

    @Test
    void shipped_cannot_transition_to_cancelled() {
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
    }

    @Test
    void shipped_cannot_transition_back_to_processing() {
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.PROCESSING)).isFalse();
    }

    @Test
    void inventoryReserved_cannot_transition_back_to_pending() {
        assertThat(OrderStatus.INVENTORY_RESERVED.canTransitionTo(OrderStatus.PENDING)).isFalse();
    }

    @Test
    void paymentFailed_cannot_go_to_processing() {
        assertThat(OrderStatus.PAYMENT_FAILED.canTransitionTo(OrderStatus.PROCESSING)).isFalse();
    }

    // ---- allowedTransitions content ----

    @Test
    void pending_allowedTransitions_contains_expected_statuses() {
        assertThat(OrderStatus.PENDING.allowedTransitions())
                .containsExactlyInAnyOrder(
                        OrderStatus.INVENTORY_RESERVED,
                        OrderStatus.INVENTORY_FAILED,
                        OrderStatus.CANCELLED);
    }

    @Test
    void inventoryReserved_allowedTransitions_contains_expected_statuses() {
        assertThat(OrderStatus.INVENTORY_RESERVED.allowedTransitions())
                .containsExactlyInAnyOrder(
                        OrderStatus.PAYMENT_CONFIRMED,
                        OrderStatus.PAYMENT_FAILED,
                        OrderStatus.CANCELLED);
    }

    @Test
    void delivered_allowedTransitions_is_empty() {
        assertThat(OrderStatus.DELIVERED.allowedTransitions()).isEmpty();
    }

    // ---- service-level validation helper ----

    @Test
    void validateTransition_throwsInvalidStateTransitionException_for_invalid_transition() {
        assertThatThrownBy(() -> validateTransition(OrderStatus.PENDING, OrderStatus.SHIPPED))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("PENDING")
                .hasMessageContaining("SHIPPED");
    }

    @Test
    void validateTransition_doesNotThrow_for_valid_transition() {
        assertThatCode(() -> validateTransition(OrderStatus.PENDING, OrderStatus.INVENTORY_RESERVED))
                .doesNotThrowAnyException();
    }

    private void validateTransition(OrderStatus from, OrderStatus to) {
        if (!from.canTransitionTo(to)) {
            throw new InvalidStateTransitionException(
                    String.format("Cannot transition from %s to %s", from, to));
        }
    }
}
