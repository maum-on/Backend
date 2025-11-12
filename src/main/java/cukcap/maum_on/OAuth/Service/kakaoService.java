package cukcap.maum_on.OAuth.Service;

import cukcap.maum_on.OAuth.Entity.User;
import cukcap.maum_on.OAuth.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class kakaoService {
    private final UserRepository userRepository;

    @Value("${kakao.client-id}")
    private String clientId;
    @Value("${kakao.redirect-uri}")
    private String redirectUri;
    @Value("${kakao.token-uri}")
    private String tokenUri;
    @Value("${kakao.user-info-uri}")
    private String userInfoUri;


    // 1. 인가 코드로 액세스 토큰 요청
    public String getAccessToken(String code) {
        RestTemplate restTemplate = new RestTemplate();
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", clientId);
        params.add("redirect_uri", redirectUri);
        params.add("code", code);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE + ";charset=utf-8");
        HttpEntity<MultiValueMap<String, String>> kakaoTokenRequest = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(tokenUri, HttpMethod.POST, kakaoTokenRequest, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return (String) response.getBody().get("access_token");
            }
        } catch (Exception e) {
            log.error("카카오 액세스 토큰 발급 실패: {}", e.getMessage());
        }
        throw new RuntimeException("카카오 액세스 토큰 발급 실패");
    }

    // 2. 액세스 토큰으로 사용자 정보 요청
    public Map<String, Object> getUserInfo(String accessToken) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE + ";charset=utf-8");

        HttpEntity<String> kakaoProfileRequest = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(userInfoUri, HttpMethod.POST, kakaoProfileRequest, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("카카오 사용자 정보 요청 실패: {}", e.getMessage());
        }
        throw new RuntimeException("카카오 사용자 정보 요청 실패");
    }

    // 3. 회원가입/로그인 처리
    public User kakaoLoginProcess(Map<String, Object> userInfo) {
        Long kakaoId = (Long) userInfo.get("id");
        Map<String, Object> kakaoAccount = (Map<String, Object>) userInfo.get("kakao_account");
        Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");

        // 이메일은 동의하지 않을 수 있으므로 null 처리 필요
        String email = (String) kakaoAccount.get("email");
        String nickname = (String) profile.get("nickname");

        Optional<User> optionalUser = userRepository.findByKakaoId(kakaoId);
        User user;

        if (optionalUser.isPresent()) {
            // 로그인 (정보 업데이트)
            user = optionalUser.get();
            user.updateNickname(nickname);
            userRepository.save(user);
        } else {
            // 회원가입
            user = User.builder()
                    .kakaoId(kakaoId)
                    .email(email)
                    .nickname(nickname)
                    .role("ROLE_USER")
                    .build();
            userRepository.save(user);
        }
        return user;
    }
}
