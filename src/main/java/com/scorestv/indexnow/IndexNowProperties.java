package com.scorestv.indexnow;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * IndexNow (Bing/Yandex hızlı indeksleme) entegrasyonu ayarları
 * — {@code scorestv.indexnow.*}.
 *
 * <p>Yeni oluşan ve yeni biten maçların canonical URL'leri periyodik olarak
 * IndexNow API'sine gönderilir. {@code enabled=false} iken hiç istek atılmaz
 * (deploy güvenli varsayılan — kullanıcı sonra açar).
 *
 * <p>{@code key} public bir dosyayla doğrulanır: web tarafında
 * {@code https://scorestv.com/<key>.txt} yayınlanmıştır. keyLocation ve host
 * {@link com.scorestv.football.seo.SeoProperties#siteUrl()} üzerinden türetilir.
 */
@ConfigurationProperties(prefix = "scorestv.indexnow")
public record IndexNowProperties(

        /** Gönderim açık mı? Varsayılan kapalı — deploy güvenli olsun. */
        @DefaultValue("false") boolean enabled,

        /** IndexNow anahtarı (public .txt dosyasıyla eşleşmeli). Boşsa no-op. */
        @DefaultValue("") String key
) {
}
