package com.investome.api.mypage;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MyPagePortfolioResponse {
    private JsonNode holdings;
    private Long targetAmount;
}