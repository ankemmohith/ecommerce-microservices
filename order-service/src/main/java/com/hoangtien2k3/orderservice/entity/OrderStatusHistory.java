package com.hoangtien2k3.orderservice.entity;

import lombok.*;

import jakarta.persistence.*;
import java.io.Serial;
import java.time.Instant;

@Entity
@Table(name = "order_status_history")
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class OrderStatusHistory {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Integer orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private OrderStatus toStatus;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "changed_by")
    private String changedBy;

    @Column(name = "reason", length = 500)
    private String reason;
}
