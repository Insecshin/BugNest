package com.nolla.bugnest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
@SpringBootTest
class FlywayPostgresIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:18.6-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldApplyFlywayMigrationToPostgres() {
        Integer tableCount = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'app_users'
                """,
                Integer.class
        );

        Integer migrationCount = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM flyway_schema_history
                WHERE version = '1' AND success = true
                """,
                Integer.class
        );

        Integer columnCount = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'app_users'
                  AND column_name IN ('user_id', 'user_name', 'username', 'email', 'password_hash', 'created_at')
                """,
                Integer.class
        );

        Integer emailIndexCount = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM pg_indexes
                WHERE schemaname = 'public' AND indexname = 'uq_app_users_email_ci'
                """,
                Integer.class
        );

        assertEquals(1, tableCount);
        assertEquals(1, migrationCount);
        assertEquals(6, columnCount);
        assertEquals(1, emailIndexCount);
    }
}
