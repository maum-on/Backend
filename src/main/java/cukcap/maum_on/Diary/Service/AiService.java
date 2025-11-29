package cukcap.maum_on.Diary.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import cukcap.maum_on.Diary.Dto.AiResponse;
import cukcap.maum_on.Diary.Dto.UnifiedChatResponse; // [중요] UnifiedChatResponse import 확인
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

    // AI 서버 기본 주소
    private final String AI_BASE_URL = "http://3.107.0.206:8000";
    private final String DIARY_REPLY_PATH = "/diary/diary/reply";
    private final String CHAT_TO_DIARY_PATH = "/chat-diary/chat-to-diary";

    private final ObjectMapper objectMapper;

    // 1. 일기 분석
    public AiResponse analyzeDiaryText(Long userId, String date, String text) {
        String url = AI_BASE_URL + DIARY_REPLY_PATH;

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("text", text);
        requestBody.put("user_id", String.valueOf(userId));
        requestBody.put("date", date);
        requestBody.put("meta", new HashMap<>());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            log.info("AI 요청 Body: {}", requestBody);
            ResponseEntity<AiResponse> response = restTemplate.postForEntity(url, entity, AiResponse.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("AI 일기 분석 요청 실패. URL: {}, Error: {}", url, e.getMessage());
            AiResponse.Analysis fallbackAnalysis = new AiResponse.Analysis();
            fallbackAnalysis.setEmotions(Collections.singletonList("normal"));
            return AiResponse.builder()
                    .reply("AI 서버 연결에 실패하여 답장을 가져오지 못했어요.")
                    .analysis(fallbackAnalysis)
                    .build();
        }
    }

    // 2. 채팅 파일 분석 (UnifiedChatResponse 리스트 전송)
    public AiResponse analyzeChatFile(List<UnifiedChatResponse> chatData) {
        String url = AI_BASE_URL + "/chat/analyze"; // (경로 확인 필요)

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<List<UnifiedChatResponse>> entity = new HttpEntity<>(chatData, headers);

        try {
            ResponseEntity<AiResponse> response = restTemplate.postForEntity(url, entity, AiResponse.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("AI 채팅 분석 요청 실패: {}", e.getMessage());
            AiResponse.Analysis fallbackAnalysis = new AiResponse.Analysis();
            fallbackAnalysis.setEmotions(Collections.singletonList("normal"));
            fallbackAnalysis.setSummary("채팅 분석에 실패했습니다.");
            return AiResponse.builder()
                    .analysis(fallbackAnalysis)
                    .build();
        }
    }

    // 3. 채팅 -> 일기 변환 (파일 전송)
    // [중요] 파라미터 타입이 UnifiedChatResponse 이어야 합니다!
    public Map<String, Object> chatToDiary(UnifiedChatResponse chatData, String meHint) {
        String url = AI_BASE_URL + CHAT_TO_DIARY_PATH;

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        // (필요 시) User-Agent 추가
        headers.add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");

        try {
            // DTO -> JSON 변환
            String jsonString = objectMapper.writeValueAsString(chatData);
            log.info("Sending JSON to AI: {}", jsonString);
            byte[] fileContent = jsonString.getBytes(StandardCharsets.UTF_8);

            ByteArrayResource fileResource = new ByteArrayResource(fileContent) {
                @Override
                public String getFilename() {
                    return "filtered_chat.json";
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", fileResource);
            body.add("me_hint", meHint);

            // [중요] thread_id를 Form Data로 추가
            body.add("thread_id", chatData.getThreadId());

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);
            return response.getBody();

        } catch (Exception e) {
            log.error("AI 채팅 분석 요청 실패: {}", e.getMessage());
            throw new RuntimeException("AI 서버 통신 오류: " + e.getMessage());
        }
    }
}