package com.example.tool.device.exception;

/**
 * Raised when a MAC address cannot be parsed or is missing.
 * Inherits the {@code code} field from {@link BusinessException}.
 */
public class MACAnalysisException extends BusinessException {

    public MACAnalysisException(Integer code, String message) {
        super(code, message);
    }

    public MACAnalysisException(Integer code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
