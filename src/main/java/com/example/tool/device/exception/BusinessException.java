package com.example.tool.device.exception;

/**
 * Base exception for all custom business errors.
 * Carries an optional {@code code} so callers can return a stable error code to clients
 * while keeping the default constructor simple ({@code new BusinessException(msg)}).
 */
public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(String message) {
        super(message);
        this.code = null;
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.code = null;
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(Integer code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }
}
