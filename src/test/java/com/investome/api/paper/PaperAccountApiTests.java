package com.investome.api.paper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investome.api.config.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:paper-account-api-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=paper-account-test-only-secret-long-enough-for-hs256"
})
@AutoConfigureMockMvc
class PaperAccountApiTests {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @Autowired PaperAccountRepository accounts;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void resetAccounts() {
        accounts.deleteAll();
    }

    private String bearer(long userId) {
        return "Bearer " + tokens.createAccessToken(userId, "test@example.invalid", "USER");
    }

    @Test
    void requiresAuthenticationForReadAndCreate() throws Exception {
        mvc.perform(get("/api/paper/account")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/paper/account")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/paper/account").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        assertEquals(0L, accounts.count());
    }

    @Test
    void missingAccountThenCreateThenRead() throws Exception {
        String auth = bearer(101L);
        mvc.perform(get("/api/paper/account").header("Authorization", auth))
                .andExpect(status().isNotFound());
        String body = mvc.perform(post("/api/paper/account").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashBalance").value(10_000_000))
                .andExpect(jsonPath("$.accountId").isNumber())
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/paper/account").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(content().json(body));
        assertEquals(1L, accounts.count());
    }

    @Test
    void repeatedCreationPreservesExistingBalanceAndAccountId() throws Exception {
        String auth = bearer(102L);
        String body = mvc.perform(post("/api/paper/account").header("Authorization", auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long accountId = json.readTree(body).get("accountId").asLong();
        // Arrange a changed balance directly; this is not a trading API test.
        jdbc.update("update paper_accounts set cash_balance = ? where id = ?", 7_000_000L, accountId);
        String repeated = mvc.perform(post("/api/paper/account").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashBalance").value(7_000_000))
                .andReturn().getResponse().getContentAsString();
        assertEquals(accountId, json.readTree(repeated).get("accountId").asLong());
        assertEquals(1L, accounts.count());
    }

    @Test
    void usesAuthenticatedUserRatherThanClientSuppliedUserId() throws Exception {
        mvc.perform(post("/api/paper/account")
                        .header("Authorization", bearer(103L))
                        .contentType("application/json")
                        .content("{\"userId\":104,\"cashBalance\":999999999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashBalance").value(10_000_000));
        assertEquals(103L, accounts.findAll().get(0).getUserId());
        mvc.perform(get("/api/paper/account").header("Authorization", bearer(104L)))
                .andExpect(status().isNotFound());
    }
}