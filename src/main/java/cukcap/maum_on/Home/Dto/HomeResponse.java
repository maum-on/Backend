package cukcap.maum_on.Home.Dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
public class HomeResponse {

    private BigDecimal temperature;

    private Map<String, Integer> emotions; // "happy": 2, "sad": 0 ...

    @JsonProperty("diary_existence")
    private Map<String, DiaryStatusDto> diaryExistence; // "2025.09.09": { ... }

    @JsonProperty("activity_recommend")
    private String activityRecommend;

    @JsonProperty("psychological_test")
    private String psychologicalTest; // 추후 구현 예정이라 명세서에 맞춤

    @Data
    @Builder
    public static class DiaryStatusDto {
        private boolean write;  // 글 작성 여부
        private boolean files;  // 파일(JSON 등) 첨부 여부
        private boolean draw;   // 그림 존재 여부
        private String emotion; // 대표 감정
    }
}