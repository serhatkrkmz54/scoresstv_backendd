package com.scorestv.volleyball;

import com.scorestv.storage.MinioStorageService;
import com.scorestv.volleyball.domain.VolleyballLeague;
import com.scorestv.volleyball.domain.VolleyballLeagueRepository;
import com.scorestv.volleyball.domain.VolleyballStanding;
import com.scorestv.volleyball.domain.VolleyballStandingRepository;
import com.scorestv.volleyball.domain.VolleyballTeam;
import com.scorestv.volleyball.domain.VolleyballTeamLeagueSeasonRepository;
import com.scorestv.volleyball.domain.VolleyballTeamRepository;
import com.scorestv.volleyball.web.dto.VolleyballLeagueTeamView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Bir voleybol liginin takim listesini hafif DTO olarak doner.
 *
 * <p>2-katmanli kaynak hiyerarsisi: junction tablo (kanonik) → games fallback.
 * Junction bosken arka planda /teams sync tetiklenir.
 */
@Service
public class VolleyballLeagueTeamsService {

    private static final Logger log = LoggerFactory.getLogger(VolleyballLeagueTeamsService.class);

    private final VolleyballLeagueRepository leagueRepository;
    private final VolleyballTeamRepository teamRepository;
    private final VolleyballTeamLeagueSeasonRepository junctionRepository;
    private final VolleyballTeamSyncService teamSyncService;
    private final VolleyballReferenceService referenceService;
    private final VolleyballStandingRepository standingRepository;
    private final VolleyballStandingsSyncService standingsSyncService;
    private final MinioStorageService storage;

    public VolleyballLeagueTeamsService(VolleyballLeagueRepository leagueRepository,
                                        VolleyballTeamRepository teamRepository,
                                        VolleyballTeamLeagueSeasonRepository junctionRepository,
                                        VolleyballTeamSyncService teamSyncService,
                                        VolleyballReferenceService referenceService,
                                        VolleyballStandingRepository standingRepository,
                                        VolleyballStandingsSyncService standingsSyncService,
                                        MinioStorageService storage) {
        this.leagueRepository = leagueRepository;
        this.teamRepository = teamRepository;
        this.junctionRepository = junctionRepository;
        this.teamSyncService = teamSyncService;
        this.referenceService = referenceService;
        this.standingRepository = standingRepository;
        this.standingsSyncService = standingsSyncService;
        this.storage = storage;
    }

    @Transactional
    public List<VolleyballLeagueTeamView> getTeams(Long leagueId, String season, boolean turkish) {
        // 1) Verilen ya da DB'deki current_season ile dene.
        String resolvedSeason = season;
        if (resolvedSeason == null || resolvedSeason.isBlank()) {
            VolleyballLeague league = leagueRepository.findById(leagueId).orElse(null);
            resolvedSeason = league != null ? league.getCurrentSeason() : null;
        }
        List<VolleyballLeagueTeamView> teams = collectTeams(leagueId, resolvedSeason, turkish);
        if (!teams.isEmpty()) {
            return teams;
        }

        // 2) Bos → ligi API'den tazele (gercek current_season'i al) ve sezon
        //    farkliysa tekrar dene. Bizdeki current_season NULL ya da BAYAT
        //    olabilir (orn. Efeler 172: API'de current=2025 ama bizde eski/yok).
        //    Bu, manuel mudahale olmadan onboarding'i kendi iyilestirir.
        VolleyballLeague refreshed = referenceService.syncOneLeague(leagueId);
        String freshSeason = refreshed != null ? refreshed.getCurrentSeason() : null;
        if (freshSeason != null && !freshSeason.isBlank()
                && !freshSeason.equals(resolvedSeason)) {
            teams = collectTeams(leagueId, freshSeason, turkish);
        }
        return teams;
    }

