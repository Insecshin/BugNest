package com.nolla.bugnest.service;

import com.nolla.bugnest.dto.SignUpRequest;
import com.nolla.bugnest.dto.UsernamePreviewRequest;
import com.nolla.bugnest.dto.UsernamePreviewResponse;
import com.nolla.bugnest.exception.AccountConflictException;
import com.nolla.bugnest.model.Account;
import com.nolla.bugnest.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountServiceTest {
    private final FakeAccountRepository repository = new FakeAccountRepository();
    private final PasswordEncoder passwordEncoder = new PasswordEncoder() {
        @Override
        public String encode(CharSequence rawPassword) {
            return "hash:" + rawPassword;
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return encodedPassword.equals(encode(rawPassword));
        }
    };
    private final AccountService service = new AccountService(
            repository,
            passwordEncoder,
            new PasswordPolicy(Set.of()),
            new UsernameGenerator()
    );

    @Test
    void shouldCreateAccountWithGeneratedUsernameAndHashedPassword() {
        Long id = service.signUp(new SignUpRequest(
                " USER@example.com ",
                "123456789012345",
                "123456789012345",
                "张三",
                null
        ));

        Account saved = repository.findById(id).orElseThrow();
        assertEquals(1L, id);
        assertEquals("user@example.com", saved.getEmail());
        assertEquals("zhang_san", saved.getUserName());
        assertEquals("hash:123456789012345", saved.getPasswordHash());
        assertEquals("张三", saved.getNickname());
    }

    @Test
    void shouldRejectDuplicateExplicitUsername() {
        repository.save(new Account("zhang_san", "old@example.com", "hash", "旧用户"));

        assertThrows(
                AccountConflictException.class,
                () -> service.signUp(new SignUpRequest(
                        "new@example.com",
                        "123456789012345",
                        "123456789012345",
                        "新用户",
                        "zhang_san"
                ))
        );
    }

    @Test
    void shouldPreviewExplicitUsernameAvailability() {
        repository.save(new Account("taken", "old@example.com", "hash", "旧用户"));

        UsernamePreviewResponse response = service.previewUsername(
                new UsernamePreviewRequest("新用户", "taken")
        );

        assertEquals("taken", response.userName());
        assertEquals(false, response.available());
    }

    @Test
    void shouldRetryGeneratedUsernameWhenInsertLosesRace() {
        repository.save(new Account("zhang_san", "old@example.com", "hash", "旧用户"));
        repository.failNextGeneratedSave = true;

        Long id = service.signUp(new SignUpRequest(
                "new@example.com",
                "123456789012345",
                "123456789012345",
                "张三",
                null
        ));

        assertEquals(2L, id);
        assertEquals("张三", repository.findById(id).orElseThrow().getNickname());
    }

    private static class FakeAccountRepository implements AccountRepository {
        private final Map<Long, Account> accounts = new HashMap<>();
        private long nextId = 1;
        private boolean failNextGeneratedSave;

        @Override
        public Account save(Account account) {
            if (failNextGeneratedSave) {
                failNextGeneratedSave = false;
                throw new AccountConflictException("User name already exists");
            }
            if (existsByUserName(account.getUserName())) {
                throw new AccountConflictException("User name already exists");
            }
            Account saved = new Account(
                    nextId++,
                    account.getUserName(),
                    account.getEmail(),
                    account.getPasswordHash(),
                    account.getNickname(),
                    null
            );
            accounts.put(saved.getId(), saved);
            return saved;
        }

        @Override
        public Optional<Account> findById(Long id) {
            return Optional.ofNullable(accounts.get(id));
        }

        @Override
        public Optional<Account> findByEmail(String email) {
            return accounts.values().stream()
                    .filter(account -> account.getEmail().equals(email))
                    .findFirst();
        }

        @Override
        public boolean existsByUserName(String userName) {
            return accounts.values().stream()
                    .anyMatch(account -> account.getUserName().equals(userName));
        }

        @Override
        public Account updateNickname(Long id, String nickname) {
            Account account = accounts.get(id);
            if (account == null) {
                throw new IllegalArgumentException("Account not found");
            }
            account.changeNickname(nickname);
            return account;
        }
    }
}
