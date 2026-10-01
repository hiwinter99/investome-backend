package com.investome.api.paper;

import com.investome.api.exception.BadRequestException;
import com.investome.api.exception.ConflictException;
import com.investome.api.exception.ResourceNotFoundException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaperTradingService {
    private final PaperAccountRepository accounts;
    private final PaperHoldingRepository holdings;
    private final PaperOrderRepository orders;
    private final PaperQuoteProvider quotes;
    private final Validator validator;
    private final PaperUniverseService universe;
    private final org.springframework.transaction.support.TransactionTemplate transaction;

    public PaperOrderResponse buy(Long userId, BuyOrderRequest request) {
        return execute(userId, request, PaperOrder.Side.BUY);
    }

    public PaperOrderResponse sell(Long userId, BuyOrderRequest request) {
        return execute(userId, request, PaperOrder.Side.SELL);
    }

    private PaperOrderResponse execute(Long userId, BuyOrderRequest request, PaperOrder.Side side) {
        if (request == null || !validator.validate(request).isEmpty()) {
            throw new BadRequestException("종목코드, 수량, 요청 ID를 확인해 주세요.");
        }
        PaperAccount current = accountOf(userId);
        var completed = orders.findByAccount_IdAndRequestId(current.getId(), request.requestId());
        if (completed.isPresent()) return replay(completed.get(), request, side);
        if (side == PaperOrder.Side.BUY) universe.requireBuyable(request.symbol());
        // Remote I/O finishes before taking a database lock or opening a write transaction.
        PaperQuoteProvider.Quote quote = quotes.quoteOf(request.symbol());
        return transaction.execute(status -> executeLocked(userId, request, side, quote));
    }

    private PaperOrderResponse replay(PaperOrder order, BuyOrderRequest request, PaperOrder.Side side) {
        if (order.getSide() != side || !order.getSymbol().equals(request.symbol()) || order.getQuantity() != request.quantity()) {
            throw new ConflictException("같은 요청 ID를 다른 주문에 사용할 수 없습니다.");
        }
        return PaperOrderResponse.from(order);
    }

    private PaperOrderResponse executeLocked(Long userId, BuyOrderRequest request, PaperOrder.Side side, PaperQuoteProvider.Quote quote) {
        // Serialize account mutations BEFORE inspecting the idempotency record or balance.
        PaperAccount account = accounts.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("모의계좌를 먼저 개설해 주세요."));
        var existing = orders.findByAccount_IdAndRequestId(account.getId(), request.requestId());
        if (existing.isPresent()) return replay(existing.get(), request, side);
        // A quote may age while waiting for a busy account lock. Refuse stale executions.
        if (quote.fetchedAt().isBefore(java.time.Instant.now().minusSeconds(10))) throw PaperQuoteProvider.unavailable();
        long price = quote.price();
        long total = Math.multiplyExact(price, request.quantity().longValue());
        var holding = holdings.findByAccount_IdAndSymbol(account.getId(), request.symbol());
        if (side == PaperOrder.Side.BUY) {
            account.debit(total);
            if (holding.isPresent()) {
                holding.get().buyMore(request.quantity(), BigDecimal.valueOf(price));
            } else {
                holdings.save(new PaperHolding(account, request.symbol(), request.quantity(), BigDecimal.valueOf(price)));
            }
        } else {
            PaperHolding owned = holding.orElseThrow(() -> new BadRequestException("보유하지 않은 종목입니다."));
            owned.sell(request.quantity());
            account.credit(total);
            if (owned.getQuantity() == 0) holdings.delete(owned);
        }
        PaperOrder order = orders.saveAndFlush(new PaperOrder(account, request, price, total, side, quote));
        return PaperOrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public List<PaperHoldingResponse> getHoldings(Long userId) {
        return holdings.findAllByAccount_Id(accountOf(userId).getId()).stream()
                .sorted(Comparator.comparing(PaperHolding::getSymbol))
                .map(PaperHoldingResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PaperOrderResponse> getOrders(Long userId) {
        return orders.findTop100ByAccount_IdOrderByIdDesc(accountOf(userId).getId()).stream()
                .map(PaperOrderResponse::from).toList();
    }

    private PaperAccount accountOf(Long userId) {
        return accounts.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("모의계좌를 먼저 개설해 주세요."));
    }
}