package cukcap.maum_on.Diary.Controller;

import cukcap.maum_on.Diary.Dto.SttResponse;
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

    @PostMapping(value = "/stt/{userId}/{date}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadVoiceDiary(
            @AuthenticationPrincipal PrincipalDetails principalDetails,
            @PathVariable Long userId,
            @PathVariable String date,
            @RequestPart("audio") MultipartFile audioFile // "audio" 키로 파일 받음
    ) {
        // 1. 권한 확인
        if (!principalDetails.getId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("code", 403, "message", "권한이 없습니다."));
        }

        if (audioFile == null || audioFile.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", 400, "message", "음성 파일이 없습니다."));
        }

        try {
            // 2. AI 서비스 호출 (STT 변환)
            // (여기서는 DB 저장을 안 하고 변환된 텍스트만 프론트로 돌려줍니다.)
            SttResponse sttResult = aiService.convertVoiceToText(audioFile);

            // 3. 응답 생성
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "음성 변환 성공");
            response.put("data", sttResult); // transcript, diary 포함

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("STT 처리 중 오류", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "음성 변환 실패: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/files/{user_id}/{date}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadChat(
            @AuthenticationPrincipal PrincipalDetails principalDetails,
            @PathVariable("user_id") Long userId,
            @PathVariable("date") String date,

            @RequestPart(value = "file") MultipartFile file,
            @RequestParam(value = "me_hint", required = false) String meHint
    ) {
        // 0. SecurityConfig 체크 (로그인이 안 된 상태로 오면 principalDetails가 null일 수 있음)
        if (principalDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", 401, "message", "로그인이 필요합니다."));
        }

        // 1. 본인 확인 (로그인한 사람 vs URL의 user_id)
        if (!principalDetails.getId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("code", 403, "message", "권한이 없습니다. 본인의 파일만 업로드 가능합니다."));
        }

        try {
            // 2. 서비스 호출
            diaryService.saveChatFile(userId, date, file, meHint);

            // 3. 응답
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "파일 등록 성공");

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("파일 업로드 요청 오류: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("code", 400, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("파일 업로드 중 서버 오류", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "서버 오류: " + e.getMessage()));
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