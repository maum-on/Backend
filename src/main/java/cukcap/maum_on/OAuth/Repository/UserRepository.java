package cukcap.maum_on.OAuth.Repository;

import cukcap.maum_on.OAuth.Entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // 카카오 로그인 시 가장 중요한 조회 메서드: kakaoId로 User를 찾음
    Optional<User> findByKakaoId(Long kakaoId);
}