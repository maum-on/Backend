package cukcap.maum_on.MyPage.Dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class MyPageResponse {

    private String nickname;

    private String email;

    @JsonProperty("diary_count")
    private long diaryCount; // 총 일기 작성 일수

    @JsonProperty("this_month_avg_temp")
    private BigDecimal thisMonthAvgTemp; // 이번 달 평균 마음 온도
}   