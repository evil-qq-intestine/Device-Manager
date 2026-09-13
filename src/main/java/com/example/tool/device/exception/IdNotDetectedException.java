package com.example.tool.device.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Raised when a referenced entity (device, monitor, etc.) cannot be found.
 * Maps to HTTP 404 via {@link ResponseStatus}.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class IdNotDetectedException extends BusinessException {

    public IdNotDetectedException(String message) {
        super(message);
    }

    public IdNotDetectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
