package com.scorestv.news;

import com.scorestv.football.domain.Country;
import com.scorestv.football.domain.CountryRepository;
import com.scorestv.football.domain.League;
import com.scorestv.football.domain.LeagueRepository;
import com.scorestv.football.domain.PlayerCareerTeam;
import com.scorestv.football.domain.PlayerCareerTeamRepository;
import com.scorestv.football.domain.Team;
import com.scorestv.football.domain.TeamLeagueSeasonRepository;
import com.scorestv.football.domain.TeamRepository;
import com.scorestv.mobile.domain.DeviceMatchSubscription;
import com.scorestv.mobile.domain.DeviceMatchSubscriptionRepository;
import com.scorestv.mobile.domain.MobileDeviceToken;
import com.scorestv.mobile.domain.MobileDeviceTokenRepository;
import com.scorestv.mobile.domain.UserNotificationPref;
import com.scorestv.mobile.domain.UserNotificationPrefRepository;
import com.scorestv.mobile.fcm.FcmMessagingService;
import com.scorestv.storage.MinioStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Yayinlanan bir haberi FCM push olarak ilgili kullanicilara gonderir.
 *
 * <p><b>Hedefleme (target):</b>
 * <ul>
 *   <li>{@code ALL} — haberin diliyle (locale) eslesen, bildirimi + haber
 *       toggle'i acik TUM cihazlar.</li>
 *   <li>{@code FAVORITES} — habere bagli takim/lig/oyuncu/ulke'yi takip eden
 *       cihazlar. Lig → o ligin (current season) takimlari; oyuncu → oyuncunun
 *       kariyerindeki (en son) takim(lar)i; ulke → o ulke adina kayitli takimlar
 *       (kulup + milli takim). Hepsi bir TAKIM ID kumesine indirgenir; o
 *       takimlari takip eden cihazlar {@code UserNotificationPref} uzerinden
 *       bulunur.</li>
 * </ul>
 *
 * <p><b>Idempotency:</b> {@code news_push_log} bir haber icin UNIQUE(article_id)
 * tutar. Push oncesi varlik kontrolu + sonrasinda log satiri → bir haber en
 * fazla BIR kez push edilir.
 *
 * <p><b>Async + commit-sonrasi:</b> {@link NewsService} publish transaction'i
 * commit oldugunda {@code afterCommit} ile bu metodu cagirir. {@code @Async}
 * sayesinde FCM I/O publish request'ini bloke etmez; commit-sonrasi cagri
 * sayesinde rollback'te yanlis bildirim gitmez. Push hatasi ASLA yayinlamayi
 * dusurmez (metot kendi icinde try/catch + log yapar).
 *
 * <p><b>Mesaj:</b> title = haber basligi, body = ozet (yoksa govdeden ~120
 * karakter), data = {type: news, slug, articleId, lang}. Kapak gorseli URL'si
 * data'ya {@code image} olarak eklenir (outbox/FCM notification image alani
 * kullanilmadigi mevcut multicast yolunda, mobil bunu data'dan okuyabilir).
 */
@Service
public class NewsNotificationService {

    private static final Logger log =
            LoggerFactory.getLogger(NewsNotificationService.class);

    /** Ozet yoksa govdeden alinan on-izleme uzunlugu (yaklasik). */
    private static final int BODY_PREVIEW_MAX = 120;
    /** FCM body alani sinirinin altinda guvenli bir ust sinir. */
    private static final int BODY_HARD_MAX = 240;

