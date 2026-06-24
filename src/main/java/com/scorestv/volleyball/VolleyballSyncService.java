package com.scorestv.volleyball;

import com.scorestv.volleyball.domain.VolleyballGame;
import com.scorestv.volleyball.domain.VolleyballGameRepository;
import com.scorestv.volleyball.domain.VolleyballLeague;
import com.scorestv.volleyball.domain.VolleyballLeagueRepository;
import com.scorestv.volleyball.domain.VolleyballTeam;
import com.scorestv.volleyball.domain.VolleyballTeamRepository;
import com.scorestv.volleyball.notify.VolleyballNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * API-Volleyball {@code /games} verisini DB'ye senkronlar (upsert).
 * Football/basketball sync'inden bagimsiz — kendi client'i, kendi tablolari.
 *
 * <p><b>Voleybol skor modeli:</b> scores.home/away = kazanilan set sayisi,
 * periods.first..fifth = her setteki sayi.
 */
@Service
public class VolleyballSyncService {

    private static final Logger log = LoggerFactory.getLogger(VolleyballSyncService.class);

    /** Canli (in-play) voleybol durum kodlari = devam eden set numarasi. */
    private static final Set<String> LIVE_STATUSES =
            Set.of("S1", "S2", "S3", "S4", "S5");
    /** Bitmis durum kodlari (FT=finished, AW=awarded). */
    private static final Set<String> FINISHED_STATUSES = Set.of("FT", "AW");

    private final VolleyballApiClient client;
    private final VolleyballProperties props;
    private final VolleyballGameRepository gameRepo;
    private final VolleyballLeagueRepository leagueRepo;
    private final VolleyballTeamRepository teamRepo;
    private final VolleyballNotificationService notifications;

    public VolleyballSyncService(VolleyballApiClient client,
                                 VolleyballProperties props,
                                 VolleyballGameRepository gameRepo,
                                 VolleyballLeagueRepository leagueRepo,
                                 VolleyballTeamRepository teamRepo,
                                 VolleyballNotificationService notifications) {
        this.client = client;
        this.props = props;
        this.gameRepo = gameRepo;
        this.leagueRepo = leagueRepo;
        this.teamRepo = teamRepo;
        this.notifications = notifications;
    }

    /** Bir takvim gununun maclarini cek + upsert. Doner: yazilan mac sayisi. */
    @Transactional
    public int syncDate(LocalDate date) {
        var games = fetch(date.toString());
        // Fikstur backfill — bildirim YOK (notify=false).
        int n = upsertAll(games, false);
        log.info("Voleybol fikstur sync ({}): {} mac", date, n);
        return n;
    }

    /**
     * Canli skor guncellemesi — API-Volleyball'da {@code live=all} YOK; canli
     * skor BUGUNUN maclarini cekerek alinir.
     */
    @Transactional
    public int syncLive() {
        LocalDate today = LocalDate.now(ZoneId.of(props.timezone()));
        var games = fetch(today.toString());
        // Canli (bugun) sync — durum gecislerinde bildirim ACIK (notify=true).
        int n = upsertAll(games, true);
        if (n > 0) log.debug("Voleybol canli (bugun) sync: {} mac", n);
        return n;
    }

    /**
     * Kayan pencere tarihleri: bugunden {@code windowDaysBefore} geriye,
     * {@code windowDaysAfter} ileriye.
     */
    public List<LocalDate> windowDates() {
        LocalDate today = LocalDate.now(ZoneId.of(props.timezone()));
        List<LocalDate> dates = new ArrayList<>();
        for (int off = -props.windowDaysBefore(); off <= props.windowDaysAfter(); off++) {
            dates.add(today.plusDays(off));
        }
        return dates;
    }

    private List<VbGameDto> fetch(String date) {
        try {
            return client.fetchGamesByDate(date, props.timezone());
        } catch (Exception e) {
            log.warn("Voleybol /games cagrisi basarisiz {}: {}", date, e.toString());
            return List.of();
        }
    }

    /**
     * Package-public — H2hSyncService gibi diger servisler ham
     * {@link VbGameDto} listesini DB'ye yazmak icin cagirir. {@code notify=false}
     * H2H gecmis maclari icin bildirim yaratmaz.
     */
    @Transactional
    public int upsertAll(List<VbGameDto> games, boolean notify) {
        int n = 0;
        for (VbGameDto g : games) {
            if (upsert(g, notify)) n++;
        }
        return n;
    }

    private boolean upsert(VbGameDto dto, boolean notify) {
        if (dto.id() == null || dto.league() == null || dto.teams() == null
                || dto.teams().home() == null || dto.teams().away() == null) {
            return false;
        }
        VolleyballLeague league = upsertLeague(dto.league(), dto.country());
        VolleyballTeam home = upsertTeam(dto.teams().home());
        VolleyballTeam away = upsertTeam(dto.teams().away());
        if (league == null || home == null || away == null) return false;

        Instant startAt = resolveStart(dto);
        if (startAt == null) return false;

        VolleyballGame game = gameRepo.findById(dto.id()).orElseGet(VolleyballGame::new);
        final String oldStatus = game.getStatusShort();

        game.setId(dto.id());
        game.setLeague(league);
        game.setSeason(dto.league().season());
        game.setHomeTeam(home);
        game.setAwayTeam(away);
        game.setStartAt(startAt);
        game.setWeek(dto.week());
        if (dto.status() != null) {
            game.setStatusShort(dto.status().shortName());
            game.setStatusLong(dto.status().longName());
        }
        applyScores(game, dto);

        if (notify) {
            detectAndNotify(game, oldStatus, home, away);
        }

        gameRepo.save(game);
        return true;
    }

