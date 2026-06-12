package com.scorestv.basketball;

import com.scorestv.basketball.domain.BasketballLeague;
import com.scorestv.basketball.domain.BasketballLeagueRepository;
import com.scorestv.basketball.domain.BasketballSeason;
import com.scorestv.basketball.domain.BasketballSeasonRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * API-Basketball {@code /standings} senkronu.
 *
 * <p>Akis: client.fetchStandings → upserter.replaceAll → season meta update.
 * Hata yonetimi: API hatasi sessizce sayilir (mevcut DB veri korunur).
 */
@Service
public class BasketballStandingsSyncService {

    private static final Logger log = LoggerFactory.getLogger(BasketballStandingsSyncService.class);

    private final BasketballApiClient client;
    private final BasketballLeagueRepository leagueRepo;
    private final BasketballSeasonRepository seasonRepo;
    private final BasketballStandingsUpserter upserter;

    public BasketballStandingsSyncService(BasketballApiClient client,
                                          BasketballLeagueRepository leagueRepo,
                                          BasketballSeasonRepository seasonRepo,
                                          BasketballStandingsUpserter upserter) {
        this.client = client;
        this.leagueRepo = leagueRepo;
        this.seasonRepo = seasonRepo;
        this.upserter = upserter;
    }

    /**
     * Bir lig + sezon icin standings'i ceker ve DB'ye replace eder.
     *
     * @return yazilan satir sayisi (0 = bos cevap veya hata)
     */
    @Transactional
    public int sync(long leagueId, String season) {
        BasketballLeague league = leagueRepo.findById(leagueId).orElse(null);
        if (league == null) {
            log.warn("Standings sync atlandi — lig DB'de yok: id={}", leagueId);
            return 0;
        }
        if (season == null || season.isBlank()) {
            log.warn("Standings sync atlandi — sezon bos: league={}", leagueId);
            return 0;
        }

        List<List<BkStandingDto>> groups;
        try {
            groups = client.fetchStandings(leagueId, season);
        } catch (Exception e) {
            log.warn("Standings API hatasi league={} season={}: {}",
                    leagueId, season, e.toString());
            return 0;
        }

        int written = upserter.replaceAll(league, season, groups);
        log.info("Basketbol standings sync: league={} season={} -> {} satir",
                leagueId, season, written);

        // Season meta cache guncelle (Daily job icin son sync zamani).
        BasketballSeason meta = seasonRepo.findByLeagueIdAndSeason(leagueId, season)
                .orElseGet(() -> {
                    BasketballSeason ns = new BasketballSeason();
                    ns.setLeague(league);
                    ns.setSeason(season);
                    ns.setCoverageStandings(true);
                    return ns;
                });
        meta.setStandingsLastSyncedAt(Instant.now());
        seasonRepo.save(meta);

        return written;
    }
}
