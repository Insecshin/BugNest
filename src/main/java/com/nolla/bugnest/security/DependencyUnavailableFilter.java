package com.nolla.bugnest.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransientConnectionException;

public class DependencyUnavailableFilter extends OncePerRequestFilter {
    private final JsonLoginFailureHandler failureHandler;

    public DependencyUnavailableFilter(JsonLoginFailureHandler failureHandler) {
        this.failureHandler = failureHandler;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // Hold API responses until Spring Session has successfully saved the session.
        ContentCachingResponseWrapper buffered = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(request, buffered);
        } catch (RuntimeException | ServletException | IOException failure) {
            if (!isUnavailable(failure)) throw failure;
            SecurityContextHolder.clearContext();
            response.reset();
            failureHandler.onAuthenticationFailure(request, response, new LoginRateLimitUnavailableException(failure));
            return;
        }
        buffered.copyBodyToResponse();
    }

    private boolean isUnavailable(Throwable failure) {
        boolean redisFailure = false;
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.springframework.data.redis.RedisSystemException) redisFailure = true;
            if (redisFailure && cause instanceof IOException) return true;
            if (cause instanceof DataAccessResourceFailureException
                    || cause instanceof TransientDataAccessResourceException
                    || cause instanceof QueryTimeoutException
                    || cause instanceof SQLTimeoutException
                    || cause instanceof SQLTransientConnectionException
                    || cause instanceof SocketTimeoutException
                    || cause instanceof SocketException) return true;
        }
        return false;
    }
}
