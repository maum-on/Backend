package cukcap.maum_on.Diary.Service;

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

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryService {

    private final DiaryRepository diaryRepository;
    private final DiaryFileRepository diaryFileRepository;
    private final UserRepository userRepository;

    // 파일 저장 경로 (프로젝트 루트/uploads/)
    private static final String UPLOAD_DIR = System.getProperty("user.dir") + "/uploads/";

    @Transactional(readOnly = true)
    public DiaryDetailResponse getDiaryDetail(Long userId, String dateStr) {
        // 1. 날짜 파싱 (yyyy.MM.dd)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");
        LocalDate date = LocalDate.parse(dateStr, formatter);

        // 2. 일기 조회 (없으면 예외 발생 대신 빈 DTO 혹은 에러 처리, 여기선 예외 발생)
        Diary diary = diaryRepository.findByUserIdAndDiaryDate(userId, date)
                .orElseThrow(() -> new IllegalArgumentException("해당 날짜에 작성된 일기가 없습니다."));

        // 3. DTO 변환 후 반환
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
        // 1. 날짜 파싱
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");
        LocalDate date = LocalDate.parse(dateStr, formatter);

        // 2. 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        // 3. 일기 조회 또는 생성 (Upsert: 있으면 가져오고 없으면 새로 만듦)
        Diary diary = diaryRepository.findByUserIdAndDiaryDate(userId, date)
                .orElse(Diary.builder()
                        .user(user)
                        .diaryDate(date)
                        .build());

        // 4. 텍스트 내용 업데이트
        diary.updateWriteDiary(text);

        // 5. 일기 저장 (insert or update)
        Diary savedDiary = diaryRepository.save(diary);

        // 6. 오디오 파일 처리 (파일이 있을 경우에만)
        if (audioFile != null && !audioFile.isEmpty()) {
            saveFile(savedDiary, audioFile, "audio");
        }

        return savedDiary.getId();
    }

    private void saveFile(Diary diary, MultipartFile file, String fileType) throws IOException {
        // 1. 저장할 디렉토리 생성
        File directory = new File(UPLOAD_DIR);
        if (!directory.exists()) {
            boolean mkdirs = directory.mkdirs();
            if (!mkdirs) {
                log.error("디렉토리 생성 실패: {}", UPLOAD_DIR);
            }
        }

        // 2. 파일명 중복 방지 (UUID 사용)
        String originalFilename = file.getOriginalFilename();
        String savedFileName = UUID.randomUUID() + "_" + originalFilename;
        String filePath = UPLOAD_DIR + savedFileName;

        // 3. 실제 파일 저장 (로컬)
        file.transferTo(new File(filePath));

        // 4. DB에 파일 정보 저장
        DiaryFile diaryFile = DiaryFile.builder()
                .diary(diary)
                .fileType(fileType)     // "audio", "json" 등
                .fileUrl(filePath)      // 실제 파일 경로 (나중에 S3 URL 등으로 교체 가능)
                .fileName(originalFilename) // 원본 파일명
                .build();

        diaryFileRepository.save(diaryFile);
    }

    @Transactional
    public String saveDraw(Long userId, String dateStr, MultipartFile drawFile) throws IOException {
        // 1. 날짜 파싱
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");
        LocalDate date = LocalDate.parse(dateStr, formatter);

        // 2. 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        // 3. 일기 조회 또는 생성 (그림을 먼저 그릴 수도 있으므로 없으면 생성)
        Diary diary = diaryRepository.findByUserIdAndDiaryDate(userId, date)
                .orElse(Diary.builder()
                        .user(user)
                        .diaryDate(date)
                        .build());

        // 4. 파일 저장 처리
        if (drawFile != null && !drawFile.isEmpty()) {
            // 4-1. 파일명 생성 (중복 방지 UUID)
            String originalFilename = drawFile.getOriginalFilename();
            String savedFileName = UUID.randomUUID() + "_draw_" + originalFilename; // 구분하기 쉽게 _draw_ 추가
            String filePath = UPLOAD_DIR + savedFileName;

            // 4-2. 실제 파일 저장 (로컬)
            // AWS S3 업로드 코드로만 바꾸면 됨
            File directory = new File(UPLOAD_DIR);
            if (!directory.exists()) {
                directory.mkdirs();
            }
            drawFile.transferTo(new File(filePath));

            // 4-3. Diary 엔티티의 draw_url 필드 업데이트
            diary.updateDrawUrl(filePath);
            // 추후 S3 도입 시 filePath 대신 S3 URL을 넣으면 됨
        }

        // 5. DB 저장
        diaryRepository.save(diary);

        return diary.getDrawUrl();
    }
}