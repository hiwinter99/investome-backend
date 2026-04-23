package com.investome.api.assetcommunity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssetCommentRepository extends JpaRepository<AssetComment, Long> {

    List<AssetComment> findTop100ByMarketAndSymbolOrderByCreatedAtDescIdDesc(String market, String symbol);
}
