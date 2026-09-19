package com.nolla.bugnest.security;

import com.nolla.bugnest.dto.LoginResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

@Component
public class JsonLoginSuccessHandler implements AuthenticationSuccessHandler {
    private final JsonMapper objectMapper;

    public JsonLoginSuccessHandler(JsonMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        AccountPrincipal principal = (AccountPrincipal) authentication.getPrincipal();
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                new LoginResponse(principal.id(), principal.userName(), principal.nickname()));
    }
}
