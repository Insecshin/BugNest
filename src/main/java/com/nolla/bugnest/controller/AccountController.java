package com.nolla.bugnest.controller;

import com.nolla.bugnest.dto.SignUpRequest;
import com.nolla.bugnest.dto.SignUpResponse;
import com.nolla.bugnest.dto.UpdateNicknameRequest;
import com.nolla.bugnest.dto.UsernamePreviewRequest;
import com.nolla.bugnest.dto.UsernamePreviewResponse;
import com.nolla.bugnest.service.AccountService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/auth/sign-up")
    @ResponseStatus(HttpStatus.CREATED)
    public SignUpResponse signUp(@RequestBody SignUpRequest request) {
        return new SignUpResponse(accountService.signUp(request));
    }

    @PostMapping("/auth/sign-up/username-preview")
    public UsernamePreviewResponse previewUsername(@RequestBody UsernamePreviewRequest request) {
        return accountService.previewUsername(request);
    }

    @PatchMapping("/accounts/{id}/nickname")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateNickname(
            @PathVariable Long id,
            @RequestBody UpdateNicknameRequest request
    ) {
        accountService.updateNickname(id, request == null ? null : request.nickname());
    }
}
