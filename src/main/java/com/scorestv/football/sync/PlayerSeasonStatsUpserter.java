package com.scorestv.football.sync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scorestv.football.domain.League;
import com.scorestv.football.domain.LeagueRepository;
import com.scorestv.football.domain.PlayerSeasonStatRepository;
import com.scorestv.football.domain.Team;
import com.scorestv.football.domain.TeamRepository;
import com.scorestv.football.sync.dto.PlayerSeasonApiDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Bir takim+sezon icin /players?team=X&season=Y yanitlarini DB'ye yazar.
 *
 * <p>REPLACE pattern: tum eski (team, season) satirlari silinir, gelen tam
 * set yazilir. Sayfa sayfa cagrilar arasinda ara silme yapmaz — caller (sync
 * service) ilk sayfa oncesi {@code replaceStart()} ile siler, sonra her
 * sayfa icin {@code upsertPage()} cagirir.
 *
 * <p>Yan etki: her oyuncu master tabloya (PlayerUpserter) yazilir; foto'su
 * varsa MinIO aynalamasi icin kuyruga girer.
 */
@Service
public class PlayerSeasonStatsUpserter {

    private static final Logger log = LoggerFactory.getLogger(PlayerSeasonStatsUpserter.class);

    private final PlayerSeasonStatRepository repository;
    private final TeamRepository teamRepository;
    private final LeagueRepository leagueRepository;
    private final PlayerUpserter playerUpserter;

    /**
     * Local Jackson ObjectMapper — Map -> JSON string serialize icin yeter.
     * Bean inject etmiyoruz cunku proje Jackson 3.x ({@code tools.jackson})
     * kullanir, classic {@code com.fasterxml.jackson.databind.ObjectMapper}
     * bean'i yok. SeoBuilder'larla ayni pattern (local static instance).
     */
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    public PlayerSeasonStatsUpserter(PlayerSeasonStatRepository repository,
                                     TeamRepository teamRepository,
                                     LeagueRepository leagueRepository,
                                     PlayerUpserter playerUpserter) {
        this.repository = repository;
        this.teamRepository = teamRepository;
        this.leagueRepository = leagueRepository;
        this.playerUpserter = playerUpserter;
    }

    /** Map -> JSON string (Postgres jsonb cast icin). */
    private String toJson(Map<String, Object> fields) {
        if (fields == null) return "{}";
        try {
            return JSON_MAPPER.writeValueAsString(fields);
        } catch (JsonProcessingException e) {
            log.warn("PlayerSeasonStat stats_json serialize hatasi: {}", e.getMessage());
            return "{}";
        }
    }

    /** REPLACE pattern: ilk sayfa oncesi tum eski kayitlari siler. */
    @Transactional
    public void replaceStart(Long teamId, Integer season) {
        repository.deleteByTeamIdAndSeason(teamId, season);
    }

    /** REPLACE pattern: player+season — PlayerProfileSyncService oncesi cagrilir. */
    @Transactional
    public void replaceStartByPlayer(Long playerId, Integer season) {
        repository.deleteByPlayerIdAndSeason(playerId, season);
    }

    /**
     * Tek bir statistics entry'sini yazar (replaceStartByPlayer sonrasi).
     * teamId/season filtresi yok — entry'nin kendi alanlarini kullanir.
     *
     * <p>Atomik native upsert (ON CONFLICT DO UPDATE) — replaceStart sonrasi
     * bile race condition'da dup gelirse tx-poisoning olmaz, INSERT update'e
     * donusur. WARN log'lari kalkar.
     *
     * @return 1 (her zaman — ON CONFLICT update da yazma sayilir)
     */
    @Transactional
    public int upsertSingleEntry(Long playerId,
                                  PlayerSeasonApiDto.StatisticsEntry entry) {
        if (playerId == null || entry == null
                || entry.team() == null || entry.league() == null) {
            return 0;
        }
        Long entryTeamId = entry.team().id();
        Long entryLeagueId = entry.league().id();
        Integer entrySeason = entry.league().season();
        if (entryTeamId == null || entryLeagueId == null || entrySeason == null) return 0;

        // FK guard — team/league master tabloda yoksa atla.
        Team team = teamRepository.findById(entryTeamId).orElse(null);
        League league = leagueRepository.findById(entryLeagueId).orElse(null);
        if (team == null || league == null) {
            log.debug("PlayerSeasonStat single atlandi (team/league DB'de yok): "
                            + "playerId={} teamId={} leagueId={}",
                    playerId, entryTeamId, entryLeagueId);
            return 0;
        }

        repository.upsertNative(playerId, entryTeamId, entryLeagueId,
                entrySeason, toJson(entry.getFields()));
        return 1;
    }

    /**
     * Tek bir sayfa icin upsert. {@code replaceStart} oncesinde cagrilmis olmali.
     * Donen: bu sayfada yazilan satir sayisi (player × league kombinasyonu).
     */
    @Transactional
    public int upsertPage(Long teamId, Integer season,
                          List<PlayerSeasonApiDto> items) {
        if (items == null || items.isEmpty()) return 0;
        int written = 0;
        for (PlayerSeasonApiDto dto : items) {
            if (dto == null || dto.player() == null) continue;
            PlayerSeasonApiDto.Player p = dto.player();
            if (p.id() == null) continue;

            // Player master tablo upsert (foto mirror altyapisi).
            playerUpserter.upsert(p.id(), p.name(), p.photo());

            if (dto.statistics() == null) continue;
            for (PlayerSeasonApiDto.StatisticsEntry entry : dto.statistics()) {
                if (entry == null || entry.team() == null || entry.league() == null) continue;
                Long entryTeamId = entry.team().id();
                Long entryLeagueId = entry.league().id();
                Integer entrySeason = entry.league().season();
                if (entryTeamId == null || entryLeagueId == null || entrySeason == null) continue;

                // Sorgu teamId ile yapildi ama API mid-season transfer'leri de
                // listeleyebilir — sadece sorgu team'iyle eslesen satirlari yaz.
                if (!teamId.equals(entryTeamId)) continue;
                if (!season.equals(entrySeason)) continue;

                Team team = teamRepository.findById(entryTeamId).orElse(null);
                League league = leagueRepository.findById(entryLeagueId).orElse(null);
                if (team == null || league == null) {
                    log.debug("PlayerSeasonStat atlandi (team/league DB'de yok): "
                                    + "playerId={} teamId={} leagueId={}",
                            p.id(), entryTeamId, entryLeagueId);
                    continue;
                }

                // Native ON CONFLICT DO UPDATE — race-safe, tx-poisoning yok
                repository.upsertNative(p.id(), entryTeamId, entryLeagueId,
                        entrySeason, toJson(entry.getFields()));
                written++;
            }
        }
        return written;
    }
}
