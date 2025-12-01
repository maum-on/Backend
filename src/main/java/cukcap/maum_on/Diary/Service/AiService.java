package cukcap.maum_on.Diary.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import cukcap.maum_on.Diary.Dto.AiPictureResponse;
import cukcap.maum_on.Diary.Dto.AiResponse;
import cukcap.maum_on.Diary.Dto.SttResponse;
import cukcap.maum_on.Diary.Dto.UnifiedChatResponse; // [중요] UnifiedChatResponse import 확인
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

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
    private final String AI_BASE_URL1 = "http://3.107.0.206:8000";
    private final String DIARY_REPLY_PATH = "/diary/diary/reply";
    private final String CHAT_TO_DIARY_PATH = "/chat-diary/chat-to-diary";
    private final String PICTURE_ANALYZE_PATH = "/picture-diary/analyze";

    private final String AI_BASE_URL2 = "http://15.134.86.188:8080";
    private final String STT_PATH = "/diary/stt";
    private final String BOOST_PATH = "/boost/from-json";

    private final ObjectMapper objectMapper;

    // 1. 일기 분석
    public AiResponse analyzeDiaryText(Long userId, String date, String text) {
        String url = AI_BASE_URL1 + DIARY_REPLY_PATH;

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

    // 2. 채팅 -> 일기 변환 (파일 전송)
    public Map<String, Object> chatToDiary(UnifiedChatResponse chatData, String meHint) {
        String url = AI_BASE_URL1 + CHAT_TO_DIARY_PATH;

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

            body.add("thread_id", chatData.getThreadId());

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);
            return response.getBody();

        } catch (Exception e) {
            log.error("AI 채팅 분석 요청 실패: {}", e.getMessage());
            throw new RuntimeException("AI 서버 통신 오류: " + e.getMessage());
        }
    }

    public AiPictureResponse analyzePicture(String imageUrl) {
        String url = AI_BASE_URL1 + PICTURE_ANALYZE_PATH;

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Request Body 생성
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("image_url", imageUrl);

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

        try {
            log.info("AI 그림 분석 요청 URL: {}, Body: {}", url, requestBody);

            ResponseEntity<AiPictureResponse> response = restTemplate.postForEntity(url, entity, AiPictureResponse.class);
            return response.getBody();

        } catch (Exception e) {
            log.error("AI 그림 분석 요청 실패: {}", e.getMessage());
            // 실패 시 null 반환하여 Service에서 처리
            return null;
        }
    }

    public SttResponse convertVoiceToText(MultipartFile audioFile) {
        String url = AI_BASE_URL2 + STT_PATH;

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        try {
            // 1. 파일 리소스 생성 (수정된 부분)
            ByteArrayResource fileResource = new ByteArrayResource(audioFile.getBytes()) {
                @Override
                public String getFilename() {
                    // 원본 파일명이 있으면 사용하되, 없으면 기본값 부여
                    String filename = audioFile.getOriginalFilename();
                    if (filename == null || filename.isEmpty()) {
                        return "voice_record.wav"; // 확장자는 실제 포맷에 맞게, 혹은 wav/m4a 등 일반적인 것 사용
                    }
                    return filename;
                }
            };

            // 2. Body 구성 ("audio" 키)
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("audio", fileResource);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // 3. 전송 및 응답
            log.info("AI STT 요청 시작 URL: {}, 파일 크기: {}", url, audioFile.getSize());
            ResponseEntity<SttResponse> response = restTemplate.postForEntity(url, requestEntity, SttResponse.class);

            return response.getBody();

        } catch (Exception e) {
            log.error("AI STT 변환 실패: {}", e.getMessage());
            throw new RuntimeException("음성 변환 중 오류가 발생했습니다.");
        }
    }

    public String sendDiaryToBoost(Object requestDto) {
        String url = AI_BASE_URL2 + BOOST_PATH;

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Object> entity = new HttpEntity<>(requestDto, headers);

        try {
            log.info("AI Boost 요청 Body: {}", requestDto);

            // AI 서버가 String(단순 문자열)을 반환한다고 가정
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            return response.getBody();

        } catch (Exception e) {
            log.error("AI Boost 요청 실패: {}", e.getMessage());
            return "AI 응원 메시지를 가져오는데 실패했어요.";
        }
    }
}