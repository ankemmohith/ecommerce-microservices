package com.hoangtien2k3.orderservice.dto.order;

import com.hoangtien2k3.orderservice.entity.OrderStatus;
import com.hoangtien2k3.orderservice.entity.OrderStatusTrigger;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class OrderStatusHistoryDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Integer orderId;
    private OrderStatus previousStatus;
    private OrderStatus newStatus;
    private Instant changedAt;
    private OrderStatusTrigger triggeredBy;
    private String metadata;
}
