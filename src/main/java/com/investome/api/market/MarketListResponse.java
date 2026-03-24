package com.investome.api.market;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class MarketListResponse {
    private List<MarketItemResponse> items;
}