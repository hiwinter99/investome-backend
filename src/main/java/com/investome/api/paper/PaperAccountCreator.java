package com.investome.api.paper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaperAccountCreator {

    private final PaperAccountRepository accountRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaperAccount createIfAbsent(Long userId) {
        return accountRepository.findByUserId(userId)
                .orElseGet(() ->
                        accountRepository.save(new PaperAccount(userId)));
    }
}