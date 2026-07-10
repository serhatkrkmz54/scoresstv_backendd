package com.scorestv.football.insight;

import com.scorestv.football.domain.FixtureRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bir lig+sezonun takım reytinglerini (RatingEngine ile) hesaplar ve bellekte
 * TTL ile cache'ler. Ağır hesaplama (tüm sezon maçları üzerinde walk-forward)
 * lig başına ~yarım saatte bir yapılır; okuma anlıktır. Ekstra tablo/job yok.
 */
@Service
public class RatingService {

    private final FixtureRepository fixtureRepository;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    @Value("${scorestv.insight.rating-ttl-minutes:30}")
    private long ttlMinutes;

    private record Cached(RatingEngine.Ratings ratings, Instant at) {}

    public RatingService(FixtureRepository fixtureRepository) {
        this.fixtureRepository = fixtureRepository;
    }

    /** Lig+sezon reytingleri — cache'ten ya da (bayatsa) yeniden hesaplanır. */
    @Transactional(readOnly = true)
    public RatingEngine.Ratings ratingsFor(Long leagueId, Integer season) {
        String key = leagueId + ":" + season;
        Cached c = cache.get(key);
        if (c != null && Duration.between(c.at(), Instant.now()).toMinutes() < ttlMinutes) {
            return c.ratings();
        }
        RatingEngine.Ratings fresh = compute(leagueId, season);
        cache.put(key, new Cached(fresh, Instant.now()));
        return fresh;
    }

    private RatingEngine.Ratings compute(Long leagueId, Integer season) {
        List<Object[]> raw = fixtureRepository.findRatingRows(leagueId, season);
        List<RatingEngine.MatchRow> rows = new ArrayList<>(raw.size());
        for (Object[] r : raw) {
            if (r[0] == null || r[1] == null || r[2] == null || r[3] == null) {
                continue;
            }
            long hid = ((Number) r[0]).longValue();
            long aid = ((Number) r[1]).longValue();
            int hg = ((Number) r[2]).intValue();
            int ag = ((Number) r[3]).intValue();
            Double hxg = parseXg(r.length > 4 ? r[4] : null);
            Double axg = parseXg(r.length > 5 ? r[5] : null);
            rows.add(new RatingEngine.MatchRow(hid, aid, hg, ag, hxg, axg));
        }
        return RatingEngine.compute(rows);
    }

    private static Double parseXg(Object v) {
        if (v == null) {
            return null;
        }
        String s = v.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(s.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
