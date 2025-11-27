package cukcap.maum_on.OAuth.Controller;

import cukcap.maum_on.OAuth.Entity.PrincipalDetails;
import cukcap.maum_on.OAuth.Entity.User;
import cukcap.maum_on.OAuth.Config.Jwt.JwtTokenProvider;
import cukcap.maum_on.OAuth.Service.KakaoService;
import cukcap.maum_on.OAuth.Service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class OauthController {

    private final KakaoService kakaoService;
    private final JwtTokenProvider tokenProvider;
    private final UserService userService;

    @Value("${kakao.client-id}")
    private String clientId;

    @Value("${kakao.redirect-uri}")
    private String redirectUri;

    @Value("${kakao.auth-url}")
    private String authUrl;

    @GetMapping("/auth/kakao/login")
    public String kakaoLogin() {
        String loginUrl = authUrl + "/oauth/authorize?"
                + "response_type=code"
                + "&client_id=" + clientId
                + "&redirect_uri=" + redirectUri;

        return "redirect:" + loginUrl;
    }

    @GetMapping("/auth/kakao/callback")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> kakaoCallback(@RequestParam("code") String code) {
        try {
            // 1. 사용자 정보 처리 및 User 객체 반환
            Map<String, Object> userInfo = kakaoService.getUserInfo(kakaoService.getAccessToken(code));
            User loggedInUser = kakaoService.kakaoLoginProcess(userInfo);

            // 2. JWT 토큰 생성
            String jwtToken = tokenProvider.createToken(loggedInUser);

            // 3. Response Body 구성 (여기가 중요!)
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "카카오 로그인 성공");

            response.put("accessToken", jwtToken);       // JWT 토큰
            response.put("userId", loggedInUser.getId()); // 사용자 ID (홈 화면 조회 등에 사용)

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("카카오 로그인 처리 실패", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("code", 401);
            errorResponse.put("message", "로그인 처리 중 오류 발생");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        }
    }

    @PostMapping("/auth/kakao/logout")
    @ResponseBody
    public ResponseEntity<Map<String, String>> logout(@AuthenticationPrincipal PrincipalDetails principalDetails) {
        if (principalDetails != null) {
            Long kakaoId = principalDetails.getUser().getKakaoId();
            kakaoService.kakaoLogout(kakaoId);
        }

        Map<String, String> response = new HashMap<>();
        response.put("message", "로그아웃 성공");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/auth/kakao/withdraw")
    @ResponseBody
    public ResponseEntity<Map<String, String>> withdraw(@AuthenticationPrincipal PrincipalDetails principalDetails) {
        if (principalDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요합니다."));
        }

        Long userId = principalDetails.getId();
        Long kakaoId = userService.deleteUserAccount(userId);
        kakaoService.kakaoUnlink(kakaoId);

        Map<String, String> response = new HashMap<>();
        response.put("message", "회원 탈퇴 완료");
        return ResponseEntity.ok(response);
    }
}