package com.scorestv.mobile.notify;

import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureEvent;
import com.scorestv.football.domain.FixtureEventRepository;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.mobile.domain.DeviceMatchSubscription;
import com.scorestv.mobile.domain.DeviceMatchSubscriptionRepository;
import com.scorestv.mobile.domain.UserNotificationPref;
import com.scorestv.mobile.domain.UserNotificationPrefRepository;
import com.scorestv.mobile.fcm.FcmMessagingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Canli mac olaylarini (gol, kart, penalti, kickoff, final) ilgili kullanicilara
 * FCM push notification olarak gonderir.
 *
 * <p><b>Akis (event):</b>
 * <ol>
 *   <li>FixtureEventsLiveProcessor yeni event tespit eder → bizi cagirir</li>
 *   <li>Event type+detail'i mobile event tipine (gol/kirmizi/penalti) cevir</li>
 *   <li>Event'in oldugu takimi (event.team) al — bu takimi takip eden cihazlari bul</li>
 *   <li>Her recipient icin FCM mesaji formatla (TR/EN), batch gonder</li>
 * </ol>
 *
 * <p><b>Akis (status change — kickoff/final):</b>
 * <ol>
 *   <li>LiveTickerService Snapshot karsilastirmasinda status NS→1H veya
 *       canli→FT/AET/PEN gecisini gorur → bizi cagirir</li>
 *   <li>Fixture'in iki takimini al — her ikisini takip eden cihazlari bul</li>
 *   <li>FCM mesaji "Galatasaray vs Fenerbahce başladı" formatinda</li>
 * </ol>
 *
 * <p><b>Async:</b> tum dispatch'ler @Async — live ticker tick'ini bekletmez.
 * <b>Idempotency:</b> ayni event icin tekrar cagri yapilsa bile recipient
 * unique olur (DB-level UNIQUE on device_token_id + team_id zaten korur).
 */
@Service
public class NotificationDispatcherService {

    private static final Logger log =
            LoggerFactory.getLogger(NotificationDispatcherService.class);

    private final UserNotificationPrefRepository prefRepository;
    private final DeviceMatchSubscriptionRepository matchSubRepository;
    private final FcmMessagingService fcmMessaging;
    private final NotificationMessageBuilder messageBuilder;
    private final FixtureRepository fixtureRepository;
    private final FixtureEventRepository fixtureEventRepository;

    /**
     * Cift bildirim onleme memo'su. Key formati:
     *   "{fixtureId}:{type}:{teamId}:{score-or-minute}"
     * Value: gonderim epoch_ms. Ayni anahtar son {@link #DEDUP_TTL_MS} icinde
     * varsa skip — LiveTickerService bir skor degisimini iki tick'te gorse
     * veya FixtureEventsLiveProcessor ayni event'i yeniden islese tekrar push
     * gitmez.
     *
     * <p>Map process-ici (clustered ortamda her node ayri memo) — bizim
     * single-app deployment'imizda yeterli. Cluster gerekirse Redis bazli
     * dedup.
     */
    private final java.util.concurrent.ConcurrentHashMap<String, Long> _dedupMemo =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final long DEDUP_TTL_MS = 120_000L; // 2 dakika

    /** Son N ms icinde ayni key icin push gonderildi mi? */
    private boolean _isRecentlySent(String key) {
        Long lastAt = _dedupMemo.get(key);
        if (lastAt == null) return false;
        long age = System.currentTimeMillis() - lastAt;
        if (age < DEDUP_TTL_MS) return true;
        // TTL gectir — temizle ve tekrar gonderebiliriz
        _dedupMemo.remove(key);
        return false;
    }

    /** Push gonderildi olarak isaretle + memo'yu basit sekilde budamayi dene. */
    private void _markSent(String key) {
        _dedupMemo.put(key, System.currentTimeMillis());
        // Lightweight cleanup: memo 500 entry'i gectiyse expired olanlari sil
        if (_dedupMemo.size() > 500) {
            long now = System.currentTimeMillis();
            _dedupMemo.entrySet().removeIf(
                    e -> now - e.getValue() > DEDUP_TTL_MS);
        }
    }

