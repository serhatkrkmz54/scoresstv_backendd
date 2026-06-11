package com.scorestv.basketball;

import com.scorestv.basketball.domain.BasketballGame;
import com.scorestv.basketball.domain.BasketballGameRepository;
import com.scorestv.basketball.domain.BasketballLeague;
import com.scorestv.basketball.domain.BasketballLeagueRepository;
import com.scorestv.basketball.domain.BasketballTeam;
import com.scorestv.basketball.domain.BasketballTeamRepository;
import com.scorestv.basketball.notify.BasketballNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * API-Basketball {@code /games} verisini DB'ye senkronlar (upsert).
 * Football sync'inden bağımsız — kendi client'ı, kendi tabloları.
 */
@Service
public class BasketballSyncService {

    private static final Logger log = LoggerFactory.getLogger(BasketballSyncService.class);

    private static final ParameterizedTypeReference<BasketballApiResponse<BkGameDto>> GAMES_TYPE =
            new ParameterizedTypeReference<>() {};

    /** Canlı (in-play) basketbol durum kodları. */
    private static final Set<String> LIVE_STATUSES =
            Set.of("Q1", "Q2", "Q3", "Q4", "OT", "BT", "HT");
    /** Bitmiş durum kodları. */
    private static final Set<String> FINISHED_STATUSES = Set.of("FT", "AOT");

    private final BasketballApiClient client;
    private final BasketballProperties props;
    private final BasketballGameRepository gameRepo;
    private final BasketballLeagueRepository leagueRepo;
    private final BasketballTeamRepository teamRepo;
    private final BasketballNotificationService notifications;

    public BasketballSyncService(BasketballApiClient client,
                                 BasketballProperties props,
                                 BasketballGameRepository gameRepo,
                                 BasketballLeagueRepository leagueRepo,
                                 BasketballTeamRepository teamRepo,
                                 BasketballNotificationService notifications) {
        this.client = client;
        this.props = props;
        this.gameRepo = gameRepo;
        this.leagueRepo = leagueRepo;
        this.teamRepo = teamRepo;
        this.notifications = notifications;
    }

    /** Bir takvim gününün maçlarını çek + upsert. Döner: yazılan maç sayısı. */
    @Transactional
    public int syncDate(LocalDate date) {
        var games = fetch(Map.of(
                "date", date.toString(),
                "timezone", props.timezone()));
        // Fikstür backfill (geçmiş/gelecek) — bildirim YOK (notify=false).
        int n = upsertAll(games, false);
        log.info("Basketbol fikstür sync ({}): {} maç", date, n);
        return n;
    }

    /**
     * Canlı skor güncellemesi — API-Basketball'da {@code /games?live=all} YOK;
     * canlı skor BUGÜNÜN maçlarını çekerek alınır (API her 15 sn günceller,
     * in-play maçların statüsü Q1-Q4/OT/HT olur ve skorları taze gelir).
     */
    @Transactional
    public int syncLive() {
        LocalDate today = LocalDate.now(ZoneId.of(props.timezone()));
        var games = fetch(Map.of(
                "date", today.toString(),
                "timezone", props.timezone()));
        // Canlı (bugün) sync — durum geçişlerinde bildirim AÇIK (notify=true).
        int n = upsertAll(games, true);
        if (n > 0) log.debug("Basketbol canlı (bugün) sync: {} maç", n);
        return n;
    }

    /**
     * Kayan pencere tarihleri: bugünden {@code windowDaysBefore} geriye,
     * {@code windowDaysAfter} ileriye. Bugüne göre hesaplandığı için pencere
     * her gün kendiliğinden ileri kayar (geçmiş düşer, yeni gün eklenir).
     */
    public List<LocalDate> windowDates() {
        LocalDate today = LocalDate.now(ZoneId.of(props.timezone()));
        List<LocalDate> dates = new ArrayList<>();
        for (int off = -props.windowDaysBefore(); off <= props.windowDaysAfter(); off++) {
            dates.add(today.plusDays(off));
        }
        return dates;
    }

