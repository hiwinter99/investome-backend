package com.investome.api.mypage;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MyPagePortfolioRequest {
    private JsonNode holdings;
    private Long targetAmount;
}