    public NotificationDispatcherService(
            UserNotificationPrefRepository prefRepository,
            DeviceMatchSubscriptionRepository matchSubRepository,
            FcmMessagingService fcmMessaging,
            NotificationMessageBuilder messageBuilder,
            FixtureRepository fixtureRepository,
            FixtureEventRepository fixtureEventRepository) {
        this.prefRepository = prefRepository;
        this.matchSubRepository = matchSubRepository;
        this.fcmMessaging = fcmMessaging;
        this.messageBuilder = messageBuilder;
        this.fixtureRepository = fixtureRepository;
        this.fixtureEventRepository = fixtureEventRepository;
    }

    // ============================================================
    // EVENT (gol/kart/penalti) — FixtureEventsLiveProcessor'dan cagrilir
    // ============================================================

    @Async
    @Transactional(readOnly = true)
    public void dispatchEvent(Fixture fixtureArg, FixtureEvent eventArg) {
        if (!fcmMessaging.isEnabled()) return;
        if (fixtureArg == null || eventArg == null) return;

        // Async thread'de YENI session açılır; çağırandan gelen detached lazy
        // proxy'ler (takım vb.) burada init edilemez (LazyInitializationException).
        // Id ile MANAGED kopyaları çek — takım isimleri bu session içinde yüklenir.
        final Fixture fixture =
                fixtureRepository.findById(fixtureArg.getId()).orElse(null);
        if (fixture == null) return;
        final FixtureEvent event = eventArg.getId() != null
                ? fixtureEventRepository.findById(eventArg.getId()).orElse(eventArg)
                : eventArg;

        final String mobileType = _mapEventType(event);
        if (mobileType == null) return; // ilgilenmedigimiz event (orn. subst)

        // GOL bildirimi artik SKOR degisiminden ANINDA gonderiliyor (dispatchGoal,
        // LiveTickerService tetikler) — burada tekrar gondermeyiz, cift olmasin.
        // Kirmizi kart / penalti event-tetikli kalir (skoru degistirmezler).
        if ("gol".equals(mobileType)) return;

        final Long teamId = event.getTeam() != null ? event.getTeam().getId() : null;
        if (teamId == null) return;

        // Cift bildirim guardu: ayni fixture + type + teamId + dakika(+uzatma)
        // icin son 120sn'de push gonderildiyse skip.
        //
        // ONEMLI (cift kirmizi kart bug fix): anahtara event.getId() VE playerId
        // DAHIL EDILMEZ. API kirmizi karti once ISIMSIZ gonderiyor; isim gelince
        // olay silinip YENI id ile yeniden yaziliyor (events her sync'te replace
        // edilir — bkz. FixtureEvent javadoc). Eski anahtar event.getId()
        // icerdiginden isimli surum (yeni id) dedup'a TAKILMIYOR ve IKINCI push
        // gidiyordu. Dakika + uzatma + tip + takim yeterince ayirt edici; ayni
        // takimin ayni dakikadaki iki AYRI kirmizisi (cok nadir) tek bildirim alir.
        final int evExtra = event.getTimeExtra() == null ? 0 : event.getTimeExtra();
        final String evDedupKey = String.format("ev:%d:%s:%d:%d:%d",
                fixture.getId(), mobileType, teamId,
                event.getTimeElapsed() == null ? 0 : event.getTimeElapsed(),
                evExtra);
        if (_isRecentlySent(evDedupKey)) {
            log.info("Event push SKIP (dedup) fixtureId={} key={}",
                    fixture.getId(), evDedupKey);
            return;
        }
        _markSent(evDedupKey);

        // 1) Takim takibinden gelen recipient'lar (user_notification_prefs)
        final List<UserNotificationPref> teamRecipients =
                _recipientsFor(mobileType, teamId);

        // 2) Mac-bazli favori abonelikleri (device_match_subscriptions) —
        //    favori yapan tum cihazlar default tum event'leri alir.
        final List<DeviceMatchSubscription> favRecipients =
                matchSubRepository.findRecipientsForFixture(fixture.getId());

        if (teamRecipients.isEmpty() && favRecipients.isEmpty()) {
            log.debug("Dispatch event: alici yok fixtureId={} type={} teamId={}",
                    fixture.getId(), mobileType, teamId);
            return;
        }

        // LinkedHashSet — token bazinda dedup (ayni cihaz hem takim takip ediyor
        // hem maci favoriledikse cift bildirim almasin).
        final Set<String> tokens = new LinkedHashSet<>();
        for (UserNotificationPref p : teamRecipients) {
            tokens.add(p.getDeviceToken().getFcmToken());
        }
        for (DeviceMatchSubscription s : favRecipients) {
            tokens.add(s.getDeviceToken().getFcmToken());
        }

        final var msg = messageBuilder.buildEventMessage(fixture, event, mobileType);
        final Map<String, String> data = new HashMap<>();
        data.put("type", mobileType);
        data.put("fixtureId", String.valueOf(fixture.getId()));
        data.put("teamId", String.valueOf(teamId));
        data.put("eventMinute", String.valueOf(event.getTimeElapsed()));

        final int sent = fcmMessaging.sendMulticast(
                List.copyOf(tokens), msg.title(), msg.body(), data);
        log.info("FCM event dispatch: fixtureId={} type={} alici={} (takim={} favori={}) gonderildi={}",
                fixture.getId(), mobileType, tokens.size(),
                teamRecipients.size(), favRecipients.size(), sent);
    }

