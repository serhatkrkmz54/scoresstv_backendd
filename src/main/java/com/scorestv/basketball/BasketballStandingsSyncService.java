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
import java.util.ArrayList;
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

        // 1) Once mevcut stage'leri ogren — NBA gibi liglerde "Regular Season",
        //    "Playoffs" gibi cok stage olabilir; her birinin standings'i ayri.
        //    Stage listesi bossa ya da hata varsa, tek default cagri yapilir.
        List<String> stages;
        try {
            stages = client.fetchStandingsStages(leagueId, season);
        } catch (Exception e) {
            stages = List.of();
        }

        // 2) Her stage icin standings cek + birlestir. Stage'lerden bagimsiz
        //    fallback cagri sirasinda stages.isEmpty() durumunda tek cagri.
        List<List<BkStandingDto>> groups = new ArrayList<>();
        try {
            if (stages.isEmpty()) {
                // Stage yoksa default standings cek (eski davranis)
                groups.addAll(client.fetchStandings(leagueId, season));
            } else {
                // Her stage icin ayri cagri — sonuclari birlestir
                for (String stage : stages) {
                    try {
                        var stageGroups = client.fetchStandings(
                                leagueId, season, stage, null);
                        if (!stageGroups.isEmpty()) groups.addAll(stageGroups);
                    } catch (Exception e) {
                        log.debug("Standings stage cagri hata league={} season={} stage={}: {}",
                                leagueId, season, stage, e.toString());
                    }
                }
            }
        } catch (Exception e) {
            // Cause chain dahil log — Jackson MismatchedInputException gibi
            // hatalarda gercek alan adi/satir bilgisi cause icinde.
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            log.warn("Standings API hatasi league={} season={} -> {}: {}",
                    leagueId, season, root.getClass().getSimpleName(),
                    root.getMessage());
            return 0;
        }

        int written = upserter.replaceAll(league, season, groups);
        log.info("Basketbol standings sync: league={} season={} stages={} -> {} satir",
                leagueId, season, stages.size(), written);

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
