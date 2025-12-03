package cukcap.maum_on.Diary.Dto;

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
public class AiPictureResponse {

    private String emotion;       // 감정 (예: happy)

    @JsonProperty("emotion_ko")
    private String emotionKo;     // 감정 한글명

    private String reason;        // 분석 결과/이유
}