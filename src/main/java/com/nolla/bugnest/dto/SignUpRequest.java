package com.nolla.bugnest.dto;

public record SignUpRequest(
        String email,
        String password,
        String confirmPassword,
        String nickname,
        String userName
) {
}