    // ============================================================
    // GOL — LiveTickerService SKOR degisimini gorunce ANINDA cagrilir.
    // Olay (golcu) beklenmez; o an DB'de varsa eklenir, yoksa skor-only.
    // ============================================================

    @Async
    @Transactional(readOnly = true)
    public void dispatchGoal(Long fixtureId, Long scoringTeamId) {
        if (!fcmMessaging.isEnabled() || fixtureId == null) return;
        final Fixture fixture =
                fixtureRepository.findById(fixtureId).orElse(null);
        if (fixture == null) return;

        // Cift bildirim guardu: ayni mac + ayni skor + ayni dakika icin son
        // 120sn'de push gonderildiyse skip. LiveTickerService bazen ayni skor
        // degisimini iki tick'te (5sn ara) goruyor — kullanici 2-3 push aliyor.
        final String dedupKey = String.format("goal:%d:%d:%d-%d",
                fixtureId,
                scoringTeamId == null ? 0L : scoringTeamId,
                fixture.getHomeGoals() == null ? 0 : fixture.getHomeGoals(),
                fixture.getAwayGoals() == null ? 0 : fixture.getAwayGoals());
        if (_isRecentlySent(dedupKey)) {
            log.info("Skor-gol push SKIP (dedup) fixtureId={} key={}",
                    fixtureId, dedupKey);
            return;
        }
        _markSent(dedupKey);

        // Alicilar: gol atan takim takipcileri ("gol" acik) + favori-mac aboneleri.
        final Set<String> tokens = new LinkedHashSet<>();
        if (scoringTeamId != null) {
            for (UserNotificationPref p : _recipientsFor("gol", scoringTeamId)) {
                tokens.add(p.getDeviceToken().getFcmToken());
            }
        }
        for (DeviceMatchSubscription s :
                matchSubRepository.findRecipientsForFixture(fixtureId)) {
            tokens.add(s.getDeviceToken().getFcmToken());
        }
        if (tokens.isEmpty()) return;

        // Skor-only — beklemeden ANINDA gonderilir (hiz oncelikli). Golcu bu
        // anda henuz DB'de degil (skor /fixtures?live=all'dan, golcu ayri /events
        // cagrisindan gelir); uygulamada saniyeler icinde WebSocket'le gorunur.
        // A-Faz5 rotuş: o anki mac dakikasini (fixture.elapsed) gecirip
        // title/body'de "12'" formatinda gostermek icin buildScoreGoal'a
        // minute geciriyoruz. Golcu olmasa da dakika gorunur olur.
        final Integer minute = fixture.getElapsed();
        final var msg = messageBuilder.buildScoreGoal(fixture, null, minute);
        final Map<String, String> data = new HashMap<>();
        data.put("type", "gol");
        data.put("fixtureId", String.valueOf(fixtureId));
        if (minute != null) data.put("eventMinute", String.valueOf(minute));
        final int sent = fcmMessaging.sendMulticast(
                List.copyOf(tokens), msg.title(), msg.body(), data);
        log.info("FCM skor-gol dispatch: fixtureId={} dakika={} alici={} gonderildi={}",
                fixtureId, minute, tokens.size(), sent);
    }

