package com.scorestv.broadcasts;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * TheSportsDB (thesportsdb.com) TV yayın bilgisi entegrasyonu.
 *
 * <p>Bir maçın hangi kanallarda yayınlandığını verir. API-Football fixture
 * id'si TheSportsDB event'inin {@code idAPIfootball} alanıyla bire bir
 * eşleştirilir — isim belirsizliğine takılmadan kesin eşleşme sağlanır.
 *
 * <p>v1 API ücretsiz anahtarı herkes için {@code "123"}; premium anahtar
 * kişiye özeldir. Ücretsiz kota dar (30 istek/dk) olduğu için sonuçlar fixture
 * bazında uzun TTL ile cache'lenir (TV programı sık değişmez).
 */
@ConfigurationProperties(prefix = "scorestv.thesportsdb")
public record TheSportsDbProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("https://www.thesportsdb.com/api/v1/json") String baseUrl,
        /** v1 ücretsiz anahtar = "123"; premium kişiye özel. */
        @DefaultValue("123") String apiKey,
        /** date hesaplaması için timezone (TheSportsDB UTC tutar). */
        @DefaultValue("Etc/UTC") String timezone,
        /** Dolu sonuç cache süresi (dakika). */
        @DefaultValue("720") int cacheTtlMinutes,
        /** Boş sonuç cache süresi (dakika) — yayın bilgisi maçtan günler önce
         *  girilebilir, bu yüzden boş sonuç da bir süre cache'lenir. */
        @DefaultValue("180") int emptyCacheTtlMinutes
) {}
