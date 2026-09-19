package com.nolla.bugnest.security;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class JsonLoginFailureHandlerTest {
    @Test
    void returnsGenericServiceUnavailableWhenRedisIsDown() throws Exception {
        JsonLoginFailureHandler handler = new JsonLoginFailureHandler(JsonMapper.builder().build());
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(
                new MockHttpServletRequest(),
                response,
                new LoginRateLimitUnavailableException(new RedisConnectionFailureException("redis down"))
        );

        assertEquals(503, response.getStatus());
        assertFalse(response.getContentAsString().contains("redis down"));
    }
}
