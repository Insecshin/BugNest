package com.nolla.bugnest.security;

import com.nolla.bugnest.dto.CsrfResponse;
import com.nolla.bugnest.dto.LoginRequest;
import com.nolla.bugnest.dto.SignUpRequest;
import com.nolla.bugnest.service.AccountService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

import java.text.Normalizer;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class LoginSecurityIntegrationTest {
    private static final String PASSWORD = "correct horse café battery staple";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6-alpine");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.10.1-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountService accountService;

    @Autowired
    private LoginRateLimiter rateLimiter;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JsonMapper jsonMapper;

    private String email;
    private String userName;
    private Long accountId;

    @BeforeEach
    void createAccount() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        email = suffix + "@example.com";
        userName = "login_" + suffix;
        accountId = accountService.signUp(new SignUpRequest(
                email, PASSWORD, PASSWORD, "登录测试", userName
        ));
    }

    @Test
    void loginByEmailAndUsernameReturnsOnlyPublicFieldsAndPersistsSession() throws Exception {
        CsrfContext csrf = fetchCsrf(null);
        String oldSessionId = csrf.cookie().getValue();

        MvcResult login = performLogin(csrf, email, Normalizer.normalize(PASSWORD, Normalizer.Form.NFD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(accountId))
                .andExpect(jsonPath("$.userName").value(userName))
                .andExpect(jsonPath("$.nickname").value("登录测试"))
                .andReturn();

        Cookie authenticatedCookie = cookieFrom(login.getResponse().getHeader("Set-Cookie"));
        assertNotNull(authenticatedCookie);
        assertNotEquals(oldSessionId, authenticatedCookie.getValue());
        String responseCookie = login.getResponse().getHeader("Set-Cookie");
        assertNotNull(responseCookie);
        assertTrue(responseCookie.startsWith("__Host-bugnest_session="));
        assertTrue(responseCookie.contains("Path=/"));
        assertTrue(responseCookie.contains("HttpOnly"));
        assertTrue(responseCookie.contains("Secure"));
        assertTrue(responseCookie.contains("SameSite=Lax"));
        String body = login.getResponse().getContentAsString();
        assertFalse(body.contains(email));
        assertFalse(body.contains(PASSWORD));

        CsrfContext refreshed = fetchCsrf(authenticatedCookie);
        assertNotEquals(csrf.token(), refreshed.token());
        mockMvc.perform(patch("/accounts/{id}/nickname", accountId)
                        .cookie(refreshed.cookie())
                        .header(refreshed.headerName(), refreshed.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"本人更新\"}"))
                .andExpect(status().isNoContent());

        Long anotherAccountId = accountService.signUp(new SignUpRequest(
                "other-" + UUID.randomUUID() + "@example.com",
                PASSWORD, PASSWORD, "另一个账户", "other_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10)
        ));
        mockMvc.perform(patch("/accounts/{id}/nickname", anotherAccountId)
                        .cookie(refreshed.cookie())
                        .header(refreshed.headerName(), refreshed.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"越权更新\"}"))
                .andExpect(status().isForbidden());

        CsrfContext secondLoginCsrf = fetchCsrf(null);
        performLogin(secondLoginCsrf, userName, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userName").value(userName));
    }

    @Test
    void unknownAccountAndWrongPasswordHaveTheSameUnauthorizedResponse() throws Exception {
        CsrfContext csrf = fetchCsrf(null);
        String wrongPasswordResponse = performLogin(csrf, email, "incorrect password")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownAccountResponse = performLogin(csrf, "missing_" + UUID.randomUUID(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertEquals(wrongPasswordResponse, unknownAccountResponse);
        assertEquals("{\"code\":\"AUTHENTICATION_FAILED\",\"message\":\"用户名或密码错误\"}", wrongPasswordResponse);
        performLogin(csrf, userName, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void emailAndUsernameShareTheSameAccountFailureCooldown() throws Exception {
        CsrfContext csrf = fetchCsrf(null);
        for (int i = 0; i < 5; i++) {
            performLogin(csrf, email, "wrong password")
                    .andExpect(status().isUnauthorized());
        }
        performLogin(csrf, userName, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(result -> {
                    long retryAfter = Long.parseLong(result.getResponse().getHeader("Retry-After"));
                    assertTrue(retryAfter > 0 && retryAfter <= 30);
                });
    }

    @Test
    void csrfIsRequiredForLoginAndRegistration() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsBytes(new LoginRequest(userName, PASSWORD))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/auth/login")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/auth/csrf")).andExpect(status().isOk());

        CsrfContext csrf = fetchCsrf(null);
        mockMvc.perform(post("/auth/login")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void redisScriptsEnforceIpBucketAndAccountBackoffWithoutExtendingCooldown() {
        String ip = "203.0.113." + (10 + Math.floorMod(UUID.randomUUID().hashCode(), 200));
        for (int i = 0; i < 30; i++) {
            rateLimiter.consumeIpToken(ip);
        }
        LoginRateLimitedException ipLimit = assertThrows(
                LoginRateLimitedException.class, () -> rateLimiter.consumeIpToken(ip)
        );
        assertTrue(ipLimit.retryAfterSeconds() > 0 && ipLimit.retryAfterSeconds() <= 2);

        String accountKey = rateLimiter.accountKey(Math.floorMod(
                UUID.randomUUID().getMostSignificantBits(), Long.MAX_VALUE
        ));
        for (int i = 0; i < 5; i++) {
            rateLimiter.recordFailure(accountKey);
        }
        LoginRateLimitedException initialCooldown = assertThrows(
                LoginRateLimitedException.class, () -> rateLimiter.checkAccount(accountKey)
        );
        assertTrue(initialCooldown.retryAfterSeconds() > 0 && initialCooldown.retryAfterSeconds() <= 30);

        redis.opsForHash().put(accountKey, "blockedUntil", "1");
        rateLimiter.recordFailure(accountKey);
        String blockedUntil = redis.<String, String>opsForHash().get(accountKey, "blockedUntil");
        LoginRateLimitedException extendedCooldown = assertThrows(
                LoginRateLimitedException.class, () -> rateLimiter.checkAccount(accountKey)
        );
        assertTrue(extendedCooldown.retryAfterSeconds() > 0 && extendedCooldown.retryAfterSeconds() <= 60);
        assertEquals(blockedUntil, redis.<String, String>opsForHash().get(accountKey, "blockedUntil"));
        assertTrue(redis.getExpire(accountKey) >= 86_390);

        rateLimiter.recordSuccess(accountKey);
        assertFalse(Boolean.TRUE.equals(redis.hasKey(accountKey)));

        String rawIdentifier = "private-" + UUID.randomUUID() + "@example.com";
        String unknownKey = rateLimiter.unknownIdentifierKey(rawIdentifier);
        assertTrue(unknownKey.startsWith("bugnest:login:unknown:"));
        assertFalse(unknownKey.contains(rawIdentifier));
    }

    private CsrfContext fetchCsrf(Cookie cookie) throws Exception {
        var request = get("/auth/csrf");
        if (cookie != null) {
            request.cookie(cookie);
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        CsrfResponse response = jsonMapper.readValue(
                result.getResponse().getContentAsByteArray(), CsrfResponse.class
        );
        Cookie nextCookie = cookieFrom(result.getResponse().getHeader("Set-Cookie"));
        return new CsrfContext(nextCookie == null ? cookie : nextCookie, response.headerName(), response.token());
    }

    private org.springframework.test.web.servlet.ResultActions performLogin(
            CsrfContext csrf,
            String identifier,
            String password
    ) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .cookie(csrf.cookie())
                .header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsBytes(new LoginRequest(identifier, password))));
    }

    private Cookie cookieFrom(String setCookie) {
        if (setCookie == null || !setCookie.startsWith("__Host-bugnest_session=")) {
            return null;
        }
        String value = setCookie.substring("__Host-bugnest_session=".length()).split(";", 2)[0];
        return new Cookie("__Host-bugnest_session", value);
    }

    private record CsrfContext(Cookie cookie, String headerName, String token) {
    }
}
