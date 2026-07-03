package com.scorestv.indexnow;

import com.scorestv.common.SlugUtil;
import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.football.seo.SeoProperties;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Yeni oluşan ve yeni biten maçların canonical URL'lerini periyodik olarak
 * IndexNow'a ({@link IndexNowService}) gönderir — Bing/Yandex hızlı indeksleme.
 *
 * <p><b>Ne gönderilir:</b>
 * <ul>
 *   <li><b>Yeni oluşan maçlar:</b> son {@link #LOOKBACK} içinde güncellenmiş,
 *       kickoff'u gelecekte olan fikstürler ({@code findUpcomingUpdatedSince}).
 *       Fixture'ta {@code createdAt} yok (kendi atanmış @Id'si var, BaseEntity
 *       extend etmez) — {@code updatedAt} + "gelecekte başlıyor" yeni fikstür
 *       kayıtlarını temsil eder.</li>
 *   <li><b>Yeni biten maçlar:</b> {@code statusShort ∈ {FT, AET, PEN}} ve son
 *       {@link #LOOKBACK} içinde güncellenmiş
 *       ({@code findRecentlyFinishedUpdatedSince}, son 48 saatte oynanmış).</li>
 * </ul>
 *
 * <p>Job aralığından ({@code ~15dk}) biraz geniş bir {@link #LOOKBACK} penceresi
 * (20 dk) kullanılır ki tick kaymalarında değişiklik kaçmasın. İki liste id'ye
 * göre dedup edilir; her biri için {@link com.scorestv.football.seo.MatchDetailSeoBuilder}
 * ile birebir aynı canonical URL üretilir ({@code siteUrl + "/" + slug}).
 *
 * <p>Bean yalnız {@code scorestv.indexnow.enabled=true} ile aktif — kapalıyken
 * ne bean ne job vardır (deploy güvenli varsayılan).
 */
@Component
@ConditionalOnProperty(name = "scorestv.indexnow.enabled", havingValue = "true")
public class IndexNowSubmitJob {

    private static final Logger log = LoggerFactory.getLogger(IndexNowSubmitJob.class);

    /**
     * Değişiklik penceresi — job aralığından (~15dk) biraz geniş tutulur ki
     * tick kaymalarında yeni/biten maç kaçmasın.
     */
    private static final Duration LOOKBACK = Duration.ofMinutes(20);

    /**
     * "Yeni oluşan maç" kolunda yalnız YAKIN vadeli (bu pencere içindeki) maçlar
     * gönderilir. Hızlı indeksleme asıl yakın maçlarda değerlidir; ileri-tarihli
     * fikstürler zaten sitemap'ten taranır. Sınır, toplu sync'lerin çok sayıda
     * uzak maçı tekrar tekrar IndexNow'a göndermesini engeller (spam koruması).
     */
    private static final Duration UPCOMING_WINDOW = Duration.ofDays(7);

    /** Sonucu kesinleşmiş "biten" maç statüleri — MatchDetailSeoBuilder ile aynı. */
    private static final Set<String> FINISHED_STATUSES = Set.of("FT", "AET", "PEN");

    /**
     * "Yeni biten maç" kolunda yalnız SON bu kadar süre içinde OYNANMIŞ maçlar
     * gönderilir — eski biten maçların istatistik/hydrate senkronu {@code updatedAt}'i
     * tazeleyince binlerce eski URL'nin yeniden gönderilmesini engeller.
     */
    private static final Duration FINISHED_KICKOFF_WINDOW = Duration.ofHours(48);

    /**
     * Bir turda gönderilecek MAKS URL — IndexNow'u sel gibi doldurup 429 almamak
     * için tavan. Aşan maçlar zaten sitemap'ten taranır.
     */
    private static final int MAX_PER_RUN = 1000;

    private final FixtureRepository fixtureRepository;
    private final IndexNowService indexNowService;
    private final SeoProperties seoProperties;

    public IndexNowSubmitJob(FixtureRepository fixtureRepository,
                             IndexNowService indexNowService,
                             SeoProperties seoProperties) {
        this.fixtureRepository = fixtureRepository;
        this.indexNowService = indexNowService;
        this.seoProperties = seoProperties;
    }

    /**
     * Her {@code scorestv.indexnow.submit-interval-minutes} (varsayılan 15)
     * dakikada bir, son {@link #LOOKBACK} içinde yeni oluşan / yeni biten
     * maçların canonical URL'lerini IndexNow'a gönderir.
     *
     * <p>{@code readOnly} transaction: canonical slug için home/away takım
     * adlarına erişilir — sorgular JOIN FETCH'li olsa da transaction sınırı
     * içinde kalmak güvenlidir.
     */
    @Scheduled(
            fixedDelayString = "${scorestv.indexnow.submit-interval-minutes:15}",
            timeUnit = TimeUnit.MINUTES)
    @SchedulerLock(name = "indexNowSubmit", lockAtMostFor = "PT5M")
    @Transactional(readOnly = true)
    public void run() {
        Instant now = Instant.now();
        Instant since = now.minus(LOOKBACK);
        Instant until = now.plus(UPCOMING_WINDOW);
        Instant finishedFrom = now.minus(FINISHED_KICKOFF_WINDOW);
        Pageable limit = PageRequest.of(0, MAX_PER_RUN);

        String baseUrl = trimTrailingSlash(seoProperties.siteUrl());

        // id → canonical URL (dedup; iki listede aynı maç olabilir). Her kol
        // updatedAt DESC sıralı + MAX_PER_RUN limitli gelir.
        Map<Long, String> byId = new LinkedHashMap<>();
        for (Fixture f : fixtureRepository.findUpcomingUpdatedSince(since, now, until, limit)) {
            byId.put(f.getId(), canonicalUrl(baseUrl, f));
        }
        for (Fixture f : fixtureRepository
                .findRecentlyFinishedUpdatedSince(FINISHED_STATUSES, since, finishedFrom, limit)) {
            byId.put(f.getId(), canonicalUrl(baseUrl, f));
        }

        if (byId.isEmpty()) {
            return;
        }
        List<String> urls = new ArrayList<>(byId.values());
        // İki kol birleşince tavanı aşabilir — toplamı da MAX_PER_RUN ile sınırla.
        if (urls.size() > MAX_PER_RUN) {
            urls = urls.subList(0, MAX_PER_RUN);
        }
        indexNowService.submit(urls);
        log.info("IndexNow: {} maç URL'si gönderime verildi.", urls.size());
    }

    /**
     * Bir maçın canonical URL'i — {@link com.scorestv.football.seo.MatchDetailSeoBuilder}
     * ile BİREBİR aynı: {@code baseUrl + "/" + fixtureSlug(home, away, id)}.
     * Slug daima İngilizce takım adlarından üretilir (dil bağımsız kök).
     */
    private static String canonicalUrl(String baseUrl, Fixture f) {
        String slug = SlugUtil.fixtureSlug(
                f.getHomeTeam().getName(), f.getAwayTeam().getName(), f.getId());
        return baseUrl + "/" + slug;
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
