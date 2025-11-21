package cukcap.maum_on.Diary.Dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // 정의되지 않은 필드는 무시
public class AiResponse {
    // AI 서버 JSON 키: "reply_normal" -> 자바 필드: reply
    @JsonProperty("reply_normal")
    private String reply;

    // AI 서버 JSON 키: "analysis" (객체)
    private Analysis analysis;

    // 내부 클래스로 중첩된 JSON 처리
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Analysis {
        // AI 서버 JSON 키: "emotions" (리스트)
        private List<String> emotions;

        private String summary;
    }

    // 편의 메서드: 감정 리스트 중 첫 번째 감정을 문자열로 반환
    public String getPrimaryEmotion() {
        if (analysis != null && analysis.getEmotions() != null && !analysis.getEmotions().isEmpty()) {
            return analysis.getEmotions().get(0);
        }
        return "normal"; // 기본값
    }
}