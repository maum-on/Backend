package cukcap.maum_on.Diary.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import cukcap.maum_on.Diary.Dto.UnifiedChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileProcessingService {

    private final ObjectMapper objectMapper;

    // 정규표현식: 날짜 라인 (예: --------------- 2025년 9월 5일 금요일 ---------------)
    private static final Pattern DATE_PATTERN = Pattern.compile("^-+ (\\d{4})년 (\\d{1,2})월 (\\d{1,2})일 .* -+$");

    // 정규표현식: 메시지 라인 (예: [가톨릭대 23 캡스톤 김가은] [오후 3:18] 메시지내용)
    // 그룹 1: 이름, 그룹 2: 오전/오후, 그룹 3: 시, 그룹 4: 분, 그룹 5: 내용
    private static final Pattern MESSAGE_PATTERN = Pattern.compile("^\\[(.*?)\\] \\[(오전|오후) (\\d{1,2}):(\\d{1,2})\\] (.*)$");

    public UnifiedChatResponse processKakaoFile(MultipartFile file) throws IOException {
        UnifiedChatResponse chatDto = new UnifiedChatResponse();
        chatDto.setTitle("KakaoTalk Conversation");
        chatDto.setThreadPath("kakao_import");
        chatDto.setStillParticipant(true);
        chatDto.setMagicWords(new java.util.ArrayList<>());

        Set<String> participantNames = new HashSet<>();
        LocalDate currentDate = LocalDate.now(); // 기본값

        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // 1. 날짜 변경 라인 체크
                Matcher dateMatcher = DATE_PATTERN.matcher(line);
                if (dateMatcher.matches()) {
                    int year = Integer.parseInt(dateMatcher.group(1));
                    int month = Integer.parseInt(dateMatcher.group(2));
                    int day = Integer.parseInt(dateMatcher.group(3));
                    currentDate = LocalDate.of(year, month, day);
                    continue;
                }

                // 2. 메시지 라인 체크
                Matcher msgMatcher = MESSAGE_PATTERN.matcher(line);
                if (msgMatcher.matches()) {
                    String sender = msgMatcher.group(1);
                    String ampm = msgMatcher.group(2);
                    int hour = Integer.parseInt(msgMatcher.group(3));
                    int minute = Integer.parseInt(msgMatcher.group(4));
                    String content = msgMatcher.group(5);

                    // 시간 변환 (12시간제 -> 24시간제)
                    if (ampm.equals("오후") && hour != 12) hour += 12;
                    if (ampm.equals("오전") && hour == 12) hour = 0;

                    // Timestamp 계산 (Milliseconds)
                    LocalDateTime ldt = currentDate.atTime(hour, minute);
                    long timestamp = ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

                    // 메시지 추가
                    UnifiedChatResponse.MessageDto msgDto = UnifiedChatResponse.MessageDto.builder()
                            .senderName(sender)
                            .timestampMs(timestamp)
                            .content(content)
                            .isGeoblockedForViewer(false)
                            .build();

                    chatDto.getMessages().add(msgDto);
                    participantNames.add(sender);
                }
            }
        }

        // 참여자 목록 설정
        for (String name : participantNames) {
            chatDto.getParticipants().add(new UnifiedChatResponse.ParticipantDto(name));
        }

        return chatDto;
    }

    public UnifiedChatResponse processInstaFile(MultipartFile file) throws IOException {
        // 인스타 파일은 이미 JSON 형식이므로 바로 매핑
        return objectMapper.readValue(file.getInputStream(), UnifiedChatResponse.class);
    }
}