    // ============================================================
    // KICKOFF — LiveTickerService status NS→1H tespit edince cagrilir
    // ============================================================

    @Async
    @Transactional(readOnly = true)
    public void dispatchKickoff(Fixture fixtureArg) {
        if (!fcmMessaging.isEnabled() || fixtureArg == null) return;
        final Fixture fixture =
                fixtureRepository.findById(fixtureArg.getId()).orElse(null);
        if (fixture == null) return;

        // Cift bildirim guardu — LiveTickerService NS→1H gecisini bazen iki
        // tick'te goruyor.
        final String koKey = "kickoff:" + fixture.getId();
        if (_isRecentlySent(koKey)) {
            log.info("Kickoff push SKIP (dedup) fixtureId={}", fixture.getId());
            return;
        }
        _markSent(koKey);
        _dispatchMatchStatus(fixture, "basladi");
    }

    // ============================================================
    // FINAL — LiveTickerService 1H/2H/ET→FT/AET/PEN tespit edince cagrilir
    // ============================================================

    @Async
    @Transactional(readOnly = true)
    public void dispatchFinal(Fixture fixtureArg) {
        // dispatchFinal body asagida — dedup en uste eklenir.
        // (Mevcut implementasyon korunur — sadece guard.)
        if (fixtureArg != null) {
            final String fnKey = "final:" + fixtureArg.getId();
            if (_isRecentlySent(fnKey)) {
                log.info("Final push SKIP (dedup) fixtureId={}",
                        fixtureArg.getId());
                return;
            }
            _markSent(fnKey);
        }
        if (!fcmMessaging.isEnabled() || fixtureArg == null) return;
        final Fixture fixture =
                fixtureRepository.findById(fixtureArg.getId()).orElse(null);
        if (fixture == null) return;
        _dispatchMatchStatus(fixture, "bitti");
    }

    // ============================================================
    // KADRO (İlk 11) — ImminentLineupsJob kadro İLK açıklandığında cagrilir.
    // ============================================================

    @Async
    @Transactional(readOnly = true)
    public void dispatchLineup(Long fixtureId) {
        if (!fcmMessaging.isEnabled() || fixtureId == null) return;

        // Cift bildirim guardu — job ayni maci pespese tick'te gormesin.
        final String key = "lineup:" + fixtureId;
        if (_isRecentlySent(key)) {
            log.info("Kadro push SKIP (dedup) fixtureId={}", fixtureId);
            return;
        }
        _markSent(key);

        final Fixture fixture =
                fixtureRepository.findById(fixtureId).orElse(null);
        if (fixture == null) return;
        _dispatchMatchStatus(fixture, "kadro");
    }

