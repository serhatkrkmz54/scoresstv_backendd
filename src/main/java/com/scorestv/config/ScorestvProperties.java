package com.scorestv.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * application.yml icindeki "scorestv.*" ayarlarini tasiyan tip-guvenli yapilandirma.
 */
@ConfigurationProperties(prefix = "scorestv")
public record ScorestvProperties(
        ApiFootball apiFootball,
        Security security,
        Cors cors,
        Mail mail
) {
    public record ApiFootball(
            String baseUrl,
            String key,
            /** Dakikalik API istek limiti; tum istekler buna gore esit araliklarla seri hale getirilir. */
            @DefaultValue("250") int requestsPerMinute
    ) {}

    public record Security(
            Jwt jwt,
            Google google
    ) {}

    public record Jwt(
            String secret,
            Duration accessTokenTtl,
            Duration refreshTokenTtl
    ) {}

    /** Google Sign-In - kabul edilen OAuth client ID'leri (web, android, ios). */
    public record Google(
            List<String> clientIds
    ) {}

    /** CORS - web/frontend istemcilerin eristigi izinli origin'ler. */
    public record Cors(
            List<String> allowedOrigins
    ) {}

    /** E-posta ayarlari. */
    public record Mail(
            String passwordResetUrl
    ) {}
}