    private final NewsArticleRepository articleRepository;
    private final NewsPushLogRepository pushLogRepository;
    private final ArticleTeamLinkRepository teamLinkRepository;
    private final ArticleLeagueLinkRepository leagueLinkRepository;
    private final ArticleCountryLinkRepository countryLinkRepository;
    private final ArticlePlayerLinkRepository playerLinkRepository;
    private final ArticleFixtureLinkRepository fixtureLinkRepository;
    private final MobileDeviceTokenRepository deviceRepository;
    private final DeviceMatchSubscriptionRepository matchSubscriptionRepository;
    private final UserNotificationPrefRepository prefRepository;
    private final TeamRepository teamRepository;
    private final LeagueRepository leagueRepository;
    private final CountryRepository countryRepository;
    private final TeamLeagueSeasonRepository teamLeagueSeasonRepository;
    private final PlayerCareerTeamRepository careerTeamRepository;
    private final NewsSanitizer sanitizer;
    private final MinioStorageService storage;
    private final FcmMessagingService fcm;

    public NewsNotificationService(
            NewsArticleRepository articleRepository,
            NewsPushLogRepository pushLogRepository,
            ArticleTeamLinkRepository teamLinkRepository,
            ArticleLeagueLinkRepository leagueLinkRepository,
            ArticleCountryLinkRepository countryLinkRepository,
            ArticlePlayerLinkRepository playerLinkRepository,
            ArticleFixtureLinkRepository fixtureLinkRepository,
            MobileDeviceTokenRepository deviceRepository,
            DeviceMatchSubscriptionRepository matchSubscriptionRepository,
            UserNotificationPrefRepository prefRepository,
            TeamRepository teamRepository,
            LeagueRepository leagueRepository,
            CountryRepository countryRepository,
            TeamLeagueSeasonRepository teamLeagueSeasonRepository,
            PlayerCareerTeamRepository careerTeamRepository,
            NewsSanitizer sanitizer,
            MinioStorageService storage,
            FcmMessagingService fcm) {
        this.articleRepository = articleRepository;
        this.pushLogRepository = pushLogRepository;
        this.teamLinkRepository = teamLinkRepository;
        this.leagueLinkRepository = leagueLinkRepository;
        this.countryLinkRepository = countryLinkRepository;
        this.playerLinkRepository = playerLinkRepository;
        this.fixtureLinkRepository = fixtureLinkRepository;
        this.deviceRepository = deviceRepository;
        this.matchSubscriptionRepository = matchSubscriptionRepository;
        this.prefRepository = prefRepository;
        this.teamRepository = teamRepository;
        this.leagueRepository = leagueRepository;
        this.countryRepository = countryRepository;
        this.teamLeagueSeasonRepository = teamLeagueSeasonRepository;
        this.careerTeamRepository = careerTeamRepository;
        this.sanitizer = sanitizer;
        this.storage = storage;
        this.fcm = fcm;
    }

    /** En fazla deneme — asilirsa satir FAILED olur (sonsuz dongu olmaz). */
    private static final int MAX_ATTEMPTS = 6;

    /** SENDING lease suresi — bu suredeicinde bitmezse (crash) worker devralir. */
    private static final java.time.Duration LEASE = java.time.Duration.ofMinutes(3);

    /**
     * Outbox satirini gonder — dusuk gecikme yolu (yayin commit'i sonrasi
     * {@link NewsPushPublisher} cagirir). Worker ({@link NewsPushDispatchJob})
     * ayni islemi emniyet agi olarak periyodik yapar; atomik claim sayesinde
     * ikisi ayni satiri ASLA iki kez gondermez.
     */
    @Async
    @Transactional
    public void dispatchOneAsync(Long articleId) {
        doDispatch(articleId);
    }

    /** Worker girisi — kendi transaction'inda tek satir isler. */
    @Transactional
    public void dispatchOne(Long articleId) {
        doDispatch(articleId);
    }

