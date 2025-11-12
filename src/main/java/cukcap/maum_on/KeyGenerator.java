package cukcap.maum_on;

import java.security.SecureRandom;
import java.util.Base64;

public class KeyGenerator {
    public static void main(String[] args) {
        // HS512 (64바이트) 요구사항에 맞춰 64바이트 길이의 무작위 데이터 생성
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[64];
        random.nextBytes(bytes);

        // BASE64 인코딩하여 환경 변수에 넣을 문자열 생성
        String secretKey = Base64.getEncoder().encodeToString(bytes);
        System.out.println("Generated JWT Secret Key (64 bytes, Base64 Encoded): \n" + secretKey);
    }
}