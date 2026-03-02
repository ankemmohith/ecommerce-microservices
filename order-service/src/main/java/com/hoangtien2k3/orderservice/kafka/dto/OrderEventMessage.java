package com.hoangtien2k3.orderservice.kafka.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class OrderEventMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String messageId;
    private String sagaId;
    private Integer orderId;
    private String eventType;
    private String payload;
    private String timestamp;
}
