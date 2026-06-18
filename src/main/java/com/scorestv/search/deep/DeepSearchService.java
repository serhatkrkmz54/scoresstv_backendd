package com.scorestv.search.deep;

import com.scorestv.football.ApiFootballClient;
import com.scorestv.football.ApiFootballResponse;
import com.scorestv.football.sync.CoachUpserter;
import com.scorestv.football.sync.PlayerProfileUpserter;
import com.scorestv.football.sync.ReferenceUpserter;
import com.scorestv.football.sync.TeamUpserter;
import com.scorestv.football.sync.dto.CoachApiDto;
import com.scorestv.football.sync.dto.CountryApiDto;
import com.scorestv.football.sync.dto.LeagueApiDto;
import com.scorestv.football.sync.dto.PlayerSeasonApiDto;
import com.scorestv.football.sync.dto.TeamApiDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * "Derin arama" — kullanıcı sistemde OLMAYAN bir takım/oyuncu/koç/lig/ülke
 * aradığında (lokal Elasticsearch sonuç vermediğinde), API-Football'un
 * {@code ?search=} uçlarından ilgili kayıtları çekip DB'ye upsert eder.
 *
 * <p><b>Otomatik indeksleme:</b> Team/Player/League/Country VE Coach
 * upserter'ları {@link com.scorestv.search.events.EntityIndexedEvent}
 * yayınladığı için upsert sonrası ES'e OTOMATİK yazılır (commit sonrası,
 * async). Böylece kullanıcı kısa süre sonra (frontend silent-retry veya
 * yeniden arama) sonucu görür. Koç da artık {@code scorestv_coaches}
 * indeksinde aranabilir (CoachDoc + SearchService.searchCoaches).
 *
 * <p><b>Kota koruması (ZORUNLU):</b>
 * <ul>
 *   <li>min uzunluk 3 — tek/iki harfli sorgu API'ye gitmez</li>
 *   <li>negatif cache 15 dk — aynı sorgu için tekrar tekrar API'ye gidilmez</li>
 *   <li>Semaphore(2) — eşzamanlı derin arama sınırı (bot/spam'e karşı)</li>
 *   <li>{@code scorestv.search.deep-import.enabled=false} ile tamamen kapatılır</li>
 * </ul>
 * Ek olarak {@link ApiFootballClient} kendi 429/cooldown korumasını uygular.
 */
@Service
public class DeepSearchService {

    private static final Logger log = LoggerFactory.getLogger(DeepSearchService.class);

    private static final ParameterizedTypeReference<ApiFootballResponse<List<TeamApiDto>>>
            TEAMS_TYPE = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiFootballResponse<List<CoachApiDto>>>
            COACHS_TYPE = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiFootballResponse<List<LeagueApiDto>>>
            LEAGUES_TYPE = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiFootballResponse<List<CountryApiDto>>>
            COUNTRIES_TYPE = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiFootballResponse<List<PlayerSeasonApiDto>>>
            PLAYERS_TYPE = new ParameterizedTypeReference<>() {};

    private static final int MIN_LEN = 3;
    private static final long ATTEMPT_TTL_MS = 15 * 60 * 1000L;
    private static final int MAX_CACHE = 5000;

    private final ApiFootballClient client;
    private final TeamUpserter teamUpserter;
    private final CoachUpserter coachUpserter;
    private final ReferenceUpserter referenceUpserter;
    private final PlayerProfileUpserter playerProfileUpserter;
    private final boolean enabled;

    /** Son denenen (normalize) sorgular → epochMillis. TTL boyunca tekrar denenmez. */
    private final ConcurrentHashMap<String, Long> attempted = new ConcurrentHashMap<>();
    /** Eşzamanlı derin arama sınırı. */
    private final Semaphore slots = new Semaphore(2);

    public DeepSearchService(ApiFootballClient client,
                             TeamUpserter teamUpserter,
                             CoachUpserter coachUpserter,
                             ReferenceUpserter referenceUpserter,
                             PlayerProfileUpserter playerProfileUpserter,
                             @Value("${scorestv.search.deep-import.enabled:true}")
                             boolean enabled) {
        this.client = client;
        this.teamUpserter = teamUpserter;
        this.coachUpserter = coachUpserter;
        this.referenceUpserter = referenceUpserter;
        this.playerProfileUpserter = playerProfileUpserter;
        this.enabled = enabled;
    }

    /**
     * Lokal arama boş döndüğünde tetiklenir (fire-and-forget). Tüm tipleri
     * API'den arar, DB'ye upsert eder. İstek thread'ini bekletmez (@Async).
     */
    @Async
    public void triggerAsync(String query) {
        if (!enabled || query == null) return;
        final String q = query.trim();
        if (q.length() < MIN_LEN) return;
        final String key = normalize(q);
        if (key.isEmpty()) return;

        // API-Football'un ?search= alani YALNIZCA harf/rakam/boşluk kabul eder;
        // nokta/kesme/tire/iki nokta vb. iceren sorgular 400 verir ("The Search
        // field may only contain alpha-numeric characters and spaces"). Orn.
        // "S. Olsson", "O'Brien", "FC-X". Bu yuzden API'ye göndermeden temizle.
        final String apiQ = sanitizeForApi(q);
        if (apiQ.length() < MIN_LEN) {
            log.debug("Derin arama atlandı (temizlenmiş sorgu < {} karakter): '{}'",
                    MIN_LEN, q);
            return;
        }

        final long now = System.currentTimeMillis();
        final Long last = attempted.get(key);
        if (last != null && (now - last) < ATTEMPT_TTL_MS) return; // yakın zamanda denendi
        if (!slots.tryAcquire()) {
            log.debug("Derin arama atlandı (slot dolu): '{}'", q);
            return;
        }
        try {
            attempted.put(key, now);
            if (attempted.size() > MAX_CACHE) attempted.clear();
            log.info("Derin arama başladı: '{}' (api sorgusu='{}')", q, apiQ);
            importTeams(apiQ);
            importCoaches(apiQ);
            importLeagues(apiQ);
            importCountries(apiQ);
            importPlayers(apiQ);
        } finally {
            slots.release();
        }
    }

    private void importTeams(String q) {
        try {
            ApiFootballResponse<List<TeamApiDto>> r =
                    client.get("/teams", Map.of("search", q), TEAMS_TYPE);
            List<TeamApiDto> items = r.response();
            if (items != null && !items.isEmpty()) {
                teamUpserter.upsert(items);
                log.info("Derin arama: {} takım içe aktarıldı ('{}')", items.size(), q);
            }
        } catch (RuntimeException e) {
            log.warn("Derin arama /teams hata ('{}'): {}", q, e.toString());
        }
    }

    private void importCoaches(String q) {
        try {
            ApiFootballResponse<List<CoachApiDto>> r =
                    client.get("/coachs", Map.of("search", q), COACHS_TYPE);
            List<CoachApiDto> items = r.response();
            if (items != null) {
                int n = 0;
                for (CoachApiDto dto : items) {
                    if (dto == null || dto.id() == null) continue;
                    try {
                        coachUpserter.upsert(dto);
                        n++;
                    } catch (RuntimeException ignore) {
                        // tek koç hatası diğerlerini bloklamasın
                    }
                }
                if (n > 0) log.info("Derin arama: {} koç içe aktarıldı ('{}')", n, q);
            }
        } catch (RuntimeException e) {
            log.warn("Derin arama /coachs hata ('{}'): {}", q, e.toString());
        }
    }

    private void importLeagues(String q) {
        try {
            ApiFootballResponse<List<LeagueApiDto>> r =
                    client.get("/leagues", Map.of("search", q), LEAGUES_TYPE);
            List<LeagueApiDto> items = r.response();
            if (items != null) {
                int n = 0;
                for (LeagueApiDto dto : items) {
                    if (dto == null) continue;
                    try {
                        referenceUpserter.upsertLeague(dto);
                        n++;
                    } catch (RuntimeException ignore) {
                    }
                }
                if (n > 0) log.info("Derin arama: {} lig içe aktarıldı ('{}')", n, q);
            }
        } catch (RuntimeException e) {
            log.warn("Derin arama /leagues hata ('{}'): {}", q, e.toString());
        }
    }

    private void importCountries(String q) {
        try {
            ApiFootballResponse<List<CountryApiDto>> r =
                    client.get("/countries", Map.of("search", q), COUNTRIES_TYPE);
            List<CountryApiDto> items = r.response();
            if (items != null && !items.isEmpty()) {
                referenceUpserter.upsertCountries(items);
                log.info("Derin arama: {} ülke içe aktarıldı ('{}')", items.size(), q);
            }
        } catch (RuntimeException e) {
            log.warn("Derin arama /countries hata ('{}'): {}", q, e.toString());
        }
    }

    private void importPlayers(String q) {
        try {
            ApiFootballResponse<List<PlayerSeasonApiDto>> r =
                    client.get("/players/profiles", Map.of("search", q), PLAYERS_TYPE);
            List<PlayerSeasonApiDto> items = r.response();
            if (items != null) {
                int n = 0;
                for (PlayerSeasonApiDto dto : items) {
                    if (dto == null || dto.player() == null) continue;
                    try {
                        playerProfileUpserter.upsert(dto.player());
                        n++;
                    } catch (RuntimeException ignore) {
                    }
                }
                if (n > 0) log.info("Derin arama: {} oyuncu içe aktarıldı ('{}')", n, q);
            }
        } catch (RuntimeException e) {
            log.warn("Derin arama /players/profiles hata ('{}'): {}", q, e.toString());
        }
    }

    /**
     * API-Football ?search= icin sorguyu temizler: yalnizca harf (Türkçe/aksanli
     * dahil), rakam ve TEK boşluk birakir; nokta/kesme/tire/iki nokta gibi her
     * şeyi boşlukla degistirip coklu boşluklari sadelestrir. Boylece "S. Olsson"
     * → "S Olsson", "O'Brien" → "O Brien" olur ve API 400 vermez.
     */
    private static String sanitizeForApi(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            } else if (Character.isWhitespace(c)) {
                sb.append(' ');
            }
            // diger tum noktalama/sembol karakterleri atilir
        }
        return sb.toString().trim().replaceAll("\\s+", " ");
    }

    private static String normalize(String s) {
        String x = s.toLowerCase(Locale.forLanguageTag("tr"));
        x = x.replace('ı', 'i').replace('İ', 'i')
                .replace('ş', 's').replace('ğ', 'g')
                .replace('ü', 'u').replace('ö', 'o').replace('ç', 'c');
        x = Normalizer.normalize(x, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return x.replaceAll("[^a-z0-9]", "");
    }
}
