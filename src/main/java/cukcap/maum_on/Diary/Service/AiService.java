package cukcap.maum_on.Diary.Service;

import cukcap.maum_on.Diary.Dto.AiResponse;
import cukcap.maum_on.Diary.Dto.UnifiedChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    // AI 서버 기본 주소 (Koyeb)
    private final String AI_BASE_URL = "https://automatic-loraine-gaeun6707-a9fd2f7a.koyeb.app";
    private final String DIARY_REPLY_PATH = "/diary/diary/reply";
    private final String CHAT_ANALYSIS_PATH = "/chat/analyze";
    
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

    public AiResponse analyzeChatFile(List<UnifiedChatResponse> chatData) {
        String url = AI_BASE_URL + CHAT_ANALYSIS_PATH;

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<List<UnifiedChatResponse>> entity = new HttpEntity<>(chatData, headers);

        try {
            ResponseEntity<AiResponse> response = restTemplate.postForEntity(url, entity, AiResponse.class);
            return response.getBody();

        } catch (Exception e) {
            log.error("AI 채팅 분석 요청 실패: {}", e.getMessage());

            // 실패 시 기본값 생성 (DTO 구조 변경 반영)
            AiResponse.Analysis fallbackAnalysis = new AiResponse.Analysis();
            fallbackAnalysis.setEmotions(Collections.singletonList("normal"));
            fallbackAnalysis.setSummary("채팅 분석에 실패했습니다.");

            return AiResponse.builder()
                    .analysis(fallbackAnalysis)
                    .build();
        }
    }
}