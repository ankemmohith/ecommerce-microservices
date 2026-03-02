package com.hoangtien2k3.orderservice.exception.wrapper;

public class InvalidOrderStateException extends RuntimeException {
    public InvalidOrderStateException(String message) {
        super(message);
    }
}
