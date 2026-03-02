package com.hoangtien2k3.orderservice.service;

import com.hoangtien2k3.orderservice.entity.OrderSaga;
import com.hoangtien2k3.orderservice.entity.OrderStatusHistory;
import com.hoangtien2k3.orderservice.kafka.dto.OrderEventMessage;

import java.util.List;

public interface SagaOrchestrationService {

    OrderSaga startSaga(Integer orderId);

    OrderSaga retrySaga(Integer orderId);

    OrderSaga getSagaBySagaId(String sagaId);

    List<OrderStatusHistory> getStatusHistory(Integer orderId);

    void handleInventoryReserved(OrderEventMessage event);

    void handleInventoryFailed(OrderEventMessage event);

    void handlePaymentCompleted(OrderEventMessage event);

    void handlePaymentFailed(OrderEventMessage event);
}
