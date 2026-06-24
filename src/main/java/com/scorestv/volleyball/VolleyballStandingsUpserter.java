package com.scorestv.volleyball;

import com.scorestv.volleyball.domain.VolleyballLeague;
import com.scorestv.volleyball.domain.VolleyballStanding;
import com.scorestv.volleyball.domain.VolleyballStandingRepository;
import com.scorestv.volleyball.domain.VolleyballTeam;
import com.scorestv.volleyball.domain.VolleyballTeamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Voleybol puan durumu upsert'i — REPLACE strategy.
 *
 * <p>Her sync icin {@code (league_id, season)} icin TUM standings silinip
 * yeniden yazilir. Group-aware unique key. {@code groupName} her zaman string
 * (null degil).
 *
 * <p><b>Veri-kaybi korumasi:</b> API bos liste donerse (groups bos veya tum
 * gruplar bos) mevcut DB satirlari SILINMEZ — eski veri korunur. Yalniz API
 * gercek satir dondurdugunde replace yapilir.
 *
 * <p>In-memory dedup: ayni grup+takim kombinasyonu iki kez gelirse sadece ilki.
 */
@Component
public class VolleyballStandingsUpserter {

    private static final Logger log = LoggerFactory.getLogger(VolleyballStandingsUpserter.class);

    private final VolleyballStandingRepository standingRepo;
    private final VolleyballTeamRepository teamRepo;

    public VolleyballStandingsUpserter(VolleyballStandingRepository standingRepo,
                                       VolleyballTeamRepository teamRepo) {
        this.standingRepo = standingRepo;
        this.teamRepo = teamRepo;
    }

    /**
     * Bir lig+sezon icin tum standings'i replace eder.
     *
     * @return yazilan satir sayisi
     */
    @Transactional
    public int replaceAll(VolleyballLeague league,
                          String season,
                          List<List<VbStandingDto>> groups) {
        if (league == null || season == null || groups == null) return 0;

        // Veri-kaybi korumasi: API bos cevap dondurduyse mevcut veriyi silme.
        boolean anyRows = groups.stream().anyMatch(g -> g != null && !g.isEmpty());
        if (!anyRows) {
            log.debug("Voleybol standings bos cevap league={} season={} — mevcut veri korunuyor",
                    league.getId(), season);
            return 0;
        }

        standingRepo.deleteByLeagueAndSeason(league.getId(), season);

        int written = 0;
        Set<String> seen = new HashSet<>();   // dedup key: groupName|teamId
        for (List<VbStandingDto> group : groups) {
            if (group == null) continue;
            for (VbStandingDto dto : group) {
                if (dto == null || dto.team() == null || dto.team().id() == null) continue;

                String groupName = dto.group() != null && dto.group().name() != null
                        ? dto.group().name() : "";
                String dedupKey = groupName + "|" + dto.team().id();
                if (!seen.add(dedupKey)) {
                    log.warn("Voleybol standings duplicate atlandi: league={} season={} group='{}' team={}",
                            league.getId(), season, groupName, dto.team().id());
                    continue;
                }

                VolleyballTeam team = teamRepo.findById(dto.team().id()).orElseGet(() -> {
                    VolleyballTeam t = new VolleyballTeam();
                    t.setId(dto.team().id());
                    t.setName(dto.team().name() != null ? dto.team().name()
                            : ("Takim #" + dto.team().id()));
                    t.setLogo(dto.team().logo());
                    return teamRepo.save(t);
                });

                VolleyballStanding s = new VolleyballStanding();
                s.setLeague(league);
                s.setSeason(season);
                s.setTeam(team);
                s.setStage(dto.stage());
                s.setGroupName(groupName);
                s.setPosition(dto.position());

                if (dto.games() != null) {
                    if (dto.games().win() != null) {
                        s.setWon(dto.games().win().total());
                        s.setWonPercentage(dto.games().win().percentage());
                    }
                    if (dto.games().lose() != null) {
                        s.setLost(dto.games().lose().total());
                        s.setLostPercentage(dto.games().lose().percentage());
                    }
                    Object played = dto.games().played();
                    if (played instanceof Number n) {
                        s.setGamesPlayed(n.intValue());
                    } else if (played instanceof java.util.Map<?, ?> m) {
                        s.setGamesPlayed(asInt(m.get("all")));
                    }
                }
                if (dto.goals() != null) {
                    s.setSetsFor(dto.goals().setsFor());
                    s.setSetsAgainst(dto.goals().against());
                }
                s.setPoints(dto.points());
                s.setForm(dto.form());
                s.setDescription(dto.description());

                standingRepo.save(s);
                written++;
            }
        }
        return written;
    }

    /** Defansif int conversion — Map'ten gelen Object'i Integer'a cevirir. */
    private static Integer asInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(v.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
