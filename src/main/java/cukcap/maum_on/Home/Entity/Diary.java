package cukcap.maum_on.Home.Entity;

import cukcap.maum_on.OAuth.Entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "Diary", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_date", columnNames = {"user_id", "diary_date"})
})
public class Diary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "diary_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "diary_date", nullable = false)
    private LocalDate diaryDate;

    @Column(length = 20)
    private String emotion;

    @Column(name = "write_diary", columnDefinition = "TEXT")
    private String writeDiary;

    @Column(name = "draw_url")
    private String drawUrl;

    @Column(name = "ai_reply", columnDefinition = "TEXT")
    private String aiReply;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    // 파일 존재 여부 확인을 위한 관계 설정
    @OneToMany(mappedBy = "diary", fetch = FetchType.LAZY)
    @Builder.Default
    private List<DiaryFile> diaryFiles = new ArrayList<>();

    public void updateWriteDiary(String writeDiary) {
        this.writeDiary = writeDiary;
    }
}