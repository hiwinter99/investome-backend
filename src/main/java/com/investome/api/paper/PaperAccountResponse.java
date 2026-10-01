package com.investome.api.paper;

public record PaperAccountResponse(
        Long accountId,
        long cashBalance
) {
}