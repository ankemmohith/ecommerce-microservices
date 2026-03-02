package com.hoangtien2k3.orderservice.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import lombok.*;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_status_history")
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class OrderStatusHistory implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Integer orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status")
    private OrderStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false)
    private OrderStatus newStatus;

    @JsonFormat(shape = Shape.STRING)
    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "triggered_by", nullable = false)
    private TriggeredBy triggeredBy;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

}
