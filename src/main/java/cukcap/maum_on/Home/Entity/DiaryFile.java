package cukcap.maum_on.Home.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "diary_file") // DB 스키마 이름: DiaryFile
public class DiaryFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long fileId;

    // 외래 키: 일기 ID (diary_id)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diary_id")
    private Diary diary;

    @Column(nullable = false, length = 10)
    private String fileType; // 파일 유형 (text, json, audio)

    @Column(nullable = false, length = 255)
    private String fileUrl; // 파일 저장 경로

    @Column(length = 255)
    private String fileName; // 원본 파일 이름

    @Column(columnDefinition = "TEXT")
    private String summaryText; // 파일 요약 내용

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}