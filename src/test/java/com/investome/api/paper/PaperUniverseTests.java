package com.investome.api.paper;

import com.investome.api.market.*;
import com.investome.api.exception.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
 "spring.datasource.url=jdbc:h2:mem:paper-universe-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
 "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
 "spring.jpa.hibernate.ddl-auto=create-drop", "jwt.secret=paper-universe-test-only-secret-long-enough-for-hs256"
})
class PaperUniverseTests {
 @MockitoBean StockMarketService market;
 @Autowired PaperUniverseService universe;
 @Autowired PaperUniverseRepository repository;
 @Autowired PaperStockCatalog catalog;
 List<MarketItemResponse> ranking;
 @BeforeEach void setup() {
   repository.deleteAll();
   ranking = catalog.all().stream().filter(s -> s.market().equals("KOSPI")).limit(30)
       .map(s -> MarketItemResponse.builder().symbol(s.symbol()).name(s.name()).build()).toList();
 }
 @Test void freezesCompleteRankingAndRejectsOutsideBuy() throws Exception {
   when(market.getFreshKospiTop30()).thenReturn(ranking);
   var first = universe.stocks();
   when(market.getFreshKospiTop30()).thenThrow(new RuntimeException("network"));
   assertEquals(first, universe.stocks());
   assertEquals(30, repository.count());
   verify(market, times(1)).getFreshKospiTop30();
   universe.requireBuyable(first.get(0).symbol());
   assertThrows(BadRequestException.class, () -> universe.requireBuyable("999999"));
 }
 @Test void incompleteSnapshotIsNotSavedAndCanRetry() throws Exception {
   when(market.getFreshKospiTop30()).thenReturn(ranking.subList(0, 29));
   assertThrows(ApiException.class, universe::stocks);
   assertEquals(0, repository.count());
   when(market.getFreshKospiTop30()).thenReturn(ranking);
   assertEquals(30, universe.stocks().size());
 }
 @Test void yesterdaySnapshotDoesNotAuthorizeToday() throws Exception {
   var day = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
   repository.saveAndFlush(new PaperUniverseEntry(day.minusDays(1), "005930", "삼성전자", 0));
   when(market.getFreshKospiTop30()).thenReturn(ranking);
   assertEquals(30, universe.stocks().size());
   assertEquals(30, repository.findBySnapshotDayOrderByPositionAsc(day).size());
 }
 @Test void supportsAuthoritativeRankingSymbolMissingFromBundledCatalog() throws Exception {
   var expanded = new java.util.ArrayList<>(ranking);
   expanded.set(29, MarketItemResponse.builder().symbol("999998").name("신규 상장 종목").build());
   when(market.getFreshKospiTop30()).thenReturn(expanded);
   assertEquals(30, universe.stocks().size());
   assertEquals("신규 상장 종목", catalog.require("999998").name());
 }
}
