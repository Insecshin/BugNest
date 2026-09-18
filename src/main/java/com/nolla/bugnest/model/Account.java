package com.nolla.bugnest.model;

import java.time.Instant;

public class Account {
    private final Long id;
    private final String userName;
    private final String email;
    private final String passwordHash;
    private String nickname;
    private final Instant createdAt;

    public Account(String userName, String email, String passwordHash, String nickname) {
        this(null, userName, email, passwordHash, nickname, null);
    }

    public Account(
            Long id,
            String userName,
            String email,
            String passwordHash,
            String nickname,
            Instant createdAt
    ) {
        this.id = id;
        this.userName = userName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getUserName() {
        return userName;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getNickname() {
        return nickname;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }
}