    private List<BkGameDto> fetch(Map<String, ?> params) {
        try {
            return client.get("/games", params, GAMES_TYPE).responseOrEmpty();
        } catch (Exception e) {
            log.warn("Basketbol /games çağrısı başarısız {}: {}", params, e.toString());
            return List.of();
        }
    }

    private int upsertAll(List<BkGameDto> games, boolean notify) {
        int n = 0;
        for (BkGameDto g : games) {
            if (upsert(g, notify)) n++;
        }
        return n;
    }

    private boolean upsert(BkGameDto dto, boolean notify) {
        if (dto.id() == null || dto.league() == null || dto.teams() == null
                || dto.teams().home() == null || dto.teams().away() == null) {
            return false;
        }
        BasketballLeague league = upsertLeague(dto.league(), dto.country());
        BasketballTeam home = upsertTeam(dto.teams().home());
        BasketballTeam away = upsertTeam(dto.teams().away());
        if (league == null || home == null || away == null) return false;

        Instant startAt = resolveStart(dto);
        if (startAt == null) return false;

        BasketballGame game = gameRepo.findById(dto.id()).orElseGet(BasketballGame::new);
        // Durum geçişi tespiti için ESKİ statüyü (overwrite'tan önce) yakala.
        final String oldStatus = game.getStatusShort();

        game.setId(dto.id());
        game.setLeague(league);
        game.setSeason(dto.league().season());
        game.setHomeTeam(home);
        game.setAwayTeam(away);
        game.setStartAt(startAt);
        game.setStage(dto.stage());
        game.setWeek(dto.week());
        if (dto.status() != null) {
            game.setStatusShort(dto.status().shortName());
            game.setStatusLong(dto.status().longName());
            game.setTimer(dto.status().timer());
        }
        applyScores(game, dto.scores());

        // Bildirim: yalnızca canlı sync'te (notify=true) durum geçişlerini yay.
        // Flag'ler entity'de tutulur → save ile kalıcı (idempotent, restart-safe).
        if (notify) {
            detectAndNotify(game, oldStatus, home, away);
        }

        gameRepo.save(game);
        return true;
    }

    /**
     * Eski→yeni statü geçişini analiz edip ilgili FCM push'larını tetikler.
     * Flag/sayaç entity üzerinde güncellenir (save ile persist edilir):
     * <ul>
     *   <li>NS/null → canlı : maç başladı (bir kez)</li>
     *   <li>çeyrek ilerledi : sonu geçilen her çeyrek için "çeyrek bitti" (skorlu)</li>
     *   <li>→ FT/AOT : maç bitti (bir kez)</li>
     * </ul>
     */
    private void detectAndNotify(BasketballGame game, String oldStatus,
                                 BasketballTeam home, BasketballTeam away) {
        final String newStatus = game.getStatusShort();
        if (newStatus == null) return;

        // Backfill koruması: maçı İLK kez (önceki statü yok) bitmiş görüyorsak
        // geçmiş tüm çeyrekleri+finali topluca atma — sadece durumu işaretle.
        if (oldStatus == null && FINISHED_STATUSES.contains(newStatus)) {
            game.setNotifiedStart(true);
            game.setNotifiedFinal(true);
            game.setLastNotifiedPeriod(4);
            return;
        }

        final Long gameId = game.getId();
        final String homeName = home.getNameTr() != null && !home.getNameTr().isBlank()
                ? home.getNameTr() : home.getName();
        final String awayName = away.getNameTr() != null && !away.getNameTr().isBlank()
                ? away.getNameTr() : away.getName();

        // oldStatus null olabilir (yeni maç) — Set.of(...).contains(null) NPE
        // fırlatır, bu yüzden null-güvenli yerel değişkenler.
        final boolean oldLive = oldStatus != null && LIVE_STATUSES.contains(oldStatus);
        final boolean oldFinished =
                oldStatus != null && FINISHED_STATUSES.contains(oldStatus);

        // 1) Maç başladı — NS/null/bilinmeyen → canlı.
        if (!game.isNotifiedStart()
                && LIVE_STATUSES.contains(newStatus)
                && !oldLive
                && !oldFinished) {
            game.setNotifiedStart(true);
            notifications.dispatchStart(gameId, homeName, awayName);
        }

        // 2) Çeyrek sonu — sonu geçilen her çeyrek için (monotonik sayaç).
        int target = completedQuarters(newStatus);
        if (target > game.getLastNotifiedPeriod()) {
            for (int q = game.getLastNotifiedPeriod() + 1; q <= target && q <= 4; q++) {
                notifications.dispatchPeriodEnd(gameId, homeName, awayName, q,
                        game.getHomeTotal(), game.getAwayTotal());
            }
            game.setLastNotifiedPeriod(Math.min(target, 4));
        }

        // 3) Maç bitti.
        if (!game.isNotifiedFinal() && FINISHED_STATUSES.contains(newStatus)) {
            game.setNotifiedFinal(true);
            notifications.dispatchFinal(gameId, homeName, awayName,
                    game.getHomeTotal(), game.getAwayTotal());
        }
    }

