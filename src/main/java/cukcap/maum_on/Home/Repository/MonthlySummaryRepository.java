package cukcap.maum_on.Home.Repository;

import cukcap.maum_on.Home.Entity.MonthlySummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface MonthlySummaryRepository extends JpaRepository<MonthlySummary, Long> {
    // 특정 유저의 특정 월(YYYY-MM-01) 요약 정보 조회
    Optional<MonthlySummary> findByUserIdAndSummaryMonth(Long userId, LocalDate summaryMonth);

    void deleteAllByUserId(Long userId);
}