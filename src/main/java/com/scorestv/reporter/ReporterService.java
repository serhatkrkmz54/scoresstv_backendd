package com.scorestv.reporter;

import com.scorestv.common.ApiException;
import com.scorestv.common.PageResponse;
import com.scorestv.common.SlugUtil;
import com.scorestv.football.detail.FixtureDetailCacheEvictor;
import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.football.domain.League;
import com.scorestv.football.domain.LeagueRepository;
import com.scorestv.football.domain.Team;
import com.scorestv.football.domain.TeamRepository;
import com.scorestv.football.live.LiveBroadcaster;
import com.scorestv.game.ScoresCoinService;
import com.scorestv.storage.MinioStorageService;
import com.scorestv.reporter.ReporterDtos.ActionRequest;
import com.scorestv.reporter.ReporterDtos.AdminApplicationView;
import com.scorestv.reporter.ReporterDtos.ApplicationView;
import com.scorestv.reporter.ReporterDtos.ApplyRequest;
import com.scorestv.reporter.ReporterDtos.AssignedLeagueView;
import com.scorestv.reporter.ReporterDtos.CreateFixtureRequest;
import com.scorestv.reporter.ReporterDtos.CreateTeamRequest;
import com.scorestv.reporter.ReporterDtos.FixtureView;
import com.scorestv.reporter.ReporterDtos.MeResponse;
import com.scorestv.reporter.ReporterDtos.ReviewRequest;
import com.scorestv.reporter.ReporterDtos.TeamView;
import com.scorestv.user.User;
import com.scorestv.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Saha Muhabiri programı — API kapsamı dışındaki liglerin kullanıcılar
 * tarafından elle girilmesi.
 *
 * <p><b>İzolasyon garantisi:</b> Manuel varlıklar 900M+ id uzayında ve
 * {@code source=manual} işaretiyle yaşar. Sync API id'siyle upsert ettiği
 * için manuel kayıtlara dokunAMAZ; bu servis de yalnız {@code source=manual}
 * kayıtlara yazar — API verisine dokunamaz (her yazma öncesi doğrulanır).
 */
@Service
public class ReporterService {

    private static final Logger log = LoggerFactory.getLogger(ReporterService.class);

    /** Bitirilen (FT) her manuel maç için muhabire verilen Scores Puanı. */
    static final int POINTS_PER_MATCH = 100;

    private static final ZoneId TZ = ZoneId.of("Europe/Istanbul");

    private final ReporterApplicationRepository applicationRepo;
    private final ReporterAssignmentRepository assignmentRepo;
    private final ReporterLeagueTeamLink leagueTeams;
    private final ManualIdAllocator idAllocator;
    private final LeagueRepository leagueRepo;
    private final TeamRepository teamRepo;
    private final FixtureRepository fixtureRepo;
    private final UserRepository userRepo;
    private final ScoresCoinService coinService;
    private final LiveBroadcaster liveBroadcaster;
    private final FixtureDetailCacheEvictor detailCacheEvictor;
    private final MinioStorageService storage;

    public ReporterService(ReporterApplicationRepository applicationRepo,
                           ReporterAssignmentRepository assignmentRepo,
                           ReporterLeagueTeamLink leagueTeams,
                           ManualIdAllocator idAllocator,
                           LeagueRepository leagueRepo,
                           TeamRepository teamRepo,
                           FixtureRepository fixtureRepo,
                           UserRepository userRepo,
                           ScoresCoinService coinService,
                           LiveBroadcaster liveBroadcaster,
                           FixtureDetailCacheEvictor detailCacheEvictor,
                           MinioStorageService storage) {
        this.applicationRepo = applicationRepo;
        this.assignmentRepo = assignmentRepo;
        this.leagueTeams = leagueTeams;
        this.idAllocator = idAllocator;
        this.leagueRepo = leagueRepo;
        this.teamRepo = teamRepo;
        this.fixtureRepo = fixtureRepo;
        this.userRepo = userRepo;
        this.coinService = coinService;
        this.liveBroadcaster = liveBroadcaster;
        this.detailCacheEvictor = detailCacheEvictor;
        this.storage = storage;
    }

