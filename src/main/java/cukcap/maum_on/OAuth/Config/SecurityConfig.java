package cukcap.maum_on.OAuth.Config;

import cukcap.maum_on.OAuth.Config.Jwt.JwtAuthenticationFilter;
import cukcap.maum_on.OAuth.Config.Jwt.JwtTokenProvider;
import cukcap.maum_on.OAuth.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration // <--- 설정 파일임을 명시
@EnableWebSecurity // <--- Spring Security 활성화
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 1. CORS 설정
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 2. CSRF 비활성화 (모바일 환경/REST API)
                .csrf(AbstractHttpConfigurer::disable)

                // 3. Session 관리 비활성화 (JWT 사용 = STATELESS)
                .sessionManagement(sessionManagement ->
                        sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 4. 요청 접근 권한 설정
                .authorizeHttpRequests(authorize -> authorize
                        // 카카오 로그인 경로는 인증 없이 허용 (iOS 앱의 첫 접근 경로)
                        .requestMatchers("/auth/kakao/**").permitAll()
                        // 그 외 모든 요청은 인증된 사용자만 접근 허용
                        .anyRequest().authenticated()
                )

                // 5. 커스텀 필터 등록: JWT 필터를 표준 인증 필터 이전에 실행
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtTokenProvider, userRepository),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    // CORS 설정 Bean (iOS 앱 환경에서 필요할 수 있음)
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOriginPatterns(List.of("*")); // 모든 도메인 허용 (개발 시)
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*")); // 모든 헤더 허용
        configuration.setAllowCredentials(true); // 쿠키/인증 정보 허용

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
