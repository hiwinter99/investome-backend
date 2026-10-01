package com.investome.api.paper;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Entity @Getter @NoArgsConstructor
@Table(name = "paper_universe", uniqueConstraints = @UniqueConstraint(columnNames = {"snapshot_day", "symbol"}))
public class PaperUniverseEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "snapshot_day", nullable = false) private LocalDate snapshotDay;
    @Column(nullable = false) private String symbol;
    @Column(nullable = false) private String name;
    private int position;
    public PaperUniverseEntry(LocalDate day, String symbol, String name, int position) {
        this.snapshotDay = day; this.symbol = symbol; this.name = name; this.position = position;
    }
}
