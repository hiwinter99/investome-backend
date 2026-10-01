package com.investome.api.paper;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/paper")
public class PaperAccountController {

    private final PaperAccountService accountService;

    @GetMapping("/account")
    public PaperAccountResponse getAccount(
            @AuthenticationPrincipal Long userId
    ) {
        return accountService.getAccount(userId);
    }

    @PostMapping("/account")
    public PaperAccountResponse createAccount(
            @AuthenticationPrincipal Long userId
    ) {
        return accountService.createAccount(userId);
    }
}
