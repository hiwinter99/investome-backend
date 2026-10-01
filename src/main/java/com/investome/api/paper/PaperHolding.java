package com.investome.api.paper;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "paper_holdings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_paper_holding_account_symbol",
                columnNames = {"account_id", "symbol"}
        )
)
public class PaperHolding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private PaperAccount account;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false)
    private int quantity;

    @Column(
            name = "average_price",
            nullable = false,
            precision = 19,
            scale = 4
    )
    private BigDecimal averagePrice;

    public PaperHolding(
            PaperAccount account,
            String symbol,
            int quantity,
            BigDecimal price
    ) {
        if (account == null || symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("계좌와 종목코드가 필요합니다.");
        }
        validatePurchase(quantity, price);

        this.account = account;
        this.symbol = symbol;
        this.quantity = quantity;
        this.averagePrice = price.setScale(4, RoundingMode.HALF_UP);
    }

    public void sell(int soldQuantity) {
        if (soldQuantity <= 0 || soldQuantity > quantity) {
            throw new com.investome.api.exception.BadRequestException("매도 가능한 수량이 부족합니다.");
        }
        quantity -= soldQuantity;
    }

    public void buyMore(int addedQuantity, BigDecimal price) {
        validatePurchase(addedQuantity, price);

        int newQuantity = Math.addExact(this.quantity, addedQuantity);

        BigDecimal oldCost = this.averagePrice
                .multiply(BigDecimal.valueOf(this.quantity));

        BigDecimal addedCost = price
                .multiply(BigDecimal.valueOf(addedQuantity));

        this.averagePrice = oldCost.add(addedCost)
                .divide(
                        BigDecimal.valueOf(newQuantity),
                        4,
                        RoundingMode.HALF_UP
                );

        this.quantity = newQuantity;
    }

    private static void validatePurchase(int quantity, BigDecimal price) {
        if (quantity <= 0 || price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("수량과 가격은 양수여야 합니다.");
        }
    }
}
