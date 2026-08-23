package com.example.tool.device.exception;

import java.io.Serial;

public class MACAnalysisException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;
    private final Integer code;

    public MACAnalysisException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public MACAnalysisException(Integer code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }
}
