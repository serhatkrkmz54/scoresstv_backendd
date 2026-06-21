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
 * Apple "Sign in with Apple" identity token'ını (JWT) doğrular — imzayı Apple'ın
 * JWKS anahtarlarıyla, ayrıca issuer ve audience (App ID + Service ID) kontrol
 * eder. {@link GoogleTokenVerifier} ile aynı desen.
 *
 * <p>Audience (aud):
 * <ul>
 *   <li>iOS native akış → App ID bundle id ({@code com.scorestv.mobile})</li>
 *   <li>Web / Android web akışı → Service ID ({@code com.scorestv.signin})</li>
 * </ul>
 * Her ikisi de {@code scorestv.security.apple.client-ids} listesinde kabul edilir.
 *
 * <p>Not: Apple'ın {@code name} alanı token'da YOKTUR (yalnız ilk girişte ayrı
 * gelir). E-posta gizli relay olabilir ({@code ...@privaterelay.appleid.com}).
 * {@code email_verified} string ("true") olarak gelir; Apple e-postaları zaten
 * Apple tarafından doğrulanmış sayıldığı için bunu zorunlu tutmuyoruz.
 */
@Service
public class AppleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(AppleTokenVerifier.class);

    private static final String JWKS_URI = "https://appleid.apple.com/auth/keys";
    private static final String ISSUER = "https://appleid.apple.com";

    private final JwtDecoder decoder;
    private final List<String> clientIds;

    public AppleTokenVerifier(ScorestvProperties properties) {
        this.clientIds = properties.security().apple().clientIds();
        log.info("AppleTokenVerifier yapılandırıldı — kabul edilen client-ids: {}",
                clientIds);

        NimbusJwtDecoder nimbusDecoder = NimbusJwtDecoder.withJwkSetUri(JWKS_URI).build();
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(ISSUER);
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> {
            if (jwt.getAudience() != null
                    && jwt.getAudience().stream().anyMatch(clientIds::contains)) {
                return OAuth2TokenValidatorResult.success();
            }
            log.warn("Apple identity token audience eşleşmedi. token.aud={} kabul edilen={}",
                    jwt.getAudience(), clientIds);
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_audience"));
        };
        nimbusDecoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator));
        this.decoder = nimbusDecoder;
    }

    /** Token geçersizse ApiException (401) fırlatır. */
    public AppleUser verify(String identityToken) {
        Jwt jwt;
        try {
            jwt = decoder.decode(identityToken);
        } catch (JwtException e) {
            log.warn("Apple identity token verify hatası: {}", e.getMessage());
            throw ApiException.unauthorized("Apple kimlik doğrulaması geçersiz");
        }
        // email yoksa (kullanıcı gizledi + relay vermediyse) null olabilir — çağıran
        // tarafta ele alınır.
        return new AppleUser(jwt.getSubject(), jwt.getClaimAsString("email"));
    }

    /** Apple identity token'ından çıkan temel kullanıcı bilgisi. */
    public record AppleUser(String appleId, String email) {}
}
