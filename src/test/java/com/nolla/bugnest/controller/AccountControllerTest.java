package com.nolla.bugnest.controller;

import com.nolla.bugnest.dto.SignUpRequest;
import com.nolla.bugnest.dto.SignUpResponse;
import com.nolla.bugnest.model.Account;
import com.nolla.bugnest.repository.AccountRepository;
import com.nolla.bugnest.service.AccountService;
import com.nolla.bugnest.service.PasswordPolicy;
import com.nolla.bugnest.service.UsernameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccountControllerTest {
    @Test
    void shouldReturnOnlyCreatedUserId() {
        PasswordEncoder encoder = new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                return "hash";
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return true;
            }
        };
        AccountService service = new AccountService(
                new StubAccountRepository(),
                encoder,
                new PasswordPolicy(Set.of()),
                new UsernameGenerator()
        );
        AccountController controller = new AccountController(service);

        SignUpResponse response = controller.signUp(new SignUpRequest(
                "user@example.com",
                "123456789012345",
                "123456789012345",
                "张三",
                null
        ));

        assertEquals(42L, response.userId());
    }

    private static class StubAccountRepository implements AccountRepository {
        @Override
        public Account save(Account account) {
            return new Account(42L, account.getUserName(), account.getEmail(), account.getPasswordHash(), account.getNickname(), null);
        }

        @Override
        public Optional<Account> findById(Long id) {
            return Optional.empty();
        }

        @Override
        public Optional<Account> findByEmail(String email) {
            return Optional.empty();
        }

        @Override
        public boolean existsByUserName(String userName) {
            return false;
        }

        @Override
        public Account updateNickname(Long id, String nickname) {
            throw new UnsupportedOperationException();
        }
    }
}
