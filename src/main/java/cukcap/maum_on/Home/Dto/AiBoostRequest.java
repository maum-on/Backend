package cukcap.maum_on.Home.Dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AiBoostRequest {

    @JsonProperty("user_id")
    private String userId;

    private int code;
    private String message;
    private BoostData data;

    @Data
    @Builder
    public static class BoostData {
        private String emotion;

        @JsonProperty("draw")
        private String drawUrl;

        @JsonProperty("write_diary")
        private String writeDiary;

        @JsonProperty("file_summation")
        private List<String> fileSummation;

        @JsonProperty("ai_reply")
        private String aiReply;

        @JsonProperty("ai_draw_reply")
        private String aiDrawReply;
    }
}