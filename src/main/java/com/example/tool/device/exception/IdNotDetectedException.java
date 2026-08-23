package com.example.tool.device.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class IdNotDetectedException extends RuntimeException {
    public IdNotDetectedException(String message) {
        super(message);
    }
}
