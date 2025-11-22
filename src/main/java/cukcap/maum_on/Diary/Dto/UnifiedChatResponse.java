package cukcap.maum_on.Diary.Dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // 정의되지 않은 필드는 무시
@JsonPropertyOrder({"thread_id", "title", "participants", "messages", "is_still_participant", "thread_path", "magic_words"})
public class UnifiedChatResponse {
    @JsonProperty("thread_id")
    private String threadId;

    private String title;

    @Builder.Default
    private List<ParticipantDto> participants = new ArrayList<>();

    @Builder.Default
    private List<MessageDto> messages = new ArrayList<>();

    @JsonProperty("is_still_participant")
    private boolean isStillParticipant;

    @JsonProperty("thread_path")
    private String threadPath;

    @JsonProperty("magic_words")
    private List<String> magicWords;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantDto {
        private String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageDto {
        @JsonProperty("sender_name")
        private String senderName;

        @JsonProperty("timestamp_ms")
        private Long timestampMs;

        private String content;

        @JsonProperty("is_geoblocked_for_viewer")
        private boolean isGeoblockedForViewer;
    }
}