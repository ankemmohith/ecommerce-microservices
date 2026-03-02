package com.hoangtien2k3.orderservice.dto.saga;

import com.hoangtien2k3.orderservice.entity.SagaStatus;
import com.hoangtien2k3.orderservice.entity.SagaStep;
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
public class OrderSagaDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String sagaId;
    private Integer orderId;
    private SagaStep currentStep;
    private SagaStatus status;
    private String stepsData;
    private int retryCount;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;
}
