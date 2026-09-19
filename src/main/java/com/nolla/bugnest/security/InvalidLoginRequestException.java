package com.nolla.bugnest.security;

import org.springframework.security.core.AuthenticationException;

public class InvalidLoginRequestException extends AuthenticationException {
    public InvalidLoginRequestException(String message) {
        super(message);
    }

    public InvalidLoginRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
