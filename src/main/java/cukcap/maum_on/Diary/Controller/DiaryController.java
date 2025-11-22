package cukcap.maum_on.Diary.Controller;

import cukcap.maum_on.Diary.Dto.UnifiedChatResponse;
import cukcap.maum_on.Diary.Service.AiService;
import cukcap.maum_on.Diary.Service.DiaryService;
import cukcap.maum_on.Diary.Service.FileProcessingService;
import cukcap.maum_on.OAuth.Entity.PrincipalDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService diaryService;
    private final FileProcessingService fileProcessingService;
    private final AiService aiService;

    @PostMapping(value = "/write/{userId}/{date}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> writeDiary(
            @AuthenticationPrincipal PrincipalDetails principalDetails,
            @PathVariable Long userId,
            @PathVariable String date, // yyyy.MM.dd
            @RequestParam("text") String text, // 프론트에서 'text' 키로 보낸 일기 내용
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        // 1. 본인 확인
        if (!principalDetails.getId().equals(userId)) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 403);
            error.put("message", "권한이 없습니다.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }

        try {
            // 2. 서비스 호출
            Long diaryId = diaryService.saveDiary(userId, date, text, file);

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "일기 저장 성공");
            response.put("diaryId", diaryId);

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("파일 저장 중 오류 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "파일 저장 실패"));
        } catch (Exception e) {
            log.error("일기 저장 중 오류 발생", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("code", 400, "message", e.getMessage()));
        }
    }

    @PostMapping(value = "/files/{userId}/{date}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadChatFiles(
            @AuthenticationPrincipal PrincipalDetails principalDetails,
            @PathVariable Long userId,
            @PathVariable("date") String dateStr,
            @RequestPart(value = "kakao", required = false) MultipartFile kakaoFile,
            @RequestPart(value = "insta", required = false) MultipartFile instaFile
    ) {
        if (!principalDetails.getId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("code", 403, "message", "권한이 없습니다."));
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");
            LocalDate targetDate = LocalDate.parse(dateStr, formatter);

            UnifiedChatResponse filteredChat = null;

            if (kakaoFile != null && !kakaoFile.isEmpty()) {
                filteredChat = fileProcessingService.processKakaoFile(kakaoFile, targetDate);
            } else if (instaFile != null && !instaFile.isEmpty()) {
                filteredChat = fileProcessingService.processInstaFile(instaFile, targetDate);
            } else {
                return ResponseEntity.badRequest().body(Map.of("message", "업로드할 파일이 없습니다."));
            }

            if (filteredChat.getMessages().isEmpty()) {
                return ResponseEntity.ok(Map.of("code", 200, "message", "해당 날짜의 대화 내용이 없습니다."));
            }

            String threadIdVal = String.valueOf(userId);
            filteredChat.setThreadId(threadIdVal);
            log.info("Setting thread_id for AI: {}", filteredChat.getThreadId());

            // DTO(JSON 내부)에 thread_id 값을 넣어줍니다.
            filteredChat.setThreadId(String.valueOf(userId));

            // AI로 전송 (me_hint만 전달)
            String meHint = principalDetails.getUser().getNickname();
            Map<String, Object> aiResult = aiService.chatToDiary(filteredChat, meHint);

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "채팅 분석 성공");
            response.put("data", aiResult);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("파일 처리 중 오류", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "오류: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/draw/{userId}/{date}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadDraw(
            @AuthenticationPrincipal PrincipalDetails principalDetails,
            @PathVariable Long userId,
            @PathVariable String date,
            @RequestPart(value = "file") MultipartFile file // 프론트에서 'file' 키로 보낸 이미지
    ) {
        // 1. 본인 확인
        if (!principalDetails.getId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("code", 403, "message", "권한이 없습니다."));
        }

        try {
            // 2. 서비스 호출 (그림 저장)
            String savedUrl = diaryService.saveDraw(userId, date, file);

            // 3. 응답
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "그림 저장 성공");
            response.put("drawUrl", savedUrl); // 저장된 경로 반환

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("그림 파일 저장 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "서버 저장소 오류"));
        } catch (Exception e) {
            log.error("그림 등록 중 오류", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("code", 400, "message", e.getMessage()));
        }
    }
}