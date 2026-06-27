package com.scorestv.rankings.sync;

import com.scorestv.football.FootballCacheNames;
import com.scorestv.rankings.domain.UefaCountryRanking;
import com.scorestv.rankings.domain.UefaCountryRankingRepository;
import com.scorestv.rankings.notify.RankingChange;
import com.scorestv.rankings.notify.RankingChangePublisher;
import com.scorestv.rankings.sync.dto.UefaCoefficientApiDto;
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
 * UEFA Milli Takim Katsayisi senkronu.
 *
 * <p>Paginated — 50/page, ~55 ulke → 2 sayfa. Yapi UefaClubRanking ile
 * paraleldir; sadece coefficientType ve hedef tablo farkli.
 */
@Service
public class UefaCountryRankingSyncService {

    private static final Logger log = LoggerFactory.getLogger(UefaCountryRankingSyncService.class);

    private static final int MAX_PAGES = 5;

    private final RankingsHttpClient httpClient;
    private final UefaCountryRankingRepository repository;
    private final RankingChangePublisher changePublisher;

    public UefaCountryRankingSyncService(RankingsHttpClient httpClient,
                                          UefaCountryRankingRepository repository,
                                          RankingChangePublisher changePublisher) {
        this.httpClient = httpClient;
        this.repository = repository;
        this.changePublisher = changePublisher;
    }

    @CacheEvict(value = FootballCacheNames.RANKINGS, allEntries = true)
    @Transactional
    public int sync(Integer targetSeasonYear) {
        if (targetSeasonYear == null) {
            log.warn("UEFA country ranking: targetSeasonYear null, sync atlandi");
            return 0;
        }
        // Degisim tespiti: delete'ten ONCE eski siralari snapshot'la (uefaId→rank).
        Map<String, Integer> oldRanks = new HashMap<>();
        for (UefaCountryRanking old :
                repository.findByTargetSeasonYearOrderByRankAsc(targetSeasonYear)) {
            if (old.getCountryUefaId() != null && old.getRank() != null) {
                oldRanks.put(old.getCountryUefaId(), old.getRank());
            }
        }

        repository.deleteByTargetSeasonYear(targetSeasonYear);
        Instant now = Instant.now();
        int totalWritten = 0;
        List<RankingChange> changes = new ArrayList<>();

        for (int page = 1; page <= MAX_PAGES; page++) {
            UefaCoefficientApiDto response = httpClient.fetchUefaCoefficient(
                    "MEN_ASSOCIATION", page, targetSeasonYear);
            if (response == null || response.data() == null
                    || response.data().members() == null
                    || response.data().members().isEmpty()) {
                break;
            }
            int written = writeMembers(response.data().members(), targetSeasonYear,
                    now, oldRanks, changes);
            totalWritten += written;
            if (response.data().members().size() < 50) break;
        }
        log.info("UEFA country ranking sync: {} ulke yazildi (season={}), {} sira degisimi",
                totalWritten, targetSeasonYear, changes.size());
        changePublisher.publishAfterCommit(changes);
        return totalWritten;
    }

    private int writeMembers(java.util.List<UefaCoefficientApiDto.Member> members,
                             Integer targetSeasonYear, Instant now,
                             Map<String, Integer> oldRanks,
                             List<RankingChange> changes) {
        int written = 0;
        for (UefaCoefficientApiDto.Member m : members) {
            if (m.member() == null || m.overallRanking() == null) continue;
            UefaCoefficientApiDto.MemberInfo info = m.member();
            UefaCoefficientApiDto.OverallRanking r = m.overallRanking();
            if (info.id() == null || r.position() == null
                    || r.totalPoints() == null) {
                continue;
            }
            // country_name NOT NULL: iki isim alani da bossa uyeyi atla (yoksa 23502).
            String countryName = info.displayName() != null
                    ? info.displayName() : info.countryName();
            if (countryName == null || countryName.isBlank()) {
                continue;
            }

            UefaCountryRanking entity = new UefaCountryRanking();
            entity.setCountryUefaId(info.id());
            entity.setCountryName(countryName);
            entity.setCountryCode(info.countryCode() != null ? info.countryCode() : "");
            entity.setLogoUrl(info.logoUrl());
            entity.setBigLogoUrl(info.bigLogoUrl());
            entity.setMediumLogoUrl(info.mediumLogoUrl());
            entity.setAssociationId(info.associationId());
            entity.setRank(r.position());
            entity.setTotalPoints(r.totalPoints());
            entity.setTrend(r.trend());
            entity.setNumberOfMatches(r.numberOfMatches());
            entity.setNumberOfTeams(r.numberOfTeams());
            entity.setTargetSeasonYear(targetSeasonYear);
            entity.setSeasonRankingsJson(m.seasonRankings());
            entity.setLastSyncedAt(now);
            repository.save(entity);
            written++;

            Integer oldRank = oldRanks.get(entity.getCountryUefaId());
            if (oldRank != null && !oldRank.equals(entity.getRank())
                    && entity.getCountryCode() != null
                    && !entity.getCountryCode().isBlank()) {
                changes.add(RankingChange.uefaCountry(entity.getCountryName(),
                        entity.getCountryCode(), oldRank, entity.getRank()));
            }
        }
        return written;
    }
}
