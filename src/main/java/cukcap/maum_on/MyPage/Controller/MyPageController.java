package cukcap.maum_on.MyPage.Controller;

import cukcap.maum_on.MyPage.Dto.MyPageResponse;
import cukcap.maum_on.MyPage.Service.MyPageService;
import cukcap.maum_on.OAuth.Entity.PrincipalDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class MyPageController {

    private final MyPageService myPageService;

    @GetMapping("/mypage/{userId}")
    public ResponseEntity<Map<String, Object>> getMyPage(
            @AuthenticationPrincipal PrincipalDetails principalDetails,
            @PathVariable Long userId
    ) {
        // 1. 권한 검증
        if (!principalDetails.getId().equals(userId)) {
            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("code", 403);
            errorResponse.put("message", "권한이 없습니다. 본인의 정보만 조회 가능합니다.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
        }

        // 2. 서비스 호출
        MyPageResponse myPageData = myPageService.getMyPageInfo(userId);

        // 3. 응답 반환
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", 200);
        response.put("message", "마이페이지 조회 성공");
        response.put("data", myPageData);

        return ResponseEntity.ok(response);
    }
}