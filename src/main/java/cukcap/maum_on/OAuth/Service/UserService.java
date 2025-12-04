package cukcap.maum_on.OAuth.Service;
import cukcap.maum_on.Diary.Reposiroty.DiaryFileRepository;
import cukcap.maum_on.Home.Entity.Diary;
import cukcap.maum_on.Home.Repository.DiaryRepository;
import cukcap.maum_on.Home.Repository.MonthlySummaryRepository;
import cukcap.maum_on.OAuth.Entity.User;
import cukcap.maum_on.OAuth.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final DiaryRepository diaryRepository;
    private final DiaryFileRepository diaryFileRepository;
    private final MonthlySummaryRepository monthlySummaryRepository;

    @Transactional
    public Long deleteUserAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        Long kakaoId = user.getKakaoId();

        // 1. MonthlySummary 삭제
        monthlySummaryRepository.deleteAllByUserId(userId); // *Repository에 메소드 추가 필요

        // 2. DiaryFile 삭제 (일기를 순회하며 삭제)
        List<Diary> diaries = diaryRepository.findAllByUserId(userId); // *Repository에 메소드 추가 필요
        for (Diary diary : diaries) {
            diaryFileRepository.deleteAllByDiaryId(diary.getId()); // *Repository에 메소드 추가 필요
        }

        // 3. Diary 삭제
        diaryRepository.deleteAllByUserId(userId);

        // 4. User 삭제
        userRepository.delete(user);

        return kakaoId; // 카카오 연결 끊기를 위해 kakaoId 반환
    }

    // 카카오 ID로 회원 탈퇴 처리 (웹훅용)
    @Transactional
    public void deleteUserByKakaoId(Long kakaoId) {
        // 1. 카카오 ID로 유저 찾기
        User user = userRepository.findByKakaoId(kakaoId)
                .orElse(null); // 이미 삭제된 유저일 수도 있음

        if (user != null) {
            // 2. 기존 탈퇴 로직 재사용 (내부 ID로 삭제)
            deleteUserAccount(user.getId());
        }
    }
}