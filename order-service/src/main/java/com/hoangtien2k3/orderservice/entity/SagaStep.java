package com.hoangtien2k3.orderservice.entity;

public enum SagaStep {
    RESERVE_INVENTORY,
    PROCESS_PAYMENT,
    UPDATE_ORDER_STATUS,
    NOTIFY_CUSTOMER
}
