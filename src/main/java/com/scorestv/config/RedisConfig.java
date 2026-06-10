package com.scorestv.config;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.scorestv.football.FootballCacheNames;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis cache altyapisi.
 * <ul>
 *   <li>@Cacheable / @CacheEvict Redis uzerinden calisir (Spring Cache soyutlamasi).</li>
 *   <li>Anahtarlar string, "scorestv:cache:" on eki ile.</li>
 *   <li>Adi ozel tanimlanmamis cache'ler (orn. "settings") 10 dk TTL kullanir.</li>
 *   <li>API-Football cache'leri katmanli TTL kullanir; her katman dokumantasyondaki
 *       "Recommended Calls" sikligina karsilik gelir.</li>
 *   <li>Deger serializer'i {@link GenericJackson2JsonRedisSerializer} (JSON).
 *       Spring Data Redis'in no-arg ctor'u zaten polymorfik tip cikarimi
 *       (@class), default typing ve list/map handling'i dogru ayarliyor;
 *       biz sadece {@code configure()} ile JavaTimeModule (Instant) ekliyoruz.
 *       JDK serialization'dan kacindik: DevTools restart class loader degisince
 *       eski cache satirlari ClassCastException atiyordu — JSON restart-safe.</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class RedisConfig {

    private static final String KEY_PREFIX = "scorestv:cache:";

    /**
     * Cache deger serializer'i — Spring Data Redis'in tam default'u +
     * JavaTimeModule. {@code configure()} mapper'in kendisini exposes eder,
     * Spring'in default polymorfik typing'i ve list/map handling'i bozulmadan
     * Java 8 date/time modulu kayitli olur.
     *
     * <p>Bu metod her cache config'i icin tek bir ortak serializer ureticidir
     * — yeni bir tane allocate etmek de gerekirdi ama paylasilan instance
     * thread-safe ve uretim icin daha verimli.
     */
    private static GenericJackson2JsonRedisSerializer cacheValueSerializer() {
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();
        serializer.configure(mapper -> mapper.registerModule(new JavaTimeModule()));
        return serializer;
    }

    /**
     * Paylasilan serializer instance — config bean'lerinin tumunde ayni.
     * {@code GenericJackson2JsonRedisSerializer} thread-safe; tek instance yeterli.
     */
    private static final GenericJackson2JsonRedisSerializer VALUE_SERIALIZER = cacheValueSerializer();

    /** Adi ozel tanimlanmamis cache'ler icin varsayilan yapilandirma. */
    @Bean
    public RedisCacheConfiguration redisCacheConfiguration() {
        return baseConfig(Duration.ofMinutes(10));
    }

    /**
     * Cache yoneticisi: varsayilan ayar + API-Football icin katmanli TTL'li
     * cache'ler. Listede olmayan cache adlari varsayilan ayarla olusturulur.
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                          RedisCacheConfiguration defaultConfig) {
        Map<String, RedisCacheConfiguration> caches = new HashMap<>();
        // Nadiren degisen referans veri (ulkeler, ligler, sezonlar).
        caches.put(FootballCacheNames.STATIC, baseConfig(Duration.ofHours(24)));
        // Gunluk tazelenen veri (fikstur listeleri, sakatlar, kadrolar).
        caches.put(FootballCacheNames.DAILY, baseConfig(Duration.ofHours(6)));
        // Saatlik tazelenen veri (puan durumu).
        caches.put(FootballCacheNames.HOURLY, baseConfig(Duration.ofMinutes(60)));
        // Sik tazelenen veri (canli skorlar, fikstur listesi, mac detayi).
        // 15 sn = API-Football'un kendi guncelleme cadence'i.
        caches.put(FootballCacheNames.LIVE, baseConfig(Duration.ofSeconds(15)));
        // Kadro verisi (lineups) — API'nin guncelleme cadence'i 15 dakika.
        caches.put(FootballCacheNames.LINEUPS, baseConfig(Duration.ofMinutes(15)));
        // Rankings (FIFA + UEFA) — gunluk tazelenir, evict ile aninda yansir.
        caches.put(FootballCacheNames.RANKINGS, baseConfig(Duration.ofHours(6)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(caches)
                .build();
    }

    /**
     * Verilen TTL ile, string anahtarli + on ekli + JSON-degerli temel
     * cache yapilandirmasi.
     */
    private RedisCacheConfiguration baseConfig(Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .prefixCacheNameWith(KEY_PREFIX)
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(VALUE_SERIALIZER));
    }
}
