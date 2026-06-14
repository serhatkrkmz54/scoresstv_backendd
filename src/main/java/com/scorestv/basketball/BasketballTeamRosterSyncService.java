package com.scorestv.basketball;

import com.scorestv.basketball.domain.BasketballLeague;
import com.scorestv.basketball.domain.BasketballLeagueRepository;
import com.scorestv.basketball.domain.BasketballPlayer;
import com.scorestv.basketball.domain.BasketballPlayerRepository;
import com.scorestv.basketball.domain.BasketballPlayerSeasonStat;
import com.scorestv.basketball.domain.BasketballPlayerSeasonStatRepository;
import com.scorestv.basketball.domain.BasketballTeam;
import com.scorestv.basketball.domain.BasketballTeamRepository;
import com.scorestv.common.SlugUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Bir basketbol takiminin sezon kadrosunu API'den ({@code /players?team=X&season=Y})
 * cekip {@link BasketballPlayer} master tablosuna + {@link BasketballPlayerSeasonStat}'a
 * minimal satir olarak yazar.
 *
 * <p><b>Roster vs top players:</b> {@link BasketballTopPlayersSyncService}
 * sezon istatistiklerini ({@code /players?league=X&season=Y}) cekiyor, bu
 * yalniz "istatistik kaydi olan" oyunculari getirir. Roster ise takimin
 * tum kadrosunu doner (sezon basinda kadroda olan ama henuz mac oynamayan
 * oyuncular dahil).
 *
 * <p>API yaniti basit: {@code id, name, number, country, position, age}.
 * Bunu {@link BasketballPlayer} entity'sine yazarken {@code name}'i bosluga
 * gore split edip {@code lastName + firstName} olarak ayiriyoruz (API
 * "Soyad Adi" sirasinda donuyor — orn. "Marjanovic Boban").
 *
 * <p>Sezon-stat tablosuna minimal satir yazmamizin sebebi: takim detay
 * sayfasi roster'i bu tablodan okuyor ({@code findRosterByTeamLeagueSeason}).
 * Mevcut sorguyu degistirmemek icin pragmatik yol.
 */
@Service
public class BasketballTeamRosterSyncService {

    private static final Logger log =
            LoggerFactory.getLogger(BasketballTeamRosterSyncService.class);

    private final BasketballApiClient client;
    private final BasketballTeamRepository teamRepo;
    private final BasketballLeagueRepository leagueRepo;
    private final BasketballPlayerRepository playerRepo;
    private final BasketballPlayerSeasonStatRepository statRepo;

    public BasketballTeamRosterSyncService(BasketballApiClient client,
                                            BasketballTeamRepository teamRepo,
                                            BasketballLeagueRepository leagueRepo,
                                            BasketballPlayerRepository playerRepo,
                                            BasketballPlayerSeasonStatRepository statRepo) {
        this.client = client;
        this.teamRepo = teamRepo;
        this.leagueRepo = leagueRepo;
        this.playerRepo = playerRepo;
        this.statRepo = statRepo;
    }

    /**
     * Roster'i senkronize eder. {@code leagueId} verilmemisse minimal satir
     * yazilamaz (FK gerekli) — sadece master tabloyu doldururuz.
     *
     * @return upsertlenen oyuncu sayisi
     */
    @Transactional
    public int sync(long teamId, Long leagueId, String season) {
        BasketballTeam team = teamRepo.findById(teamId).orElse(null);
        if (team == null) {
            log.debug("Roster sync: takim bulunamadi id={}", teamId);
            return 0;
        }
        BasketballLeague league = leagueId != null
                ? leagueRepo.findById(leagueId).orElse(null) : null;

        List<BkRosterPlayerDto> roster;
        try {
            roster = client.fetchRosterByTeamSeason(teamId, season);
        } catch (Exception e) {
            log.warn("Roster API hatasi team={} season={}: {}",
                    teamId, season, e.toString());
            return 0;
        }
        if (roster.isEmpty()) {
            log.debug("Roster API bos yanit team={} season={}", teamId, season);
            return 0;
        }

        int upserted = 0;
        for (BkRosterPlayerDto dto : roster) {
            if (dto.id() == null || dto.name() == null) continue;
            BasketballPlayer player = upsertPlayer(dto);

            // Lig biliniyorsa sezon-stat tablosuna minimal satir
            if (league != null) {
                upsertMinimalStat(player, team, league, season);
            }
            upserted++;
        }

        log.info("Roster sync OK team={} league={} season={} → {} oyuncu",
                teamId, leagueId, season, upserted);
        return upserted;
    }

    /**
     * Master {@code basketball_players} satirini ekler/gunceller. Mevcut
     * satir varsa eksik alanlari doldurur ({@code position, jerseyNumber,
     * nationality}); var olan tam profil bilgilerine ({@code photo, heightCm,
     * lastProfileSyncedAt}) dokunmaz.
     */
    private BasketballPlayer upsertPlayer(BkRosterPlayerDto dto) {
        BasketballPlayer p = playerRepo.findById(dto.id())
                .orElseGet(BasketballPlayer::new);
        p.setId(dto.id());
        if (p.getName() == null || p.getName().isBlank()) {
            p.setName(dto.name());
        }

        // "Soyad Adi" sirasi — split ilk bosluga gore
        String[] parts = dto.name().trim().split("\\s+", 2);
        if (parts.length >= 1 && (p.getLastName() == null || p.getLastName().isBlank())) {
            p.setLastName(parts[0]);
        }
        if (parts.length == 2 && (p.getFirstName() == null || p.getFirstName().isBlank())) {
            p.setFirstName(parts[1]);
        }

        if (dto.position() != null) p.setPosition(dto.position());
        if (dto.number() != null && !dto.number().isBlank()) {
            try {
                p.setJerseyNumber(Integer.parseInt(dto.number()));
            } catch (NumberFormatException ignored) { /* skip */ }
        }
        if (dto.country() != null && !dto.country().isBlank()
                && p.getNationality() == null) {
            p.setNationality(dto.country());
        }

        // Slug — yoksa uret
        if (p.getSlug() == null || p.getSlug().isBlank()) {
            p.setSlug(SlugUtil.playerSlug(
                    p.getFirstName(), p.getLastName(), dto.name(), dto.id()));
        }
        return playerRepo.save(p);
    }

    /**
     * Sezon-stat tablosuna kadro satiri ekler (yoksa). Istatistik alanlari
     * null kalir; {@link BasketballTopPlayersSyncService} bir sonraki
     * calismasinda gercek degerleri yazar.
     */
    private void upsertMinimalStat(BasketballPlayer player, BasketballTeam team,
                                     BasketballLeague league, String season) {
        Optional<BasketballPlayerSeasonStat> existing =
                statRepo.findByPlayerIdAndLeagueIdAndSeason(
                        player.getId(), league.getId(), season);
        if (existing.isPresent()) {
            // Mevcut satirda team yoksa guncelle (transfer durumu)
            var s = existing.get();
            if (s.getTeam() == null || !team.getId().equals(s.getTeam().getId())) {
                s.setTeam(team);
                statRepo.save(s);
            }
            return;
        }
        BasketballPlayerSeasonStat s = new BasketballPlayerSeasonStat();
        s.setPlayer(player);
        s.setLeague(league);
        s.setSeason(season);
        s.setTeam(team);
        // Stat field'lari null — top players sync dolduracak
        statRepo.save(s);
    }
}
