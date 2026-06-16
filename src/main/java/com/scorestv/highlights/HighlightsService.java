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

    @Transactional(readOnly = true)
    public List<HighlightView> forFixture(Long fixtureId) {
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

        List<HighlightView> views = new ArrayList<>();
        for (HighlightlyHighlightDto d : client.fetchHighlights(date, home, away)) {
            if (d.url() == null || d.url().isBlank()) continue;

            boolean embeddable = false;
            List<String> blocked = List.of();
            // Yalnız embedUrl'i olanlar için geo-restriction sorgula (ücretli plan).
            if (d.embedUrl() != null && !d.embedUrl().isBlank() && d.id() != null) {
                HighlightlyGeoRestrictionDto geo = client.fetchGeoRestriction(d.id());
                if (geo != null) {
                    embeddable = Boolean.TRUE.equals(geo.embeddable());
                    blocked = geo.blockedCountries() != null
                            ? geo.blockedCountries() : List.of();
                } else {
                    // geo çağrısı yapılamadı — embedUrl var, iyimser gömülebilir
                    // say; oynatıcı + 'tarayıcıda aç' yedeği geo'yu yine halleder.
                    embeddable = true;
                }
            }
            views.add(HighlightView.of(d, embeddable, blocked));
        }

        return putAndReturn(fixtureId, views);
    }

    private List<HighlightView> putAndReturn(Long fixtureId, List<HighlightView> views) {
        long ttlMin = views.isEmpty()
                ? props.emptyCacheTtlMinutes()
                : props.cacheTtlMinutes();
        cache.put(fixtureId, new CacheEntry(
                views, Instant.now().plus(Duration.ofMinutes(ttlMin))));
        return views;
    }

    private record CacheEntry(List<HighlightView> data, Instant expiresAt) {}
}
