package com.investome.api.market;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketItemResponse {
    private Integer rank;
    private String market;
    private String name;
    private String displayNameEN;
    private String symbol;
    private String iconUrl;
    private String coinId;
    private Long capKRW;
    private Double priceKRW;
    private Double changePct;
}