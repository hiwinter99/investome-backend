package com.investome.api.assetcommunity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "asset_comments")
public class AssetComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String market;

    @Column(nullable = false, length = 40)
    private String symbol;

    @Column(nullable = false, length = 120)
    private String assetName;

    @Column(nullable = false)
    private Long authorId;

    @Column(nullable = false, length = 100)
    private String authorNickname;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
