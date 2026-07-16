package com.scorestv.highlights;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Highlightly (soccer.highlightly.net) maç özeti/highlight entegrasyonu.
 *
 * <p>API anahtarı yalnızca sunucuda durur ({@code x-rapidapi-key}). Free plan
 * kotası sınırlı olduğu için sonuçlar fixture bazında TTL cache'lenir.
 *
 * <p>{@code enabled=false} (varsayılan) iken hiç çağrı yapılmaz; özellik kapalı.
 * Sunucuda {@code HIGHLIGHTLY_ENABLED=true} + {@code HIGHLIGHTLY_API_KEY=...}
 * verilince devreye girer.
 */
@ConfigurationProperties(prefix = "scorestv.highlightly")
public record HighlightlyProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("https://soccer.highlightly.net") String baseUrl,
        /** Highlightly/RapidAPI anahtarı (x-rapidapi-key). */
        String apiKey,
        /** date hesaplaması + sorgu timezone'u. */
        @DefaultValue("Etc/UTC") String timezone,
        /** Sorgu başına maksimum highlight (0..40). */
        @DefaultValue("8") int limit,
        /** Dolu sonuç cache süresi (dakika). */
        @DefaultValue("360") int cacheTtlMinutes,
        /** Boş sonuç cache süresi (dakika) — highlight maç bitiminden 1-48s
         *  sonra gelir, bu yüzden boş sonuç kısa süre cache'lenir. */
        @DefaultValue("30") int emptyCacheTtlMinutes,
        /** Bu kadar GÜNDEN eski maçlar için Highlightly'e HİÇ çağrı yapılmaz
         *  (günlük kota koruması). Eski/obskür maçlarda highlight zaten yok/gitmiş
         *  → boşuna kota yakılır. Boş dönülür, uzun cache'lenir. 0 = kapalı. */
        @DefaultValue("45") int maxAgeDays
) {}
