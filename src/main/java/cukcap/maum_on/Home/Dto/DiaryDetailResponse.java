package cukcap.maum_on.Home.Dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DiaryDetailResponse {
    private Long diaryId;
    private String date;       // yyyy.MM.dd
    private String emotion;    // 감정
    private String content;    // 일기 내용 (write_diary)
    private String drawUrl;    // 그림 URL
    private String aiReply;    // AI 답장
    private List<FileDto> files; // 첨부 파일 목록

    @Data
    @Builder
    public static class FileDto {
        private Long fileId;
        private String fileType;
        private String fileUrl;
    }
}