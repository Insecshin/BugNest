package com.nolla.bugnest.exception;

public class AccountConflictException extends RuntimeException {
    public AccountConflictException(String message) {
        super(message);
    }

    public AccountConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
