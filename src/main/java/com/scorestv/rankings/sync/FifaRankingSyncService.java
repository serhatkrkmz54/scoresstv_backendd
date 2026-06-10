package com.scorestv.rankings.sync;

import com.scorestv.football.FootballCacheNames;
import com.scorestv.rankings.domain.FifaRanking;
import com.scorestv.rankings.domain.FifaRankingRepository;
import com.scorestv.rankings.sync.dto.FifaRankingApiDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * FIFA Erkek Milli Takim Siralamasi senkronu.
 *
 * <p>Strateji: REPLACE — eski tum satirlar silinir, API'den gelenler yazilir.
 * Bu sayede ulke birakilirsa otomatik temizlenir. Tipik 211 satir.
 */
@Service
public class FifaRankingSyncService {

    private static final Logger log = LoggerFactory.getLogger(FifaRankingSyncService.class);

    private final RankingsHttpClient httpClient;
    private final FifaRankingRepository repository;

    public FifaRankingSyncService(RankingsHttpClient httpClient,
                                  FifaRankingRepository repository) {
        this.httpClient = httpClient;
        this.repository = repository;
    }

    /**
     * REPLACE sync. Sonunda tum rankings cache'ini evict eder — onceki bos/eski
     * sonuc cache'de takilmis olabilir, hemen tazelenir.
     */
    @CacheEvict(value = FootballCacheNames.RANKINGS, allEntries = true)
    @Transactional
    public int sync() {
        FifaRankingApiDto response = httpClient.fetchFifaRanking();
        if (response == null || response.Results() == null
                || response.Results().isEmpty()) {
            log.warn("FIFA ranking: API bos donduurdu, sync atlandi");
            return 0;
        }

        repository.deleteAllRows();
        Instant now = Instant.now();
        int written = 0;
        for (FifaRankingApiDto.Row row : response.Results()) {
            if (row.IdTeam() == null || row.Rank() == null
                    || row.TotalPoints() == null) {
                continue;
            }
            FifaRanking entity = new FifaRanking();
            entity.setTeamId(row.IdTeam());
            entity.setTeamName(extractTeamName(row.TeamName()));
            entity.setCountryCode(row.IdCountry() != null ? row.IdCountry() : "");
            entity.setConfederation(row.ConfederationName());
            entity.setConfederationId(row.IdConfederation());
            entity.setRank(row.Rank());
            entity.setPrevRank(row.PrevRank());
            entity.setMovement(row.RankingMovement());
            entity.setTotalPoints(row.TotalPoints());
            entity.setPrevPoints(row.PrevPoints());
            entity.setRatedMatches(row.RatedMatches());
            entity.setLastSyncedAt(now);
            repository.save(entity);
            written++;
        }
        log.info("FIFA ranking sync: {} satir yazildi", written);
        return written;
    }

    /** TeamName listesinden ilk EN aciklamayi (ya da herhangi birini) cikar. */
    private static String extractTeamName(List<FifaRankingApiDto.TeamName> names) {
        if (names == null || names.isEmpty()) return "";
        // Genelde sadece tek dil dondurur — ilki yeterli.
        return names.get(0).Description() != null ? names.get(0).Description() : "";
    }
}
