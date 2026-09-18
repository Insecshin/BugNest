package com.nolla.bugnest.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UsernameGeneratorTest {
    private final UsernameGenerator generator = new UsernameGenerator();

    @Test
    void shouldTransliterateDifferentScriptsToAsciiUsernameBase() {
        assertEquals("zhang_san", generator.baseFrom("张三"));
        assertEquals("ivan", generator.baseFrom("Иван"));
        assertEquals("muller", generator.baseFrom("Müller"));
    }

    @Test
    void shouldUseFallbackWhenNicknameHasNoTransliteratableCharacters() {
        assertEquals("user", generator.baseFrom("😀!!!"));
    }
}
