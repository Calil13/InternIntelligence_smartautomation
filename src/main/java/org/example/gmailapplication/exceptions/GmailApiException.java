package org.example.gmailapplication.exceptions;

import org.springframework.http.HttpStatus;

public class GmailApiException extends RuntimeException {
    private final HttpStatus status;

    public GmailApiException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public GmailApiException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
