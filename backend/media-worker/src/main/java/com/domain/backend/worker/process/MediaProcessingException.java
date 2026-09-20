package com.domain.backend.worker.process;

public class MediaProcessingException extends RuntimeException {

    private final String errorCode;
    private final boolean retryable;

    public MediaProcessingException(String errorCode, String message, boolean retryable) {
        super(message);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
