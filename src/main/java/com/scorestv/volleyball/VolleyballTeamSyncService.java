package com.scorestv.volleyball;

import com.scorestv.volleyball.domain.VolleyballLeague;
import com.scorestv.volleyball.domain.VolleyballLeagueRepository;
import com.scorestv.volleyball.domain.VolleyballTeam;
import com.scorestv.volleyball.domain.VolleyballTeamLeagueSeason;
import com.scorestv.volleyball.domain.VolleyballTeamLeagueSeasonRepository;
import com.scorestv.volleyball.domain.VolleyballTeamRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * API-Volleyball {@code /teams?league=X&season=Y} → {@code volleyball_teams}
 * + {@code volleyball_team_league_seasons} junction senkronu.
 *
 * <p>Junction games'ten bagimsiz oldugu icin sezon basinda mac oynanmadan da
 * TAM kadro listesi getirir. Onboarding "favori voleybol takimi sec" icin.
 *
 * <p>Replace pattern: cagri her seferinde junction'daki (lig, sezon) kadrosunu
 * silip yeniden yazar.
 */
@Service
public class VolleyballTeamSyncService {

    private static final Logger log = LoggerFactory.getLogger(VolleyballTeamSyncService.class);

    private static final Duration RESYNC_AFTER = Duration.ofHours(12);

    private final VolleyballApiClient client;
    private final VolleyballTeamRepository teamRepo;
    private final VolleyballLeagueRepository leagueRepo;
    private final VolleyballTeamLeagueSeasonRepository junctionRepo;

    @PersistenceContext
    private EntityManager em;

    public VolleyballTeamSyncService(VolleyballApiClient client,
                                     VolleyballTeamRepository teamRepo,
                                     VolleyballLeagueRepository leagueRepo,
                                     VolleyballTeamLeagueSeasonRepository junctionRepo) {
        this.client = client;
        this.teamRepo = teamRepo;
        this.leagueRepo = leagueRepo;
        this.junctionRepo = junctionRepo;
    }

    /**
     * Bir voleybol liginin (sezon) takim kadrosunu API'den cekip
     * {@code volleyball_teams}'e upsert + junction'a replace yazar.
     *
     * @return upsert edilen takim sayisi (0 = API bos veya hata).
     */
    @Transactional
    public int syncTeamsForLeague(Long leagueId, String season) {
        if (leagueId == null || season == null || season.isBlank()) return 0;

        List<VbTeamDto> teams;
        try {
            teams = client.fetchTeamsByLeagueSeason(leagueId, season);
        } catch (Exception e) {
            log.warn("Voleybol /teams?league={}&season={} hatasi: {}",
                    leagueId, season, e.toString());
            return 0;
        }
        if (teams.isEmpty()) {
            log.info("Voleybol /teams?league={}&season={} bos dondu", leagueId, season);
            return 0;
        }

        // Replace pattern.
        junctionRepo.deleteByLeagueAndSeason(leagueId, season);
        em.flush();

        // 1) Tum takimlari upsert et + flush (junction FK'si guvende olsun).
        int n = 0;
        for (VbTeamDto dto : teams) {
            if (dto.id() == null || dto.name() == null) continue;
            VolleyballTeam team = teamRepo.findById(dto.id()).orElseGet(VolleyballTeam::new);
            team.setId(dto.id());
            team.setName(dto.name());
            if (dto.logo() != null) {
                team.setLogo(dto.logo());
            }
            teamRepo.save(team);
            n++;
        }
        em.flush();

        // 2) Junction kayitlari.
        Instant now = Instant.now();
        for (VbTeamDto dto : teams) {
            if (dto.id() == null) continue;
            VolleyballTeamLeagueSeason j = new VolleyballTeamLeagueSeason(
                    dto.id(), leagueId, season);
            j.setSyncedAt(now);
            junctionRepo.save(j);
        }
        em.flush();

        log.info("Voleybol /teams league={} season={}: {} takim upsert + junction",
                leagueId, season, n);
        return n;
    }

    /** Tum voleybol ligleri icin (currentSeason'u olan) takim kadrosu. */
    @Transactional
    public int syncAllCurrentSeasons() {
        int processed = 0;
        Instant cutoff = Instant.now().minus(RESYNC_AFTER);
        for (VolleyballLeague l : leagueRepo.findAll()) {
            String season = l.getCurrentSeason();
            if (season == null || season.isBlank()) continue;
            Instant last = junctionRepo.findLastSyncedAt(l.getId(), season);
            if (last != null && last.isAfter(cutoff)) continue;
            syncTeamsForLeague(l.getId(), season);
            processed++;
        }
        if (processed > 0) {
            log.info("Voleybol team sync: {} lig icin kadro tazelendi", processed);
        }
        return processed;
    }

    /** Tek lig icin "bos ise sync". */
    @Transactional
    public int syncIfMissing(Long leagueId, String season) {
        if (junctionRepo.existsAnyByLeagueAndSeason(leagueId, season)) return 0;
        return syncTeamsForLeague(leagueId, season);
    }
}
