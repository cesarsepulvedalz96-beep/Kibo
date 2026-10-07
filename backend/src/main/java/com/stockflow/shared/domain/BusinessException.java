package com.stockflow.shared.domain;

public class BusinessException extends RuntimeException {
    private final int status;

    public BusinessException(String code, int status) {
        super(code);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public static BusinessException notFound() {
        return new BusinessException("NOT_FOUND", 404);
    }
}
