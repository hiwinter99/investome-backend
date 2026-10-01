package com.investome.api.paper;

import jakarta.persistence.*;
import com.investome.api.exception.BadRequestException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "paper_accounts")
public class PaperAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    private Long userId;

    @Column(name = "cash_balance", nullable = false)
    private long cashBalance;

    public PaperAccount(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("사용자 ID가 올바르지 않습니다.");
        }
        this.userId = userId;
        this.cashBalance = 10_000_000L;
    }

    public void credit(long amount) {
        if (amount <= 0) throw new BadRequestException("입금 금액은 양수여야 합니다.");
        cashBalance = Math.addExact(cashBalance, amount);
    }

    public void debit(long amount) {
        if (amount <= 0) {
            throw new BadRequestException("차감 금액은 양수여야 합니다.");
        }

        if (cashBalance < amount) {
            throw new BadRequestException("현금 잔액이 부족합니다.");
        }

        cashBalance -= amount;
    }
}