    // ================= Başvuru =================

    @Transactional
    public ApplicationView apply(Long userId, ApplyRequest req) {
        if (applicationRepo.countByUserIdAndStatus(
                userId, ReporterApplication.STATUS_PENDING) >= 3) {
            throw ApiException.badRequest(
                    "En fazla 3 bekleyen başvurunuz olabilir.");
        }
        ReporterApplication a = new ReporterApplication();
        a.setUserId(userId);
        a.setLeagueName(req.leagueName().trim());
        a.setRegion(req.region() != null ? req.region().trim() : null);
        a.setMessage(req.message().trim());
        a = applicationRepo.save(a);
        log.info("Muhabir başvurusu id={} user={} lig='{}'", a.getId(), userId, a.getLeagueName());
        return ApplicationView.from(a);
    }

    @Transactional(readOnly = true)
    public MeResponse me(Long userId) {
        List<AssignedLeagueView> leagues = new ArrayList<>();
        for (ReporterAssignment as : assignmentRepo.findByUserIdAndActiveTrue(userId)) {
            League l = leagueRepo.findById(as.getLeagueId()).orElse(null);
            if (l == null) continue;
            List<Long> teamIds = leagueTeams.teamIds(l.getId());
            leagues.add(new AssignedLeagueView(
                    l.getId(), l.getName(),
                    resolveLogo(l.getLogoKey(), l.getLogoUrl()),
                    teamIds.size(),
                    fixtureRepo.countByLeagueId(l.getId())));
        }
        List<ApplicationView> apps = applicationRepo
                .findTop20ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(ApplicationView::from)
                .toList();
        return new MeResponse(leagues, apps);
    }

    // ================= Panel (EDITOR/ADMIN) =================

    @Transactional(readOnly = true)
    public PageResponse<AdminApplicationView> adminApplications(String status, Pageable pageable) {
        var page = (status == null || status.isBlank())
                ? applicationRepo.findAllByOrderByCreatedAtDesc(pageable)
                : applicationRepo.findByStatusOrderByCreatedAtDesc(status.toUpperCase(), pageable);
        return PageResponse.from(page.map(this::toAdminView));
    }

    /**
     * Başvuruyu onayla: manuel lig oluştur (source=manual, 900M+ id,
     * covered=false → hiçbir sync job'u dokunmaz) + muhabiri ata.
     */
    @Transactional
    public AdminApplicationView approve(Long id, ReviewRequest req, Long reviewerId) {
        ReporterApplication a = applicationRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("Başvuru bulunamadı"));
        if (!ReporterApplication.STATUS_PENDING.equals(a.getStatus())) {
            throw ApiException.conflict("Bu başvuru zaten değerlendirilmiş.");
        }

        League league = new League();
        league.setId(idAllocator.next());
        league.setName(a.getLeagueName());
        league.setNameTr(a.getLeagueName());
        league.setType("League");
        league.setCountryName(a.getRegion() != null ? a.getRegion() : "Türkiye");
        league.setCurrentSeason(Instant.now().atZone(TZ).getYear());
        league.setCovered(false); // sync job'ları bu lige ASLA gitmesin
        league.setSource("manual");
        league = leagueRepo.save(league);

        ReporterAssignment as = new ReporterAssignment();
        as.setUserId(a.getUserId());
        as.setLeagueId(league.getId());
        assignmentRepo.save(as);

