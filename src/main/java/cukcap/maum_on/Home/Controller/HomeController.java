package cukcap.maum_on.Home.Controller;

import cukcap.maum_on.Home.Dto.HomeResponse;
import cukcap.maum_on.Home.Service.HomeService;
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
public class HomeController {

    private final HomeService homeService;

    @GetMapping("/home/{userId}/{today}")
    public ResponseEntity<Map<String, Object>> getHome(
            @AuthenticationPrincipal PrincipalDetails principalDetails,
            @PathVariable Long userId,
            @PathVariable String today // format: yyyy.MM.dd
    ) {
        // 1. 보안 검증: 로그인한 사람(JWT)과 요청한 userId가 같은지 확인
        if (!principalDetails.getId().equals(userId)) {
            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("code", 403);
            errorResponse.put("message", "권한이 없습니다. 본인의 정보만 조회 가능합니다.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
        }

        // 2. 서비스 호출 (today가 속한 '월'의 데이터를 가져옴)
        HomeResponse homeData = homeService.getHomeData(userId, today);

        // 3. 응답 포맷팅
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", 200);
        response.put("message", "이번 달 정보 조회 성공");
        response.put("data", homeData);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/home/boost/{userId}/{today}")
    public ResponseEntity<String> getAiBoost(
            @AuthenticationPrincipal PrincipalDetails principalDetails,
            @PathVariable Long userId,
            @PathVariable String today // yyyy.MM.dd
    ) {
        // 1. 본인 확인
        if (!principalDetails.getId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("권한이 없습니다.");
        }

        // 2. 서비스 호출
        String aiResponse = homeService.getBoostMessage(userId, today);

        // 3. 결과 반환 (AI가 준 String 그대로 리턴)
        return ResponseEntity.ok(aiResponse);
    }
}