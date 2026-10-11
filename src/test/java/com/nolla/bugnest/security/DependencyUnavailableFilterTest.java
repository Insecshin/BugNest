package com.nolla.bugnest.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

class DependencyUnavailableFilterTest {
    private final DependencyUnavailableFilter filter = new DependencyUnavailableFilter(
            new JsonLoginFailureHandler(JsonMapper.builder().build()));

    @Test
    void aSessionSaveFailureReplacesBufferedSuccessAndRemovesItsCookie() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), response, (request, buffered) -> {
            buffered.setContentType("application/json");
            ((HttpServletResponse) buffered).setHeader("Set-Cookie", "session=private-session; Path=/; HttpOnly");
            buffered.getWriter().write("{\"userId\":1}");
            buffered.flushBuffer();
            throw new DataAccessResourceFailureException("private dependency detail");
        });
        assertEquals(503, response.getStatus());
        assertNull(response.getHeader("Set-Cookie"));
        assertTrue(response.getContentAsString().contains("AUTHENTICATION_UNAVAILABLE"));
        assertFalse(response.getContentAsString().contains("userId"));
        assertFalse(response.getContentAsString().contains("private"));
    }

    @Test
    void wrappedTimeoutIsUnavailableButProgrammingErrorsAreNotHidden() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), response, (request, buffered) -> {
            throw new ServletException(new QueryTimeoutException("private query"));
        });
        assertEquals(503, response.getStatus());
        assertThrows(IllegalStateException.class, () -> filter.doFilter(
                new MockHttpServletRequest(), new MockHttpServletResponse(), (request, buffered) -> {
                    throw new IllegalStateException("programming error");
                }));
    }

    @Test
    void successfulResponsesAreCopiedUnchanged() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), response, (request, buffered) -> {
            ((HttpServletResponse) buffered).setStatus(429);
            ((HttpServletResponse) buffered).setHeader("Retry-After", "1");
            buffered.getWriter().write("busy");
        });
        assertEquals(429, response.getStatus());
        assertEquals("1", response.getHeader("Retry-After"));
        assertEquals("busy", response.getContentAsString());
    }
}