    /**
     * Eski→yeni statu gecisini analiz edip ilgili FCM push'larini tetikler:
     * <ul>
     *   <li>NS/null → canli : mac basladi (bir kez)</li>
     *   <li>set ilerledi : sonu gecilen her set icin "set bitti" (skorlu)</li>
     *   <li>→ FT/AW : mac bitti (bir kez)</li>
     * </ul>
     */
    private void detectAndNotify(VolleyballGame game, String oldStatus,
                                 VolleyballTeam home, VolleyballTeam away) {
        final String newStatus = game.getStatusShort();
        if (newStatus == null) return;

        // Backfill korumasi: maci ILK kez bitmis goruyorsak topluca atma.
        if (oldStatus == null && FINISHED_STATUSES.contains(newStatus)) {
            game.setNotifiedStart(true);
            game.setNotifiedFinal(true);
            game.setLastNotifiedPeriod(5);
            return;
        }

        final Long gameId = game.getId();
        final Long homeTeamId = home.getId();
        final Long awayTeamId = away.getId();
        final String homeName = home.getNameTr() != null && !home.getNameTr().isBlank()
                ? home.getNameTr() : home.getName();
        final String awayName = away.getNameTr() != null && !away.getNameTr().isBlank()
                ? away.getNameTr() : away.getName();

        final boolean oldLive = oldStatus != null && LIVE_STATUSES.contains(oldStatus);
        final boolean oldFinished =
                oldStatus != null && FINISHED_STATUSES.contains(oldStatus);

        // 1) Mac basladi.
        if (!game.isNotifiedStart()
                && LIVE_STATUSES.contains(newStatus)
                && !oldLive
                && !oldFinished) {
            game.setNotifiedStart(true);
            notifications.dispatchStart(gameId, homeTeamId, awayTeamId,
                    homeName, awayName);
        }

        // 2) Set sonu — sonu gecilen her set icin (monotonik sayac).
        int target = completedSets(newStatus);
        if (target > game.getLastNotifiedPeriod()) {
            for (int s = game.getLastNotifiedPeriod() + 1; s <= target && s <= 5; s++) {
                notifications.dispatchSetEnd(gameId, homeTeamId, awayTeamId,
                        homeName, awayName, s,
                        game.getHomeTotal(), game.getAwayTotal());
            }
            game.setLastNotifiedPeriod(Math.min(target, 5));
        }

        // 3) Mac bitti.
        if (!game.isNotifiedFinal() && FINISHED_STATUSES.contains(newStatus)) {
            game.setNotifiedFinal(true);
            notifications.dispatchFinal(gameId, homeTeamId, awayTeamId,
                    homeName, awayName,
                    game.getHomeTotal(), game.getAwayTotal());
        }
    }

    /**
     * Statuye gore "su ana kadar SONU GELMIS" set sayisi (monotonik):
     * S1=0, S2=1, S3=2, S4=3, S5=4, FT/AW=5. Belirsiz (NS) = 0.
     */
    private static int completedSets(String status) {
        if (status == null) return 0;
        return switch (status) {
            case "S2" -> 1;
            case "S3" -> 2;
            case "S4" -> 3;
            case "S5" -> 4;
            case "FT", "AW" -> 5;
            default -> 0; // NS, S1, POST, CANC, INTR, ABD...
        };
    }

    private VolleyballLeague upsertLeague(VbGameDto.League l, VbGameDto.Country c) {
        if (l.id() == null) return null;
        VolleyballLeague e = leagueRepo.findById(l.id()).orElseGet(VolleyballLeague::new);
        e.setId(l.id());
        e.setName(l.name() != null ? l.name() : ("Lig #" + l.id()));
        e.setType(l.type());
        e.setLogo(l.logo());
        if (c != null) {
            e.setCountryName(c.name());
            e.setCountryCode(c.code());
            e.setCountryFlag(c.flag());
        }
        return leagueRepo.save(e);
    }

    private VolleyballTeam upsertTeam(VbGameDto.TeamRef t) {
        if (t.id() == null) return null;
        VolleyballTeam e = teamRepo.findById(t.id()).orElseGet(VolleyballTeam::new);
        e.setId(t.id());
        e.setName(t.name() != null ? t.name() : ("Takim #" + t.id()));
        e.setLogo(t.logo());
        return teamRepo.save(e);
    }

    private void applyScores(VolleyballGame g, VbGameDto dto) {
        // Kazanilan set sayilari.
        if (dto.scores() != null) {
            g.setHomeTotal(dto.scores().home());
            g.setAwayTotal(dto.scores().away());
        }
        // Set bazli sayilar.
        var p = dto.periods();
        if (p != null) {
            applySet(p.first(), g::setHomeSet1, g::setAwaySet1);
            applySet(p.second(), g::setHomeSet2, g::setAwaySet2);
            applySet(p.third(), g::setHomeSet3, g::setAwaySet3);
            applySet(p.fourth(), g::setHomeSet4, g::setAwaySet4);
            applySet(p.fifth(), g::setHomeSet5, g::setAwaySet5);
        }
    }

    private void applySet(VbGameDto.SetScore set,
                          java.util.function.Consumer<Integer> homeSetter,
                          java.util.function.Consumer<Integer> awaySetter) {
        if (set == null) return;
        homeSetter.accept(set.home());
        awaySetter.accept(set.away());
    }

    /** Baslangic zamani: once timestamp (epoch sn), yoksa ISO offset tarih. */
    private Instant resolveStart(VbGameDto dto) {
        if (dto.timestamp() != null && dto.timestamp() > 0) {
            return Instant.ofEpochSecond(dto.timestamp());
        }
        if (dto.date() != null && !dto.date().isBlank()) {
            try {
                return OffsetDateTime.parse(dto.date()).toInstant();
            } catch (Exception ignore) {
                // parse edilemezse maci atla
            }
        }
        return null;
    }
}
