package com.scorestv.rankings.sync;

import com.scorestv.football.FootballCacheNames;
import com.scorestv.rankings.domain.UefaCountryRanking;
import com.scorestv.rankings.domain.UefaCountryRankingRepository;
import com.scorestv.rankings.sync.dto.UefaCoefficientApiDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

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

    public UefaCountryRankingSyncService(RankingsHttpClient httpClient,
                                          UefaCountryRankingRepository repository) {
        this.httpClient = httpClient;
        this.repository = repository;
    }

    @CacheEvict(value = FootballCacheNames.RANKINGS, allEntries = true)
    @Transactional
    public int sync(Integer targetSeasonYear) {
        if (targetSeasonYear == null) {
            log.warn("UEFA country ranking: targetSeasonYear null, sync atlandi");
            return 0;
        }
        repository.deleteByTargetSeasonYear(targetSeasonYear);
        Instant now = Instant.now();
        int totalWritten = 0;

        for (int page = 1; page <= MAX_PAGES; page++) {
            UefaCoefficientApiDto response = httpClient.fetchUefaCoefficient(
                    "MEN_ASSOCIATION", page, targetSeasonYear);
            if (response == null || response.data() == null
                    || response.data().members() == null
                    || response.data().members().isEmpty()) {
                break;
            }
            int written = writeMembers(response.data().members(), targetSeasonYear, now);
            totalWritten += written;
            if (response.data().members().size() < 50) break;
        }
        log.info("UEFA country ranking sync: {} ulke yazildi (season={})",
                totalWritten, targetSeasonYear);
        return totalWritten;
    }

    private int writeMembers(java.util.List<UefaCoefficientApiDto.Member> members,
                             Integer targetSeasonYear, Instant now) {
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
        }
        return written;
    }
}
