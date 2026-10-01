package com.investome.api.paper;

import com.investome.api.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

@Service
@RequiredArgsConstructor
public class PaperAccountService {

    private final PaperAccountRepository accountRepository;
    private final PaperAccountCreator accountCreator;

    @Transactional(readOnly = true)
    public PaperAccountResponse getAccount(Long userId) {
        PaperAccount account = accountRepository.findByUserId(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("모의계좌가 없습니다. 새로 개설해 주세요."));

        return new PaperAccountResponse(
                account.getId(),
                account.getCashBalance()
        );
    }

    public PaperAccountResponse createAccount(Long userId) {
        PaperAccount account;

        try {
            account = accountCreator.createIfAbsent(userId);
        } catch (DataIntegrityViolationException e) {
            account = accountRepository.findByUserId(userId)
                    .orElseThrow(() -> e);
        }

        return new PaperAccountResponse(
                account.getId(),
                account.getCashBalance()
        );
    }
}
