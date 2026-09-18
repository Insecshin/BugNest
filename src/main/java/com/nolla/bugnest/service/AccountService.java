package com.nolla.bugnest.service;

import com.nolla.bugnest.dto.SignUpRequest;
import com.nolla.bugnest.dto.UsernamePreviewRequest;
import com.nolla.bugnest.dto.UsernamePreviewResponse;
import com.nolla.bugnest.exception.AccountConflictException;
import com.nolla.bugnest.model.Account;
import com.nolla.bugnest.repository.AccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

@Service
public class AccountService {
    private static final Pattern USER_NAME_PATTERN = Pattern.compile("[a-z0-9_]+");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+$");
    private static final int MAX_NICKNAME_LENGTH = 50;
    private static final int MAX_USER_NAME_LENGTH = 30;
    private static final int MAX_GENERATION_ATTEMPTS = 10;

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final UsernameGenerator usernameGenerator;

    public AccountService(
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            UsernameGenerator usernameGenerator
    ) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.usernameGenerator = usernameGenerator;
    }

    public Long signUp(SignUpRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }

        String email = normalizeEmail(request.email());
        String nickname = normalizeNickname(request.nickname());
        String password = passwordPolicy.validateAndNormalize(
                request.password(),
                request.confirmPassword()
        );
        String requestedUserName = request.userName();
        boolean hasExplicitUserName = requestedUserName != null && !requestedUserName.isBlank();
        if (hasExplicitUserName) {
            requestedUserName = validateUserName(requestedUserName);
            if (accountRepository.existsByUserName(requestedUserName)) {
                throw new AccountConflictException("User name already exists");
            }
        }

        String passwordHash = passwordEncoder.encode(password);
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String userName = hasExplicitUserName
                    ? requestedUserName
                    : findAvailableUserName(nickname);
            try {
                return accountRepository.save(
                        new Account(userName, email, passwordHash, nickname)
                ).getId();
            } catch (AccountConflictException exception) {
                if (hasExplicitUserName || accountRepository.findByEmail(email).isPresent()) {
                    throw exception;
                }
            }
        }

        throw new AccountConflictException("Could not create account with a unique user name");
    }

    public UsernamePreviewResponse previewUsername(UsernamePreviewRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }

        String nickname = normalizeNickname(request.nickname());
        if (request.userName() != null && !request.userName().isBlank()) {
            String userName = validateUserName(request.userName());
            return new UsernamePreviewResponse(
                    userName,
                    !accountRepository.existsByUserName(userName)
            );
        }

        return new UsernamePreviewResponse(findAvailableUserName(nickname), true);
    }

    public void updateNickname(Long id, String nickname) {
        String normalizedNickname = normalizeNickname(nickname);
        if (accountRepository.findById(id).isEmpty()) {
            throw new NoSuchElementException("Account not found");
        }
        accountRepository.updateNickname(id, normalizedNickname);
    }

    private String findAvailableUserName(String nickname) {
        String base = usernameGenerator.baseFrom(nickname);
        if (!accountRepository.existsByUserName(base)) {
            return base;
        }

        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String suffix = "_" + ThreadLocalRandom.current().nextInt(1000, 10000);
            int prefixLength = MAX_USER_NAME_LENGTH - suffix.length();
            String prefix = base.substring(0, Math.min(prefixLength, base.length()));
            String candidate = prefix + suffix;
            if (!accountRepository.existsByUserName(candidate)) {
                return candidate;
            }
        }

        throw new AccountConflictException("Could not generate a unique user name");
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            throw new IllegalArgumentException("Email is required");
        }
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() > 254 || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Email is invalid");
        }
        return normalized;
    }

    private String normalizeNickname(String nickname) {
        if (nickname == null) {
            throw new IllegalArgumentException("Nickname is required");
        }
        String normalized = nickname.strip();
        int length = normalized.codePointCount(0, normalized.length());
        if (normalized.isBlank() || length > MAX_NICKNAME_LENGTH) {
            throw new IllegalArgumentException("Nickname must contain 1 to 50 characters");
        }
        return normalized;
    }

    private String validateUserName(String userName) {
        String normalized = userName.strip();
        if (normalized.length() > MAX_USER_NAME_LENGTH
                || normalized.isBlank()
                || !USER_NAME_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("User name must contain only lowercase letters, numbers, and underscores");
        }
        return normalized;
    }
}
