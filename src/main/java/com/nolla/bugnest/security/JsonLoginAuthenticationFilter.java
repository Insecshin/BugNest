package com.nolla.bugnest.security;

import com.nolla.bugnest.dto.LoginRequest;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

public class JsonLoginAuthenticationFilter extends AbstractAuthenticationProcessingFilter {
    private final JsonMapper objectMapper;
    private final LoginRateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;

    public JsonLoginAuthenticationFilter(
            JsonMapper objectMapper,
            LoginRateLimiter rateLimiter,
            ClientIpResolver clientIpResolver
    ) {
        super(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/auth/login"));
        this.objectMapper = objectMapper;
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    public Authentication attemptAuthentication(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException, ServletException {
        rateLimiter.consumeIpToken(clientIpResolver.resolve(request));

        String contentType = request.getContentType();
        if (contentType == null || !isJsonContentType(contentType)) {
            throw new InvalidLoginRequestException("Content-Type must be application/json");
        }

        LoginRequest loginRequest;
        try {
            byte[] body = request.getInputStream().readNBytes(8193);
            if (body.length > 8192) {
                throw new InvalidLoginRequestException("Login request is too large");
            }
            loginRequest = objectMapper.readValue(body, LoginRequest.class);
        } catch (JacksonException exception) {
            throw new InvalidLoginRequestException("Login request is invalid", exception);
        }

        if (loginRequest == null
                || loginRequest.identifier() == null || loginRequest.identifier().isBlank()
                || loginRequest.identifier().length() > 254
                || loginRequest.password() == null
                || loginRequest.password().codePointCount(0, loginRequest.password().length()) > 1024) {
            throw new InvalidLoginRequestException("Identifier and password are required");
        }

        return getAuthenticationManager().authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        loginRequest.identifier(),
                        loginRequest.password()
                )
        );
    }

    private static boolean isJsonContentType(String contentType) {
        try {
            MediaType mediaType = MediaType.parseMediaType(contentType);
            return "application".equalsIgnoreCase(mediaType.getType())
                    && "json".equalsIgnoreCase(mediaType.getSubtype());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
