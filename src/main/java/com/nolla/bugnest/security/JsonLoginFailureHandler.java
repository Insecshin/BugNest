package com.nolla.bugnest.security;

import com.nolla.bugnest.dto.AuthenticationErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

@Component
public class JsonLoginFailureHandler implements AuthenticationFailureHandler {
    private static final Logger logger = LoggerFactory.getLogger(JsonLoginFailureHandler.class);

    private final JsonMapper objectMapper;

    public JsonLoginFailureHandler(JsonMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        if (exception instanceof LoginRateLimitedException limited) {
            response.setHeader("Retry-After", Long.toString(limited.retryAfterSeconds()));
            write(response, 429,
                    "RATE_LIMITED", "请求过于频繁，请稍后重试");
        } else if (exception instanceof LoginRateLimitUnavailableException) {
            logger.error("Login rate limiting is unavailable", exception);
            write(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "AUTHENTICATION_UNAVAILABLE", "登录服务暂不可用");
        } else if (exception instanceof InvalidLoginRequestException) {
            write(response, HttpServletResponse.SC_BAD_REQUEST,
                    "INVALID_REQUEST", "登录请求格式无效");
        } else {
            write(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "AUTHENTICATION_FAILED", "用户名或密码错误");
        }
    }

    private void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), new AuthenticationErrorResponse(code, message));
    }
}
