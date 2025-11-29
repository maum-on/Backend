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
import java.util.UUID; // UUID 임포트 필수
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

    public UnifiedChatResponse processKakaoFile(MultipartFile file, LocalDate targetDate) throws IOException {
        UnifiedChatResponse chatDto = new UnifiedChatResponse();

        // [수정 1] AI 서버 오류 해결을 위해 thread_id 생성 (필수 필드)
        chatDto.setThreadId(UUID.randomUUID().toString());

        chatDto.setTitle("KakaoTalk");
        chatDto.setStillParticipant(true);
        chatDto.setParticipants(new ArrayList<>());
        chatDto.setMessages(new ArrayList<>());
        chatDto.setMagicWords(new ArrayList<>()); // 빈 리스트라도 초기화 추천

        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            LocalDate currentDate = null;

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                Matcher dateMatcher = DATE_PATTERN.matcher(line);
                if (dateMatcher.matches()) {
                    int year = Integer.parseInt(dateMatcher.group(1));
                    int month = Integer.parseInt(dateMatcher.group(2));
                    int day = Integer.parseInt(dateMatcher.group(3));
                    currentDate = LocalDate.of(year, month, day);
                    continue;
                }

                if (currentDate == null || !currentDate.isEqual(targetDate)) {
                    continue;
                }

                Matcher msgMatcher = MESSAGE_PATTERN.matcher(line);
                if (msgMatcher.matches()) {
                    String sender = msgMatcher.group(1);
                    String ampm = msgMatcher.group(2);
                    int hour = Integer.parseInt(msgMatcher.group(3));
                    int minute = Integer.parseInt(msgMatcher.group(4));
                    String content = msgMatcher.group(5);

                    if (ampm.equals("오후") && hour != 12) hour += 12;
                    if (ampm.equals("오전") && hour == 12) hour = 0;

                    LocalDateTime ldt = currentDate.atTime(hour, minute);
                    long timestamp = ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

                    UnifiedChatResponse.MessageDto msgDto = UnifiedChatResponse.MessageDto.builder()
                            .senderName(sender)
                            .timestampMs(timestamp)
                            .content(content)
                            .isGeoblockedForViewer(false)
                            .build();

                    chatDto.getMessages().add(msgDto);

                    boolean exists = chatDto.getParticipants().stream().anyMatch(p -> p.getName().equals(sender));
                    if (!exists) {
                        chatDto.getParticipants().add(new UnifiedChatResponse.ParticipantDto(sender));
                    }
                }
            }
        }
        return chatDto;
    }

    public UnifiedChatResponse processInstaFile(MultipartFile file, LocalDate targetDate) throws IOException {
        UnifiedChatResponse fullChat = objectMapper.readValue(file.getInputStream(), UnifiedChatResponse.class);

        // [수정 2] 인스타 파일에도 thread_id가 없으면 채워넣기
        if (fullChat.getThreadId() == null || fullChat.getThreadId().isEmpty()) {
            fullChat.setThreadId(UUID.randomUUID().toString());
        }

        List<UnifiedChatResponse.MessageDto> filteredMessages = fullChat.getMessages().stream()
                .filter(msg -> {
                    LocalDate msgDate = java.time.Instant.ofEpochMilli(msg.getTimestampMs())
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate();
                    return msgDate.isEqual(targetDate);
                })
                .collect(Collectors.toList());

        fullChat.setMessages(filteredMessages);
        return fullChat;
    }
}