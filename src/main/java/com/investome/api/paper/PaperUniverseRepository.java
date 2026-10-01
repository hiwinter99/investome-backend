package com.investome.api.paper;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
public interface PaperUniverseRepository extends JpaRepository<PaperUniverseEntry, Long> {
    List<PaperUniverseEntry> findBySnapshotDayOrderByPositionAsc(LocalDate day);
}