    private void _dispatchMatchStatus(Fixture fixture, String mobileType) {
        // 1) Takim takibinden gelen recipient'lar — hem ev hem deplasman takim
        final Set<Long> teamIds = new LinkedHashSet<>();
        if (fixture.getHomeTeam() != null) teamIds.add(fixture.getHomeTeam().getId());
        if (fixture.getAwayTeam() != null) teamIds.add(fixture.getAwayTeam().getId());

        final Set<String> tokens = new LinkedHashSet<>();
        int teamRecipientCount = 0;
        for (Long teamId : teamIds) {
            final List<UserNotificationPref> recipients =
                    _recipientsFor(mobileType, teamId);
            teamRecipientCount += recipients.size();
            for (UserNotificationPref p : recipients) {
                tokens.add(p.getDeviceToken().getFcmToken());
            }
        }

        // 2) Mac-bazli favori abonelikleri — favori yapan tum cihazlar
        //    basladi/bitti event'lerini de alir.
        final List<DeviceMatchSubscription> favRecipients =
                matchSubRepository.findRecipientsForFixture(fixture.getId());
        for (DeviceMatchSubscription s : favRecipients) {
            tokens.add(s.getDeviceToken().getFcmToken());
        }

        if (tokens.isEmpty()) {
            log.debug("Dispatch {}: alici yok fixtureId={}", mobileType, fixture.getId());
            return;
        }

        final var msg = switch (mobileType) {
            case "basladi" -> messageBuilder.buildKickoffMessage(fixture);
            case "kadro" -> messageBuilder.buildLineupMessage(fixture);
            default -> messageBuilder.buildFinalMessage(fixture);
        };
        final Map<String, String> data = new HashMap<>();
        data.put("type", mobileType);
        data.put("fixtureId", String.valueOf(fixture.getId()));

        final int sent = fcmMessaging.sendMulticast(
                List.copyOf(tokens), msg.title(), msg.body(), data);
        log.info("FCM {} dispatch: fixtureId={} alici={} (takim={} favori={}) gonderildi={}",
                mobileType, fixture.getId(), tokens.size(),
                teamRecipientCount, favRecipients.size(), sent);
    }

    // ============================================================
    // Yardimcilar
    // ============================================================

    /**
     * API-Football event type+detail'i mobile bildirim tipine cevir.
     *
     * <p>API-Football event kombinasyonlari:
     * <ul>
     *   <li>Goal + "Normal Goal" / "Own Goal" → "gol"</li>
     *   <li>Goal + "Penalty" → "penalti" (penaltidan gol)</li>
     *   <li>Goal + "Missed Penalty" → "penalti" (kacirilan penalti)</li>
     *   <li>Card + "Red Card" / "Second Yellow card" → "kirmizi"</li>
     *   <li>Var + detail icinde "Penalty" → "penalti" (VAR karari)</li>
     *   <li>Diger (Yellow Card, subst, normal Var) → null (bildirim yok)</li>
     * </ul>
     */
    private String _mapEventType(FixtureEvent e) {
        if (e.getType() == null) return null;
        final String type = e.getType().toLowerCase();
        final String detail = e.getDetail() == null ? "" : e.getDetail().toLowerCase();

        if ("goal".equals(type)) {
            if (detail.contains("penalty")) return "penalti";
            return "gol";
        }
        if ("card".equals(type)) {
            if (detail.contains("red") || detail.contains("second yellow")) {
                return "kirmizi";
            }
            return null; // sari kart bildirim listemizde yok
        }
        if ("var".equals(type)) {
            if (detail.contains("penalty")) return "penalti";
            return null;
        }
        return null;
    }

    /**
     * Verilen olay tipi + takim icin "bildirim acik" olan tum recipient kayitlari.
     * Repository'deki ayri ayri findRecipientsFor* metodlari kullanilir.
     */
    private List<UserNotificationPref> _recipientsFor(String mobileType, Long teamId) {
        return switch (mobileType) {
            case "gol" -> prefRepository.findRecipientsForGoal(teamId);
            case "kirmizi" -> prefRepository.findRecipientsForRedCard(teamId);
            case "penalti" -> prefRepository.findRecipientsForPenalty(teamId);
            case "basladi" -> prefRepository.findRecipientsForKickoff(teamId);
            case "bitti" -> prefRepository.findRecipientsForFinal(teamId);
            case "kadro" -> prefRepository.findRecipientsForLineup(teamId);
            default -> List.of();
        };
    }
}
