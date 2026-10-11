package com.nolla.bugnest.security;

import org.springframework.security.authentication.AuthenticationServiceException;

public class LoginRateLimitUnavailableException extends AuthenticationServiceException {
    public LoginRateLimitUnavailableException(Throwable cause) {
        super("Authentication infrastructure or lease is unavailable", cause);
    }
}
