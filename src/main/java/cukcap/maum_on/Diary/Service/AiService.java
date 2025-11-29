package cukcap.maum_on.Diary.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import cukcap.maum_on.Diary.Dto.AiResponse;
import cukcap.maum_on.Diary.Dto.UnifiedChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    // AI 서버 기본 주소 (Koyeb)
    private final String AI_BASE_URL = "http://3.107.0.206:8000";
    private final String DIARY_REPLY_PATH = "/diary/diary/reply";
    private final String CHAT_SUMMARY_PATH = "/chat-diary/chat-to-diary";

    private final ObjectMapper objectMapper;

    public AiResponse analyzeDiaryText(Long userId, String date, String text) {
        String url = AI_BASE_URL + DIARY_REPLY_PATH;

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("text", text);
        requestBody.put("user_id", String.valueOf(userId)); // Long -> String 변환
        requestBody.put("date", date);
        requestBody.put("meta", new HashMap<>()); // 빈 객체라도 보내는 게 안전함

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            log.info("AI 요청 Body: {}", requestBody);

            ResponseEntity<AiResponse> response = restTemplate.postForEntity(url, entity, AiResponse.class);
            return response.getBody();

        } catch (Exception e) {
            log.error("AI 일기 분석 요청 실패. URL: {}, Error: {}", url, e.getMessage());

            // 실패 시 기본값 생성 (DTO 구조 변경 반영)
            AiResponse.Analysis fallbackAnalysis = new AiResponse.Analysis();
            fallbackAnalysis.setEmotions(Collections.singletonList("normal")); // 기본 감정 설정

            return AiResponse.builder()
                    .reply("AI 서버 연결에 실패하여 답장을 가져오지 못했어요.") // reply_normal 대응
                    .analysis(fallbackAnalysis)
                    .build();
        }
    }

    public String summarizeChatLog(UnifiedChatResponse chatData, String meHint) {
        String url = AI_BASE_URL + CHAT_SUMMARY_PATH;
        RestTemplate restTemplate = new RestTemplate();

        try {
            // 1. DTO -> JSON String -> Byte Array 변환 (파일처럼 보내기 위해)
            String jsonContent = objectMapper.writeValueAsString(chatData);
            ByteArrayResource fileResource = new ByteArrayResource(jsonContent.getBytes(StandardCharsets.UTF_8)) {
                @Override
                public String getFilename() {
                    return "chat_log.json"; // 파일명 지정
                }
            };

            // 2. Header 설정 (Multipart)
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            // 3. Body 설정
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", fileResource); // AI 서버가 요구하는 key: "file"
            if (meHint != null && !meHint.isEmpty()) {
                body.add("me_hint", meHint); // AI 서버가 요구하는 key: "me_hint"
            }

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // 4. 요청 및 응답 (String 반환)
            log.info("AI 채팅 요약 요청 시작. URL: {}", url);
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                // 응답 값이 따옴표로 감싸진 JSON String일 수 있으므로 처리 (예: "요약내용")
                String result = response.getBody();
                // 혹시 앞뒤에 따옴표가 있다면 제거 (선택 사항)
                if (result.startsWith("\"") && result.endsWith("\"")) {
                    result = result.substring(1, result.length() - 1);
                }
                return result;
            }

        } catch (Exception e) {
            log.error("AI 채팅 요약 요청 실패. Error: {}", e.getMessage());
        }

        return "채팅 요약에 실패했습니다."; // 실패 시 기본 문구
    }
}