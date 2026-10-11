package com.nolla.bugnest.controller;

import com.nolla.bugnest.config.WebSecurityConfiguration;
import com.nolla.bugnest.dto.CsrfResponse;
import com.nolla.bugnest.dto.LoginResponse;
import com.nolla.bugnest.security.*;
import com.nolla.bugnest.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({FrontendController.class, AccountController.class, CsrfController.class})
@Import({WebSecurityConfiguration.class, FrontendSecurityTest.TestSecurity.class})
class FrontendSecurityTest {
    @TestConfiguration
    @EnableWebSecurity
    static class TestSecurity { }

    @Autowired MockMvc mvc;
    @Autowired JsonMapper mapper;
    @MockitoBean AccountService accounts;
    @MockitoBean AccountAuthenticationProvider provider;
    @MockitoBean PasswordEncoder passwordEncoder;
    @MockitoBean LoginRateLimiter rateLimiter;
    @MockitoBean ClientIpResolver clientIpResolver;
    @MockitoBean JsonLoginSuccessHandler successHandler;
    @MockitoBean JsonLoginFailureHandler failureHandler;

    @Test
    void frontendRoutesArePublicAndForwardToTheEntryWithoutOpeningApis() throws Exception {
        for (String path : List.of("/", "/issues", "/settings", "/login", "/register")) {
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        }
        mvc.perform(get("/assets/missing.js")).andExpect(status().isNotFound());
        mvc.perform(get("/accounts/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/private")).andExpect(status().isUnauthorized());
        mvc.perform(post("/settings")).andExpect(status().isForbidden());
    }

    @Test
    void currentAccountReadsTheAuthenticatedIdAndReturnsOnlyPublicFields() throws Exception {
        when(accounts.currentAccount(42L)).thenReturn(new LoginResponse(42L, "profile", "最新昵称"));
        mvc.perform(get("/accounts/me").session(authenticatedSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.userName").value("profile"))
                .andExpect(jsonPath("$.nickname").value("最新昵称"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void logoutRequiresCsrfAndInvalidatesTheServerSession() throws Exception {
        MockHttpSession session = authenticatedSession();
        mvc.perform(post("/auth/logout").session(session)).andExpect(status().isForbidden());
        var csrfResult = mvc.perform(get("/auth/csrf").session(session))
                .andExpect(status().isOk()).andReturn();
        CsrfResponse csrf = mapper.readValue(csrfResult.getResponse().getContentAsByteArray(), CsrfResponse.class);
        mvc.perform(post("/auth/logout").session(session).header(csrf.headerName(), csrf.token()))
                .andExpect(status().isNoContent());
        assertTrue(session.isInvalid());
        mvc.perform(get("/accounts/me")).andExpect(status().isUnauthorized());
    }

    private MockHttpSession authenticatedSession() {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new AccountPrincipal(42L, "profile", "旧昵称"), null, List.of()));
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }
}
