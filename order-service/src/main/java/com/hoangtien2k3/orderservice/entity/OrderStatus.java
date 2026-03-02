package com.hoangtien2k3.orderservice.entity;

import java.util.EnumSet;
import java.util.Set;

public enum OrderStatus {

    PENDING {
        @Override
        public Set<OrderStatus> validTransitions() {
            return EnumSet.of(PAYMENT_CONFIRMED, PAYMENT_FAILED, CANCELLED);
        }
    },
    PAYMENT_CONFIRMED {
        @Override
        public Set<OrderStatus> validTransitions() {
            return EnumSet.of(SHIPPED, CANCELLED);
        }
    },
    PAYMENT_FAILED {
        @Override
        public Set<OrderStatus> validTransitions() {
            return EnumSet.of(CANCELLED);
        }
    },
    SHIPPED {
        @Override
        public Set<OrderStatus> validTransitions() {
            return EnumSet.of(DELIVERED);
        }
    },
    DELIVERED {
        @Override
        public Set<OrderStatus> validTransitions() {
            return EnumSet.of(REFUNDED);
        }
    },
    CANCELLED {
        @Override
        public Set<OrderStatus> validTransitions() {
            return EnumSet.noneOf(OrderStatus.class);
        }
    },
    REFUNDED {
        @Override
        public Set<OrderStatus> validTransitions() {
            return EnumSet.noneOf(OrderStatus.class);
        }
    };

    public abstract Set<OrderStatus> validTransitions();

    public boolean canTransitionTo(OrderStatus target) {
        return validTransitions().contains(target);
    }
}
