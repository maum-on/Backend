package cukcap.maum_on.Home.Dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiBoostResponse {

    private String version;

    private String status;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("diary_used")
    private boolean diaryUsed;

    @JsonProperty("audio_path")
    private String audioPath;

    @JsonProperty("diary_meta")
    private DiaryMeta diaryMeta;

    @Data
    @NoArgsConstructor
    public static class DiaryMeta {
        @JsonProperty("has_diary")
        private boolean hasDiary;

        private String emotion;
    }
}