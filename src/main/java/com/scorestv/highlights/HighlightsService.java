package com.scorestv.highlights;

import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.highlights.dto.HighlightView;
import com.scorestv.highlights.dto.HighlightlyGeoRestrictionDto;
import com.scorestv.highlights.dto.HighlightlyHighlightDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maç highlight/özet servisi. Fixture'ın takım adları + tarihini kullanıp
 * Highlightly'den highlight'ları çeker. Yalnızca BİTEN maçlar için sonuç döner
 * (highlight'lar maç sonrası gelir). Free plan kotasını korumak için fixture
 * bazında TTL cache uygulanır.
 *
 * <p>Geo: Highlightly bir maç için birden çok kaynaktan (youtube, streamin...)
 * highlight döner; bazıları belli ülkelerde coğrafi engelli olur. Ham liste +
 * geo verisi (allowed/blocked) ülkeden bağımsız cache'lenir; isteğe göre
 * kullanıcının ülkesinde OYNAYABİLECEĞİ highlight'lar {@code embeddable=true}
 * işaretlenip öne sıralanır. Böylece istemci "ülkenizde kullanılamıyor" iframe'i
 * yerine oynayabilir kaynağı gömer, engellileri yedeğe (tarayıcıda aç) düşürür.
 */
@Service
public class HighlightsService {

    /** Biten maç statüleri — yalnızca bunlarda highlight aranır. */
    private static final Set<String> FINISHED = Set.of("FT", "AET", "PEN");

    private final FixtureRepository fixtureRepository;
    private final HighlightlyClient client;
    private final HighlightlyProperties props;

    private final ConcurrentHashMap<Long, CacheEntry> cache = new ConcurrentHashMap<>();

    public HighlightsService(FixtureRepository fixtureRepository,
                             HighlightlyClient client,
                             HighlightlyProperties props) {
        this.fixtureRepository = fixtureRepository;
        this.client = client;
        this.props = props;
    }

    /**
     * Bir fixture'ın highlight'larını, isteği yapan kullanıcının ülkesine göre
     * gömülebilirlik hesaplayarak döner. {@code country} ISO-3166 alfa-2
     * (ör. "TR"); null/boş olabilir.
     */
    @Transactional(readOnly = true)
    public List<HighlightView> forFixture(Long fixtureId, String country) {
        List<GeoHighlight> base = baseList(fixtureId);
        if (base.isEmpty()) return List.of();

        String cc = (country == null || country.isBlank())
                ? null : country.trim().toUpperCase();

        List<HighlightView> out = new ArrayList<>(base.size());
        for (GeoHighlight g : base) {
            boolean embeddable = g.baseEmbeddable() && canPlay(g.allowed(), g.blocked(), cc);
            out.add(HighlightView.of(g.dto(), embeddable));
        }
        // Oynayabilir (embeddable) olanları öne al — sıralama kararlı.
        out.sort((a, b) -> Boolean.compare(b.embeddable(), a.embeddable()));
        return out;
    }

    /**
     * Kullanıcının ülkesinde bu highlight oynar mı?
     * <ul>
     *   <li>Ülke biliniyorsa: engelli listesinde değil VE (izinli liste boş ya da
     *       izinli listede) ise oynar.</li>
     *   <li>Ülke bilinmiyorsa: yalnız kısıtsız (allowed/blocked boş — her yerde
     *       açık) olanları oynar say. Böylece "ülkenizde yok" iframe'i çıkmaz.</li>
     * </ul>
     */
    private static boolean canPlay(List<String> allowed, List<String> blocked, String cc) {
        boolean hasAllowed = allowed != null && !allowed.isEmpty();
        boolean hasBlocked = blocked != null && !blocked.isEmpty();
        if (cc == null) {
            return !hasAllowed && !hasBlocked;
        }
        if (hasBlocked && contains(blocked, cc)) return false;
        if (hasAllowed && !contains(allowed, cc)) return false;
        return true;
    }

    private static boolean contains(List<String> list, String cc) {
        for (String s : list) {
            if (s != null && cc.equalsIgnoreCase(s.trim())) return true;
        }
        return false;
    }

    /**
     * Highlight YouTube kaynaklı mı? YouTube içerikleri (özellikle FIFA/resmi)
     * sık sık üçüncü taraf embed'i kapatır; bunları inline gömmek yerine harici
     * (YouTube'da izle) açarız. Kaynak adı + embedUrl/url alan adından tespit.
     */
    private static boolean isYouTube(HighlightlyHighlightDto d) {
        String src = d.source() == null ? "" : d.source().toLowerCase();
        if (src.contains("youtube") || src.contains("youtu.be")) return true;
        String e = d.embedUrl() == null ? "" : d.embedUrl().toLowerCase();
        String u = d.url() == null ? "" : d.url().toLowerCase();
        return e.contains("youtube.com") || e.contains("youtu.be")
                || e.contains("youtube-nocookie.com")
                || u.contains("youtube.com") || u.contains("youtu.be");
    }

    /**
     * Ülkeden bağımsız ham liste (her highlight için base embeddable + geo
     * allowed/blocked). Cache'li — geo çağrıları yalnız cache dolarken yapılır.
     */
    private List<GeoHighlight> baseList(Long fixtureId) {
        if (!props.enabled()) return List.of();

        CacheEntry cached = cache.get(fixtureId);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.data();
        }

        Fixture f = fixtureRepository.findById(fixtureId).orElse(null);
        if (f == null) return List.of();

        // Sadece biten maçlar.
        String status = f.getStatusShort();
        if (status == null || !FINISHED.contains(status)) {
            return putAndReturn(fixtureId, List.of());
        }

        String home = f.getHomeTeam() != null ? f.getHomeTeam().getName() : null;
        String away = f.getAwayTeam() != null ? f.getAwayTeam().getName() : null;
        if (home == null || away == null || f.getKickoffAt() == null) {
            return putAndReturn(fixtureId, List.of());
        }

        String date = LocalDate
                .ofInstant(f.getKickoffAt(), ZoneId.of(props.timezone()))
                .toString(); // YYYY-MM-DD

        List<GeoHighlight> items = new ArrayList<>();
        for (HighlightlyHighlightDto d : client.fetchHighlights(date, home, away)) {
            if (d.url() == null || d.url().isBlank()) continue;

            boolean baseEmbeddable = false;
            List<String> allowed = List.of();
            List<String> blocked = List.of();
            // YouTube içerikleri (özellikle FIFA/resmi yayıncılar) çoğu kez
            // üçüncü taraf site/app'te gömmeyi kapatır ("iş ortağı engelledi") —
            // bu coğrafi değil, sahibin embed yasağıdır ve aşılamaz. Bunları hiç
            // gömme; küçük-resim + "YouTube'da izle" harici yedeğine düşsünler.
            // Yalnız gömmeye izin veren kaynakları (streamin, dazn vb.) geo'ya
            // bakıp göm — geo çağrısından da tasarruf olur.
            if (d.embedUrl() != null && !d.embedUrl().isBlank() && d.id() != null
                    && !isYouTube(d)) {
                HighlightlyGeoRestrictionDto geo = client.fetchGeoRestriction(d.id());
                if (geo != null) {
                    baseEmbeddable = Boolean.TRUE.equals(geo.embeddable());
                    allowed = geo.allowedCountries() != null
                            ? geo.allowedCountries() : List.of();
                    blocked = geo.blockedCountries() != null
                            ? geo.blockedCountries() : List.of();
                } else {
                    // geo çağrısı yapılamadı — embedUrl var, gömülebilir say ama
                    // kısıtsız olarak (allowed/blocked boş). Ülke bilinmezse yine
                    // güvenli; biliniyorsa kısıt yok kabul edilir.
                    baseEmbeddable = true;
                }
            }
            items.add(new GeoHighlight(d, baseEmbeddable, allowed, blocked));
        }

        return putAndReturn(fixtureId, items);
    }

    private List<GeoHighlight> putAndReturn(Long fixtureId, List<GeoHighlight> items) {
        long ttlMin = items.isEmpty()
                ? props.emptyCacheTtlMinutes()
                : props.cacheTtlMinutes();
        cache.put(fixtureId, new CacheEntry(
                items, Instant.now().plus(Duration.ofMinutes(ttlMin))));
        return items;
    }

    /** Ülkeden bağımsız ham highlight + geo verisi (cache içi). */
    private record GeoHighlight(HighlightlyHighlightDto dto, boolean baseEmbeddable,
                                List<String> allowed, List<String> blocked) {}

    private record CacheEntry(List<GeoHighlight> data, Instant expiresAt) {}
}
