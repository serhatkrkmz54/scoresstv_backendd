package com.scorestv.social;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * SocialData.tools (X/Twitter veri sağlayıcı) entegrasyonu yapılandırması.
 *
 * <p>{@code enabled=false} (varsayılan) iken hiç istek atılmaz; sağ raydaki
 * tweet bölümü boş döner. API anahtarı GİZLİ — yalnız sunucu .env'inde
 * {@code SOCIALDATA_API_KEY} ile verilir, asla frontend'e sızmaz.
 *
 * <p>Maliyet: tweet başına ~$0.0002. Bu yüzden tarayıcı başına DEĞİL, arka
 * planda {@code refresh-ms} aralığıyla TEK SEFER çekilir ve bellek cache'inden
 * tüm ziyaretçilere servis edilir.
 */
@ConfigurationProperties(prefix = "scorestv.social")
public record SocialDataProperties(

        @DefaultValue("false") boolean enabled,

        /** SocialData API anahtarı (Bearer). Boşsa modül pasif. */
        @DefaultValue("") String apiKey,

        @DefaultValue("https://api.socialdata.tools") String baseUrl,

        /** Çekilecek X hesapları (@'siz kullanıcı adları). */
        @DefaultValue({"Fenerbahce", "GalatasaraySK", "Besiktas", "Trabzonspor",
                "FIFAWorldCup", "TFF_Org"})
        List<String> accounts,

        /** Hesap başına saklanacak en fazla tweet. */
        @DefaultValue("8") int maxPerAccount,

        /** Harmanlanmış akışta tutulacak en fazla tweet. */
        @DefaultValue("60") int maxTotal,

        /** Yanıtları hariç tut (sadece tweet). */
        @DefaultValue("true") boolean excludeReplies,

        /** Retweet'leri hariç tut. */
        @DefaultValue("true") boolean excludeRetweets
) {}
