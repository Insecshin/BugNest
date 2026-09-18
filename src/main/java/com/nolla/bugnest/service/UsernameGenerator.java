package com.nolla.bugnest.service;

import com.ibm.icu.text.Transliterator;

import java.text.Normalizer;
import java.util.Locale;

public class UsernameGenerator {
    private static final int MAX_BASE_LENGTH = 24;
    private static final String FALLBACK = "user";
    private final Transliterator transliterator =
            Transliterator.getInstance("Any-Latin; Latin-ASCII");

    public String baseFrom(String nickname) {
        String normalized = Normalizer.normalize(nickname, Normalizer.Form.NFKC);
        String transliterated = transliterator.transliterate(normalized)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");

        if (transliterated.isBlank()) {
            return FALLBACK;
        }

        String base = transliterated.substring(0, Math.min(MAX_BASE_LENGTH, transliterated.length()));
        base = base.replaceAll("_+$", "");
        return base.isBlank() ? FALLBACK : base;
    }
}
