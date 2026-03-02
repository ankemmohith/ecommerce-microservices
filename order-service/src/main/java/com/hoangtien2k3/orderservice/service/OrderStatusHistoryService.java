package com.hoangtien2k3.orderservice.service;

import com.hoangtien2k3.orderservice.entity.Order;
import com.hoangtien2k3.orderservice.entity.OrderStatus;
import com.hoangtien2k3.orderservice.entity.OrderStatusHistory;
import com.hoangtien2k3.orderservice.entity.OrderStatusTrigger;
import com.hoangtien2k3.orderservice.repository.OrderStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class OrderStatusHistoryService {

    private final OrderStatusHistoryRepository orderStatusHistoryRepository;

    public OrderStatusHistory saveHistory(Order order,
                                          OrderStatus previousStatus,
                                          OrderStatus newStatus,
                                          OrderStatusTrigger triggeredBy,
                                          String metadata) {
        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(order)
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .changedAt(Instant.now())
                .triggeredBy(triggeredBy)
                .metadata(metadata)
                .build();
        return orderStatusHistoryRepository.save(history);
    }
}
