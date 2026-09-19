package com.nolla.bugnest.security;

import org.springframework.security.core.AuthenticationException;

public class LoginRateLimitedException extends AuthenticationException {
    private final long retryAfterSeconds;

    public LoginRateLimitedException(long retryAfterSeconds) {
        super("Login rate limit exceeded");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
