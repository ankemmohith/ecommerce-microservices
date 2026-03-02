package com.hoangtien2k3.orderservice.exception.wrapper;

import java.io.Serial;

public class InvalidOrderStateException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidOrderStateException(String message) {
        super(message);
    }
}
