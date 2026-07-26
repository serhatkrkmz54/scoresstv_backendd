package com.scorestv.reporter;

import com.scorestv.broadcasts.domain.MatchBroadcast;
import com.scorestv.broadcasts.domain.MatchBroadcastRepository;
import com.scorestv.broadcasts.domain.TvChannel;
import com.scorestv.broadcasts.domain.TvChannelRepository;
import com.scorestv.common.ApiException;
import com.scorestv.football.detail.FixtureDetailCacheEvictor;
import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureEvent;
import com.scorestv.football.domain.FixtureEventRepository;
import com.scorestv.football.domain.FixtureLineup;
import com.scorestv.football.domain.FixtureLineupPlayer;
import com.scorestv.football.domain.FixtureLineupPlayerRepository;
import com.scorestv.football.domain.FixtureLineupRepository;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.football.domain.Team;
import com.scorestv.football.live.EventBroadcaster;
import com.scorestv.football.live.LiveBroadcaster;
import com.scorestv.reporter.ReporterDtos.BroadcastsView;
import com.scorestv.reporter.ReporterDtos.EventRequest;
import com.scorestv.reporter.ReporterDtos.EventResult;
import com.scorestv.reporter.ReporterDtos.EventView;
import com.scorestv.reporter.ReporterDtos.LineupPlayerInput;
import com.scorestv.reporter.ReporterDtos.LineupRequest;
import com.scorestv.reporter.ReporterDtos.LineupView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Muhabir maç verileri — olaylar (gol/kart/değişiklik/VAR), kadrolar, yayın
 * kanalları. Hepsi FUTBOLUN MEVCUT tablolarına yazar (fixture_events,
 * fixture_lineups, match_broadcasts) — maç sayfası API maçlarıyla birebir
 * aynı şekilde render eder; olaylar WebSocket'ten canlı yayılır.
 *
 * <p>Korkuluk: yalnız source=manual maçlar + aktif lig ataması.
 */
@Service
public class ReporterMatchDataService {

    private static final Logger log = LoggerFactory.getLogger(ReporterMatchDataService.class);

    private final FixtureRepository fixtureRepo;
    private final FixtureEventRepository eventRepo;
    private final FixtureLineupRepository lineupRepo;
    private final FixtureLineupPlayerRepository lineupPlayerRepo;
    private final TvChannelRepository channelRepo;
    private final MatchBroadcastRepository broadcastRepo;
    private final ReporterAssignmentRepository assignmentRepo;
    private final LiveBroadcaster liveBroadcaster;
    private final EventBroadcaster eventBroadcaster;
    private final FixtureDetailCacheEvictor detailCacheEvictor;

    public ReporterMatchDataService(FixtureRepository fixtureRepo,
                                    FixtureEventRepository eventRepo,
                                    FixtureLineupRepository lineupRepo,
                                    FixtureLineupPlayerRepository lineupPlayerRepo,
                                    TvChannelRepository channelRepo,
                                    MatchBroadcastRepository broadcastRepo,
                                    ReporterAssignmentRepository assignmentRepo,
                                    LiveBroadcaster liveBroadcaster,
                                    EventBroadcaster eventBroadcaster,
                                    FixtureDetailCacheEvictor detailCacheEvictor) {
        this.fixtureRepo = fixtureRepo;
        this.eventRepo = eventRepo;
        this.lineupRepo = lineupRepo;
        this.lineupPlayerRepo = lineupPlayerRepo;
        this.channelRepo = channelRepo;
        this.broadcastRepo = broadcastRepo;
        this.assignmentRepo = assignmentRepo;
        this.liveBroadcaster = liveBroadcaster;
        this.eventBroadcaster = eventBroadcaster;
        this.detailCacheEvictor = detailCacheEvictor;
    }

    // ================= Olaylar =================

    @Transactional(readOnly = true)
    public List<EventView> listEvents(Long userId, Long fixtureId) {
        guard(userId, fixtureId);
        return eventRepo.findByFixtureIdOrderByTimeElapsedAsc(fixtureId).stream()
                .map(ReporterMatchDataService::toEventView)
                .toList();
    }

