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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileProcessingService {

    private final ObjectMapper objectMapper;

    // 정규식 등은 기존과 동일
    private static final Pattern DATE_PATTERN = Pattern.compile("^-+ (\\d{4})년 (\\d{1,2})월 (\\d{1,2})일 .* -+$");
    private static final Pattern MESSAGE_PATTERN = Pattern.compile("^\\[(.*?)\\] \\[(오전|오후) (\\d{1,2}):(\\d{1,2})\\] (.*)$");

    public UnifiedChatResponse processKakaoFile(MultipartFile file, LocalDate targetDate) throws IOException {
        UnifiedChatResponse chatDto = new UnifiedChatResponse();

        // 1. 필수값 생성 (thread_id)
        chatDto.setThreadId(UUID.randomUUID().toString());

        // 2. thread_path가 비어있으면 에러가 날 수 있으므로 더미 값 추가
        chatDto.setThreadPath("kakao_talk_upload");

        chatDto.setTitle("KakaoTalk Conversation");
        chatDto.setStillParticipant(true);
        chatDto.setParticipants(new ArrayList<>());
        chatDto.setMessages(new ArrayList<>());
        chatDto.setMagicWords(new ArrayList<>());

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

        if (fullChat.getThreadId() == null || fullChat.getThreadId().isEmpty()) {
            fullChat.setThreadId(UUID.randomUUID().toString());
        }

        List<UnifiedChatResponse.MessageDto> filteredMessages = fullChat.getMessages().stream()
                .filter(msg -> {
                    // timestamp 체크 안전장치
                    if(msg.getTimestampMs() == null) return false;

                    LocalDate msgDate = Instant.ofEpochMilli(msg.getTimestampMs())
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate();
                    return msgDate.isEqual(targetDate);
                })
                .collect(Collectors.toList());

        fullChat.setMessages(filteredMessages);
        return fullChat;
    }
}