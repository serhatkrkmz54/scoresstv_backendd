package com.scorestv.football.sync;

import com.scorestv.football.domain.League;
import com.scorestv.football.domain.LeagueRepository;
import com.scorestv.football.domain.Standing;
import com.scorestv.football.domain.StandingRepository;
import com.scorestv.football.domain.Team;
import com.scorestv.football.domain.TeamRepository;
import com.scorestv.football.sync.dto.StandingApiDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Puan durumunu DB'ye <b>replace</b> stratejisiyle yazar:
 * verilen lig + sezon için TÜM satırlar silinir, gelenler yazılır.
 *
 * <p>Bu yaklaşım: takım sıralaması düşse, yeni grup açılsa, takım eklense
 * dahi DB API'yi birebir yansıtır. UNIQUE(league, season, team) ile veri
 * bütünlüğü korunur.
 */
@Service
public class StandingsUpserter {

    private static final Logger log = LoggerFactory.getLogger(StandingsUpserter.class);

    private final StandingRepository standingRepository;
    private final LeagueRepository leagueRepository;
    private final TeamRepository teamRepository;

    /**
     * Delete + insert ayni TX icinde — Hibernate insert'i once flush ediyor,
     * delete bekliyor → eski satir DB'de dururken yeni satir patliyor.
     * EntityManager.flush() ile delete'i manuel flush'larsak duplicate cikmaz.
     */
    @PersistenceContext
    private EntityManager entityManager;

    public StandingsUpserter(StandingRepository standingRepository,
                             LeagueRepository leagueRepository,
                             TeamRepository teamRepository) {
        this.standingRepository = standingRepository;
        this.leagueRepository = leagueRepository;
        this.teamRepository = teamRepository;
    }

    @Transactional
    public int replace(Long leagueId,
                       Integer season,
                       List<List<StandingApiDto.Row>> groups) {
        // Veri-kaybi korumasi: API bos dondurduyse mevcut standings'i SILME.
        if (groups == null || groups.isEmpty()) {
            return 0;
        }
        // 1) Eski satirlari sil — Hibernate normalde insert'leri once flush
        //    ettiginden delete ariya kalir, unique constraint patlar. Manuel
        //    flush + clear ile delete'in DB'ye anlik gitmesini garantileriz.
        standingRepository.deleteByLeagueIdAndSeason(leagueId, season);
        entityManager.flush();
        entityManager.clear();
        League leagueRef = leagueRepository.getReferenceById(leagueId);
        // 2) API-Football bazi yarismalarda ayni (takim, grup) kombinasyonunu
        //    iki kez donduruyor. Unique constraint (league, season, team,
        //    group_name) bunu kabul etmedigi icin in-memory dedup yapariz.
        //    Anahtari trim + lowercase normalize ederiz; whitespace/case farki
        //    olan grup adlari (orn. "Group A" vs "GROUP A") ayni sayilir.
        Set<String> seen = new HashSet<>();
        int written = 0;
        for (List<StandingApiDto.Row> group : groups) {
            if (group == null) {
                continue;
            }
            for (StandingApiDto.Row row : group) {
                if (row == null || row.team() == null || row.team().id() == null
                        || row.rank() == null || row.points() == null) {
                    continue;
                }
                String dedupKey = row.team().id() + "|" + normalizeGroup(row.group());
                if (!seen.add(dedupKey)) {
                    continue; // bu (takim, grup) zaten yazildi — atla
                }
                // FK guard — takim master tabloda yoksa minimal kayit ile ekle.
                // API-Football bazen onceden gormedigimiz takimi standings'te
                // gonderir (ornek: kupa liglerinde alt sezondan yukselen takim).
                // FK violation yerine self-healing yapariz; TeamSyncService daha
                // sonra eksik field'lari (country, founded, venue vb.) doldurur.
                Long teamId = row.team().id();
                if (!teamRepository.existsById(teamId)) {
                    ensureTeamExists(teamId, row.team().name(), row.team().logo());
                }
                Team teamRef = teamRepository.getReferenceById(teamId);
                Standing entity = new Standing();
                entity.setLeague(leagueRef);
                entity.setSeason(season);
                entity.setTeam(teamRef);
                entity.setRank(row.rank());
                entity.setPoints(row.points());
                entity.setGoalsDiff(row.goalsDiff());
                entity.setGroupName(row.group());
                entity.setForm(row.form());
                entity.setDescription(row.description());
                applyAllStats(entity, row.all());
                standingRepository.save(entity);
                written++;
            }
        }
        return written;
    }

    /**
     * Standings sirasinda master tabloda olmayan takimi minimal ile ekle.
     * Sadece zorunlu alanlari (id, name, national=false, covered=false) doldurur;
     * country/founded/venue gibi alanlar bos kalir — DailyTeamRefreshJob ya da
     * sayfa ziyaretinde lazy sync doldurur.
     *
     * <p>Postgres ON CONFLICT DO NOTHING: ayni anda baska bir TX zaten eklemis
     * olabilir (paralel standings sync); ikinci insert sessizce gecer.
     */
    private void ensureTeamExists(Long teamId, String name, String logoUrl) {
        if (teamId == null) return;
        String safeName = (name == null || name.isBlank())
                ? ("Team #" + teamId) : name;
        try {
            Team t = new Team();
            t.setId(teamId);
            t.setName(safeName);
            t.setNational(false);
            t.setCovered(false);
            t.setLogoUrl(logoUrl);
            teamRepository.save(t);
            // Persistence context kirlenmesin diye flush+clear yapmiyoruz;
            // outer @Transactional commit sirasinda otomatik flush eder.
            log.info("Standings FK self-heal: minimal team kaydi olusturuldu "
                    + "id={} name={}", teamId, safeName);
        } catch (Exception e) {
            // Paralel sync ayni id'yi eklemis olabilir — yok say, var demektir.
            log.debug("ensureTeamExists nop (muhtemelen paralel insert): {}",
                    e.getMessage());
        }
    }

    /** Grup adini dedup icin normalize et — null/empty/whitespace/case farklarini sil. */
    private static String normalizeGroup(String raw) {
        if (raw == null) return "";
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private static void applyAllStats(Standing entity, StandingApiDto.TeamStats all) {
        if (all == null) {
            return;
        }
        entity.setPlayed(all.played());
        entity.setWin(all.win());
        entity.setDraw(all.draw());
        entity.setLose(all.lose());
        if (all.goals() != null) {
            entity.setGoalsFor(all.goals().goalsFor());
            entity.setGoalsAgainst(all.goals().against());
        }
    }
}
