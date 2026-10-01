package com.investome.api.paper;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PaperOrderRepository extends JpaRepository<PaperOrder, Long> {
    Optional<PaperOrder> findByAccount_IdAndRequestId(Long accountId, String requestId);
    List<PaperOrder> findTop100ByAccount_IdOrderByIdDesc(Long accountId);
}