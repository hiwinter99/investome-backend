package com.investome.api.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class TurnstileService {

    private final RestClient restClient;

    @Value("${turnstile.secret-key:}")
    private String secretKey;

    public TurnstileService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl("https://challenges.cloudflare.com")
                .build();
    }

    public void verifyToken(String token, String remoteIp) {
        if (secretKey == null || secretKey.isBlank()) {
            return;
        }

        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "보안 확인을 완료해주세요.");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secretKey);
        form.add("response", token);
        if (remoteIp != null && !remoteIp.isBlank()) {
            form.add("remoteip", remoteIp);
        }

        TurnstileVerifyResponse response;
        try {
            response = restClient.post()
                    .uri("/turnstile/v0/siteverify")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TurnstileVerifyResponse.class);
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "보안 확인 중 오류가 발생했어요. 잠시 후 다시 시도해주세요."
            );
        }

        if (response == null || !response.success()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "보안 확인에 실패했어요. 다시 시도해주세요."
            );
        }
    }

    private record TurnstileVerifyResponse(
            boolean success,
            String challenge_ts,
            String hostname,
            @JsonProperty("error-codes")
            List<String> errorCodes
    ) {
    }
}
