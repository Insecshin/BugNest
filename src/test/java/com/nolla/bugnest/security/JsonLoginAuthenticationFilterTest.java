package com.nolla.bugnest.security;

import com.nolla.bugnest.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class JsonLoginAuthenticationFilterTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final AuthenticationManager manager = mock(AuthenticationManager.class);
    private final JsonLoginAuthenticationFilter filter = new JsonLoginAuthenticationFilter(
            mapper, mock(LoginRateLimiter.class), mock(ClientIpResolver.class));

    @Test
    void emptyPasswordReturnsBadRequestWithoutAuthentication() throws Exception {
        filter.setAuthenticationManager(manager);
        filter.setAuthenticationFailureHandler(new JsonLoginFailureHandler(mapper));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request(""), response, new MockFilterChain());

        assertEquals(400, response.getStatus());
        assertEquals("INVALID_REQUEST", mapper.readTree(response.getContentAsString()).get("code").asText());
        verifyNoInteractions(manager);
    }

    @Test
    void spacesArePreservedAndPassedToAuthentication() throws Exception {
        filter.setAuthenticationManager(manager);
        String password = " ".repeat(15);

        filter.attemptAuthentication(request(password), new MockHttpServletResponse());

        ArgumentCaptor<Authentication> attempt = ArgumentCaptor.forClass(Authentication.class);
        verify(manager).authenticate(attempt.capture());
        assertEquals(password, attempt.getValue().getCredentials());
    }

    private MockHttpServletRequest request(String password) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setServletPath("/auth/login");
        request.setContentType("application/json");
        request.setContent(mapper.writeValueAsBytes(new LoginRequest("test_user", password)));
        return request;
    }
}
