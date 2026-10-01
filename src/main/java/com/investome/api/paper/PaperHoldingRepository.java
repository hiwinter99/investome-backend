package com.investome.api.paper;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaperHoldingRepository
        extends JpaRepository<PaperHolding, Long> {

    Optional<PaperHolding> findByAccount_IdAndSymbol(
            Long accountId,
            String symbol
    );

    List<PaperHolding> findAllByAccount_Id(Long accountId);
}