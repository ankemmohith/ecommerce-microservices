package com.hoangtien2k3.orderservice.entity;

import lombok.*;

import jakarta.persistence.*;
import java.io.Serial;
import java.time.Instant;

@Entity
@Table(name = "order_saga")
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class OrderSaga {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "saga_id", nullable = false, updatable = false, length = 36)
    private String sagaId;

    @Column(name = "order_id", nullable = false)
    private Integer orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step")
    private SagaStep currentStep;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SagaStatus status;

    @Column(name = "steps_data", columnDefinition = "TEXT")
    private String stepsData;

    @Column(name = "retry_count")
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
