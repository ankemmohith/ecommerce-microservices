package com.hoangtien2k3.orderservice.repository;

import com.hoangtien2k3.orderservice.entity.OrderSaga;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderSagaRepository extends JpaRepository<OrderSaga, String> {
    Optional<OrderSaga> findByOrderId(Integer orderId);
}
