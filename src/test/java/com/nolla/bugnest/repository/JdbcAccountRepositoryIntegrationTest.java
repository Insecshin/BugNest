package com.nolla.bugnest.repository;

import com.nolla.bugnest.model.Account;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest
class JdbcAccountRepositoryIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:18.6-alpine");

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void shouldSaveFindAndUpdateAccount() {
        Account saved = accountRepository.save(
                new Account("zhang_san", "zhang@example.com", "argon2-hash", "张三")
        );

        Account found = accountRepository.findById(saved.getId()).orElseThrow();
        assertEquals("zhang_san", found.getUserName());
        assertEquals("张三", found.getNickname());
        assertEquals("argon2-hash", found.getPasswordHash());
        assertTrue(accountRepository.findByEmail("zhang@example.com").isPresent());

        Account updated = accountRepository.updateNickname(saved.getId(), "李四");
        assertEquals("李四", updated.getNickname());
        assertEquals("zhang_san", updated.getUserName());
    }
}
