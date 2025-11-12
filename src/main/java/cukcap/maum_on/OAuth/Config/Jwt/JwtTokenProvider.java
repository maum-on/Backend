package cukcap.maum_on.OAuth.Config.Jwt;

import cukcap.maum_on.OAuth.Entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {
    private final Key key;
    private final long tokenValidityInMilliseconds = 1000 * 60 * 30; // 30분

    public JwtTokenProvider(@Value("${jwt.secret}") String secretKey) {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String createToken(User user) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + tokenValidityInMilliseconds);

        return Jwts.builder()
                .setSubject(user.getId().toString()) // 서비스 ID를 Subject로 사용
                .claim("kakaoId", user.getKakaoId())
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
        // Subject에 저장된 서비스 사용자 ID를 Long 타입으로 반환
        return Long.parseLong(claims.getSubject());
    }

    public boolean validateToken(String token) {
        try {
            // 서명 키를 이용해 토큰을 파싱하여 유효성을 검증
            Jwts.parser().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (io.jsonwebtoken.security.SecurityException | MalformedJwtException e) {
            log.info("잘못된 JWT 서명입니다.");
        } catch (ExpiredJwtException e) {
            log.info("만료된 JWT 토큰입니다.");
        } catch (UnsupportedJwtException e) {
            log.info("지원되지 않는 JWT 토큰입니다.");
        } catch (IllegalArgumentException e) {
            log.info("JWT 토큰이 잘못되었습니다.");
        }
        return false;
    }
}
