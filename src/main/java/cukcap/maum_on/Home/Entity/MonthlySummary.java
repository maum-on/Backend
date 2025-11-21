package cukcap.maum_on.Home.Entity;

import cukcap.maum_on.OAuth.Entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "MonthlySummary", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_month", columnNames = {"user_id", "summary_month"})
})
public class MonthlySummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "summary_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "summary_month", nullable = false)
    private LocalDate summaryMonth; // YYYY-MM-01 형식

    @Column(name = "avg_temp")
    private BigDecimal avgTemp;

    @Column(name = "emotions_json", columnDefinition = "JSON")
    private String emotionsJson; // String으로 받고 DTO 변환 시 파싱

    @Column(name = "recommend_text", columnDefinition = "TEXT")
    private String recommendText;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void updateStatistics(BigDecimal avgTemp, String emotionsJson) {
        this.avgTemp = avgTemp;
        this.emotionsJson = emotionsJson;
    }
}