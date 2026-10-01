package com.investome.api.paper;

import jakarta.validation.constraints.*;

public record BuyOrderRequest(
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "[A-Za-z0-9_-]+", message = "요청 ID는 영문, 숫자, 밑줄, 하이픈만 가능합니다.")
        String requestId,
        @NotBlank @Pattern(regexp = "[0-9]{6}", message = "종목코드는 숫자 6자리여야 합니다.")
        String symbol,
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = OrderQuantityDeserializer.class)
        @NotNull @Min(1) @Max(1_000_000) Integer quantity
) {}