package cukcap.maum_on.Diary.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import cukcap.maum_on.Config.S3Service;
import cukcap.maum_on.Diary.Dto.AiResponse;
import cukcap.maum_on.Diary.Reposiroty.DiaryFileRepository;
import cukcap.maum_on.Home.Dto.DiaryDetailResponse;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryService {

    private final DiaryRepository diaryRepository;
    private final DiaryFileRepository diaryFileRepository;
    private final MonthlySummaryRepository monthlySummaryRepository;
    private final UserRepository userRepository;
    private final AiService aiService;
    private final S3Service s3Service;
    private final ObjectMapper objectMapper; // JSON 변환용

    // 감정별 온도 변화량 설정 (0.5도)
    private static final BigDecimal TEMP_CHANGE = BigDecimal.valueOf(0.5);

    @Transactional(readOnly = true)
    public DiaryDetailResponse getDiaryDetail(Long userId, String dateStr) {
        // 1. 날짜 파싱
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");
        LocalDate date = LocalDate.parse(dateStr, formatter);

        // 2. 일기 조회
        Diary diary = diaryRepository.findByUserIdAndDiaryDate(userId, date)
                .orElseThrow(() -> new IllegalArgumentException("해당 날짜에 작성된 일기가 없습니다."));

        // 3. DTO 변환
        return DiaryDetailResponse.builder()
                .diaryId(diary.getId())
                .date(diary.getDiaryDate().format(formatter))
                .emotion(diary.getEmotion())
                .content(diary.getWriteDiary())
                .drawUrl(diary.getDrawUrl())
                .aiReply(diary.getAiReply())
                .files(diary.getDiaryFiles() != null ?
                        diary.getDiaryFiles().stream()
                                .map(file -> DiaryDetailResponse.FileDto.builder()
                                        .fileId(file.getFileId())
                                        .fileType(file.getFileType())
                                        .fileUrl(file.getFileUrl())
                                        .build())
                                .collect(Collectors.toList())
                        : Collections.emptyList())
                .build();
    }

    @Transactional
    public Long saveDiary(Long userId, String dateStr, String text, MultipartFile audioFile) throws IOException {
        // 1. 날짜 및 유저 조회
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");
        LocalDate date = LocalDate.parse(dateStr, formatter);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 2. 일기 생성 또는 조회
        Diary diary = diaryRepository.findByUserIdAndDiaryDate(userId, date)
                .orElse(Diary.builder()
                        .user(user)
                        .diaryDate(date)
                        .build());

        // [중요] 수정 시 통계 갱신을 위해 '기존 감정' 저장
        String oldEmotion = diary.getEmotion();

        // 3. 텍스트 업데이트
        diary.updateWriteDiary(text);

        String newEmotion = null;

        // 4. AI 분석 요청 및 결과 적용
        if (text != null && !text.isEmpty()) {
            AiResponse aiResult = aiService.analyzeDiaryText(userId, dateStr, text);

            if (aiResult != null) {
                newEmotion = aiResult.getPrimaryEmotion(); // 분석된 새 감정
                String reply = aiResult.getReply();

                log.info("AI 응답 수신 완료: 감정={}, 답장={}", newEmotion, reply);

                diary.updateEmotion(newEmotion);
                diary.updateAiReply(reply);
            }
        }

        // 5. DB 저장 (일기 본문)
        Diary savedDiary = diaryRepository.save(diary);

        // 6. [통계 갱신] 감정이 변경되었거나 새로 추가된 경우 MonthlySummary 업데이트
        if (newEmotion != null && !newEmotion.equals(oldEmotion)) {
            updateMonthlySummary(user, date, oldEmotion, newEmotion);
        }

        // 7. 오디오 파일 처리 (AWS S3 업로드)
        if (audioFile != null && !audioFile.isEmpty()) {
            String s3Url = s3Service.uploadFile(audioFile);

            DiaryFile diaryFile = DiaryFile.builder()
                    .diary(savedDiary)
                    .fileType("audio")
                    .fileUrl(s3Url)
                    .fileName(audioFile.getOriginalFilename())
                    .build();

            diaryFileRepository.save(diaryFile);
        }

        return savedDiary.getId();
    }

    @Transactional
    public String saveDraw(Long userId, String dateStr, MultipartFile drawFile) throws IOException {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");
        LocalDate date = LocalDate.parse(dateStr, formatter);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        Diary diary = diaryRepository.findByUserIdAndDiaryDate(userId, date)
                .orElse(Diary.builder()
                        .user(user)
                        .diaryDate(date)
                        .build());

        if (drawFile != null && !drawFile.isEmpty()) {
            // S3 업로드
            String s3Url = s3Service.uploadFile(drawFile);
            diary.updateDrawUrl(s3Url);
        }

        diaryRepository.save(diary);
        return diary.getDrawUrl();
    }

    private void updateMonthlySummary(User user, LocalDate date, String oldEmotion, String newEmotion) {
        YearMonth yearMonth = YearMonth.from(date);
        LocalDate summaryMonth = yearMonth.atDay(1); // 해당 월의 1일

        // 1. 요약 정보 가져오기 (없으면 생성)
        MonthlySummary summary = monthlySummaryRepository.findByUserIdAndSummaryMonth(user.getId(), summaryMonth)
                .orElse(MonthlySummary.builder()
                        .user(user)
                        .summaryMonth(summaryMonth)
                        .avgTemp(BigDecimal.valueOf(36.5)) // 초기 온도 36.5도
                        .emotionsJson("{}")
                        .build());

        // 2. JSON 문자열 -> Map 변환
        Map<String, Integer> emotionMap = new HashMap<>();
        try {
            if (summary.getEmotionsJson() != null && !summary.getEmotionsJson().isEmpty()) {
                emotionMap = objectMapper.readValue(summary.getEmotionsJson(), new TypeReference<Map<String, Integer>>() {});
            }
        } catch (Exception e) {
            log.error("감정 통계 파싱 실패", e);
        }

        BigDecimal currentTemp = summary.getAvgTemp();
        if (currentTemp == null) currentTemp = BigDecimal.valueOf(36.5);

        // 3. 이전 감정 취소 (수정인 경우: 횟수 차감, 온도 복구)
        if (oldEmotion != null && !oldEmotion.equals("empty") && !oldEmotion.equals("normal")) {
            int count = emotionMap.getOrDefault(oldEmotion, 0);
            if (count > 0) emotionMap.put(oldEmotion, count - 1);

            if (isPositive(oldEmotion)) {
                currentTemp = currentTemp.subtract(TEMP_CHANGE); // 올렸던 거 내림
            } else if (isNegative(oldEmotion)) {
                currentTemp = currentTemp.add(TEMP_CHANGE);      // 내렸던 거 올림
            }
        }

        // 4. 새로운 감정 적용 (횟수 증가, 온도 반영)
        if (newEmotion != null && !newEmotion.equals("empty") && !newEmotion.equals("normal")) {
            emotionMap.put(newEmotion, emotionMap.getOrDefault(newEmotion, 0) + 1);

            if (isPositive(newEmotion)) {
                currentTemp = currentTemp.add(TEMP_CHANGE);      // 긍정: 온도 상승
            } else if (isNegative(newEmotion)) {
                currentTemp = currentTemp.subtract(TEMP_CHANGE); // 부정: 온도 하락
            }
        }

        // 5. 최대/최소 온도 제한 (0 ~ 100도)
        currentTemp = currentTemp.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));

        // 6. 변경된 데이터 저장
        try {
            String updatedJson = objectMapper.writeValueAsString(emotionMap);

            // MonthlySummary 엔티티에 updateStatistics 메서드가 있어야 합니다.
            summary.updateStatistics(currentTemp, updatedJson);

            monthlySummaryRepository.save(summary);

        } catch (Exception e) {
            log.error("월별 통계 저장 실패", e);
        }
    }

    // 긍정 감정 판별
    private boolean isPositive(String emotion) {
        // AI 모델의 출력값에 맞춰 키워드 추가 필요
        return emotion.matches("^(happy|joy|excited|grateful|기쁨|행복|즐거움|신남).*");
    }

    // 부정 감정 판별
    private boolean isNegative(String emotion) {
        // AI 모델의 출력값에 맞춰 키워드 추가 필요
        return emotion.matches("^(sad|angry|anxious|depressed|슬픔|화남|우울|불안).*");
    }
}