    /**
     * Bir lig + sezon icin katmanli takim arama:
     * junction → standings (voleybolda EN guvenilir) → /teams sync → games.
     */
    private List<VolleyballLeagueTeamView> collectTeams(Long leagueId, String resolvedSeason,
                                                        boolean turkish) {
        if (resolvedSeason == null || resolvedSeason.isBlank()) {
            return List.of();
        }

        // 1) Junction katmani — kanonik kaynak (zaten doluysa hizli yol).
        List<Long> ids = junctionRepository.findTeamIdsByLeagueAndSeason(leagueId, resolvedSeason);
        if (!ids.isEmpty()) {
            return mapByIds(ids, turkish);
        }

        // 2) Standings'ten takimlar — /teams?league&season cogu ligde bos doner;
        //    standings dolu ve her satirda takim var (sync takimlari upsert eder).
        List<VolleyballLeagueTeamView> fromStandings =
                teamsFromStandings(leagueId, resolvedSeason, turkish);
        if (!fromStandings.isEmpty()) {
            return fromStandings;
        }

        // 3) /teams junction sync — bazi liglerde calisir.
        try {
            int n = teamSyncService.syncIfMissing(leagueId, resolvedSeason);
            if (n > 0) {
                List<Long> freshIds = junctionRepository.findTeamIdsByLeagueAndSeason(
                        leagueId, resolvedSeason);
                if (!freshIds.isEmpty()) {
                    return mapByIds(freshIds, turkish);
                }
            }
        } catch (Exception e) {
            log.warn("Voleybol getTeams junction lazy sync hata league={} season={}: {}",
                    leagueId, resolvedSeason, e.toString());
        }

        // 4) Son care: games fallback.
        List<VolleyballTeam> teams = teamRepository.findTeamsInLeague(leagueId, resolvedSeason);
        return teams.stream().map(t -> toView(t, turkish)).toList();
    }

    private List<VolleyballLeagueTeamView> mapByIds(List<Long> ids, boolean turkish) {
        Map<Long, VolleyballTeam> byId = new HashMap<>();
        for (VolleyballTeam t : teamRepository.findAllById(ids)) {
            byId.put(t.getId(), t);
        }
        return ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(t -> toView(t, turkish))
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .toList();
    }

    /**
     * Takimlari standings'ten turetir — voleybolda en guvenilir kaynak. DB'de
     * standings yoksa once sync eder (takimlar upsert edilir), sonra benzersiz
     * takimlari (ada gore sirali) doner.
     */
    private List<VolleyballLeagueTeamView> teamsFromStandings(Long leagueId, String season,
                                                              boolean turkish) {
        List<VolleyballStanding> rows = standingRepository.findByLeagueAndSeason(leagueId, season);
        if (rows.isEmpty()) {
            try {
                standingsSyncService.sync(leagueId, season);
            } catch (Exception e) {
                log.warn("Voleybol getTeams standings sync hata league={} season={}: {}",
                        leagueId, season, e.toString());
            }
            rows = standingRepository.findByLeagueAndSeason(leagueId, season);
        }
        Map<Long, VolleyballLeagueTeamView> byId = new LinkedHashMap<>();
        for (VolleyballStanding s : rows) {
            VolleyballTeam t = s.getTeam();
            if (t != null && !byId.containsKey(t.getId())) {
                byId.put(t.getId(), toView(t, turkish));
            }
        }
        List<VolleyballLeagueTeamView> out = new ArrayList<>(byId.values());
        out.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return out;
    }

    private VolleyballLeagueTeamView toView(VolleyballTeam t, boolean turkish) {
        String name = (turkish && t.getNameTr() != null && !t.getNameTr().isBlank())
                ? t.getNameTr() : t.getName();
        String nameTr = (t.getNameTr() != null && !t.getNameTr().isBlank())
                ? t.getNameTr() : t.getName();
        String logo = t.getLogoKey() != null
                ? storage.publicUrl(t.getLogoKey())
                : t.getLogo();
        return new VolleyballLeagueTeamView(
                t.getId(), name, nameTr, shortCode(name), logo);
    }

    private static String shortCode(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        String first = parts[0];
        int take = Math.min(first.length(), 3);
        return first.substring(0, take).toUpperCase();
    }
}
