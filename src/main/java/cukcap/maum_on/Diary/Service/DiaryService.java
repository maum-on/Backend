package cukcap.maum_on.Diary.Service;

import cukcap.maum_on.Config.S3Service;
import cukcap.maum_on.Diary.Dto.AiResponse;
import cukcap.maum_on.Diary.Reposiroty.DiaryFileRepository;
import cukcap.maum_on.Home.Dto.DiaryDetailResponse;
import cukcap.maum_on.Home.Entity.Diary;
import cukcap.maum_on.Home.Entity.DiaryFile;
import cukcap.maum_on.Home.Repository.DiaryRepository;
import cukcap.maum_on.OAuth.Entity.User;
import cukcap.maum_on.OAuth.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryService {

    private final DiaryRepository diaryRepository;
    private final DiaryFileRepository diaryFileRepository;
    private final UserRepository userRepository;
    private final AiService aiService;
    private final S3Service s3Service; // [추가] 파일 업로드를 위한 서비스

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

        // 3. 텍스트 업데이트
        diary.updateWriteDiary(text);

        // 4. AI 분석 요청 (변경된 DTO 구조 반영)
        if (text != null && !text.isEmpty()) {
            AiResponse aiResult = aiService.analyzeDiaryText(userId, dateStr, text);

            if (aiResult != null) {
                // AiResponse에서 감정과 답장 추출 (수정된 DTO 메서드 사용)
                String extractedEmotion = aiResult.getPrimaryEmotion();
                String extractedReply = aiResult.getReply();

                log.info("AI 응답 수신 완료: 감정={}, 답장={}", extractedEmotion, extractedReply);

                diary.updateEmotion(extractedEmotion);
                diary.updateAiReply(extractedReply);
            }
        }

        // 5. DB 저장 (일기 본문)
        Diary savedDiary = diaryRepository.save(diary);

        // 6. 오디오 파일 처리 (AWS S3 업로드)
        if (audioFile != null && !audioFile.isEmpty()) {
            // S3 업로드 후 URL 반환
            String s3Url = s3Service.uploadFile(audioFile);

            // DB에 파일 정보 저장
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
        // 1. 날짜 및 유저 조회
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");
        LocalDate date = LocalDate.parse(dateStr, formatter);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        // 2. 일기 조회 또는 생성
        Diary diary = diaryRepository.findByUserIdAndDiaryDate(userId, date)
                .orElse(Diary.builder()
                        .user(user)
                        .diaryDate(date)
                        .build());

        // 3. 그림 파일 처리 (AWS S3 업로드)
        if (drawFile != null && !drawFile.isEmpty()) {
            // S3 업로드 후 URL 반환
            String s3Url = s3Service.uploadFile(drawFile);

            // DB 업데이트 (draw_url 컬럼)
            diary.updateDrawUrl(s3Url);
        }

        // 4. DB 저장
        diaryRepository.save(diary);

        return diary.getDrawUrl();
    }
}