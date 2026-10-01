package com.investome.api.paper;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

/** Immutable successful execution record; rejected requests are not persisted in this version. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "paper_orders", uniqueConstraints = @UniqueConstraint(
        name = "uk_paper_order_account_request", columnNames = {"account_id", "request_id"}))
public class PaperOrder {
    public enum Side { BUY, SELL }
    public enum Status { FILLED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private PaperAccount account;
    @Column(name = "request_id", nullable = false, length = 64, updatable = false)
    private String requestId;
    @Column(nullable = false, length = 20, updatable = false)
    private String symbol;
    @Column(nullable = false, updatable = false)
    private int quantity;
    @Column(nullable = false, updatable = false)
    private long price;
    @Column(nullable = false, updatable = false)
    private long totalAmount;
    @Column(nullable = false, updatable = false)
    private long cashBalanceAfter;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16, updatable = false)
    private Side side;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16, updatable = false)
    private Status status;
    @Column(nullable = false, length = 32, updatable = false)
    private String priceSource;
    @Column(nullable = false, updatable = false)
    private Instant executedAt;

    @Column(updatable = false)
    private Instant quoteFetchedAt;

    public PaperOrder(PaperAccount account, BuyOrderRequest request, long price, long totalAmount, Side side, PaperQuoteProvider.Quote quote) {
        this.account = account;
        this.requestId = request.requestId();
        this.symbol = request.symbol();
        this.quantity = request.quantity();
        this.price = price;
        this.totalAmount = totalAmount;
        this.cashBalanceAfter = account.getCashBalance();
        this.side = side;
        this.status = Status.FILLED;
        this.priceSource = quote.source();
        this.quoteFetchedAt = quote.fetchedAt();
        this.executedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }
}