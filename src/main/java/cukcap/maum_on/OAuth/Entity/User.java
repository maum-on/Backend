package cukcap.maum_on.OAuth.Entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor // 기본 생성자 자동 생성
@Table(name = "user")
@Entity // JPA 엔티티임을 명시
public class User {

    @Id // 기본 키
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 자동 증가
    @Column(name = "user_id")
    private Long id; // 서비스 내 사용자 고유 ID

    // 카카오 로그인 연동을 위한 가장 중요한 식별자
    @Column(nullable = false, unique = true)
    private Long kakaoId; // 카카오 사용자 고유 ID

    @Column(length = 100)
    private String email; // 카카오에서 받은 이메일 (동의 시)

    @Column(nullable = false, length = 50)
    private String nickname; // 카카오에서 받은 닉네임

    @Column(nullable = false, length = 20)
    private String role; // 사용자 권한 (예: ROLE_USER, ROLE_ADMIN)

    @Builder
    public User(Long kakaoId, String email, String nickname, String role) {
        this.kakaoId = kakaoId;
        this.email = email;
        this.nickname = nickname;
        this.role = role;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }
}