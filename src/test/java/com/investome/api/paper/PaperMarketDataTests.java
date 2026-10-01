package com.investome.api.paper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investome.api.market.*;
import com.investome.api.exception.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PaperMarketDataTests {
    @Test
    void dailyCandlesAreChronologicalAndZeroPriceDaysAreOmitted() throws Exception {
        MarketHttpClient http = mock(MarketHttpClient.class);
        StockMarketService market = new StockMarketService(http);
        for (String field : new String[]{"kisAppKey", "kisAppSecret", "kisAccessToken"})
            ReflectionTestUtils.setField(market, field, "test-only");
        ReflectionTestUtils.setField(market, "kisBaseUrl", "https://example.invalid");
        ReflectionTestUtils.setField(market, "kisAccessTokenAt", System.currentTimeMillis());
        when(http.fetchJsonNode(anyString(), anyMap())).thenReturn(new ObjectMapper().readTree("""
            {"rt_cd":"0","output2":[
              {"stck_bsop_date":"20260922","stck_oprc":"100","stck_hgpr":"120","stck_lwpr":"90","stck_clpr":"110","acml_vol":"42"},
              {"stck_bsop_date":"20260921","stck_oprc":"100","stck_hgpr":"110","stck_lwpr":"80","stck_clpr":"90","acml_vol":"50"},
              {"stck_bsop_date":"20260920","stck_oprc":"0","stck_hgpr":"0","stck_lwpr":"0","stck_clpr":"0","acml_vol":"0"}]}
            """));
        var candles = market.getKoreanDailyChart("035420");
        assertEquals(2, candles.size());
        assertEquals("2026-09-21", candles.get(0).date());
        assertEquals(110, candles.get(1).close());
        verify(http).fetchJsonNode(contains("FID_INPUT_ISCD=035420"), argThat(headers -> "FHKST03010100".equals(headers.get("tr_id"))));
    }

    @Test
    void chartFailureDoesNotInventDataAndSuccessfulChartsAreCached() throws Exception {
        var catalog = new PaperStockCatalog(new ObjectMapper());
        var market = mock(StockMarketService.class);
        var charts = new PaperChartService(catalog, market);
        when(market.getKoreanDailyChart("035420")).thenThrow(new java.io.IOException("offline"));
        assertThrows(ApiException.class, () -> charts.get("035420"));
        doReturn(java.util.List.of()).when(market).getKoreanDailyChart("035420");
        var chart = charts.get("035420");
        assertEquals(chart, charts.get("035420"));
        verify(market, times(2)).getKoreanDailyChart("035420");
        assertEquals("KOSDAQ", catalog.require("247540").market());
        assertThrows(BadRequestException.class, () -> catalog.require("999999"));
    }
    @Test void rankingMasterIsPackagedWithTheApplication() {
        var market = new StockMarketService(mock(MarketHttpClient.class));
        java.util.Map<?, ?> master = ReflectionTestUtils.invokeMethod(market, "getKospiMasterMap");
        assertNotNull(master);
        assertTrue(master.containsKey("005930"));
        assertTrue(master.size() > 500);
    }
}
