package com.scorestv.volleyball;

import com.scorestv.volleyball.domain.VolleyballLeague;
import com.scorestv.volleyball.domain.VolleyballLeagueRepository;
import com.scorestv.volleyball.domain.VolleyballSeason;
import com.scorestv.volleyball.domain.VolleyballSeasonRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * API-Volleyball {@code /standings} senkronu.
 *
 * <p>Akis: stages -> her stage icin standings -> upserter.replaceAll -> season
 * meta update. Hata yonetimi: API hatasi sessizce sayilir (mevcut DB korunur).
 */
@Service
public class VolleyballStandingsSyncService {

    private static final Logger log = LoggerFactory.getLogger(VolleyballStandingsSyncService.class);

    private final VolleyballApiClient client;
    private final VolleyballLeagueRepository leagueRepo;
    private final VolleyballSeasonRepository seasonRepo;
    private final VolleyballStandingsUpserter upserter;

    public VolleyballStandingsSyncService(VolleyballApiClient client,
                                          VolleyballLeagueRepository leagueRepo,
                                          VolleyballSeasonRepository seasonRepo,
                                          VolleyballStandingsUpserter upserter) {
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
        VolleyballLeague league = leagueRepo.findById(leagueId).orElse(null);
        if (league == null) {
            log.warn("Voleybol standings sync atlandi — lig DB'de yok: id={}", leagueId);
            return 0;
        }
        if (season == null || season.isBlank()) {
            log.warn("Voleybol standings sync atlandi — sezon bos: league={}", leagueId);
            return 0;
        }

        // 1) Mevcut stage'leri ogren (stage yoksa tek default cagri).
        List<String> stages;
        try {
            stages = client.fetchStandingsStages(leagueId, season);
        } catch (Exception e) {
            stages = List.of();
        }

        // 2) Her stage icin standings cek + birlestir.
        List<List<VbStandingDto>> groups = new ArrayList<>();
        try {
            if (stages.isEmpty()) {
                groups.addAll(client.fetchStandings(leagueId, season));
            } else {
                for (String stage : stages) {
                    try {
                        var stageGroups = client.fetchStandings(leagueId, season, stage, null);
                        if (!stageGroups.isEmpty()) groups.addAll(stageGroups);
                    } catch (Exception e) {
                        log.debug("Voleybol standings stage hata league={} season={} stage={}: {}",
                                leagueId, season, stage, e.toString());
                    }
                }
            }
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            log.warn("Voleybol standings API hatasi league={} season={} -> {}: {}",
                    leagueId, season, root.getClass().getSimpleName(), root.getMessage());
            return 0;
        }

        int written = upserter.replaceAll(league, season, groups);
        log.info("Voleybol standings sync: league={} season={} stages={} -> {} satir",
                leagueId, season, stages.size(), written);

        VolleyballSeason meta = seasonRepo.findByLeagueIdAndSeason(leagueId, season)
                .orElseGet(() -> {
                    VolleyballSeason ns = new VolleyballSeason();
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
