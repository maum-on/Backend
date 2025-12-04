package cukcap.maum_on.Home.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import cukcap.maum_on.Diary.Service.AiService;
import cukcap.maum_on.Home.Dto.AiBoostRequest;
import cukcap.maum_on.Home.Dto.AiBoostResponse;
import cukcap.maum_on.Home.Dto.HomeResponse;
import cukcap.maum_on.Home.Entity.Diary;
import cukcap.maum_on.Home.Entity.DiaryFile;
import cukcap.maum_on.Home.Entity.MonthlySummary;
import cukcap.maum_on.Home.Repository.DiaryRepository;
import cukcap.maum_on.Home.Repository.MonthlySummaryRepository;
import cukcap.maum_on.OAuth.Entity.User;
import cukcap.maum_on.OAuth.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HomeService {

    private final DiaryRepository diaryRepository;
    private final MonthlySummaryRepository monthlySummaryRepository;
    private final UserRepository userRepository;
    private final AiService aiService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public HomeResponse getHomeData(Long userId, String todayStr) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");
        LocalDate today = LocalDate.parse(todayStr, formatter);

        YearMonth yearMonth = YearMonth.from(today);
        LocalDate startDate = yearMonth.atDay(1);

        Optional<MonthlySummary> summaryOpt = monthlySummaryRepository.findByUserIdAndSummaryMonth(userId, startDate);
        List<Diary> diaryList = diaryRepository.findAllByUserId(userId);

        BigDecimal temperature = BigDecimal.valueOf(36.5);
        String recommendText = "데이터가 충분하지 않아 추천을 생성할 수 없어요.";
        Map<String, Integer> emotions = new HashMap<>();

        if (summaryOpt.isPresent()) {
            MonthlySummary summary = summaryOpt.get();
            if (summary.getAvgTemp() != null) temperature = summary.getAvgTemp();
            if (summary.getRecommendText() != null) recommendText = summary.getRecommendText();

            if (summary.getEmotionsJson() != null) {
                try {
                    emotions = objectMapper.readValue(summary.getEmotionsJson(), new TypeReference<Map<String, Integer>>() {});
                } catch (Exception e) {
                    log.error("감정 JSON 파싱 실패", e);
                }
            }
        }

        Map<String, HomeResponse.DiaryStatusDto> diaryExistenceMap = new HashMap<>();

        for (Diary diary : diaryList) {
            String dateKey = diary.getDiaryDate().format(formatter);
            String emotion = diary.getEmotion() == null ? "empty" : diary.getEmotion();

            HomeResponse.DiaryStatusDto status = HomeResponse.DiaryStatusDto.builder()
                    .write(diary.getWriteDiary() != null && !diary.getWriteDiary().isEmpty())
                    .files(diary.getDiaryFiles() != null && !diary.getDiaryFiles().isEmpty())
                    .draw(diary.getDrawUrl() != null && !diary.getDrawUrl().isEmpty())
                    .emotion(emotion)
                    .build();

            diaryExistenceMap.put(dateKey, status);

            if (emotion != null && !emotion.equals("empty")) {
                emotions.put(emotion, emotions.getOrDefault(emotion, 0) + 1);
            }
        }

        return HomeResponse.builder()
                .temperature(temperature)
                .emotions(emotions)
                .diaryExistence(diaryExistenceMap)
                .activityRecommend(recommendText)
                .psychologicalTest(null)
                .build();
    }

    @Transactional(readOnly = true)
    public AiBoostResponse getBoostMessage(Long userId, String todayStr) {
        // 1. 유저 정보 조회 (닉네임 가져오기 위해)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 2. 날짜 계산 (어제)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");
        LocalDate today = LocalDate.parse(todayStr, formatter);
        LocalDate yesterday = today.minusDays(1);

        // 3. 어제 일기 조회
        Optional<Diary> diaryOpt = diaryRepository.findByUserIdAndDiaryDate(userId, yesterday);

        if (diaryOpt.isEmpty()) {
            return AiBoostResponse.builder()
                    .status("no_diary")
                    .diaryUsed(false)
                    .build();
        }

        Diary diary = diaryOpt.get();

        // 4. 파일 요약 리스트
        List<String> fileSummations = diary.getDiaryFiles().stream()
                .map(DiaryFile::getSummaryText)
                .filter(text -> text != null && !text.isEmpty())
                .collect(Collectors.toList());

        // 5. AI 요청 DTO 생성
        AiBoostRequest.BoostData boostData = AiBoostRequest.BoostData.builder()
                .emotion(diary.getEmotion() != null ? diary.getEmotion() : "")
                .drawUrl(diary.getDrawUrl() != null ? diary.getDrawUrl() : "")
                .writeDiary(diary.getWriteDiary() != null ? diary.getWriteDiary() : "")
                .fileSummation(fileSummations)
                .aiReply(diary.getAiReply() != null ? diary.getAiReply() : "")
                .aiDrawReply(diary.getAiDrawReply() != null ? diary.getAiDrawReply() : "")
                .build();

        AiBoostRequest request = AiBoostRequest.builder()
                //  user_id 필드에 닉네임(이름)을 넣음
                .userId(user.getNickname())
                .code(200)
                .message(yesterday.format(formatter) + " 정보 조회 성공")
                .data(boostData)
                .build();

        // 6. AI 전송 및 결과 반환
        return aiService.sendDiaryToBoost(request);
    }
}