    /**
     * Olay ekle. Gol tipleri skoru da günceller; olay hem canlı skor hem
     * olay topic'inden yayılır. OWN_GOAL'de olay (API kuralı gereği) golün
     * YAZILDIĞI takıma işlenir.
     */
    @Transactional
    public EventResult addEvent(Long userId, Long fixtureId, EventRequest req) {
        Fixture f = guard(userId, fixtureId);
        final boolean home = "HOME".equalsIgnoreCase(req.team());
        if (!home && !"AWAY".equalsIgnoreCase(req.team())) {
            throw ApiException.badRequest("team HOME veya AWAY olmalı.");
        }
        final String type = req.type().trim().toUpperCase();

        String evType;
        String evDetail;
        int scoreDelta = 0;       // olay takımının hanesine
        boolean creditOpponent = false; // own goal: skor + olay karşı takıma yazılır
        switch (type) {
            case "GOAL" -> { evType = "Goal"; evDetail = "Normal Goal"; scoreDelta = 1; }
            case "PEN_GOAL" -> { evType = "Goal"; evDetail = "Penalty"; scoreDelta = 1; }
            case "OWN_GOAL" -> { evType = "Goal"; evDetail = "Own Goal"; scoreDelta = 1; creditOpponent = true; }
            case "PEN_MISS" -> { evType = "Goal"; evDetail = "Missed Penalty"; }
            case "YELLOW" -> { evType = "Card"; evDetail = "Yellow Card"; }
            case "RED" -> { evType = "Card"; evDetail = "Red Card"; }
            case "SUB" -> { evType = "subst"; evDetail = "Substitution"; }
            case "VAR_GOAL_CANCELLED" -> { evType = "Var"; evDetail = "Goal cancelled"; }
            case "VAR_PEN_CONFIRMED" -> { evType = "Var"; evDetail = "Penalty confirmed"; }
            default -> throw ApiException.badRequest("Bilinmeyen olay türü: " + type);
        }

        // Olayın takımı: own goal'de golün yazıldığı (karşı) takım.
        final boolean creditHome = creditOpponent != home;
        Team eventTeam = creditHome ? f.getHomeTeam() : f.getAwayTeam();

        FixtureEvent ev = new FixtureEvent();
        ev.setFixture(f);
        ev.setTeam(eventTeam);
        ev.setTimeElapsed(req.minute() != null ? req.minute()
                : (f.getElapsed() != null ? f.getElapsed() : 0));
        ev.setTimeExtra(req.extra());
        ev.setType(evType);
        ev.setDetail(evDetail);
        ev.setPlayerName(trimOrNull(req.playerName()));
        ev.setAssistName(trimOrNull(req.assistName()));
        ev = eventRepo.save(ev);

        if (scoreDelta > 0) {
            if (creditHome) f.setHomeGoals(nz(f.getHomeGoals()) + scoreDelta);
            else f.setAwayGoals(nz(f.getAwayGoals()) + scoreDelta);
            f.setLastSyncedAt(Instant.now());
            f = fixtureRepo.save(f);
        }

        publish(f, ev);
        log.info("Muhabir olayı: fixture={} {} {} dk={} oyuncu={}",
                fixtureId, evType, evDetail, ev.getTimeElapsed(), ev.getPlayerName());
        return new EventResult(toEventView(ev), ReporterService.staticFixtureView(f));
    }

    /** Olay sil (yanlış giriş). Gol olayıysa skor geri alınır. */
    @Transactional
    public ReporterDtos.FixtureView deleteEvent(Long userId, Long fixtureId, Long eventId) {
        Fixture f = guard(userId, fixtureId);
        FixtureEvent ev = eventRepo.findById(eventId)
                .orElseThrow(() -> ApiException.notFound("Olay bulunamadı"));
        if (!ev.getFixture().getId().equals(fixtureId)) {
            throw ApiException.badRequest("Olay bu maça ait değil.");
        }
        boolean scoringGoal = "Goal".equals(ev.getType())
                && ("Normal Goal".equals(ev.getDetail())
                || "Penalty".equals(ev.getDetail())
                || "Own Goal".equals(ev.getDetail()));
        if (scoringGoal && ev.getTeam() != null) {
            boolean creditHome = ev.getTeam().getId().equals(f.getHomeTeam().getId());
            if (creditHome) f.setHomeGoals(Math.max(0, nz(f.getHomeGoals()) - 1));
            else f.setAwayGoals(Math.max(0, nz(f.getAwayGoals()) - 1));
            f.setLastSyncedAt(Instant.now());
            f = fixtureRepo.save(f);
        }
        eventRepo.delete(ev);
        publish(f, null);
        log.info("Muhabir olayı silindi: fixture={} event={}", fixtureId, eventId);
        return ReporterService.staticFixtureView(f);
    }

    // ================= Kadro =================

    @Transactional(readOnly = true)
    public LineupView getLineup(Long userId, Long fixtureId, String side) {
        Fixture f = guard(userId, fixtureId);
        Team team = teamOf(f, side);
        FixtureLineup lineup = lineupRepo
                .findByFixtureIdAndTeamId(fixtureId, team.getId()).orElse(null);
        if (lineup == null) return new LineupView(null, null, List.of());
        List<LineupPlayerInput> players = lineupPlayerRepo
                .findByLineupIdOrderBySortOrderAsc(lineup.getId()).stream()
                .map(p -> new LineupPlayerInput(
                        p.getPlayerName(), p.getJerseyNumber(),
                        p.getPosition(), p.isSubstitute()))
                .toList();
        return new LineupView(lineup.getFormation(), lineup.getCoachName(), players);
    }

