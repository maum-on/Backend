package cukcap.maum_on.Diary.Dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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

    private Result result;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private String type;        // 감정 타입 (예: happy) -> Diary.emotion에 저장
        private String description; // 그림 설명
        private String extra_tip;   // 조언
    }
}