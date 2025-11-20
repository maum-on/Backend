package cukcap.maum_on.Diary.Controller;

import cukcap.maum_on.Diary.Dto.UnifiedChatResponse;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService diaryService;
    private final FileProcessingService fileProcessingService;

    @PostMapping(value = "/write/{userId}/{date}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> writeDiary(
            @AuthenticationPrincipal PrincipalDetails principalDetails,
            @PathVariable Long userId,
            @PathVariable String date, // yyyy.MM.dd
            @RequestParam("text") String text, // 프론트에서 'text' 키로 보낸 일기 내용
            @RequestPart(value = "file", required = false) MultipartFile file // 프론트에서 'file' 키로 보낸 파일 (선택)
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
            @PathVariable String date,
            @RequestPart(value = "kakao", required = false) MultipartFile kakaoFile,
            @RequestPart(value = "insta", required = false) MultipartFile instaFile
    ) {
        // 1. 권한 확인
        if (!principalDetails.getId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("code", 403, "message", "권한이 없습니다."));
        }

        Map<String, Object> responseData = new HashMap<>();
        List<UnifiedChatResponse> processedChats = new ArrayList<>();

        try {
            // 2. 카카오 파일 처리 (있다면)
            if (kakaoFile != null && !kakaoFile.isEmpty()) {
                UnifiedChatResponse kakaoChat = fileProcessingService.processKakaoFile(kakaoFile);
                processedChats.add(kakaoChat);
            }

            // 3. 인스타 파일 처리 (있다면)
            if (instaFile != null && !instaFile.isEmpty()) {
                UnifiedChatResponse instaChat = fileProcessingService.processInstaFile(instaFile);
                processedChats.add(instaChat);
            }

            // 4. 결과 응답 구성 (AI에게 보낼 데이터 리스트)
            responseData.put("code", 200);
            responseData.put("message", "파일 처리 완료 (DB 저장 안 함)");
            responseData.put("data", processedChats); // 이 data 필드 안에 변환된 JSON 객체들이 들어감

            return ResponseEntity.ok(responseData);

        } catch (Exception e) {
            log.error("파일 처리 중 오류 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "파일 처리 실패: " + e.getMessage()));
        }
    }
}