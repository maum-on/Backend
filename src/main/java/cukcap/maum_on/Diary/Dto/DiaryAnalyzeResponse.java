package cukcap.maum_on.Diary.Dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DiaryAnalyzeResponse {

    private int code;
    private String message;
    private AnalyzeData data;

    @Data
    @Builder
    public static class AnalyzeData {

        private String emotion;

        @JsonProperty("draw")
        private String drawUrl;

        @JsonProperty("write_diary")
        private String writeDiary; // DB: write_diary

        @JsonProperty("file_summation") // JSON에서는 "file_summation"으로 나감
        private List<String> fileSummation; // DB: diary_file 테이블의 summary_text 목록

        @JsonProperty("ai_reply")
        private String aiReply;

        @JsonProperty("ai_draw_reply")
        private String aiDrawReply;
    }
}