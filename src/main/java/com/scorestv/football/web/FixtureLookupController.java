package com.scorestv.football.web;

import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.football.web.dto.FixtureLookupResponse;
import com.scorestv.football.web.dto.FixtureSummary;
import com.scorestv.search.service.SearchResponse;
import com.scorestv.search.service.SearchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Fikstür lookup ucu — bir takım/lig/ülke <i>arama metni</i>, verilen referans
 * tarihe göre SIRADAKI ve ÖNCEKİ maça çözülür.
 *
 * <p><b>Amaç:</b> Anasayfa (mobil/web) gün bazlı filtrede, aranan takımın o gün
 * maçı yoksa "sıradaki maç 5 Tem →" önerisi gösterebilmek.
 *
 * <p><b>Örnek:</b>
 * <pre>
 *   GET /api/v1/football/fixtures/lookup?q=galatasaray
 *   GET /api/v1/football/fixtures/lookup?q=fener&date=2026-07-05&lang=tr
 * </pre>
 *
 * <p><b>Kapsam:</b> SADECE FUTBOL ve SADECE TAKIM tipi. Arama sonucundaki en
 * iyi takım sonucu kullanılır; lig/ülke <b>bu turda desteklenmez</b> —
 * {@link FixtureRepository} lig/ülke için referans-tarih bazlı sıradaki/önceki
 * sorgusu sunmaz, o yüzden aşırı mühendislik yapmadan yalnız takım çözülür.
 * Takım bulunamazsa {@code resolved: null} + boş next/previous ile HTTP 200
 * döner (hata fırlatılmaz).
 *
 * <p><b>Auth:</b> public — {@code SecurityConfig} permitAll.
 *
 * <p><b>ES kapalıysa:</b> {@link SearchService} {@code @ConditionalOnProperty}
 * ile yüklenmeyebilir; bu controller da aynı koşulla korunur (bean yoksa
 * endpoint 404 döner) — {@code PublicSearchController} ile aynı desen.
 */
