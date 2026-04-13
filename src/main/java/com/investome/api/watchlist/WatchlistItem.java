package com.investome.api.watchlist;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(
        name = "watchlist_item",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_watchlist_user_market_symbol",
                        columnNames = {"user_id", "market", "symbol"}
                )
        }
)
public class WatchlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "market", nullable = false, length = 30)
    private String market;

    @Column(name = "symbol", nullable = false, length = 50)
    private String symbol;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "display_name_en", length = 150)
    private String displayNameEN;

    @Column(name = "icon_url", length = 500)
    private String iconUrl;

    @Column(name = "coin_id", length = 100)
    private String coinId;

    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt = LocalDateTime.now();

    @PrePersist
    public void onCreate() {
        if (addedAt == null) {
            addedAt = LocalDateTime.now();
        }
    }
}