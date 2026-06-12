package com.scorestv.basketball;

import com.scorestv.basketball.domain.BasketballLeague;
import com.scorestv.basketball.domain.BasketballLeagueRepository;
import com.scorestv.basketball.domain.BasketballTeam;
import com.scorestv.basketball.domain.BasketballTeamLeagueSeason;
import com.scorestv.basketball.domain.BasketballTeamLeagueSeasonRepository;
import com.scorestv.basketball.domain.BasketballTeamRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * API-Basketball {@code /teams?league=X&season=Y} → {@code basketball_teams}
 * + {@code basketball_team_league_seasons} junction senkronu.
 *
 * <p>Futbol {@code TeamSyncService.syncLeague}'in basketbol karşılığı; junction
 * games'ten bağımsız olduğu için sezon başında maç oynanmadan da TAM kadro
 * listesi getirir. Onboarding "favori basketbol takımı seç" akışı için kritik.
 *
 * <p>Replace pattern: çağrı her seferinde junction'daki (lig, sezon) kadrosunu
 * silip yeniden yazar — kadrodan düşen takım otomatik temizlenir.
 */
@Service
public class BasketballTeamSyncService {

    private static final Logger log = LoggerFactory.getLogger(BasketballTeamSyncService.class);

    private static final ParameterizedTypeReference<BasketballApiResponse<BkTeamDto>> TEAMS_TYPE =
            new ParameterizedTypeReference<>() {};

    /** Cron / startup re-sync debounce: bu süreden daha eski junction yeniden çekilir. */
    private static final Duration RESYNC_AFTER = Duration.ofHours(12);

    private final BasketballApiClient client;
    private final BasketballTeamRepository teamRepo;
    private final BasketballLeagueRepository leagueRepo;
    private final BasketballTeamLeagueSeasonRepository junctionRepo;

    @PersistenceContext
    private EntityManager em;

    public BasketballTeamSyncService(BasketballApiClient client,
                                     BasketballTeamRepository teamRepo,
                                     BasketballLeagueRepository leagueRepo,
                                     BasketballTeamLeagueSeasonRepository junctionRepo) {
        this.client = client;
        this.teamRepo = teamRepo;
        this.leagueRepo = leagueRepo;
        this.junctionRepo = junctionRepo;
    }

    /**
     * Bir basketbol liginin (sezon) takım kadrosunu API'den çekip
     * {@code basketball_teams}'e upsert + junction'a replace yazar.
     *
     * @return upsert edilen takım sayısı (0 = API boş veya hata).
     */
    @Transactional
    public int syncTeamsForLeague(Long leagueId, String season) {
        if (leagueId == null || season == null || season.isBlank()) return 0;

        List<BkTeamDto> teams;
        try {
            teams = client.get(
                    "/teams",
                    Map.of("league", leagueId, "season", season),
                    TEAMS_TYPE).responseOrEmpty();
        } catch (Exception e) {
            log.warn("Basketbol /teams?league={}&season={} hatası: {}",
                    leagueId, season, e.toString());
            return 0;
        }
        if (teams.isEmpty()) {
            log.info("Basketbol /teams?league={}&season={} boş döndü", leagueId, season);
            return 0;
        }

        // Replace pattern — eski kadrodan düşen takım otomatik temizlenir.
        junctionRepo.deleteByLeagueAndSeason(leagueId, season);
        // Junction delete + team upsert + junction insert AYNI transaction'da
        // batch flush sırasında karışırsa FK fail eder ("team_id is not present
        // in basketball_teams") — sırayı garanti etmek için her aşamadan sonra
        // flush yapıyoruz.
        em.flush();

        // 1) Tüm takımları upsert et + flush — junction insert'inden ÖNCE
        // teams tablosunda kayıt OLDUĞUNDAN emin ol.
        int n = 0;
        for (BkTeamDto dto : teams) {
            if (dto.id() == null || dto.name() == null) continue;
            BasketballTeam team = teamRepo.findById(dto.id()).orElseGet(BasketballTeam::new);
            team.setId(dto.id());
            team.setName(dto.name());
            if (dto.logo() != null) {
                team.setLogo(dto.logo());
            }
            teamRepo.save(team);
            n++;
        }
        em.flush(); // tüm team insert/update'leri DB'ye yazıldı.

        // 2) Junction kayıtları — FK artık güvende.
        Instant now = Instant.now();
        for (BkTeamDto dto : teams) {
            if (dto.id() == null) continue;
            BasketballTeamLeagueSeason j = new BasketballTeamLeagueSeason(
                    dto.id(), leagueId, season);
            j.setSyncedAt(now);
            junctionRepo.save(j);
        }
        em.flush();

        log.info("Basketbol /teams league={} season={}: {} takım upsert + junction",
                leagueId, season, n);
        return n;
    }

    /**
     * Tüm basketbol ligleri için (currentSeason'u olan) takım kadrosunu çeker.
     * Cron + startup runner buradan çağırır.
     *
     * <p>Debounce: junction'da son sync'i {@link #RESYNC_AFTER}'dan yeni olan
     * ligler atlanır (gereksiz API çağrısı önlenir).
     *
     * @return işlenen lig sayısı.
     */
    @Transactional
    public int syncAllCurrentSeasons() {
        int processed = 0;
        Instant cutoff = Instant.now().minus(RESYNC_AFTER);
        for (BasketballLeague l : leagueRepo.findAll()) {
            String season = l.getCurrentSeason();
            if (season == null || season.isBlank()) continue;
            Instant last = junctionRepo.findLastSyncedAt(l.getId(), season);
            if (last != null && last.isAfter(cutoff)) continue; // taze, geç
            syncTeamsForLeague(l.getId(), season);
            processed++;
        }
        if (processed > 0) {
            log.info("Basketbol team sync: {} lig için kadro tazelendi", processed);
        }
        return processed;
    }

    /**
     * Tek lig için "boş ise async sync" — onboarding'de boş takım listesi
     * gören kullanıcı için arkada API çağrısı tetiklemek.
     *
     * <p>{@code @Async} kullanmıyoruz; caller (servis) decoupled bir thread'de
     * çağırmalı veya kabul ediyorsa inline (ilk 2-3 sn yavaş ama doğru veri).
     */
    @Transactional
    public int syncIfMissing(Long leagueId, String season) {
        if (junctionRepo.existsAnyByLeagueAndSeason(leagueId, season)) return 0;
        return syncTeamsForLeague(leagueId, season);
    }
}