    /**
     * Statüye göre "şu ana kadar SONU GELMİŞ" çeyrek sayısı (monotonik):
     * Q1=0, Q2=1, HT=2, Q3=2, Q4=3, OT/FT/AOT=4. Belirsiz (NS/BT) = 0 → ilerleyen
     * çeyrek tetikler. Sayaç entity'de saklandığı için BT/HT jitter'ı düşürmez.
     */
    private static int completedQuarters(String status) {
        if (status == null) return 0;
        return switch (status) {
            case "Q2" -> 1;
            case "HT", "Q3" -> 2;
            case "Q4" -> 3;
            case "OT", "FT", "AOT" -> 4;
            default -> 0; // NS, Q1, BT, POST, CANC...
        };
    }

    private BasketballLeague upsertLeague(BkGameDto.League l, BkGameDto.Country c) {
        if (l.id() == null) return null;
        BasketballLeague e = leagueRepo.findById(l.id()).orElseGet(BasketballLeague::new);
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

    private BasketballTeam upsertTeam(BkGameDto.TeamRef t) {
        if (t.id() == null) return null;
        BasketballTeam e = teamRepo.findById(t.id()).orElseGet(BasketballTeam::new);
        e.setId(t.id());
        e.setName(t.name() != null ? t.name() : ("Takım #" + t.id()));
        e.setLogo(t.logo());
        return teamRepo.save(e);
    }

    private void applyScores(BasketballGame g, BkGameDto.Scores s) {
        if (s == null) return;
        if (s.home() != null) {
            var h = s.home();
            g.setHomeQ1(h.q1());
            g.setHomeQ2(h.q2());
            g.setHomeQ3(h.q3());
            g.setHomeQ4(h.q4());
            g.setHomeOt(h.overTime());
            g.setHomeTotal(h.total());
        }
        if (s.away() != null) {
            var a = s.away();
            g.setAwayQ1(a.q1());
            g.setAwayQ2(a.q2());
            g.setAwayQ3(a.q3());
            g.setAwayQ4(a.q4());
            g.setAwayOt(a.overTime());
            g.setAwayTotal(a.total());
        }
    }

    /** Başlangıç zamanı: önce timestamp (epoch sn), yoksa ISO offset tarih. */
    private Instant resolveStart(BkGameDto dto) {
        if (dto.timestamp() != null && dto.timestamp() > 0) {
            return Instant.ofEpochSecond(dto.timestamp());
        }
        if (dto.date() != null && !dto.date().isBlank()) {
            try {
                return OffsetDateTime.parse(dto.date()).toInstant();
            } catch (Exception ignore) {
                // parse edilemezse maçı atla
            }
        }
        return null;
    }
}
