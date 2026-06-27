package com.scorestv.rankings.sync;

import com.scorestv.football.FootballCacheNames;
import com.scorestv.rankings.domain.FifaRanking;
import com.scorestv.rankings.domain.FifaRankingRepository;
import com.scorestv.rankings.notify.RankingChange;
import com.scorestv.rankings.notify.RankingChangePublisher;
import com.scorestv.rankings.sync.dto.FifaRankingApiDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final RankingChangePublisher changePublisher;

    public FifaRankingSyncService(RankingsHttpClient httpClient,
                                  FifaRankingRepository repository,
                                  RankingChangePublisher changePublisher) {
        this.httpClient = httpClient;
        this.repository = repository;
        this.changePublisher = changePublisher;
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

        // Degisim tespiti: delete'ten ONCE eski siralari snapshot'la (teamId→rank).
        Map<String, Integer> oldRanks = new HashMap<>();
        for (FifaRanking old : repository.findAll()) {
            if (old.getTeamId() != null && old.getRank() != null) {
                oldRanks.put(old.getTeamId(), old.getRank());
            }
        }

        repository.deleteAllRows();
        Instant now = Instant.now();
        int written = 0;
        List<RankingChange> changes = new ArrayList<>();
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

            Integer oldRank = oldRanks.get(entity.getTeamId());
            if (oldRank != null && !oldRank.equals(entity.getRank())
                    && entity.getCountryCode() != null
                    && !entity.getCountryCode().isBlank()) {
                changes.add(RankingChange.fifa(entity.getTeamName(),
                        entity.getCountryCode(), oldRank, entity.getRank()));
            }
        }
        log.info("FIFA ranking sync: {} satir yazildi, {} sira degisimi",
                written, changes.size());
        changePublisher.publishAfterCommit(changes);
        return written;
    }

    /** TeamName listesinden ilk EN aciklamayi (ya da herhangi birini) cikar. */
    private static String extractTeamName(List<FifaRankingApiDto.TeamName> names) {
        if (names == null || names.isEmpty()) return "";
        // Genelde sadece tek dil dondurur — ilki yeterli.
        return names.getFirst().Description() != null ? names.getFirst().Description() : "";
    }
}