        a.setStatus(ReporterApplication.STATUS_APPROVED);
        a.setReviewedBy(reviewerId);
        a.setReviewedAt(Instant.now());
        a.setReviewNote(req != null ? trimOrNull(req.note()) : null);
        a.setLeagueId(league.getId());
        applicationRepo.save(a);
        log.info("Muhabir başvurusu onaylandı id={} → manuel lig id={} user={}",
                id, league.getId(), a.getUserId());
        return toAdminView(a);
    }

    @Transactional
    public AdminApplicationView reject(Long id, ReviewRequest req, Long reviewerId) {
        ReporterApplication a = applicationRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("Başvuru bulunamadı"));
        if (!ReporterApplication.STATUS_PENDING.equals(a.getStatus())) {
            throw ApiException.conflict("Bu başvuru zaten değerlendirilmiş.");
        }
        a.setStatus(ReporterApplication.STATUS_REJECTED);
        a.setReviewedBy(reviewerId);
        a.setReviewedAt(Instant.now());
        a.setReviewNote(req != null ? trimOrNull(req.note()) : null);
        applicationRepo.save(a);
        return toAdminView(a);
    }

    // ================= Muhabir konsolu =================

    @Transactional
    public TeamView createTeam(Long userId, Long leagueId, CreateTeamRequest req) {
        requireAssignment(userId, leagueId);
        Team t = new Team();
        t.setId(idAllocator.next());
        t.setName(req.name().trim());
        t.setNameTr(req.name().trim());
        t.setNational(false);
        t.setCovered(false);
        t.setSource("manual");
        t = teamRepo.save(t);
        leagueTeams.link(leagueId, t.getId());
        return new TeamView(t.getId(), t.getName(), null);
    }

    @Transactional(readOnly = true)
    public List<TeamView> listTeams(Long userId, Long leagueId) {
        requireAssignment(userId, leagueId);
        List<TeamView> out = new ArrayList<>();
        for (Long teamId : leagueTeams.teamIds(leagueId)) {
            teamRepo.findById(teamId).ifPresent(t -> out.add(new TeamView(
                    t.getId(), t.getName(),
                    resolveLogo(t.getLogoKey(), t.getLogoUrl()))));
        }
        out.sort((x, y) -> x.name().compareToIgnoreCase(y.name()));
        return out;
    }

    // ================= Logo yükleme =================

    /** İzin verilen görsel tipleri ve tavan boyut (2MB). */
    private static final java.util.Map<String, String> IMAGE_EXT = java.util.Map.of(
            "image/png", "png", "image/jpeg", "jpg", "image/webp", "webp");
    private static final long MAX_LOGO_BYTES = 2L * 1024 * 1024;

    /** Manuel takım logosu — MinIO'ya benzersiz anahtarla yüklenir (CDN dostu). */
    @Transactional
    public TeamView uploadTeamLogo(Long userId, Long teamId,
                                   byte[] data, String contentType) {
        Team t = teamRepo.findById(teamId)
                .orElseThrow(() -> ApiException.notFound("Takım bulunamadı"));
        requireManual(t.getSource());
        boolean allowed = leagueTeams.leagueIdsOfTeam(teamId).stream().anyMatch(lid ->
                assignmentRepo.findByUserIdAndLeagueIdAndActiveTrue(userId, lid).isPresent());
        if (!allowed) throw ApiException.forbidden("Bu takım için muhabir yetkiniz yok.");
        String key = storeLogo("reporter/teams/" + teamId, data, contentType);
        t.setLogoKey(key);
        t.setLogoUrl(storage.publicUrl(key));
        teamRepo.save(t);
        return new TeamView(t.getId(), t.getName(), t.getLogoUrl());
    }

    /** Manuel lig logosu. */
    @Transactional
    public AssignedLeagueView uploadLeagueLogo(Long userId, Long leagueId,
                                               byte[] data, String contentType) {
        requireAssignment(userId, leagueId);
        League l = leagueRepo.findById(leagueId)
                .orElseThrow(() -> ApiException.notFound("Lig bulunamadı"));
        requireManual(l.getSource());
        String key = storeLogo("reporter/leagues/" + leagueId, data, contentType);
        l.setLogoKey(key);
        l.setLogoUrl(storage.publicUrl(key));
        leagueRepo.save(l);
        return new AssignedLeagueView(l.getId(), l.getName(), l.getLogoUrl(),
                leagueTeams.teamIds(leagueId).size(),
                fixtureRepo.countByLeagueId(leagueId));
    }

    /** Doğrulama + benzersiz anahtarla MinIO yüklemesi; nesne anahtarını döner. */
    private String storeLogo(String prefix, byte[] data, String contentType) {
        if (data == null || data.length == 0) {
            throw ApiException.badRequest("Dosya boş.");
        }
        if (data.length > MAX_LOGO_BYTES) {
            throw ApiException.badRequest("Logo en fazla 2MB olabilir.");
        }
        String ext = IMAGE_EXT.get(contentType);
        if (ext == null) {
            throw ApiException.badRequest("Yalnız PNG, JPG veya WebP yüklenebilir.");
        }
        // Benzersiz anahtar → CDN/istemci cache'i güvenle immutable tutulabilir.
        String key = prefix + "-" + java.util.UUID.randomUUID() + "." + ext;
        storage.upload(key, data, contentType, "public, max-age=31536000, immutable");
        return key;
    }

    /** MinIO anahtarı varsa CDN URL'i, yoksa ham URL. */
    private String resolveLogo(String key, String fallback) {
        if (key != null && !key.isBlank()) return storage.publicUrl(key);
        return fallback;
    }

    @Transactional
    public FixtureView createFixture(Long userId, Long leagueId, CreateFixtureRequest req) {
        requireAssignment(userId, leagueId);
        League league = leagueRepo.findById(leagueId)
                .orElseThrow(() -> ApiException.notFound("Lig bulunamadı"));
        requireManual(league.getSource());
        if (req.homeTeamId().equals(req.awayTeamId())) {
            throw ApiException.badRequest("Ev sahibi ve deplasman aynı olamaz.");
        }
        List<Long> allowed = leagueTeams.teamIds(leagueId);
        if (!allowed.contains(req.homeTeamId()) || !allowed.contains(req.awayTeamId())) {
            throw ApiException.badRequest("Takımlar bu lige ait değil.");
        }
        Team home = teamRepo.findById(req.homeTeamId())
                .orElseThrow(() -> ApiException.notFound("Ev sahibi bulunamadı"));
        Team away = teamRepo.findById(req.awayTeamId())
                .orElseThrow(() -> ApiException.notFound("Deplasman bulunamadı"));

        Fixture f = new Fixture();
        f.setId(idAllocator.next());
        f.setLeague(league);
        f.setSeason(req.kickoffAt().atZone(TZ).getYear());
        f.setRound(trimOrNull(req.round()));
        f.setHomeTeam(home);
        f.setAwayTeam(away);
        f.setKickoffAt(req.kickoffAt());
        f.setStatusShort("NS");
        f.setStatusLong("Not Started");
        f.setSource("manual");
        f = fixtureRepo.save(f);
        log.info("Manuel maç oluşturuldu id={} lig={} {}-{}",
                f.getId(), leagueId, home.getName(), away.getName());
        return toFixtureView(f);
    }

    @Transactional(readOnly = true)
    public List<FixtureView> listFixtures(Long userId, Long leagueId) {
        requireAssignment(userId, leagueId);
        return fixtureRepo.findTop100ByLeagueIdOrderByKickoffAtDesc(leagueId).stream()
                .map(this::toFixtureView)
                .toList();
    }

    /**
     * Canlı konsol aksiyonu — durum makinesi + skor. Her aksiyon sonrası
     * WebSocket yayını (mevcut borular) + detay cache tahliyesi. FT'ye geçişte
     * muhabire {@value #POINTS_PER_MATCH} Scores Puanı verilir (tek sefer;
     * FT'den geri dönüş yok).
     */
    @Transactional
    public FixtureView action(Long userId, Long fixtureId, ActionRequest req) {
        Fixture f = fixtureRepo.findById(fixtureId)
                .orElseThrow(() -> ApiException.notFound("Maç bulunamadı"));
        requireManual(f.getSource());
        requireAssignment(userId, f.getLeague().getId());

        final String action = req.action().trim().toUpperCase();
        final String st = f.getStatusShort();
        switch (action) {
            case "START" -> {
                requireStatus(st, "NS", "PST");
                f.setStatusShort("1H");
                f.setStatusLong("First Half");
                f.setElapsed(1);
                f.setHomeGoals(0);
                f.setAwayGoals(0);
                startPhase(f, 1); // otomatik dakika bu andan işler
            }
            case "SET_SCORE" -> {
                requireLive(st);
                if (req.homeGoals() == null || req.awayGoals() == null
                        || req.homeGoals() < 0 || req.awayGoals() < 0) {
                    throw ApiException.badRequest("Geçerli skor girin.");
                }
                f.setHomeGoals(req.homeGoals());
                f.setAwayGoals(req.awayGoals());
            }
            case "HT" -> {
                requireStatus(st, "1H");
                f.setStatusShort("HT");
                f.setStatusLong("Halftime");
                f.setElapsed(45);
                stopClock(f);
                f.setScoreHtHome(nz(f.getHomeGoals()));
                f.setScoreHtAway(nz(f.getAwayGoals()));
            }
            case "SECOND_HALF" -> {
                requireStatus(st, "HT");
                f.setStatusShort("2H");
                f.setStatusLong("Second Half");
                f.setElapsed(46);
                startPhase(f, 46); // 46'dan itibaren işler
            }
            // Hakemin ilan ettiği uzatma (+dk) — saat 45+N / 90+N'de tutulur.
            case "SET_STOPPAGE" -> {
                requireStatus(st, "1H", "2H", "ET");
                if (req.minute() != null && (req.minute() < 0 || req.minute() > 15)) {
                    throw ApiException.badRequest("Uzatma 0-15 dk olabilir.");
                }
                f.setManualStoppage(
                        req.minute() == null || req.minute() == 0 ? null : req.minute());
            }
            // Normal süre bitti, uzatma devreleri arası mola (ET öncesi/arası).
            case "BREAK" -> {
                requireStatus(st, "2H", "ET");
                f.setStatusShort("BT");
                f.setStatusLong("Break Time");
                // 2H sonu → 90'da durur; ET ilk devre sonu → 105'te durur.
                f.setElapsed("2H".equals(st) ? 90
                        : (f.getElapsed() != null && f.getElapsed() > 105 ? 120 : 105));
                stopClock(f);
            }
            // Uzatma devresi başlat (BT'den): ilk devre 91', ikinci devre 106'.
            case "EXTRA_TIME" -> {
                requireStatus(st, "BT");
                int base = f.getElapsed() != null && f.getElapsed() >= 105 ? 106 : 91;
                f.setStatusShort("ET");
                f.setStatusLong("Extra Time");
                f.setElapsed(base);
                startPhase(f, base);
            }
            // Penaltı atışlarına geçiş (90' beraberliğinden direkt de olabilir).
            case "PENALTIES" -> {
                requireStatus(st, "2H", "BT", "ET");
                f.setStatusShort("P");
                f.setStatusLong("Penalty In Progress");
                f.setElapsed(f.getElapsed() != null && f.getElapsed() > 90 ? 120 : 90);
                stopClock(f);
                if (f.getScorePenHome() == null) f.setScorePenHome(0);
                if (f.getScorePenAway() == null) f.setScorePenAway(0);
            }
            // Penaltı atışları skoru (seri ilerledikçe güncellenir).
            case "SET_PEN_SCORE" -> {
                requireStatus(st, "P");
                if (req.homeGoals() == null || req.awayGoals() == null
                        || req.homeGoals() < 0 || req.awayGoals() < 0
                        || req.homeGoals() > 30 || req.awayGoals() > 30) {
                    throw ApiException.badRequest("Geçerli penaltı skoru girin.");
                }
                f.setScorePenHome(req.homeGoals());
                f.setScorePenAway(req.awayGoals());
            }
            // Maç durdu (sakatlık, olay, hava...) — saat donar, skor korunur.
            case "PAUSE" -> {
                requireStatus(st, "1H", "2H", "ET");
                f.setStatusShort("INT");
                f.setStatusLong("Match Interrupted");
                f.setManualPhaseStart(null); // saat donar; elapsed olduğu yerde kalır
            }
            // Duraklatmadan devam — kaldığı dakikadan (uzatma dahil) sürer.
            case "RESUME" -> {
                requireStatus(st, "INT", "SUSP");
                int el = nz(f.getElapsed());
                String phase = el <= 45 ? "1H" : el <= 90 ? "2H" : "ET";
                f.setStatusShort(phase);
                f.setStatusLong(switch (phase) {
                    case "1H" -> "First Half";
                    case "2H" -> "Second Half";
                    default -> "Extra Time";
                });
                // Uzatma dakikasındayken durduysa (45+3 gibi) oradan devam etsin.
                startPhase(f, el + (f.getStatusExtra() != null ? f.getStatusExtra() : 0));
            }
            case "SET_ELAPSED" -> {
                requireLive(st);
                if (req.minute() == null || req.minute() < 0 || req.minute() > 130) {
                    throw ApiException.badRequest("Geçerli dakika girin (0-130).");
                }
                f.setElapsed(req.minute());
                // Saat işliyorsa taban da kaysın; yoksa job eski tabandan ezerdi.
                if (f.getManualPhaseStart() != null) {
                    startPhase(f, req.minute());
                }
            }
            case "FINISH" -> {
                requireStatus(st, "1H", "HT", "2H", "ET", "BT", "P", "INT", "SUSP");
                boolean afterEt = "ET".equals(st) || "BT".equals(st)
                        || (f.getElapsed() != null && f.getElapsed() > 90);
                if ("P".equals(st)) {
                    f.setStatusShort("PEN");
                    f.setStatusLong("Match Finished After Penalty");
                    f.setElapsed(120);
                } else if (afterEt) {
                    f.setStatusShort("AET");
                    f.setStatusLong("Match Finished After Extra Time");
                    f.setElapsed(120);
                } else {
                    f.setStatusShort("FT");
                    f.setStatusLong("Match Finished");
                    f.setElapsed(90);
                }
                stopClock(f);
                f.setScoreFtHome(nz(f.getHomeGoals()));
                f.setScoreFtAway(nz(f.getAwayGoals()));
            }
            // Maç tatil edildi (yarıda kaldı, devam etmeyecek) — puan verilmez.
            case "ABANDON" -> {
                requireStatus(st, "1H", "HT", "2H", "ET", "BT", "P", "INT", "SUSP");
                f.setStatusShort("ABD");
                f.setStatusLong("Match Abandoned");
                stopClock(f);
            }
            case "POSTPONE" -> {
                requireStatus(st, "NS");
                f.setStatusShort("PST");
                f.setStatusLong("Match Postponed");
            }
            case "CANCEL" -> {
                requireStatus(st, "NS", "PST");
                f.setStatusShort("CANC");
                f.setStatusLong("Match Cancelled");
            }
            default -> throw ApiException.badRequest("Bilinmeyen aksiyon: " + action);
        }
        f.setLastSyncedAt(Instant.now()); // frontend canlı saat referansı
        f = fixtureRepo.save(f);

        // Canlı yayın + detay cache tahliyesi — API maçlarıyla aynı borular.
        try {
            liveBroadcaster.broadcast(f);
        } catch (RuntimeException ex) {
            log.warn("Manuel maç yayını başarısız id={}: {}", fixtureId, ex.getMessage());
        }
        detailCacheEvictor.evictAll(fixtureId);

        if ("FINISH".equals(action)) {
            coinService.grant(userId, POINTS_PER_MATCH,
                    "REPORTER_MATCH", "FIXTURE", fixtureId);
            log.info("Muhabir puanı: user={} maç={} +{}", userId, fixtureId, POINTS_PER_MATCH);
        }
        return toFixtureView(f);
    }

    // ================= Yardımcılar =================

    private void requireAssignment(Long userId, Long leagueId) {
        assignmentRepo.findByUserIdAndLeagueIdAndActiveTrue(userId, leagueId)
                .orElseThrow(() -> ApiException.forbidden("Bu lig için muhabir yetkiniz yok."));
    }

    /** MUTLAK korkuluk: yalnız source=manual kayıtlara yazılabilir. */
    private static void requireManual(String source) {
        if (!"manual".equals(source)) {
            throw ApiException.forbidden("API kaynaklı veriler düzenlenemez.");
        }
    }

    /** Canlı/ara faz kümesi — skor ve dakika bu fazlarda düzenlenebilir. */
    private static final java.util.Set<String> LIVE_STATUSES =
            java.util.Set.of("1H", "HT", "2H", "ET", "BT", "P", "INT", "SUSP");

    private static void requireLive(String st) {
        if (!LIVE_STATUSES.contains(st)) {
            throw ApiException.badRequest("Bu işlem yalnız canlı maçta yapılabilir.");
        }
    }

    /** Saati başlat: taban dakika + şu an; uzatma sayacı ve ilanı sıfırlanır. */
    private static void startPhase(Fixture f, int baseMinute) {
        f.setManualPhaseBase(baseMinute);
        f.setManualPhaseStart(Instant.now());
        f.setStatusExtra(null);
        f.setManualStoppage(null);
    }

    /** Saati durdur: job artık işletmez; uzatma göstergesi temizlenir. */
    private static void stopClock(Fixture f) {
        f.setManualPhaseStart(null);
        f.setManualPhaseBase(null);
        f.setStatusExtra(null);
        f.setManualStoppage(null);
    }

    private static void requireStatus(String current, String... allowed) {
        for (String a : allowed) {
            if (a.equals(current)) return;
        }
        throw ApiException.badRequest("Maç durumu buna izin vermiyor (mevcut: " + current + ").");
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private FixtureView toFixtureView(Fixture f) {
        return staticFixtureView(f);
    }

    /** Paket içi ortak dönüşüm ({@link ReporterMatchDataService} de kullanır). */
    static FixtureView staticFixtureView(Fixture f) {
        String slug = SlugUtil.fixtureSlug(
                f.getHomeTeam().getName(), f.getAwayTeam().getName(), f.getId());
        return new FixtureView(f.getId(), slug, f.getKickoffAt(), f.getStatusShort(),
                f.getElapsed(), f.getStatusExtra(), f.getHomeGoals(), f.getAwayGoals(),
                f.getScorePenHome(), f.getScorePenAway(),
                f.getHomeTeam().getId(), f.getHomeTeam().getName(),
                f.getAwayTeam().getId(), f.getAwayTeam().getName(), f.getRound());
    }

    private AdminApplicationView toAdminView(ReporterApplication a) {
        User u = userRepo.findById(a.getUserId()).orElse(null);
        return new AdminApplicationView(a.getId(), a.getLeagueName(), a.getRegion(),
                a.getMessage(), a.getStatus(), a.getReviewNote(), a.getLeagueId(),
                a.getCreatedAt(), a.getReviewedAt(), a.getUserId(),
                u != null ? u.getEmail() : null,
                u != null ? u.getDisplayName() : null);
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
