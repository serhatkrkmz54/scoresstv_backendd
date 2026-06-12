package com.scorestv.basketball;

import com.scorestv.basketball.domain.BasketballLeague;
import com.scorestv.basketball.domain.BasketballStanding;
import com.scorestv.basketball.domain.BasketballStandingRepository;
import com.scorestv.basketball.domain.BasketballTeam;
import com.scorestv.basketball.domain.BasketballTeamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Basketbol puan durumu upsert'i — REPLACE strategy.
 *
 * <p>Her sync icin {@code (league_id, season)} icin TUM standings silinip
 * yeniden yazilir. Bu, takim sirasi degisikliklerini ve takim
 * ekleme/cikarmalarini dogru yansitir. Group-aware unique key sayesinde NBA
 * Conference + Division ayrimi korunur.
 *
 * <p>{@code groupName} her zaman string (null degil) — DB constraint NOT NULL
 * DEFAULT ''. Bu sayede unique key duplicate kontrolu sorunsuz isler (futbol
 * V195 paterni).
 *
 * <p>In-memory dedup: ayni grup+takim kombinasyonu API'den iki kez gelirse
 * (defansif) sadece ilki yazilir; sonraki gelirse atlanir. Futbol
 * StandingsUpserter'in dedup paterni.
 */
@Component
public class BasketballStandingsUpserter {

    private static final Logger log = LoggerFactory.getLogger(BasketballStandingsUpserter.class);

    private final BasketballStandingRepository standingRepo;
    private final BasketballTeamRepository teamRepo;

    public BasketballStandingsUpserter(BasketballStandingRepository standingRepo,
                                       BasketballTeamRepository teamRepo) {
        this.standingRepo = standingRepo;
        this.teamRepo = teamRepo;
    }

    /**
     * Bir lig+sezon icin tum standings'i replace eder.
     *
     * @param league hedef lig (zaten DB'de upsert edilmis)
     * @param season "2023-2024" formati
     * @param groups API'den gelen group-bazli liste (her group ayri alt-liste)
     * @return yazilan satir sayisi
     */
    @Transactional
    public int replaceAll(BasketballLeague league,
                          String season,
                          List<List<BkStandingDto>> groups) {
        if (league == null || season == null || groups == null) return 0;

        // Eski satirlari toptan sil — replace stratejisi.
        standingRepo.deleteByLeagueAndSeason(league.getId(), season);
        // Ayni transaction icinde delete sonrasi insert; flush gerek YOK,
        // unique constraint check transaction sonunda calisir.

        int written = 0;
        Set<String> seen = new HashSet<>();   // dedup key: groupName|teamId
        for (List<BkStandingDto> group : groups) {
            if (group == null) continue;
            for (BkStandingDto dto : group) {
                if (dto == null || dto.team() == null || dto.team().id() == null) continue;

                String groupName = dto.group() != null && dto.group().name() != null
                        ? dto.group().name() : "";
                String dedupKey = groupName + "|" + dto.team().id();
                if (!seen.add(dedupKey)) {
                    log.warn("Standings duplicate atlandi: league={} season={} group='{}' team={}",
                            league.getId(), season, groupName, dto.team().id());
                    continue;
                }

                BasketballTeam team = teamRepo.findById(dto.team().id()).orElseGet(() -> {
                    // Self-heal — takim hic upsert edilmemisse minimal yaz.
                    BasketballTeam t = new BasketballTeam();
                    t.setId(dto.team().id());
                    t.setName(dto.team().name() != null ? dto.team().name()
                            : ("Takim #" + dto.team().id()));
                    t.setLogo(dto.team().logo());
                    return teamRepo.save(t);
                });

                BasketballStanding s = new BasketballStanding();
                s.setLeague(league);
                s.setSeason(season);
                s.setTeam(team);
                s.setStage(dto.stage());
                s.setGroupName(groupName);
                s.setPosition(dto.position());

                if (dto.won() != null) {
                    s.setWonAll(dto.won().all());
                    s.setWonHome(dto.won().home());
                    s.setWonAway(dto.won().away());
                    s.setWonPercentage(dto.won().percentage());
                }
                if (dto.lost() != null) {
                    s.setLostAll(dto.lost().all());
                    s.setLostHome(dto.lost().home());
                    s.setLostAway(dto.lost().away());
                    s.setLostPercentage(dto.lost().percentage());
                }
                if (dto.games() != null && dto.games().played() != null) {
                    s.setGamesPlayedAll(dto.games().played().all());
                    s.setGamesPlayedHome(dto.games().played().home());
                    s.setGamesPlayedAway(dto.games().played().away());
                }
                if (dto.points() != null) {
                    s.setPointsFor(dto.points().pointsFor());
                    s.setPointsAgainst(dto.points().against());
                }
                s.setForm(dto.form());
                s.setDescription(dto.description());

                standingRepo.save(s);
                written++;
            }
        }
        return written;
    }
}
