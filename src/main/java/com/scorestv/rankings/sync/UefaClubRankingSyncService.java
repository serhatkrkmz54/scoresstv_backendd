package com.scorestv.rankings.sync;

import com.scorestv.football.FootballCacheNames;
import com.scorestv.rankings.domain.UefaClubRanking;
import com.scorestv.rankings.domain.UefaClubRankingRepository;
import com.scorestv.rankings.sync.dto.UefaCoefficientApiDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * UEFA Kulup Katsayisi senkronu.
 *
 * <p>Paginated — 50 kayit/page, ~415 kulup → ~9 sayfa. {@code seasonYear}
 * her ranking icin hedef sezon (orn. 2026 = 2025/26 sezonu).
 *
 * <p>Strateji: REPLACE — hedef sezona ait tum satirlar silinir, yeniden yazilir.
 */
@Service
public class UefaClubRankingSyncService {

    private static final Logger log = LoggerFactory.getLogger(UefaClubRankingSyncService.class);

    private static final int MAX_PAGES = 30;  // Guvenlik tavani

    private final RankingsHttpClient httpClient;
    private final UefaClubRankingRepository repository;

    public UefaClubRankingSyncService(RankingsHttpClient httpClient,
                                       UefaClubRankingRepository repository) {
        this.httpClient = httpClient;
        this.repository = repository;
    }

    /**
     * Belirli hedef sezon icin tum sayfalari cek ve REPLACE et.
     *
     * @param targetSeasonYear orn. 2026 (2025/26 sezonu)
     * @return yazilan satir sayisi
     */
    @CacheEvict(value = FootballCacheNames.RANKINGS, allEntries = true)
    @Transactional
    public int sync(Integer targetSeasonYear) {
        if (targetSeasonYear == null) {
            log.warn("UEFA club ranking: targetSeasonYear null, sync atlandi");
            return 0;
        }
        repository.deleteByTargetSeasonYear(targetSeasonYear);
        Instant now = Instant.now();
        int totalWritten = 0;

        for (int page = 1; page <= MAX_PAGES; page++) {
            UefaCoefficientApiDto response = httpClient.fetchUefaCoefficient(
                    "MEN_CLUB", page, targetSeasonYear);
            if (response == null || response.data() == null
                    || response.data().members() == null
                    || response.data().members().isEmpty()) {
                break;
            }
            int written = writeMembers(response.data().members(), targetSeasonYear, now);
            totalWritten += written;
            // Sayfada 50'den az kayit varsa son sayfa
            if (response.data().members().size() < 50) break;
        }
        log.info("UEFA club ranking sync: {} kulup yazildi (season={})",
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
            // club_name NOT NULL: iki isim alani da bossa uyeyi atla (yoksa 23502).
            String clubName = info.displayName() != null
                    ? info.displayName() : info.internationalName();
            if (clubName == null || clubName.isBlank()) {
                continue;
            }

            UefaClubRanking entity = new UefaClubRanking();
            entity.setClubId(info.id());
            entity.setClubName(clubName);
            entity.setClubShortName(info.displayNameShort());
            entity.setClubOfficialName(info.displayOfficialName());
            entity.setTeamCode(info.teamCode() != null
                    ? info.teamCode() : info.displayTeamCode());
            entity.setLogoUrl(info.logoUrl());
            entity.setBigLogoUrl(info.bigLogoUrl());
            entity.setMediumLogoUrl(info.mediumLogoUrl());
            entity.setCountryCode(info.countryCode() != null ? info.countryCode() : "");
            entity.setCountryName(info.countryName());
            entity.setAssociationId(info.associationId());
            entity.setRank(r.position());
            entity.setTotalPoints(r.totalPoints());
            entity.setTrend(r.trend());
            entity.setNumberOfMatches(r.numberOfMatches());
            entity.setNumberOfTeams(r.numberOfTeams());
            entity.setTargetSeasonYear(targetSeasonYear);
            entity.setBaseSeasonYear(r.baseSeasonYear());
            entity.setSeasonRankingsJson(m.seasonRankings());
            entity.setLastSyncedAt(now);
            repository.save(entity);
            written++;
        }
        return written;
    }
}