    /** Kadroyu kaydet — mevcut varsa tamamen değiştirilir. */
    @Transactional
    public LineupView saveLineup(Long userId, Long fixtureId, String side, LineupRequest req) {
        Fixture f = guard(userId, fixtureId);
        Team team = teamOf(f, side);
        FixtureLineup lineup = lineupRepo
                .findByFixtureIdAndTeamId(fixtureId, team.getId())
                .orElseGet(() -> {
                    FixtureLineup l = new FixtureLineup();
                    l.setFixture(f);
                    l.setTeam(team);
                    l.setAnnouncedAt(Instant.now());
                    return l;
                });
        lineup.setFormation(trimOrNull(req.formation()));
        lineup.setCoachName(trimOrNull(req.coachName()));
        lineup = lineupRepo.save(lineup);
        lineupPlayerRepo.deleteByLineupId(lineup.getId());
        int order = 0;
        for (LineupPlayerInput p : req.players()) {
            FixtureLineupPlayer lp = new FixtureLineupPlayer();
            lp.setLineup(lineup);
            lp.setPlayerName(p.name().trim());
            lp.setJerseyNumber(p.number());
            lp.setPosition(trimOrNull(p.position()));
            lp.setSubstitute(p.substitute());
            lp.setSortOrder(order++);
            lineupPlayerRepo.save(lp);
        }
        detailCacheEvictor.evictAll(fixtureId);
        log.info("Muhabir kadrosu kaydedildi: fixture={} side={} oyuncu={}",
                fixtureId, side, req.players().size());
        return getLineup(userId, fixtureId, side);
    }

    // ================= Yayın =================

    @Transactional(readOnly = true)
    public BroadcastsView getBroadcasts(Long userId, Long fixtureId) {
        guard(userId, fixtureId);
        List<String> channels = broadcastRepo
                .findByFixtureIdOrderBySortOrderAsc(fixtureId).stream()
                .map(b -> b.getChannel() != null ? b.getChannel().getName() : null)
                .filter(n -> n != null && !n.isBlank())
                .toList();
        return new BroadcastsView(channels);
    }

    /** Yayın kanallarını kaydet — tam liste, mevcutların yerine geçer. */
    @Transactional
    public BroadcastsView saveBroadcasts(Long userId, Long fixtureId, List<String> channels) {
        Fixture f = guard(userId, fixtureId);
        broadcastRepo.deleteAll(broadcastRepo.findByFixtureIdOrderBySortOrderAsc(fixtureId));
        int order = 0;
        for (String raw : channels) {
            if (raw == null || raw.isBlank()) continue;
            String name = raw.trim();
            TvChannel channel = channelRepo.findFirstByNameIgnoreCase(name)
                    .orElseGet(() -> {
                        TvChannel c = new TvChannel();
                        c.setName(name);
                        c.setNameTr(name);
                        c.setCountryCode("TR");
                        c.setActive(true);
                        return channelRepo.save(c);
                    });
            MatchBroadcast mb = new MatchBroadcast();
            mb.setFixture(f);
            mb.setChannel(channel);
            mb.setCountryCode("TR");
            mb.setSortOrder(order++);
            broadcastRepo.save(mb);
        }
        detailCacheEvictor.evictAll(fixtureId);
        return getBroadcasts(userId, fixtureId);
    }

    // ================= Yardımcılar =================

    /** Yetki + manual korkuluğu; fixture'ı döner. */
    private Fixture guard(Long userId, Long fixtureId) {
        Fixture f = fixtureRepo.findById(fixtureId)
                .orElseThrow(() -> ApiException.notFound("Maç bulunamadı"));
        if (!"manual".equals(f.getSource())) {
            throw ApiException.forbidden("API kaynaklı veriler düzenlenemez.");
        }
        assignmentRepo.findByUserIdAndLeagueIdAndActiveTrue(userId, f.getLeague().getId())
                .orElseThrow(() -> ApiException.forbidden("Bu lig için muhabir yetkiniz yok."));
        return f;
    }

    private static Team teamOf(Fixture f, String side) {
        if ("home".equalsIgnoreCase(side)) return f.getHomeTeam();
        if ("away".equalsIgnoreCase(side)) return f.getAwayTeam();
        throw ApiException.badRequest("side home veya away olmalı.");
    }

    /** Skor/olay değişimini canlı topic'lere yay + detay cache'ini tazele. */
    private void publish(Fixture f, FixtureEvent ev) {
        try {
            liveBroadcaster.broadcast(f);
            if (ev != null) eventBroadcaster.broadcast(f.getId(), ev);
        } catch (RuntimeException ex) {
            log.warn("Muhabir olay yayını başarısız fixture={}: {}", f.getId(), ex.getMessage());
        }
        detailCacheEvictor.evictAll(f.getId());
    }

    private static EventView toEventView(FixtureEvent e) {
        return new EventView(e.getId(), e.getTimeElapsed(), e.getTimeExtra(),
                e.getType(), e.getDetail(),
                e.getTeam() != null ? e.getTeam().getId() : null,
                e.getTeam() != null ? e.getTeam().getName() : null,
                e.getPlayerName(), e.getAssistName());
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
