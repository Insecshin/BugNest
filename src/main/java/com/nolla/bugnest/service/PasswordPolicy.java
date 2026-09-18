package com.nolla.bugnest.service;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class PasswordPolicy {
    private static final int MIN_CODE_POINTS = 15;
    private static final int MAX_CODE_POINTS = 1024;

    private final Set<String> blockedPasswords;

    public PasswordPolicy(Set<String> blockedPasswords) {
        this.blockedPasswords = Set.copyOf(blockedPasswords);
    }

    public static PasswordPolicy fromClasspath(String resourcePath) {
        try (InputStream inputStream = new ClassPathResource(resourcePath).getInputStream()) {
            Set<String> blocked = new HashSet<>();
            new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .lines()
                    .map(String::strip)
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .map(line -> Normalizer.normalize(line, Normalizer.Form.NFC)
                            .toLowerCase(Locale.ROOT))
                    .forEach(blocked::add);
            return new PasswordPolicy(blocked);
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot load password blocklist", exception);
        }
    }

    public String validateAndNormalize(String password, String confirmPassword) {
        if (password == null || confirmPassword == null) {
            throw new IllegalArgumentException("Password and confirmation are required");
        }

        String normalizedPassword = Normalizer.normalize(password, Normalizer.Form.NFC);
        String normalizedConfirmation = Normalizer.normalize(confirmPassword, Normalizer.Form.NFC);

        if (!normalizedPassword.equals(normalizedConfirmation)) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        int length = normalizedPassword.codePointCount(0, normalizedPassword.length());
        if (length < MIN_CODE_POINTS) {
            throw new IllegalArgumentException("Password must contain at least 15 characters");
        }
        if (length > MAX_CODE_POINTS) {
            throw new IllegalArgumentException("Password must contain at most 1024 characters");
        }

        if (blockedPasswords.contains(normalizedPassword.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Password is too common or compromised");
        }

        return normalizedPassword;
    }
}
