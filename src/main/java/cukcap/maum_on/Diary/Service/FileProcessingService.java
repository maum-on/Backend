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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileProcessingService {

    private final ObjectMapper objectMapper;

    // 정규식: 날짜 라인 (예: --------------- 2025년 9월 5일 금요일 ---------------)
    private static final Pattern DATE_PATTERN = Pattern.compile("^-+ (\\d{4})년 (\\d{1,2})월 (\\d{1,2})일 .* -+$");
    // 정규식: 메시지 라인 (예: [이름] [오전 10:20] 내용)
    private static final Pattern MESSAGE_PATTERN = Pattern.compile("^\\[(.*?)\\] \\[(오전|오후) (\\d{1,2}):(\\d{1,2})\\] (.*)$");

    /**
     카카오톡 TXT 파일 처리 -> 특정 날짜만 필터링 -> 통합 JSON 객체 반환
     **/
    public UnifiedChatResponse processKakaoFile(MultipartFile file, LocalDate targetDate) throws IOException {
        UnifiedChatResponse chatDto = new UnifiedChatResponse();
        chatDto.setTitle("KakaoTalk");
        chatDto.setStillParticipant(true);
        chatDto.setParticipants(new ArrayList<>());
        chatDto.setMessages(new ArrayList<>());

        // 파일 읽기 (UTF-8 가정)
        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            LocalDate currentDate = null; // 현재 읽고 있는 라인의 날짜

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // 1. 날짜 변경선 체크
                Matcher dateMatcher = DATE_PATTERN.matcher(line);
                if (dateMatcher.matches()) {
                    int year = Integer.parseInt(dateMatcher.group(1));
                    int month = Integer.parseInt(dateMatcher.group(2));
                    int day = Integer.parseInt(dateMatcher.group(3));
                    currentDate = LocalDate.of(year, month, day);
                    continue;
                }

                // 2. 날짜가 targetDate와 다르면 메시지 파싱 스킵
                if (currentDate == null || !currentDate.isEqual(targetDate)) {
                    continue;
                }

                // 3. 메시지 라인 체크 (날짜가 일치할 때만)
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

                    // LocalDateTime -> Timestamp(ms) 변환
                    LocalDateTime ldt = currentDate.atTime(hour, minute);
                    long timestamp = ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

                    UnifiedChatResponse.MessageDto msgDto = UnifiedChatResponse.MessageDto.builder()
                            .senderName(sender)
                            .timestampMs(timestamp)
                            .content(content)
                            .isGeoblockedForViewer(false)
                            .build();

                    chatDto.getMessages().add(msgDto);

                    // 참여자 목록에 없으면 추가
                    boolean exists = chatDto.getParticipants().stream().anyMatch(p -> p.getName().equals(sender));
                    if (!exists) {
                        chatDto.getParticipants().add(new UnifiedChatResponse.ParticipantDto(sender));
                    }
                }
            }
        }
        return chatDto;
    }

    /**
     인스타 JSON 파일 처리 -> 특정 날짜만 필터링 -> 통합 JSON 객체 반환
     **/
    public UnifiedChatResponse processInstaFile(MultipartFile file, LocalDate targetDate) throws IOException {
        // 1. 전체 JSON 파싱
        UnifiedChatResponse fullChat = objectMapper.readValue(file.getInputStream(), UnifiedChatResponse.class);

        // 2. 메시지 필터링 (timestamp_ms 기준)
        List<UnifiedChatResponse.MessageDto> filteredMessages = fullChat.getMessages().stream()
                .filter(msg -> {
                    // Timestamp(ms) -> LocalDate 변환
                    LocalDate msgDate = java.time.Instant.ofEpochMilli(msg.getTimestampMs())
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate();
                    // 날짜 일치 여부 확인
                    return msgDate.isEqual(targetDate);
                })
                .collect(Collectors.toList());

        // 3. 필터링된 메시지로 교체
        fullChat.setMessages(filteredMessages);

        return fullChat;
    }
}