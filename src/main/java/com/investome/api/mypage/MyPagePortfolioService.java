package com.investome.api.mypage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MyPagePortfolioService {

    private final MyPagePortfolioRepository repository;
    private final ObjectMapper objectMapper;

    public MyPagePortfolioResponse getByUserId(Long userId) {
        return repository.findByUserId(userId)
                .map(this::toResponse)
                .orElse(null);
    }

    public MyPagePortfolioResponse save(Long userId, MyPagePortfolioRequest request) {
        MyPagePortfolio entity = repository.findByUserId(userId)
                .orElseGet(() -> {
                    MyPagePortfolio created = new MyPagePortfolio();
                    created.setUserId(userId);
                    return created;
                });

        JsonNode holdingsNode = normalizeHoldings(request.getHoldings());
        Long targetAmount = normalizeTargetAmount(request.getTargetAmount());

        entity.setHoldingsJson(writeJson(holdingsNode));
        entity.setTargetAmount(targetAmount);

        MyPagePortfolio saved = repository.save(entity);
        return toResponse(saved);
    }

    private MyPagePortfolioResponse toResponse(MyPagePortfolio entity) {
        return new MyPagePortfolioResponse(
                readJson(entity.getHoldingsJson()),
                entity.getTargetAmount()
        );
    }

    private JsonNode normalizeHoldings(JsonNode holdings) {
        if (holdings == null || holdings.isNull() || !holdings.isArray()) {
            return objectMapper.createArrayNode();
        }
        return holdings;
    }

    private Long normalizeTargetAmount(Long targetAmount) {
        if (targetAmount == null || targetAmount < 0) {
            return 0L;
        }
        return targetAmount;
    }

    private JsonNode readJson(String raw) {
        try {
            if (raw == null || raw.isBlank()) {
                return objectMapper.createArrayNode();
            }
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            return objectMapper.createArrayNode();
        }
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return "[]";
        }
    }
}