@RestController
@RequestMapping("/api/v1/football/fixtures")
@ConditionalOnProperty(prefix = "scorestv.elasticsearch", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class FixtureLookupController {

    private final SearchService searchService;
    private final FixtureRepository fixtureRepository;
    private final FixtureQueryService queryService;

    public FixtureLookupController(SearchService searchService,
                                   FixtureRepository fixtureRepository,
                                   FixtureQueryService queryService) {
        this.searchService = searchService;
        this.fixtureRepository = fixtureRepository;
        this.queryService = queryService;
    }

    /**
     * Arama metnini bir takıma çözer ve o takımın referans andan sonraki
     * (next) / önceki (previous) maçını döner.
     *
     * @param q    aranan metin (takım adı); zorunlu
     * @param date referans tarih (ISO yyyy-MM-dd); verilmezse site saatine göre
     *             bugün. Referans <b>an</b>, bu tarihin gün başlangıcıdır —
     *             next = o günden itibaren ilk maç, previous = ondan önceki son
     *             maç. Böylece "seçili gün" mantığıyla tutarlıdır.
     * @param lang "tr" → takım/lig adları Türkçe (girilmişse), aksi halde "en"
     */
    @GetMapping("/lookup")
    public FixtureLookupResponse lookup(
            @RequestParam("q") String q,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false, defaultValue = "en") String lang) {

        boolean turkish = "tr".equalsIgnoreCase(lang);

        // Referans an: verilen tarihin (yoksa bugünün) gün başlangıcı.
        ZoneId zone = queryService.zoneId();
        LocalDate refDate = (date != null) ? date : queryService.today();
        Instant ref = refDate.atStartOfDay(zone).toInstant();

        // q'yu SADECE takım tipinde çöz; VARYANT-farkında en iyi sonucu seç
        // (ES ilk sonucu bazen kadın/genç/rezerv takımını üste koyabiliyor —
        // ör. "galatasaray" → "Galatasaray K"). Bkz. pickBestTeam.
        SearchResponse hits = searchService.search(q, Set.of("team"));
        List<SearchResponse.TeamHit> teams = hits.teams();
        if (teams == null || teams.isEmpty()) {
            // Takıma çözülemedi → 200 + boş (hata değil).
            return new FixtureLookupResponse(null, null, null);
        }
        SearchResponse.TeamHit team = pickBestTeam(teams, q);

        String name = (turkish && team.nameTr() != null && !team.nameTr().isBlank())
                ? team.nameTr()
                : team.name();
        FixtureLookupResponse.Resolved resolved = new FixtureLookupResponse.Resolved(
                "team", team.id(), name, team.logoUrl());

        // Referans andan sonraki ilk / önceki son maç (limit 1). JOIN FETCH ile
        // ilişkiler yüklü gelir; kanonik mapper FixtureSummary üretir.
        PageRequest one = PageRequest.of(0, 1);
        List<Fixture> nextRows =
                fixtureRepository.findNextByTeamAfter(team.id(), ref, one);
        List<Fixture> prevRows =
                fixtureRepository.findPreviousByTeamBefore(team.id(), ref, one);

        FixtureSummary next = nextRows.isEmpty()
                ? null : queryService.toSummary(nextRows.getFirst(), turkish);
        FixtureSummary previous = prevRows.isEmpty()
                ? null : queryService.toSummary(prevRows.getFirst(), turkish);

        return new FixtureLookupResponse(resolved, next, previous);
    }

    // ============================================================
    // Takım seçimi — varyant-farkında (kadın / genç / rezerv)
    // ============================================================

    /**
     * ES popülerlik sırasına ek olarak VARYANT-farkında takım seçimi.
     *
     * <p>ES ilk sonucu bazen kadın/genç/rezerv takımını üste koyabiliyor
     * (ör. "galatasaray" sorgusunda "Galatasaray K" ilk gelir). Sorgu bu
     * varyantı <b>açıkça</b> istemiyorsa (metinde "kadın", "women", "u21" gibi
     * işaret yoksa) senior/erkek takımı tercih edilir; sorgu ile <b>tam ad
     * eşleşmesi</b> ek puan alır. Eşitlikte ES sırası (popülerlik) korunur.
     */
    private static SearchResponse.TeamHit pickBestTeam(
            List<SearchResponse.TeamHit> teams, String query) {
        String nq = normalize(query);
        boolean wantWomen = hasToken(nq, WOMEN_TOKENS);
        boolean wantYouth = hasToken(nq, YOUTH_TOKENS);
        boolean wantReserve = hasToken(nq, RESERVE_TOKENS);

        SearchResponse.TeamHit best = teams.get(0);
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < teams.size(); i++) {
            SearchResponse.TeamHit t = teams.get(i);
            String n = normalize(t.name());
            String ntr = normalize(t.nameTr());

            int score = 0;
            // Tam ad eşleşmesi (EN veya TR) → güçlü tercih.
            if (nq.equals(n) || nq.equals(ntr)) {
                score += 100;
            }
            boolean women = hasToken(n, WOMEN_TOKENS) || hasToken(ntr, WOMEN_TOKENS);
            boolean youth = hasToken(n, YOUTH_TOKENS) || hasToken(ntr, YOUTH_TOKENS);
            boolean reserve = hasToken(n, RESERVE_TOKENS) || hasToken(ntr, RESERVE_TOKENS);
            // Sorgu istemiyorsa varyantları geri it.
            if (women && !wantWomen) score -= 60;
            if (youth && !wantYouth) score -= 50;
            if (reserve && !wantReserve) score -= 30;
            // Sorgu açıkça istiyorsa varyantı öne al.
            if (wantWomen && women) score += 60;
            if (wantYouth && youth) score += 50;
            // ES sırası (popülerlik) tiebreak — erken index bir tık üstün.
            score -= i;

            if (score > bestScore) {
                bestScore = score;
                best = t;
            }
        }
        return best;
    }

    /** Kadın takımı işaretleri (normalize edilmiş TOKEN olarak aranır). */
    private static final Set<String> WOMEN_TOKENS = Set.of(
            "k", "w", "women", "womens", "kadin", "feminin", "femenino",
            "femminile", "frauen", "dames");

    /** Genç/altyapı takımı işaretleri. */
    private static final Set<String> YOUTH_TOKENS = Set.of(
            "u16", "u17", "u18", "u19", "u20", "u21", "u23", "youth",
            "genclik", "academy", "akademi", "junior", "jr");

    /** Rezerv/ikinci takım işaretleri ("b" tek harfi RİSKLİ — dahil edilmedi). */
    private static final Set<String> RESERVE_TOKENS = Set.of(
            "ii", "reserve", "reserves");

    /** Normalize metinde verilen token kümesinden biri var mı? */
    private static boolean hasToken(String norm, Set<String> tokens) {
        if (norm.isEmpty()) return false;
        for (String tok : norm.split(" ")) {
            if (tokens.contains(tok)) return true;
        }
        return false;
    }

    /** Küçük harf + Türkçe/aksan → ascii + tek boşluk (token karşılaştırma için). */
    private static String normalize(String s) {
        if (s == null || s.isEmpty()) return "";
        String x = s.toLowerCase(Locale.ROOT)
                .replace('ı', 'i')
                .replace('ş', 's')
                .replace('ğ', 'g')
                .replace('ç', 'c')
                .replace('ö', 'o')
                .replace('ü', 'u')
                .replace('â', 'a')
                .replace('î', 'i')
                .replace('û', 'u');
        // Kalan aksanlı harfleri (é, ñ, ø vb.) ascii'ye indir.
        x = Normalizer.normalize(x, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return x.replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
