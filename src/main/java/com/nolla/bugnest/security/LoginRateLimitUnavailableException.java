package com.nolla.bugnest.security;

import org.springframework.security.authentication.AuthenticationServiceException;

public class LoginRateLimitUnavailableException extends AuthenticationServiceException {
    public LoginRateLimitUnavailableException(Throwable cause) {
        super("Login rate limiting is unavailable", cause);
    }
}
