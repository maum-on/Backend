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

    // 1. [PC 버전] 날짜/메시지 패턴
    private static final Pattern DATE_PATTERN_PC = Pattern.compile("^-+ (\\d{4})년 (\\d{1,2})월 (\\d{1,2})일 .* -+$");
    private static final Pattern MSG_PATTERN_PC = Pattern.compile("^\\[(.*?)\\] \\[(오전|오후) (\\d{1,2}):(\\d{1,2})\\] (.*)$");

    // 2. [모바일/Mac 버전] 메시지 패턴 (날짜가 라인마다 포함됨)
    // 예: 2025년 12월 4일 오후 9:21, 한태림 : 하하
    private static final Pattern MSG_PATTERN_MOBILE = Pattern.compile("^(\\d{4})년 (\\d{1,2})월 (\\d{1,2})일 (오전|오후) (\\d{1,2}):(\\d{1,2}), (.*?) : (.*)$");

    public UnifiedChatResponse processKakaoFile(MultipartFile file, LocalDate targetDate) throws IOException {
        UnifiedChatResponse chatDto = new UnifiedChatResponse();
        chatDto.setThreadId(UUID.randomUUID().toString());
        chatDto.setThreadPath("kakao_talk_upload");
        chatDto.setTitle("KakaoTalk Conversation");
        chatDto.setStillParticipant(true);
        chatDto.setParticipants(new ArrayList<>());
        chatDto.setMessages(new ArrayList<>());
        chatDto.setMagicWords(new ArrayList<>());

        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            LocalDate currentDate = null; // PC 버전용 날짜 상태

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // --- [패턴 1] PC 버전 파싱 시도 ---
                Matcher dateMatcherPc = DATE_PATTERN_PC.matcher(line);
                if (dateMatcherPc.matches()) {
                    int year = Integer.parseInt(dateMatcherPc.group(1));
                    int month = Integer.parseInt(dateMatcherPc.group(2));
                    int day = Integer.parseInt(dateMatcherPc.group(3));
                    currentDate = LocalDate.of(year, month, day);
                    continue;
                }

                Matcher msgMatcherPc = MSG_PATTERN_PC.matcher(line);
                if (msgMatcherPc.matches()) {
                    // PC 버전은 상단에 날짜 라인이 먼저 나와야 함
                    if (currentDate != null && currentDate.isEqual(targetDate)) {
                        String sender = msgMatcherPc.group(1);
                        String ampm = msgMatcherPc.group(2);
                        int hour = Integer.parseInt(msgMatcherPc.group(3));
                        int minute = Integer.parseInt(msgMatcherPc.group(4));
                        String content = msgMatcherPc.group(5);

                        addMessage(chatDto, currentDate, sender, ampm, hour, minute, content);
                    }
                    continue; // PC 패턴 매칭 성공 시 다음 라인으로
                }

                // --- [패턴 2] 모바일/Mac 버전 파싱 시도 ---
                Matcher msgMatcherMobile = MSG_PATTERN_MOBILE.matcher(line);
                if (msgMatcherMobile.matches()) {
                    // 그룹: 1(년) 2(월) 3(일) 4(오전/오후) 5(시) 6(분) 7(이름) 8(내용)
                    int year = Integer.parseInt(msgMatcherMobile.group(1));
                    int month = Integer.parseInt(msgMatcherMobile.group(2));
                    int day = Integer.parseInt(msgMatcherMobile.group(3));

                    LocalDate msgDate = LocalDate.of(year, month, day);

                    // 날짜가 targetDate와 일치하는지 확인
                    if (msgDate.isEqual(targetDate)) {
                        String ampm = msgMatcherMobile.group(4);
                        int hour = Integer.parseInt(msgMatcherMobile.group(5));
                        int minute = Integer.parseInt(msgMatcherMobile.group(6));
                        String sender = msgMatcherMobile.group(7);
                        String content = msgMatcherMobile.group(8);

                        addMessage(chatDto, msgDate, sender, ampm, hour, minute, content);
                    }
                }
            }
        }
        return chatDto;
    }

    // 메시지 추가 헬퍼 메서드
    private void addMessage(UnifiedChatResponse chatDto, LocalDate date, String sender, String ampm, int hour, int minute, String content) {
        // 시간 변환 (12시간제 -> 24시간제)
        if (ampm.equals("오후") && hour != 12) hour += 12;
        if (ampm.equals("오전") && hour == 12) hour = 0;

        LocalDateTime ldt = date.atTime(hour, minute);
        long timestamp = ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        UnifiedChatResponse.MessageDto msgDto = UnifiedChatResponse.MessageDto.builder()
                .senderName(sender)
                .timestampMs(timestamp)
                .content(content)
                .isGeoblockedForViewer(false)
                .build();

        chatDto.getMessages().add(msgDto);

        // 참여자 목록 추가
        boolean exists = chatDto.getParticipants().stream().anyMatch(p -> p.getName().equals(sender));
        if (!exists) {
            chatDto.getParticipants().add(new UnifiedChatResponse.ParticipantDto(sender));
        }
    }

    public UnifiedChatResponse processInstaFile(MultipartFile file, LocalDate targetDate) throws IOException {
        UnifiedChatResponse fullChat = objectMapper.readValue(file.getInputStream(), UnifiedChatResponse.class);

        if (fullChat.getThreadId() == null || fullChat.getThreadId().isEmpty()) {
            fullChat.setThreadId(UUID.randomUUID().toString());
        }

        List<UnifiedChatResponse.MessageDto> filteredMessages = fullChat.getMessages().stream()
                .filter(msg -> {
                    if (msg.getTimestampMs() == null) return false;
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