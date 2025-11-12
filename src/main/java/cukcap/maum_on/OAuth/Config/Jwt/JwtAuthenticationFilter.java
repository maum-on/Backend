package cukcap.maum_on.OAuth.Config.Jwt;

import cukcap.maum_on.OAuth.Entity.PrincipalDetails;
import cukcap.maum_on.OAuth.Entity.User;
import cukcap.maum_on.OAuth.Repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    // HTTP 요청 헤더에서 JWT 토큰을 추출합니다. (Bearer <Token> 형식)
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null && jwtTokenProvider.validateToken(token)) {
            // 토큰 유효성 검사 통과 시
            try {
                Long userId = jwtTokenProvider.getUserIdFromToken(token);

                // DB에서 User 객체 로드
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

                // PrincipalDetails 객체 생성 (인증 주체)
                PrincipalDetails principalDetails = new PrincipalDetails(user);

                // Authentication 객체 생성 (이미 토큰으로 인증되었으므로 비밀번호는 null)
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principalDetails,
                        null,
                        principalDetails.getAuthorities()
                );

                // SecurityContext에 Authentication 객체 저장
                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (Exception e) {
                log.error("JWT 인증 처리 중 오류 발생: {}", e.getMessage());
                // 오류 발생 시 인증 실패 처리
            }
        }

        filterChain.doFilter(request, response);
    }
}
