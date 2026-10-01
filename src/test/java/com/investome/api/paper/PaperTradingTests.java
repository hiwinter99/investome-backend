package com.investome.api.paper;

import com.investome.api.config.JwtTokenProvider;
import com.investome.api.exception.BadRequestException;
import com.investome.api.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:paper-trading-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "jwt.secret=paper-trading-test-only-secret-long-enough-for-hs256"
})
@AutoConfigureMockMvc
class PaperTradingTests {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.investome.api.market.StockMarketService market;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    PaperUniverseService universe;
    @Autowired PaperTradingService trading;
    @Autowired PaperAccountService accountService;
    @Autowired PaperAccountRepository accounts;
    @Autowired PaperHoldingRepository holdings;
    @Autowired PaperOrderRepository orders;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;

    @BeforeEach
    void prepare() throws Exception {
        org.mockito.Mockito.when(market.getCurrentKoreanPrice("005930")).thenReturn(70_000L);
        org.mockito.Mockito.when(market.getCurrentKoreanPrice("000660")).thenReturn(150_000L);
        org.mockito.Mockito.when(universe.stocks()).thenReturn(java.util.stream.IntStream.range(0, 30)
            .mapToObj(i -> new PaperStockCatalog.Stock(String.format("%06d", i), "종목" + i, "KOSPI")).toList());
        orders.deleteAll();
        holdings.deleteAll();
        accounts.deleteAll();
        accountService.createAccount(1L);
    }
    private BuyOrderRequest request(String id, int quantity) {
        return new BuyOrderRequest(id, "005930", quantity);
    }
    private long cash() { return accountService.getAccount(1L).cashBalance(); }
    private String auth(long id) { return "Bearer " + tokens.createAccessToken(id, "test@example.invalid", "USER"); }

