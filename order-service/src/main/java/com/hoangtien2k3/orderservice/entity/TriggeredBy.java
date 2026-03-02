package com.hoangtien2k3.orderservice.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum TriggeredBy {

    SYSTEM("system"),
    USER("user"),
    SAGA("saga");

    private final String source;

}
