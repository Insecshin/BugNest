package com.nolla.bugnest.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyTest {
    private final PasswordPolicy policy = new PasswordPolicy(Set.of("password123456"));

    @Test
    void shouldNormalizeUnicodeBeforeReturningPassword() {
        String password = "aaaaaaaaaaaaaa\u0065\u0301";

        assertEquals("aaaaaaaaaaaaaaé", policy.validateAndNormalize(password, password));
    }

    @Test
    void shouldRejectShortPassword() {
        assertThrows(
                IllegalArgumentException.class,
                () -> policy.validateAndNormalize("short", "short")
        );
    }

    @Test
    void shouldRejectMismatchedPassword() {
        assertThrows(
                IllegalArgumentException.class,
                () -> policy.validateAndNormalize("aaaaaaaaaaaaaaa", "bbbbbbbbbbbbbbb")
        );
    }

    @Test
    void shouldRejectBlockedPassword() {
        assertThrows(
                IllegalArgumentException.class,
                () -> policy.validateAndNormalize("password123456", "password123456")
        );
    }
}
