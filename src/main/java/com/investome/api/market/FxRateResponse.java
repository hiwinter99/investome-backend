package com.investome.api.market;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FxRateResponse {
    private String updatedUtc;
    private Double USDKRW;
    private Double JPYKRW;
    private Double EURKRW;
}
