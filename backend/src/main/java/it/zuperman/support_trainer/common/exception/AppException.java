package it.zuperman.support_trainer.common.exception;

import org.springframework.http.HttpStatus;

public class AppException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public AppException(HttpStatus status, String message) {
        super(message);
        this.status = status;
        this.errorCode = status.name();
    }

    public AppException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
