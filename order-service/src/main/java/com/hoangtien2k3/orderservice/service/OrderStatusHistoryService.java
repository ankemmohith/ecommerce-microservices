package com.hoangtien2k3.orderservice.service;

import com.hoangtien2k3.orderservice.entity.OrderStatus;
import com.hoangtien2k3.orderservice.entity.OrderStatusHistory;
import com.hoangtien2k3.orderservice.entity.TriggeredBy;
import com.hoangtien2k3.orderservice.repository.OrderStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class OrderStatusHistoryService {

    private final OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Transactional
    public OrderStatusHistory recordStatusChange(Integer orderId, OrderStatus previousStatus, 
                                                  OrderStatus newStatus, TriggeredBy triggeredBy, 
                                                  String metadata) {
        log.info("Recording status change for order {}: {} -> {}", orderId, previousStatus, newStatus);
        
        OrderStatusHistory history = OrderStatusHistory.builder()
                .orderId(orderId)
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .changedAt(LocalDateTime.now())
                .triggeredBy(triggeredBy)
                .metadata(metadata)
                .build();

        return orderStatusHistoryRepository.save(history);
    }

    public List<OrderStatusHistory> getOrderStatusHistory(Integer orderId) {
        log.info("Fetching status history for order {}", orderId);
        return orderStatusHistoryRepository.findByOrderIdOrderByChangedAtAsc(orderId);
    }

}
