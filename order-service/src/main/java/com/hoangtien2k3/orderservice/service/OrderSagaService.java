package com.hoangtien2k3.orderservice.service;

import com.hoangtien2k3.orderservice.dto.saga.OrderSagaDto;

public interface OrderSagaService {

    OrderSagaDto startSaga(Integer orderId);

    OrderSagaDto getSaga(String sagaId);

    void handleInventoryReserved(String sagaId, String messageId);

    void handleInventoryFailed(String sagaId, String messageId, String reason);

    void handlePaymentCompleted(String sagaId, String messageId);

    void handlePaymentFailed(String sagaId, String messageId, String reason);

    OrderSagaDto retrySaga(Integer orderId);
}
