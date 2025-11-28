package cukcap.maum_on.MyPage.Service;

import cukcap.maum_on.Home.Entity.MonthlySummary;
import cukcap.maum_on.Home.Repository.DiaryRepository;
import cukcap.maum_on.Home.Repository.MonthlySummaryRepository;
import cukcap.maum_on.MyPage.Dto.MyPageResponse;
import cukcap.maum_on.OAuth.Entity.User;
import cukcap.maum_on.OAuth.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

@Service
@RequiredArgsConstructor
public class MyPageService {

    private final UserRepository userRepository;
    private final DiaryRepository diaryRepository;
    private final MonthlySummaryRepository monthlySummaryRepository;

    @Transactional(readOnly = true)
    public MyPageResponse getMyPageInfo(Long userId) {
        // 1. 유저 정보 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 2. 총 일기 작성 일수 조회
        long diaryCount = diaryRepository.countByUserId(userId);

        // 3. 이번 달 평균 마음 온도 조회
        YearMonth thisMonth = YearMonth.now();
        LocalDate summaryDate = thisMonth.atDay(1); // 해당 월의 1일 기준

        MonthlySummary monthlySummary = monthlySummaryRepository.findByUserIdAndSummaryMonth(userId, summaryDate)
                .orElse(null);

        // 데이터가 없으면 기본값 36.5도, 있으면 저장된 평균 온도 사용
        BigDecimal avgTemp = (monthlySummary != null && monthlySummary.getAvgTemp() != null)
                ? monthlySummary.getAvgTemp()
                : BigDecimal.valueOf(36.5);

        // 4. 응답 DTO 생성
        return MyPageResponse.builder()
                .nickname(user.getNickname())
                .email(user.getEmail())
                .diaryCount(diaryCount)
                .thisMonthAvgTemp(avgTemp)
                .build();
    }
}