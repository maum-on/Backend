package cukcap.maum_on.OAuth.Entity;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

@Getter
public class PrincipalDetails implements UserDetails {

    private final User user;

    public PrincipalDetails(User user) {
        this.user = user;
    }

    public Long getId() {
        return user.getId();
    }

    // 해당 User의 권한을 리턴하는 곳
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // User 엔티티의 role 필드(예: "ROLE_USER")를 권한으로 변환
        return Collections.singletonList(new SimpleGrantedAuthority(user.getRole()));
    }

    @Override
    public String getPassword() {
        return null; // 카카오 로그인은 비밀번호를 사용하지 않음
    }

    @Override
    public String getUsername() {
        return user.getId().toString(); // 서비스 내 ID를 유저 이름으로 사용
    }

    // 계정 만료, 잠김 등의 상태는 모두 true로 설정
    @Override
    public boolean isAccountNonExpired() { return true; }
    @Override
    public boolean isAccountNonLocked() { return true; }
    @Override
    public boolean isCredentialsNonExpired() { return true; }
    @Override
    public boolean isEnabled() { return true; }
}