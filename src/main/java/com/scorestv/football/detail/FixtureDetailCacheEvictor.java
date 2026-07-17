package com.scorestv.football.detail;

import com.scorestv.football.FootballCacheNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * Maç detay Redis cache'inin COHERENCY'sini sağlar.
 *
 * <p>Detay cevabı {@code af-live} cache'inde {@code detail-{id}-{country}-{lang}}
 * anahtarıyla 15sn tutulur. Canlı ticker skoru/durumu DB'de değiştirdiğinde bu
 * cache girişi <b>dokunulmadığı</b> için kullanıcı bir sonraki açılışta 15sn'ye
 * kadar ESKİ skoru görebiliyordu. Bu bileşen tam olarak bu boşluğu kapatır:
 * skor/status değişince o maçın TÜM cache varyantları anında silinir → sonraki
 * okuma (veya WS push sonrası refetch) DAİMA taze DB'yi döner, api-football'a
 * hiç gidilmez. "Değişti → DB + Redis + WS → göster" akışının Redis ayağı budur.
 *
 * <p><b>Varyant sorunu:</b> anahtara kullanıcı ülke kodu ({@code country})
 * girdiği için bir maçın onlarca varyantı olabilir. İki katmanlı çözüm:
 * <ol>
 *   <li><b>Varyant index'i:</b> her cache-miss'te görülen logical key bir Redis
 *       SET'ine ({@code detvars:{id}}) yazılır; evict bu seti okuyup her birini
 *       siler — keyspace SCAN'i gerekmez, kesin ve hızlı.</li>
 *   <li><b>Güvenlik ağı:</b> mobil ve TR web kullanıcılarının tamamı
 *       {@code country=null → "TR"} varyantını kullandığından
 *       {@code detail-{id}-TR-tr} ve {@code -TR-en} her zaman ayrıca silinir —
 *       index bir varyantı kaçırsa bile mobil kullanıcı %100 taze görür.</li>
 * </ol>
 *
 * <p>Tüm işlemler Redis hatasına karşı yutulur (coherency "best-effort"; en
 * kötü ihtimalle 15sn TTL güvenlik ağı devrede kalır, canlı-skor tick'i
 * bloklanmaz).
 */
@Component
public class FixtureDetailCacheEvictor {

    private static final Logger log =
            LoggerFactory.getLogger(FixtureDetailCacheEvictor.class);

    /** Maç başına "görülen varyantlar" Redis SET anahtarı öneki. */
    private static final String IDX_PREFIX = "scorestv:live:detvars:";

    /** Index TTL — cache TTL'inden (15sn) uzun; yalnız aktif varyantları tutar. */
    private static final Duration IDX_TTL = Duration.ofMinutes(3);

    private final StringRedisTemplate redis;
    private final CacheManager cacheManager;

    public FixtureDetailCacheEvictor(StringRedisTemplate redis, CacheManager cacheManager) {
        this.redis = redis;
        this.cacheManager = cacheManager;
    }

    /**
     * Detay cache logical key'i — {@code MatchDetailService.loadCachedResponse}
     * üzerindeki {@code @Cacheable} key ifadesiyle BİREBİR aynı olmalı.
     */
    public static String variantKey(Long fixtureId, String country, boolean turkish) {
        return "detail-" + fixtureId + "-" + (country == null ? "TR" : country)
                + "-" + (turkish ? "tr" : "en");
    }

    /** Cache-miss'te çağrılır: bu (country,lang) varyantını index'e ekle. */
    public void recordVariant(Long fixtureId, String country, boolean turkish) {
        if (fixtureId == null) return;
        try {
            String k = IDX_PREFIX + fixtureId;
            redis.opsForSet().add(k, variantKey(fixtureId, country, turkish));
            redis.expire(k, IDX_TTL);
        } catch (RuntimeException ex) {
            log.debug("recordVariant hata id={}: {}", fixtureId, ex.getMessage());
        }
    }

    /** Skor/status değişiminde: maçın TÜM detay cache varyantlarını sil. */
    public void evictAll(Long fixtureId) {
        if (fixtureId == null) return;
        try {
            Cache cache = cacheManager.getCache(FootballCacheNames.LIVE);
            if (cache == null) return;
            String k = IDX_PREFIX + fixtureId;
            Set<String> variants = redis.opsForSet().members(k);
            if (variants != null) {
                for (String v : variants) {
                    cache.evict(v);
                }
            }
            // Güvenlik ağı — mobil + TR web (country=null→"TR") daima kapsanır.
            cache.evict(variantKey(fixtureId, "TR", true));
            cache.evict(variantKey(fixtureId, "TR", false));
            redis.delete(k);
        } catch (RuntimeException ex) {
            log.debug("evictAll hata id={}: {}", fixtureId, ex.getMessage());
        }
    }
}
