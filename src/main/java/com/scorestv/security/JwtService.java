package com.scorestv.security;

import com.scorestv.config.ScorestvProperties;
import com.scorestv.user.Role;
import com.scorestv.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Access token (JWT) uretir ve dogrular.
 * Refresh token JWT degildir; opak bir deger olarak veritabaninda tutulur.
 */
@Service
public class JwtService {

    private static final String ISSUER = "scorestv";

    private final SecretKey key;
    @Getter
    private final long accessTtlSeconds;

    public JwtService(ScorestvProperties props) {
        this.key = Keys.hmacShaKeyFor(
                props.security().jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.accessTtlSeconds = props.security().jwt().accessTokenTtl().toSeconds();
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTtlSeconds)))
                .signWith(key)
                .compact();
    }

    /** Token gecersiz/suresi dolmus ise JJWT istisnasi firlatir. */
    public CurrentUser parse(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(ISSUER)
                .clockSkewSeconds(60)
                .build()
                .parseSignedClaims(token);
        Claims claims = jws.getPayload();
        return new CurrentUser(
                Long.valueOf(claims.getSubject()),
                claims.get("email", String.class),
                Role.valueOf(claims.get("role", String.class))
        );
    }
}
