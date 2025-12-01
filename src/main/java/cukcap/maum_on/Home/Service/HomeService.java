package cukcap.maum_on.Home.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import cukcap.maum_on.Diary.Service.AiService;
import cukcap.maum_on.Home.Dto.AiBoostRequest;
import cukcap.maum_on.Home.Dto.HomeResponse;
import cukcap.maum_on.Home.Entity.Diary;
import cukcap.maum_on.Home.Entity.DiaryFile;
import cukcap.maum_on.Home.Entity.MonthlySummary;
import cukcap.maum_on.Home.Repository.DiaryRepository;
import cukcap.maum_on.Home.Repository.MonthlySummaryRepository;
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
    private final ObjectMapper objectMapper; // JSON String 파싱용
    private final AiService aiService;

    @Transactional(readOnly = true)
    public HomeResponse getHomeData(Long userId, String todayStr) {
        // 1. 날짜 파싱 (todayStr: "2025.09.09")
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");
        LocalDate today = LocalDate.parse(todayStr, formatter);

        // 2. 해당 월의 시작일과 종료일 계산 (2025.09.01 ~ 2025.09.30)
        YearMonth yearMonth = YearMonth.from(today);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        // 3. MonthlySummary 조회 (없을 수도 있음, 기준일은 매월 1일)
        Optional<MonthlySummary> summaryOpt = monthlySummaryRepository.findByUserIdAndSummaryMonth(userId, startDate);

        // 4. 이번 달의 모든 일기 조회
        List<Diary> diaryList = diaryRepository.findAllByUserIdAndDiaryDateBetween(userId, startDate, endDate);

        // 5. DTO 데이터 조립 - 기본값 설정
        BigDecimal temperature = BigDecimal.valueOf(36.5);
        Map<String, Integer> emotions = new HashMap<>();
        String recommendText = "데이터가 충분하지 않아 추천을 생성할 수 없어요.";

        // 5-1. MonthlySummary가 있으면 덮어쓰기
        if (summaryOpt.isPresent()) {
            MonthlySummary summary = summaryOpt.get();
            if (summary.getAvgTemp() != null) temperature = summary.getAvgTemp();
            if (summary.getRecommendText() != null) recommendText = summary.getRecommendText();

            // DB에 저장된 JSON String -> Map 변환
            if (summary.getEmotionsJson() != null) {
                try {
                    emotions = objectMapper.readValue(summary.getEmotionsJson(), new TypeReference<Map<String, Integer>>() {});
                } catch (Exception e) {
                    log.error("감정 JSON 파싱 실패", e);
                }
            }
        }

        // 6. 날짜별 일기 존재 여부 (diary_existence) 맵핑
        Map<String, HomeResponse.DiaryStatusDto> diaryExistenceMap = new HashMap<>();

        for (Diary diary : diaryList) {
            String dateKey = diary.getDiaryDate().format(formatter); // "2025.09.09" 키 생성

            HomeResponse.DiaryStatusDto status = HomeResponse.DiaryStatusDto.builder()
                    .write(diary.getWriteDiary() != null && !diary.getWriteDiary().isEmpty()) // 글 내용 있으면 true
                    .files(diary.getDiaryFiles() != null && !diary.getDiaryFiles().isEmpty()) // 파일 리스트 있으면 true
                    .draw(diary.getDrawUrl() != null && !diary.getDrawUrl().isEmpty()) // 그림 URL 있으면 true
                    .emotion(diary.getEmotion() == null ? "empty" : diary.getEmotion())
                    .build();

            diaryExistenceMap.put(dateKey, status);
        }

        // 7. 최종 반환
        return HomeResponse.builder()
                .temperature(temperature)
                .emotions(emotions)
                .diaryExistence(diaryExistenceMap)
                .activityRecommend(recommendText)
                .psychologicalTest(null) // 추후 개발
                .build();
    }

    @Transactional(readOnly = true)
    public String getBoostMessage(Long userId, String todayStr) {
        // 1. 날짜 계산 (오늘 - 1일 = 어제)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");
        LocalDate today = LocalDate.parse(todayStr, formatter);
        LocalDate yesterday = today.minusDays(1);

        // 2. 어제 일기 조회
        Optional<Diary> diaryOpt = diaryRepository.findByUserIdAndDiaryDate(userId, yesterday);

        // 3. 데이터가 없을 경우 처리
        if (diaryOpt.isEmpty()) {
            return "어제 작성된 일기가 없어서 응원 메시지를 만들 수 없어요.";
        }

        Diary diary = diaryOpt.get();

        // 4. 파일 요약 리스트 추출
        List<String> fileSummations = diary.getDiaryFiles().stream()
                .map(DiaryFile::getSummaryText)
                .filter(text -> text != null && !text.isEmpty())
                .collect(Collectors.toList());

        // 5. AI 요청 DTO 생성 (규격에 맞춤)
        AiBoostRequest.BoostData boostData = AiBoostRequest.BoostData.builder()
                .emotion(diary.getEmotion() != null ? diary.getEmotion() : "")
                .drawUrl(diary.getDrawUrl() != null ? diary.getDrawUrl() : "")
                .writeDiary(diary.getWriteDiary() != null ? diary.getWriteDiary() : "")
                .fileSummation(fileSummations)
                .aiReply(diary.getAiReply() != null ? diary.getAiReply() : "")
                .aiDrawReply(diary.getAiDrawReply() != null ? diary.getAiDrawReply() : "")
                .build();

        AiBoostRequest request = AiBoostRequest.builder()
                .userId(String.valueOf(userId))
                .code(200)
                .message(yesterday.format(formatter) + " 정보 조회 성공")
                .data(boostData)
                .build();

        // 6. AI 서버로 전송 및 결과 반환
        return aiService.sendDiaryToBoost(request);
    }
}