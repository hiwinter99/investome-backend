package com.investome.api.market;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(
        name = "market_rank_snapshot_item",
        indexes = {
                @Index(name = "idx_market_rank_snapshot_market_date", columnList = "market,snapshot_date"),
                @Index(name = "idx_market_rank_snapshot_market_symbol", columnList = "market,symbol")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_market_rank_snapshot_market_date_rank",
                        columnNames = {"market", "snapshot_date", "rank_order"}
                )
        }
)
public class MarketRankSnapshotItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "market", nullable = false, length = 30)
    private String market;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "rank_order", nullable = false)
    private Integer rankOrder;

    @Column(name = "symbol", nullable = false, length = 50)
    private String symbol;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "display_name_en", length = 200)
    private String displayNameEN;

    @Column(name = "icon_url", length = 500)
    private String iconUrl;

    @Column(name = "cap_krw")
    private Long capKRW;

    @Column(name = "source", length = 50)
    private String source;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
