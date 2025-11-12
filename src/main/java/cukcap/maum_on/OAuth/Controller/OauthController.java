package cukcap.maum_on.OAuth.Controller;

import cukcap.maum_on.OAuth.Entity.User;
import cukcap.maum_on.OAuth.Config.Jwt.JwtTokenProvider;
import cukcap.maum_on.OAuth.Service.kakaoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class OauthController {

    private final kakaoService kakaoService;
    private final JwtTokenProvider tokenProvider;

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

        // 브라우저를 카카오 로그인 페이지로 리다이렉트
        return "redirect:" + loginUrl;
    }

    @GetMapping("/auth/kakao/callback")
    @ResponseBody // JSON 응답을 위해 @Controller 대신 @RestController를 사용하거나 @ResponseBody를 붙입니다.
    public ResponseEntity<Map<String, String>> kakaoCallback(@RequestParam("code") String code) {
        try {
            // 1. 사용자 정보 처리 및 User 객체 반환
            Map<String, Object> userInfo = kakaoService.getUserInfo(kakaoService.getAccessToken(code));
            User loggedInUser = kakaoService.kakaoLoginProcess(userInfo);

            // 2. JWT 토큰 생성
            String jwtToken = tokenProvider.createToken(loggedInUser);

            // 3. 토큰을 JSON 형태로 응답
            Map<String, String> response = new HashMap<>();
            response.put("accessToken", jwtToken);
            response.put("message", "카카오 로그인 성공 및 토큰 발급");

            // 클라이언트는 이 응답에서 토큰을 추출하여 다음 요청부터 Header에 넣어 보냅니다.
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("카카오 로그인 처리 실패", e);

            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", "로그인 처리 중 오류 발생");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        }
    }
}