    /**
     * Tek outbox satirinin gonderimi: atomik claim + lease → alicilari cozumle
     * → FCM (sert hatada exception) → SENT; hatada backoff'la PENDING'e geri
     * (MAX_ATTEMPTS asilirsa FAILED). Idempotency: claim'i kazanamayan cikar.
     */
    private void doDispatch(Long articleId) {
        if (articleId == null) return;
        try {
            NewsPushLog row = pushLogRepository.findByArticleId(articleId).orElse(null);
            if (row == null || "SENT".equals(row.getStatus())
                    || "FAILED".equals(row.getStatus())) {
                return;
            }
            Instant now = Instant.now();
            // Atomik claim: yalniz vakti gelmis PENDING / lease'i dolmus SENDING.
            if (pushLogRepository.claim(row.getId(), now, now.plus(LEASE)) == 0) {
                return; // baska thread/worker gonderiyor ya da sirasi gelmedi
            }
            row = pushLogRepository.findByArticleId(articleId).orElseThrow();

            if (row.getAttempts() > MAX_ATTEMPTS) {
                row.setStatus("FAILED");
                row.setLastError("Deneme siniri asildi (" + MAX_ATTEMPTS + ")");
                pushLogRepository.save(row);
                log.warn("Haber push FAILED (deneme siniri) articleId={}", articleId);
                return;
            }

            NewsArticle article = articleRepository.findByIdAndDeletedAtIsNull(articleId)
                    .orElse(null);
            if (article == null || article.getStatus() != NewsStatus.PUBLISHED) {
                // Yayindan kalkti/silindi — gonderme, kapat (retry anlamsiz).
                row.setStatus("FAILED");
                row.setLastError("Haber yayinda degil/silinmis");
                pushLogRepository.save(row);
                return;
            }
            if (!fcm.isEnabled()) {
                // FCM kapali (ör. lokal ortam) — satiri acik birakma.
                row.setStatus("SENT");
                row.setLastError("FCM kapali — gonderim yok");
                pushLogRepository.save(row);
                return;
            }

            final NewsPushTarget target = "ALL".equals(row.getTarget())
                    ? NewsPushTarget.ALL : NewsPushTarget.FAVORITES;
            final String lang = langOf(article.getLang());
            Set<String> tokens = (target == NewsPushTarget.ALL)
                    ? resolveAllTokens(lang)
                    : resolveFavoriteTokens(articleId, lang);

            try {
                if (!tokens.isEmpty()) {
                    // Sert (batch) FCM hatasinda exception → retry'a duser;
                    // tekil token hatalari (gecersiz token) yutulur.
                    fcm.sendMulticastOrThrow(new ArrayList<>(tokens),
                            article.getTitle(), buildBody(article), buildData(article),
                            null, false);
                }
                row.setStatus("SENT");
                row.setRecipientCount(tokens.size());
                row.setLastError(null);
                pushLogRepository.save(row);
                log.info("Haber push tamam articleId={} target={} alici={} deneme={}",
                        articleId, target, tokens.size(), row.getAttempts());
            } catch (RuntimeException sendEx) {
                // Gecici FCM/ag hatasi: backoff'la tekrar dene (30s * 2^n, max 10dk).
                if (row.getAttempts() >= MAX_ATTEMPTS) {
                    row.setStatus("FAILED");
                } else {
                    row.setStatus("PENDING");
                    long backoffSec = Math.min(600,
                            30L << Math.min(10, row.getAttempts() - 1));
                    row.setNextAttemptAt(Instant.now().plusSeconds(backoffSec));
                }
                row.setLastError(clip(String.valueOf(sendEx.getMessage()), 500));
                pushLogRepository.save(row);
                log.warn("Haber push hatasi (deneme {}/{}) articleId={}: {}",
                        row.getAttempts(), MAX_ATTEMPTS, articleId, sendEx.getMessage());
            }
        } catch (Exception ex) {
            // Beklenmeyen hata gonderim dongusunu ASLA dusurmez; lease dolunca
            // worker satiri yeniden dener.
            log.warn("Haber push dispatch hatasi articleId={}: {}",
                    articleId, ex.getMessage());
        }
    }

    // ============================================================
    // Alici cozumleme
    // ============================================================

