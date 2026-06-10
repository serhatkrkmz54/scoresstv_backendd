package com.scorestv.security;

import com.scorestv.common.ApiException;
import com.scorestv.config.ScorestvProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Google ID token'ini dogrular: imzayi Google'in JWKS anahtarlariyla, ayrica
 * issuer ve audience (bizim OAuth client ID'lerimiz) kontrol eder.
 * Gecerliyse temel kullanici bilgilerini doner.
 */
@Service
public class GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerifier.class);

    private static final String JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final String ISSUER = "https://accounts.google.com";

    private final JwtDecoder decoder;
    private final List<String> clientIds;

    public GoogleTokenVerifier(ScorestvProperties properties) {
        this.clientIds = properties.security().google().clientIds();
        log.info("GoogleTokenVerifier yapılandırıldı — kabul edilen client-ids: {}",
                clientIds);

        NimbusJwtDecoder nimbusDecoder = NimbusJwtDecoder.withJwkSetUri(JWKS_URI).build();
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(ISSUER);
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> {
            if (jwt.getAudience() != null
                    && jwt.getAudience().stream().anyMatch(clientIds::contains)) {
                return OAuth2TokenValidatorResult.success();
            }
            log.warn("Google ID token audience eşleşmedi. token.aud={} kabul edilen client-ids={}",
                    jwt.getAudience(), clientIds);
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_audience"));
        };
        nimbusDecoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator));
        this.decoder = nimbusDecoder;
    }

    /** Token gecersizse ApiException (401) firlatir. */
    public GoogleUser verify(String idToken) {
        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (JwtException e) {
            // Gercek hata mesajini log'la (audience / issuer / expired / signature).
            log.warn("Google ID token verify hatası: {}", e.getMessage());
            throw ApiException.unauthorized("Google kimlik doğrulaması geçersiz");
        }
        if (!Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) {
            throw ApiException.unauthorized("Google hesabının e-postası doğrulanmamış");
        }
        return new GoogleUser(
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("name"));
    }

    /** Google ID token'indan cikan temel kullanici bilgisi. */
    public record GoogleUser(String googleId, String email, String name) {}
}
