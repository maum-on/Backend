package cukcap.maum_on.Diary.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import cukcap.maum_on.Config.S3Service;
import cukcap.maum_on.Diary.Dto.AiPictureResponse;
import cukcap.maum_on.Diary.Dto.AiResponse;
import cukcap.maum_on.Diary.Dto.DiaryAnalyzeResponse;
import cukcap.maum_on.Diary.Dto.UnifiedChatResponse;
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
import java.util.List;
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
    private final FileProcessingService fileProcessingService;

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
                .aiDrawReply(diary.getAiDrawReply())
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

        // 수정 시 통계 갱신을 위해 '기존 감정' 저장
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
    public void saveChatFile(Long userId, String dateStr, MultipartFile file, String meHint) throws IOException {
        // 1. 유저 및 날짜 확인
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");
        LocalDate date = LocalDate.parse(dateStr, formatter);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 2. 일기(Diary) 조회 혹은 생성
        Diary diary = diaryRepository.findByUserIdAndDiaryDate(userId, date)
                .orElseGet(() -> {
                    Diary newDiary = Diary.builder()
                            .user(user)
                            .diaryDate(date)
                            .emotion("normal") // 초기값
                            .build();
                    return diaryRepository.save(newDiary);
                });

        // 수정 전 기존 감정 저장 (통계 갱신용)
        String oldEmotion = diary.getEmotion();

        // 3. 파일 파싱 (메모리 상에서 처리)
        UnifiedChatResponse chatData;
        String fileName = file.getOriginalFilename();

        if (fileName != null && fileName.toLowerCase().endsWith(".json")) {
            chatData = fileProcessingService.processInstaFile(file, date);
        } else {
            chatData = fileProcessingService.processKakaoFile(file, date);
        }

        if (chatData.getMessages().isEmpty()) {
            throw new IllegalArgumentException("해당 날짜의 채팅 내역이 파일에 존재하지 않습니다.");
        }

        // 4. AI 서버로 전송 (요약 요청)
        String hint = (meHint != null && !meHint.isEmpty()) ? meHint : user.getNickname();

        // AI 분석 결과 받기 (Map)
        Map<String, Object> aiResultMap = aiService.chatToDiary(chatData, hint);

        // AI 응답 데이터를 분해하여 각 DB 컬럼에 매핑
        String newEmotion = null;
        String keywordsSummary = "";

        if (aiResultMap != null) {
            // (1) diary_text -> chat_diary 컬럼에 저장
            if (aiResultMap.containsKey("diary_text")) {
                String chatDiaryText = String.valueOf(aiResultMap.get("diary_text"));
                diary.updateChatDiary(chatDiaryText); // writeDiary 덮어쓰기 방지
            }

            // (2) emotion -> Diary 테이블의 emotion
            if (aiResultMap.containsKey("emotion")) {
                newEmotion = String.valueOf(aiResultMap.get("emotion"));
                diary.updateEmotion(newEmotion);
            }

            // (3) keywords -> DiaryFile 테이블의 summary_text
            if (aiResultMap.containsKey("keywords")) {
                Object keywordsObj = aiResultMap.get("keywords");
                // 리스트인 경우 문자열로 변환 (예: [키워드1, 키워드2])
                keywordsSummary = keywordsObj.toString();
            }

            log.info("AI 분석 데이터 분산 저장 완료: 감정={}, 키워드={}", newEmotion, keywordsSummary);
        } else {
            keywordsSummary = "AI 요약 실패";
        }

        // 5. Diary 엔티티 저장 (emotion, write_diary 업데이트 반영)
        diaryRepository.save(diary);

        // 6. 감정이 변경되었으면 MonthlySummary 업데이트
        if (newEmotion != null && !newEmotion.equals(oldEmotion)) {
            updateMonthlySummary(user, date, oldEmotion, newEmotion);
        }

        // 7. DiaryFile 저장 (keywords만 summary_text에 저장)
        DiaryFile diaryFile = DiaryFile.builder()
                .diary(diary)
                .fileType("chat_log")
                .fileUrl("NOT_STORED")
                .fileName(fileName)
                .summaryText(keywordsSummary)  // 키워드 리스트만 저장됨
                .build();

        diaryFileRepository.save(diaryFile);
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

        String oldEmotion = diary.getEmotion();
        String newEmotion = null;

        // 3. 그림 파일 처리
        if (drawFile != null && !drawFile.isEmpty()) {
            // (1) S3 업로드
            String s3Url = s3Service.uploadFile(drawFile);
            diary.updateDrawUrl(s3Url);

            // (2) AI 그림 분석 요청
            AiPictureResponse aiRes = aiService.analyzePicture(s3Url);

            // [수정] 변경된 DTO 구조에 맞춰 데이터 추출
            if (aiRes != null && aiRes.getEmotion() != null) {

                newEmotion = aiRes.getEmotion(); // 감정 (happy 등)
                String reason = aiRes.getReason(); // 분석 내용

                // DB 업데이트
                // aiDrawReply 컬럼에 'reason'을 저장
                diary.updateAiDrawReply(reason);

                // 감정 업데이트
                diary.updateEmotion(newEmotion);

                log.info("AI 그림 분석 완료: 감정={}, 내용={}", newEmotion, reason);
            }
        }

        // 4. DB 저장
        diaryRepository.save(diary);

        // 5. 통계 갱신 (기존 로직 유지)
        if (newEmotion != null && !newEmotion.equals(oldEmotion)) {
            updateMonthlySummary(user, date, oldEmotion, newEmotion);
        }

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
        return emotion.matches("^(happy|joy|excited|grateful|기쁨|행복|즐거움|신남).*");
    }

    // 부정 감정 판별
    private boolean isNegative(String emotion) {
        return emotion.matches("^(sad|angry|anxious|depressed|슬픔|화남|우울|불안).*");
    }

    @Transactional(readOnly = true)
    public DiaryAnalyzeResponse getDiaryAnalyzeData(Long userId, String dateStr) {
        // 1. 날짜 파싱
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");
        LocalDate date = LocalDate.parse(dateStr, formatter);

        // 2. 일기 조회 (없으면 예외 발생 혹은 빈 데이터 처리, 여기선 예외 처리)
        Diary diary = diaryRepository.findByUserIdAndDiaryDate(userId, date)
                .orElseThrow(() -> new IllegalArgumentException("해당 날짜에 일기 기록이 없습니다."));

        // 3. file_summation 리스트 생성 (DiaryFile의 summaryText 모음)
        List<String> fileSummations = diary.getDiaryFiles().stream()
                .map(DiaryFile::getSummaryText) // summary_text 추출
                .filter(text -> text != null && !text.isEmpty()) // null이나 빈 값 제외
                .collect(Collectors.toList());

        // 4. 내부 Data 객체 빌드
        DiaryAnalyzeResponse.AnalyzeData data = DiaryAnalyzeResponse.AnalyzeData.builder()
                .emotion(diary.getEmotion())
                .drawUrl(diary.getDrawUrl())
                .writeDiary(diary.getWriteDiary()) // 직접 쓴 일기
                .chatDiary(diary.getChatDiary())   // 채팅 기반 일기
                .fileSummation(fileSummations)
                .aiReply(diary.getAiReply())
                .aiDrawReply(diary.getAiDrawReply())
                .build();

        // 5. 최종 응답 빌드
        return DiaryAnalyzeResponse.builder()
                .code(200)
                .message(date.getMonthValue() + "월 " + date.getDayOfMonth() + "일 정보 조회 성공")
                .data(data)
                .build();
    }
}