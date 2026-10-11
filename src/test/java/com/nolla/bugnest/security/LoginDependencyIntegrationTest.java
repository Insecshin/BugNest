package com.nolla.bugnest.security;

import com.nolla.bugnest.dto.SignUpRequest;
import com.nolla.bugnest.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoginDependencyIntegrationTest {
    private static int freePort() {
        try (var socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    // Docker can reassign random published ports after stop/start; keep fault-test endpoints stable.
    private static final int POSTGRES_PORT = freePort(), REDIS_PORT = freePort();
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6-alpine")
            .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withPortBindings(
                    new com.github.dockerjava.api.model.PortBinding(
                            com.github.dockerjava.api.model.Ports.Binding.bindIpAndPort("127.0.0.1", POSTGRES_PORT),
                            new com.github.dockerjava.api.model.ExposedPort(5432))));
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.10.1-alpine").withExposedPorts(6379)
            .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withPortBindings(
                    new com.github.dockerjava.api.model.PortBinding(
                            com.github.dockerjava.api.model.Ports.Binding.bindIpAndPort("127.0.0.1", REDIS_PORT),
                            new com.github.dockerjava.api.model.ExposedPort(6379))));

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Value("${local.server.port}") int port;
    @Autowired AccountService accounts;
    @Autowired JsonMapper mapper;
    @Autowired com.zaxxer.hikari.HikariDataSource dataSource;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockitoSpyBean SecurityContextRepository contexts;
    private final HttpClient client = HttpClient.newHttpClient();
    private static final String PASSWORD = "temporary correct password phrase";
    private String email;

    @BeforeEach
    void createAccount() {
        assertEquals(2000, dataSource.getConnectionTimeout());
        assertEquals("2", dataSource.getDataSourceProperties().getProperty("socketTimeout"));
        assertEquals(2, jdbc.getQueryTimeout());
        email = UUID.randomUUID() + "@example.invalid";
        accounts.signUp(new SignUpRequest(email, PASSWORD, PASSWORD, "测试", "dep_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)));
    }

    @Test
    void stoppedDependenciesReturn503WithinFiveSecondsAndRecover() throws Exception {
        for (GenericContainer<?> dependency : new GenericContainer<?>[]{REDIS, POSTGRES}) {
            var csrf = request("/auth/csrf", null, null, null);
            assertEquals(200, csrf.statusCode());
            String cookie = cookie(csrf), token = mapper.readTree(csrf.body()).get("token").asText();
            dependency.getDockerClient().stopContainerCmd(dependency.getContainerId()).withTimeout(0).exec();
            try {
                long started = System.nanoTime();
                var response = request("/auth/login", cookie, token, credentials());
                assertUnavailable(response);
                assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() < 5000);
            } finally {
                dependency.getDockerClient().startContainerCmd(dependency.getContainerId()).exec();
            }
            assertRecovery();
        }
    }

    @Test
    void anUnresponsiveDatabaseAlsoTimesOutAs503() throws Exception {
        var csrf = request("/auth/csrf", null, null, null);
        POSTGRES.getDockerClient().pauseContainerCmd(POSTGRES.getContainerId()).exec();
        try {
            long started = System.nanoTime();
            assertUnavailable(request("/auth/login", cookie(csrf), mapper.readTree(csrf.body()).get("token").asText(), credentials()));
            assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() < 5000);
        } finally {
            POSTGRES.getDockerClient().unpauseContainerCmd(POSTGRES.getContainerId()).exec();
        }
        assertRecovery();
    }

    @Test
    void sessionPersistenceTimeoutCannotReturnLoginSuccess() throws Exception {
        var csrf = request("/auth/csrf", null, null, null);
        doAnswer(invocation -> {
            REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();
            return invocation.callRealMethod();
        }).when(contexts).saveContext(any(), any(), any());
        try {
            long started = System.nanoTime();
            var response = request("/auth/login", cookie(csrf), mapper.readTree(csrf.body()).get("token").asText(), credentials());
            assertUnavailable(response);
            assertTrue(response.headers().allValues("set-cookie").isEmpty());
            assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() < 5000);
        } finally {
            REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();
            reset(contexts);
        }
        assertRecovery();
        assertEquals(401, request("/accounts/me", cookie(csrf), null, null).statusCode());
    }

    private void assertUnavailable(HttpResponse<String> response) {
        assertEquals(503, response.statusCode());
        assertTrue(response.body().contains("AUTHENTICATION_UNAVAILABLE"));
        assertFalse(response.body().contains("userId"));
        assertFalse(response.body().contains(email));
    }

    private void assertRecovery() throws Exception {
        long until = System.nanoTime() + Duration.ofSeconds(8).toNanos();
        HttpResponse<String> csrf;
        do {
            csrf = request("/auth/csrf", null, null, null);
            if (csrf.statusCode() == 200) break;
            Thread.sleep(100);
        } while (System.nanoTime() < until);
        assertEquals(200, csrf.statusCode());
        assertEquals(200, request("/auth/login", cookie(csrf), mapper.readTree(csrf.body()).get("token").asText(), credentials()).statusCode());
    }

    private String credentials() {
        return mapper.writeValueAsString(java.util.Map.of("identifier", email, "password", PASSWORD));
    }

    private String cookie(HttpResponse<?> response) {
        return response.headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0];
    }

    private HttpResponse<String> request(String path, String cookie, String token, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(8));
        if (cookie != null) builder.header("Cookie", cookie);
        if (token != null) builder.header("X-CSRF-TOKEN", token);
        if (body != null) builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