    @Test
    void buyAndReadViaAuthenticatedApi() throws Exception {
        mvc.perform(post("/api/paper/orders/buy").header("Authorization", auth(1))
                        .contentType("application/json")
                        .content("{\"requestId\":\"api-1\",\"symbol\":\"005930\",\"quantity\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.price").value(70000))
                .andExpect(jsonPath("$.totalAmount").value(210000))
                .andExpect(jsonPath("$.cashBalanceAfter").value(9790000))
                .andExpect(jsonPath("$.status").value("FILLED"))
                .andExpect(jsonPath("$.priceSource").value("KIS_KRX"));
        mvc.perform(get("/api/paper/holdings").header("Authorization", auth(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].quantity").value(3));
        mvc.perform(get("/api/paper/orders").header("Authorization", auth(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        accountService.createAccount(2L);
        mvc.perform(get("/api/paper/orders").header("Authorization", auth(2)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/paper/quotes").header("Authorization", auth(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void endpointsRequireAuthentication() throws Exception {
        mvc.perform(post("/api/paper/realtime/start")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/paper/orders/buy").contentType("application/json")
                .content("{\"requestId\":\"x\",\"symbol\":\"005930\",\"quantity\":1}"))
                .andExpect(status().isUnauthorized());
        for (String path : List.of("holdings", "orders", "quotes", "realtime", "realtime/stream")) {
            mvc.perform(get("/api/paper/" + path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void invalidInputsAndUnsupportedSymbolsAreRejectedWithoutMutation() throws Exception {
        for (String body : List.of(
                "{\"requestId\":\"x\",\"symbol\":\"005930\",\"quantity\":0}",
                "{\"requestId\":\"x\",\"symbol\":\"005930\",\"quantity\":-1}",
                "{\"requestId\":\"x\",\"symbol\":\"005930\"}",
                "{\"requestId\":\" \",\"symbol\":\"005930\",\"quantity\":1}",
                "{\"requestId\":\"x\",\"symbol\":\"AAPL\",\"quantity\":1}",
                "{\"requestId\":\"x\",\"symbol\":\"999999\",\"quantity\":1}")) {
            mvc.perform(post("/api/paper/orders/buy").header("Authorization", auth(1))
                    .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        assertEquals(10_000_000L, cash());
        assertEquals(0, holdings.count());
        assertEquals(0, orders.count());
    }

    @Test
    void missingAccountIs404() throws Exception {
        mvc.perform(post("/api/paper/orders/buy").header("Authorization", auth(9))
                        .contentType("application/json")
                        .content("{\"requestId\":\"x\",\"symbol\":\"005930\",\"quantity\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void additionalBuysUpdateOneHoldingAndRetryReturnsOriginalSnapshot() {
        PaperOrderResponse first = trading.buy(1L, request("first", 2));
        trading.buy(1L, request("second", 3));
        assertEquals(first, trading.buy(1L, request("first", 2)));
        assertEquals(9_650_000L, cash());
        assertEquals(1, holdings.count());
        assertEquals(5, trading.getHoldings(1L).get(0).quantity());
        assertEquals(new BigDecimal("70000.0000"), trading.getHoldings(1L).get(0).averagePrice());
        assertEquals(2, orders.count());
    }

    @Test
    void sameIdWithDifferentPayloadConflictsButOtherAccountCanReuseId() {
        trading.buy(1L, request("shared", 1));
        assertThrows(ConflictException.class, () -> trading.buy(1L, request("shared", 2)));
        assertThrows(ConflictException.class, () -> trading.buy(1L, new BuyOrderRequest("shared", "000660", 1)));
        accountService.createAccount(2L);
        trading.buy(2L, request("shared", 1));
        assertEquals(2, orders.count());
        assertEquals(9_930_000L, cash());
    }

    @Test
    void insufficientCashDoesNotSaveAnything() {
        assertThrows(BadRequestException.class, () -> trading.buy(1L, request("too-big", 143)));
        assertEquals(10_000_000L, cash());
        assertEquals(0, holdings.count());
        assertEquals(0, orders.count());
    }

    @Test
    void orderInsertFailureRollsBackCashAndNewHolding() {
        jdbc.execute("alter table paper_orders add constraint test_reject_13 check (quantity <> 13)");
        try {
            assertThrows(DataIntegrityViolationException.class, () -> trading.buy(1L, request("fail", 13)));
            assertEquals(10_000_000L, cash());
            assertEquals(0, holdings.count());
            assertEquals(0, orders.count());
        } finally {
            jdbc.execute("alter table paper_orders drop constraint test_reject_13");
        }
        trading.buy(1L, request("fail", 13));
        assertEquals(9_090_000L, cash());
    }

    @Test
    void orderInsertFailureAlsoRollsBackExistingHolding() {
        trading.buy(1L, request("initial", 2));
        jdbc.execute("alter table paper_orders add constraint test_reject_13 check (quantity <> 13)");
        try {
            assertThrows(DataIntegrityViolationException.class, () -> trading.buy(1L, request("fail", 13)));
            assertEquals(9_860_000L, cash());
            assertEquals(2, trading.getHoldings(1L).get(0).quantity());
            assertEquals(1, orders.count());
        } finally {
            jdbc.execute("alter table paper_orders drop constraint test_reject_13");
        }
    }

    private <T> List<T> together(Callable<T> actionA, Callable<T> actionB) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<T> a = pool.submit(() -> { ready.countDown(); start.await(); return actionA.call(); });
            Future<T> b = pool.submit(() -> { ready.countDown(); start.await(); return actionB.call(); });
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void simultaneousDuplicateOrdersExecuteOnce() throws Exception {
        List<PaperOrderResponse> result = together(
                () -> trading.buy(1L, request("duplicate", 3)),
                () -> trading.buy(1L, request("duplicate", 3)));
        assertEquals(result.get(0), result.get(1));
        assertEquals(9_790_000L, cash());
        assertEquals(3, trading.getHoldings(1L).get(0).quantity());
        assertEquals(1, orders.count());
    }

    @Test
    void simultaneousDifferentOrdersCannotOverspend() throws Exception {
        jdbc.update("update paper_accounts set cash_balance = 100000 where user_id = 1");
        List<Boolean> result = together(() -> buyOrReject("a"), () -> buyOrReject("b"));
        assertEquals(1L, result.stream().filter(Boolean::booleanValue).count());
        assertEquals(30_000L, cash());
        assertEquals(1, orders.count());
        assertEquals(1, trading.getHoldings(1L).get(0).quantity());
    }

    @Test
    void concurrentAccountCreationReturnsOneAccount() throws Exception {
        List<PaperAccountResponse> result = together(
                () -> accountService.createAccount(42L), () -> accountService.createAccount(42L));
        assertEquals(result.get(0), result.get(1));
        assertEquals(2, accounts.count()); // user 1 from setup, plus user 42
    }

    @Test
    void rejectsClientSuppliedPriceAndIdentity() throws Exception {
        mvc.perform(post("/api/paper/orders/buy").header("Authorization", auth(1))
                        .contentType("application/json")
                        .content("{\"requestId\":\"tamper\",\"symbol\":\"005930\",\"quantity\":1,\"price\":1,\"userId\":2}"))
                .andExpect(status().isBadRequest());
        assertEquals(10_000_000L, cash());
        assertEquals(0, orders.count());
        assertEquals(0, holdings.count());
    }
    @Test
    void fractionalAndOutOfRangeQuantitiesAreRejected() throws Exception {
        for (String quantity : List.of("1.5", "\"3\"", "2147483648", "1000001", "null")) {
            mvc.perform(post("/api/paper/orders/buy").header("Authorization", auth(1))
                            .contentType("application/json")
                            .content("{\"requestId\":\"bad-number\",\"symbol\":\"005930\",\"quantity\":" + quantity + "}"))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(10_000_000L, cash());
        assertEquals(0, orders.count());
    }
    @Test
    void partialAndFullSellPreserveHistoryAndRetryResult() throws Exception {
        trading.buy(1L, request("buy", 5));
        var first = trading.sell(1L, request("sell", 2));
        assertEquals("SELL", first.side());
        assertEquals(9_790_000L, cash());
        assertEquals(3, trading.getHoldings(1L).get(0).quantity());
        assertEquals(new BigDecimal("70000.0000"), trading.getHoldings(1L).get(0).averagePrice());
        assertEquals(first, trading.sell(1L, request("sell", 2)));
        mvc.perform(post("/api/paper/orders/sell").header("Authorization", auth(1))
                .contentType("application/json")
                .content("{\"requestId\":\"sell-all\",\"symbol\":\"005930\",\"quantity\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.side").value("SELL"));
        assertEquals(0, holdings.count());
        assertEquals(10_000_000L, cash());
        assertEquals(3, orders.count());
        trading.buy(1L, request("rebuy", 1));
        assertEquals(1, holdings.count());
    }

    @Test
    void cannotSellUnownedOrExcessSharesOrReuseBuyIdForSell() {
        assertThrows(BadRequestException.class, () -> trading.sell(1L, request("absent", 1)));
        trading.buy(1L, request("buy", 2));
        assertThrows(BadRequestException.class, () -> trading.sell(1L, request("too-many", 3)));
        assertThrows(ConflictException.class, () -> trading.sell(1L, request("buy", 2)));
        assertEquals(9_860_000L, cash());
        assertEquals(2, trading.getHoldings(1L).get(0).quantity());
        assertEquals(1, orders.count());
    }

    @Test
    void concurrentSellCannotOversell() throws Exception {
        trading.buy(1L, request("buy", 1));
        var results = together(() -> sellOrReject("sell-a"), () -> sellOrReject("sell-b"));
        assertEquals(1L, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(0, holdings.count());
        assertEquals(10_000_000L, cash());
        assertEquals(2, orders.count());
    }

    @Test
    void sellRecordFailureRollsBackCreditAndHoldingDeletion() {
        trading.buy(1L, request("buy", 13));
        jdbc.execute("alter table paper_orders add constraint test_reject_sell check (side <> 'SELL')");
        try {
            assertThrows(DataIntegrityViolationException.class, () -> trading.sell(1L, request("sell", 13)));
            assertEquals(9_090_000L, cash());
            assertEquals(13, trading.getHoldings(1L).get(0).quantity());
            assertEquals(1, orders.count());
        } finally {
            jdbc.execute("alter table paper_orders drop constraint test_reject_sell");
        }
    }

    @Test
    void freshPriceIsFetchedOutsideTransactionAndSavedForReplay() throws Exception {
        org.mockito.Mockito.when(market.getCurrentKoreanPrice("005930")).thenAnswer(invocation -> {
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            return 81_500L;
        });
        var buy = trading.buy(1L, request("fresh", 2));
        assertEquals(81_500L, buy.price());
        assertEquals(9_837_000L, cash());
        assertNotNull(buy.quoteFetchedAt());
        org.mockito.Mockito.when(market.getCurrentKoreanPrice("005930")).thenReturn(82_000L);
        var sell = trading.sell(1L, request("fresh-sell", 1));
        assertEquals(82_000L, sell.price());
        assertEquals(9_919_000L, cash());
        org.mockito.Mockito.when(market.getCurrentKoreanPrice("005930")).thenThrow(new java.io.IOException("offline"));
        assertEquals(buy, trading.buy(1L, request("fresh", 2)));
        assertEquals(sell, trading.sell(1L, request("fresh-sell", 1)));
        assertEquals(2, orders.count());
    }

    @Test
    void failedOrInvalidQuotesNeverMutateAccountAndReadsStillWork() throws Exception {
        trading.buy(1L, request("owned", 2));
        long before = cash();
        org.mockito.Mockito.when(market.getCurrentKoreanPrice("005930")).thenThrow(new java.io.IOException("upstream"));
        for (String side : List.of("buy", "sell")) {
            mvc.perform(post("/api/paper/orders/" + side).header("Authorization", auth(1))
                    .contentType("application/json")
                    .content("{\"requestId\":\"failed-" + side + "\",\"symbol\":\"005930\",\"quantity\":1}"))
                    .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("QUOTE_UNAVAILABLE"));
        }
        org.mockito.Mockito.doReturn(0L).when(market).getCurrentKoreanPrice("005930");
        assertThrows(com.investome.api.exception.ApiException.class, () -> trading.buy(1L, request("zero", 1)));
        assertEquals(before, cash());
        assertEquals(2, trading.getHoldings(1L).get(0).quantity());
        assertEquals(1, trading.getOrders(1L).size());
    }

    @Test
    void expandedCatalogQuotesChartsAndTradingUseSelectedSymbol() throws Exception {
        org.mockito.Mockito.when(market.getCurrentKoreanPrice("035420")).thenReturn(200_000L);
        org.mockito.Mockito.when(market.getKoreanDailyChart("035420")).thenReturn(List.of(
            new com.investome.api.market.StockMarketService.DailyCandle("2026-09-21", 190000, 205000, 189000, 200000, 10000)));
        mvc.perform(get("/api/paper/symbols").header("Authorization", auth(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(30));
        mvc.perform(get("/api/paper/quotes/035420").header("Authorization", auth(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.symbol").value("035420"))
                .andExpect(jsonPath("$.price").value(200000));
        mvc.perform(get("/api/paper/charts/035420").header("Authorization", auth(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.candles[0].close").value(200000));
        var result = trading.buy(1L, new BuyOrderRequest("naver", "035420", 2));
        assertEquals("035420", result.symbol());
        assertEquals(9_600_000L, cash());
        assertEquals(2, trading.getHoldings(1L).get(0).quantity());
        for (String path : List.of("symbols", "quotes/035420", "charts/035420"))
            mvc.perform(get("/api/paper/" + path)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/paper/charts/999999").header("Authorization", auth(1)))
                .andExpect(status().isBadRequest());
    }

    private boolean sellOrReject(String id) {
        try { trading.sell(1L, request(id, 1)); return true; }
        catch (BadRequestException e) { return false; }
    }
    private boolean buyOrReject(String requestId) {
        try {
            trading.buy(1L, request(requestId, 1));
            return true;
        } catch (BadRequestException e) {
            return false;
        }
    }
    @Test void delistedFromTop30CanSellAndReplayButCannotBuyMore() {
        trading.buy(1L, request("before-removal", 2));
        org.mockito.Mockito.doThrow(new BadRequestException("TOP30 only")).when(universe).requireBuyable("005930");
        assertEquals(2, trading.buy(1L, request("before-removal", 2)).quantity());
        assertThrows(BadRequestException.class, () -> trading.buy(1L, request("after-removal", 1)));
        assertEquals(1, trading.sell(1L, request("sell-removed", 1)).quantity());
        assertEquals(1, trading.getHoldings(1L).get(0).quantity());
    }
    @Autowired com.investome.api.user.UserService users;
    @Test void existingLoginAndRefreshTokensWorkWithPaperAndLegacyFeatures() throws Exception {
        String suffix = java.util.UUID.randomUUID().toString();
        String email = "integration-" + suffix + "@example.invalid";
        users.signup(email, "integration-test-password", "integration-" + suffix);
        var login = users.login(email, "integration-test-password", true);
        String header = "Bearer " + login.getAccessToken();
        mvc.perform(post("/api/paper/account").header("Authorization", header)).andExpect(status().isOk());
        mvc.perform(get("/api/paper/account").header("Authorization", header)).andExpect(status().isOk());
        mvc.perform(get("/api/watchlist").header("Authorization", header)).andExpect(status().isOk());
        mvc.perform(get("/api/notifications").header("Authorization", header)).andExpect(status().isOk());
        var refreshed = users.refresh(login.getRefreshToken());
        mvc.perform(get("/api/paper/account").header("Authorization", "Bearer " + refreshed.getAccessToken())).andExpect(status().isOk());
        mvc.perform(get("/api/watchlist")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
    }
}