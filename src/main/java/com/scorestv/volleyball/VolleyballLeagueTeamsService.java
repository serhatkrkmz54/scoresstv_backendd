package com.scorestv.volleyball;

import com.scorestv.storage.MinioStorageService;
import com.scorestv.volleyball.domain.VolleyballLeague;
import com.scorestv.volleyball.domain.VolleyballLeagueRepository;
import com.scorestv.volleyball.domain.VolleyballTeam;
import com.scorestv.volleyball.domain.VolleyballTeamLeagueSeasonRepository;
import com.scorestv.volleyball.domain.VolleyballTeamRepository;
import com.scorestv.volleyball.web.dto.VolleyballLeagueTeamView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final MinioStorageService storage;

    public VolleyballLeagueTeamsService(VolleyballLeagueRepository leagueRepository,
                                        VolleyballTeamRepository teamRepository,
                                        VolleyballTeamLeagueSeasonRepository junctionRepository,
                                        VolleyballTeamSyncService teamSyncService,
                                        MinioStorageService storage) {
        this.leagueRepository = leagueRepository;
        this.teamRepository = teamRepository;
        this.junctionRepository = junctionRepository;
        this.teamSyncService = teamSyncService;
        this.storage = storage;
    }

    @Transactional
    public List<VolleyballLeagueTeamView> getTeams(Long leagueId, String season, boolean turkish) {
        String resolvedSeason = season;
        if (resolvedSeason == null || resolvedSeason.isBlank()) {
            VolleyballLeague league = leagueRepository.findById(leagueId).orElse(null);
            resolvedSeason = league != null ? league.getCurrentSeason() : null;
        }

        // 1) Junction katmani — kanonik kaynak.
        if (resolvedSeason != null && !resolvedSeason.isBlank()) {
            List<Long> ids = junctionRepository.findTeamIdsByLeagueAndSeason(leagueId, resolvedSeason);
            if (!ids.isEmpty()) {
                return mapByIds(ids, turkish);
            }
            // 2) Junction bos — senkron doldur (onboarding'de kabul edilebilir dur).
            try {
                int n = teamSyncService.syncIfMissing(leagueId, resolvedSeason);
                if (n > 0) {
                    List<Long> freshIds = junctionRepository.findTeamIdsByLeagueAndSeason(
                            leagueId, resolvedSeason);
                    return mapByIds(freshIds, turkish);
                }
            } catch (Exception e) {
                log.warn("Voleybol getTeams junction lazy sync hata league={} season={}: {}",
                        leagueId, resolvedSeason, e.toString());
            }
        }

        // 3) Son care: games fallback.
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
                .filter(t -> t != null)
                .map(t -> toView(t, turkish))
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .toList();
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
