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
            /**
             * Dakikalik API istek limiti — eski throttle icin tutulan tarihi alan.
             * Yeni priority-aware token bucket {@link #liveTokensPerSecond} ve
             * {@link #lazyTokensPerSecond} araciligiyla calistigi icin bu deger
             * artik kullanilmaz; geriye donuk uyumluluk icin korunmustur.
             */
            @DefaultValue("250") int requestsPerMinute,
            /**
             * LIVE oncelikli istekler icin saniyede ayrilan token sayisi.
             * Kullanici-bekleyen endpoint'ler ({@code /fixtures}, {@code /fixtures/*})
             * bu kovayi kullanir. Garantili rezerv slot — lazy yogunlugundan
             * etkilenmez. Custom600 plani icin onerilen: 8.
             */
            @DefaultValue("8") int liveTokensPerSecond,
            /**
             * LAZY oncelikli istekler (arka plan senkronu) icin saniyede ayrilan
             * token sayisi. {@code /teams}, {@code /squads}, {@code /transfers},
             * {@code /sidelined}, {@code /trophies}, {@code /coachs} vb.
             * Burst durumunda burada sira beklenir; live etkilenmez. Onerilen: 4.
             */
            @DefaultValue("4") int lazyTokensPerSecond
    ) {}

    public record Security(
            Jwt jwt,
            Google google,
            Apple apple
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

    /** Sign in with Apple - kabul edilen audience'lar (App ID bundle + Service ID). */
    public record Apple(
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