    /** ALL: dil eslesen tum bildirimi+haber acik cihazlarin token'lari. */
    private Set<String> resolveAllTokens(String lang) {
        Set<String> tokens = new LinkedHashSet<>();
        for (MobileDeviceToken t : deviceRepository.findNewsRecipientsByLang(lang)) {
            if (t.getFcmToken() != null) {
                tokens.add(t.getFcmToken());
            }
        }
        return tokens;
    }

    /**
     * FAVORITES: habere bagli takim/lig/oyuncu/ulke → TAKIM ID kumesi → o
     * takimlari takip eden cihaz token'lari (dedup).
     */
    private Set<String> resolveFavoriteTokens(Long articleId, String lang) {
        Set<String> tokens = new LinkedHashSet<>();

        // 1) Takim/lig/oyuncu/ulke → TAKIM ID kumesi → o takimlari takip eden
        //    cihazlar (UserNotificationPref uzerinden, dil eslesir).
        Set<Long> teamIds = resolveTargetTeamIds(articleId);
        if (!teamIds.isEmpty()) {
            for (UserNotificationPref p :
                    prefRepository.findNewsFavoriteRecipients(teamIds, lang)) {
                MobileDeviceToken t = p.getDeviceToken();
                if (t != null && t.getFcmToken() != null) {
                    tokens.add(t.getFcmToken());
                }
            }
        }

        // 2) Habere bagli maclari (fixture) FAVORILEYEN cihazlar — mac-raporu
        //    haberi o maci favorileyenlere de ulassin. device_match_subscriptions
        //    uzerinden bulunur; ALL/lang alicilariyla ayni filtre (notifyNews +
        //    locale on-eki) Java tarafinda uygulanir (findRecipientsForFixture
        //    zaten notificationsEnabled=true suzer).
        for (ArticleFixtureLink fl : fixtureLinkRepository.findByArticleId(articleId)) {
            if (fl.getFixtureId() == null) continue;
            for (DeviceMatchSubscription sub :
                    matchSubscriptionRepository.findRecipientsForFixture(fl.getFixtureId())) {
                MobileDeviceToken t = sub.getDeviceToken();
                if (t == null || t.getFcmToken() == null) continue;
                if (!t.isNotifyNews()) continue;
                if (!localeMatches(t.getLocale(), lang)) continue;
                tokens.add(t.getFcmToken());
            }
        }

        return tokens;
    }

    /**
     * Cihaz locale'i haber diline (on-ek) eslesiyor mu? findNewsRecipientsByLang
     * ile ayni semantik: cihaz "tr"/"TR"/"en-US" gibi degerler tutabilir.
     */
    private static boolean localeMatches(String locale, String lang) {
        if (locale == null || lang == null) return false;
        return locale.toLowerCase(Locale.ROOT)
                .startsWith(lang.toLowerCase(Locale.ROOT));
    }

    /**
     * Habere bagli tum varliklardan hedef TAKIM id kumesini toplar:
     * <ul>
     *   <li>bagli takimlar → dogrudan.</li>
     *   <li>bagli ligler → ligin (current season) takimlari.</li>
     *   <li>bagli oyuncular → oyuncunun kariyer takim(lar)i (en son sezon onde).</li>
     *   <li>bagli ulkeler → o ulke adina kayitli takimlar (kulup + milli).</li>
     * </ul>
     */
    private Set<Long> resolveTargetTeamIds(Long articleId) {
        Set<Long> teamIds = new LinkedHashSet<>();

        // Takimlar — dogrudan.
        for (ArticleTeamLink l : teamLinkRepository.findByArticleId(articleId)) {
            if (l.getTeamId() != null) teamIds.add(l.getTeamId());
        }

        // Ligler — ligin current season takimlari (junction tablosu).
        for (ArticleLeagueLink l : leagueLinkRepository.findByArticleId(articleId)) {
            if (l.getLeagueId() == null) continue;
            League league = leagueRepository.findById(l.getLeagueId()).orElse(null);
            Integer season = league != null ? league.getCurrentSeason() : null;
            if (season == null) continue;
            for (Team t : teamLeagueSeasonRepository
                    .findTeamsByLeagueAndSeason(l.getLeagueId(), season)) {
                if (t != null && t.getId() != null) teamIds.add(t.getId());
            }
        }

        // Oyuncular — kariyer takimlari (en son sezon oncelikli).
        for (ArticlePlayerLink l : playerLinkRepository.findByArticleId(articleId)) {
            if (l.getPlayerId() == null) continue;
            Long currentTeamId = resolvePlayerCurrentTeamId(l.getPlayerId());
            if (currentTeamId != null) teamIds.add(currentTeamId);
        }

        // Ulkeler — o ulke adina kayitli takimlar (kulup + milli takim).
        for (ArticleCountryLink l : countryLinkRepository.findByArticleId(articleId)) {
            if (l.getCountryId() == null) continue;
            Country country = countryRepository.findById(l.getCountryId()).orElse(null);
            if (country == null || country.getName() == null) continue;
            for (Team t : teamRepository.findByCountryNameIgnoreCase(country.getName())) {
                if (t != null && t.getId() != null) teamIds.add(t.getId());
            }
        }

        return teamIds;
    }

