package com.investome.api.market;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface MarketRankSnapshotItemRepository extends JpaRepository<MarketRankSnapshotItem, Long> {

    List<MarketRankSnapshotItem> findByMarketAndSnapshotDateOrderByRankOrderAsc(String market, LocalDate snapshotDate);

    @Query("""
            select max(item.snapshotDate)
            from MarketRankSnapshotItem item
            where item.market = :market
              and item.snapshotDate <= :snapshotDate
            """)
    LocalDate findLatestSnapshotDate(
            @Param("market") String market,
            @Param("snapshotDate") LocalDate snapshotDate
    );

    @Modifying
    void deleteByMarketAndSnapshotDate(String market, LocalDate snapshotDate);
}
