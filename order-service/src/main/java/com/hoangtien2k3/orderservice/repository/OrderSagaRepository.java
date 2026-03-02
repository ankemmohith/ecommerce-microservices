package com.hoangtien2k3.orderservice.repository;

import com.hoangtien2k3.orderservice.entity.OrderSaga;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderSagaRepository extends JpaRepository<OrderSaga, String> {

    Optional<OrderSaga> findTopByOrderIdOrderByCreatedAtDesc(Integer orderId);
}