    /**
     * Oyuncunun "guncel" takim id'si — kariyer takimlari icinde en son sezona
     * ({@code seasons} dizisindeki en buyuk yil) sahip olan takim. Kariyer yoksa
     * null.
     */
    private Long resolvePlayerCurrentTeamId(Long playerId) {
        List<PlayerCareerTeam> careerTeams =
                careerTeamRepository.findByPlayerIdWithTeam(playerId);
        Long bestTeamId = null;
        int bestSeason = Integer.MIN_VALUE;
        for (PlayerCareerTeam ct : careerTeams) {
            if (ct.getTeam() == null || ct.getTeam().getId() == null) continue;
            int maxSeason = maxSeason(ct.getSeasons());
            if (maxSeason > bestSeason) {
                bestSeason = maxSeason;
                bestTeamId = ct.getTeam().getId();
            }
        }
        return bestTeamId;
    }

    private static int maxSeason(List<Integer> seasons) {
        int max = Integer.MIN_VALUE;
        if (seasons != null) {
            for (Integer y : seasons) {
                if (y != null && y > max) max = y;
            }
        }
        return max;
    }

    // ============================================================
    // Mesaj uretimi
    // ============================================================

    /** body: ozet varsa ozet; yoksa govdeden strip edilmis ~120 karakter. */
    private String buildBody(NewsArticle article) {
        String summary = article.getSummary();
        if (summary != null && !summary.isBlank()) {
            return clip(summary.trim(), BODY_HARD_MAX);
        }
        String text = sanitizer.stripToText(article.getBody());
        if (text == null || text.isBlank()) {
            return "";
        }
        return clip(text.trim(), BODY_PREVIEW_MAX);
    }

    private Map<String, String> buildData(NewsArticle article) {
        Map<String, String> d = new HashMap<>();
        d.put("type", "news");
        d.put("slug", article.getSlug());
        d.put("articleId", String.valueOf(article.getId()));
        d.put("lang", article.getLang());
        // Kapak gorseli (varsa) — mobil zengin bildirim/onizleme icin data'dan okur.
        String cover = coverUrl(article.getCoverImageKey());
        if (cover != null) {
            d.put("image", cover);
        }
        return d;
    }

    private String coverUrl(String key) {
        if (key == null || key.isBlank()) return null;
        try {
            return storage.publicUrl(key);
        } catch (Exception ex) {
            log.debug("Haber kapak URL cozulemedi key={}: {}", key, ex.getMessage());
            return null;
        }
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max).trim() + "…";
    }

    /** Haber dilini "tr"/"en"e indirger (default tr). */
    private static String langOf(String raw) {
        if (raw == null || raw.isBlank()) return "tr";
        return raw.toLowerCase(Locale.ROOT).startsWith("en") ? "en" : "tr";
    }
}
