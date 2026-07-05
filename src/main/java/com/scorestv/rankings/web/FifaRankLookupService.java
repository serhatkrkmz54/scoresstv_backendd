package com.scorestv.rankings.web;

import com.scorestv.football.FootballCacheNames;
import com.scorestv.rankings.domain.FifaRanking;
import com.scorestv.rankings.domain.FifaRankingRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Milli takım → FIFA sırası araması (maç detayı + takım sayfası için).
 *
 * <p>{@link #index()} ~210 satırlık FIFA tablosunu iki map'e (kod + isim)
 * çevirip RANKINGS cache'inde (24sa) tutar; günlük FIFA sync sonrası TTL ile
 * tazelenir. {@link FifaRankIndex#rankFor} ile milli takım eşlenir.
 */
@Service
public class FifaRankLookupService {

    private final FifaRankingRepository fifaRepository;

    public FifaRankLookupService(FifaRankingRepository fifaRepository) {
        this.fifaRepository = fifaRepository;
    }

    @Cacheable(value = FootballCacheNames.RANKINGS, key = "'fifa-rank-index'")
    @Transactional(readOnly = true)
    public FifaRankIndex index() {
        Map<String, Integer> byCode = new HashMap<>();
        Map<String, Integer> byName = new HashMap<>();
        for (FifaRanking r : fifaRepository.findAll()) {
            if (r.getRank() == null) {
                continue;
            }
            if (r.getCountryCode() != null && !r.getCountryCode().isBlank()) {
                byCode.putIfAbsent(
                        r.getCountryCode().trim().toUpperCase(Locale.ROOT), r.getRank());
            }
            if (r.getTeamName() != null && !r.getTeamName().isBlank()) {
                byName.putIfAbsent(
                        r.getTeamName().trim().toLowerCase(Locale.ROOT), r.getRank());
            }
        }
        return new FifaRankIndex(Map.copyOf(byCode), Map.copyOf(byName));
    }
}
