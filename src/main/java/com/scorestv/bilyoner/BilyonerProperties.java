package com.scorestv.bilyoner;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Bilyoner iddaa oranları entegrasyonu yapılandırması.
 *
 * <p>{@code enabled=false} ise tüm oran akışı devre dışı kalır (fetch yapılmaz,
 * match-detail'de {@code odds=null} döner) — bir sorun olursa env ile anında
 * kapatılabilir.
 *
 * <p>{@code affiliateUrl}: tıkla-git linki. Bilyoner Ortaklık (affiliate)
 * programından alınan takip linki buraya konur; boşsa bilyoner.com'a gider.
 */
@ConfigurationProperties(prefix = "scorestv.bilyoner")
public record BilyonerProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("https://www.bilyoner.com/api/v3/mobile/aggregator/gamelist/all/v1?tabType=1&bulletinType=2")
        String apiUrl,
        @DefaultValue("https://www.bilyoner.com") String affiliateUrl,
        /** Oran snapshot TTL (saniye) — bu süre boyunca tek fetch cache'lenir. */
        @DefaultValue("120") int cacheSeconds,
        /** Eşleşme için kickoff zaman toleransı (dakika) — TZ farklarına karşı geniş. */
        @DefaultValue("240") int kickoffToleranceMinutes
) {}
