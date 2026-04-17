package com.investome.api.market;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockRankingSnapshotScheduler {

    private final StockRankingSnapshotService stockRankingSnapshotService;

    @Scheduled(cron = "0 5 8 * * *", zone = "Asia/Seoul")
    public void refreshDailySnapshots() {
        stockRankingSnapshotService.refreshTodaySnapshots();
        log.info("Daily market-cap ranking snapshots refreshed");
    }
}
