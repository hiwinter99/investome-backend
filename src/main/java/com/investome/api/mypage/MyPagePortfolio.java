package com.investome.api.mypage;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "my_page_portfolio")
public class MyPagePortfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "target_amount")
    private Long targetAmount;

    @Lob
    @Column(name = "holdings_json", columnDefinition = "TEXT", nullable = false)
    private String holdingsJson = "[]";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (holdingsJson == null || holdingsJson.isBlank()) holdingsJson = "[]";
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (holdingsJson == null || holdingsJson.isBlank()) holdingsJson = "[]";
    }
}