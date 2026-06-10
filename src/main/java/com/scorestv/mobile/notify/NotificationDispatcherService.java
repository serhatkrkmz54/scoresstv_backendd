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
        final var msg = messageBuilder.buildScoreGoal(fixture, null, null);
        final Map<String, String> data = new HashMap<>();
        data.put("type", "gol");
        data.put("fixtureId", String.valueOf(fixtureId));
        final int sent = fcmMessaging.sendMulticast(
                List.copyOf(tokens), msg.title(), msg.body(), data);
        log.info("FCM skor-gol dispatch: fixtureId={} alici={} gonderildi={}",
                fixtureId, tokens.size(), sent);
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
        _dispatchMatchStatus(fixture, "basladi");
    }

    // ============================================================
    // FINAL — LiveTickerService 1H/2H/ET→FT/AET/PEN tespit edince cagrilir
    // ============================================================

    @Async
    @Transactional(readOnly = true)
    public void dispatchFinal(Fixture fixtureArg) {
        if (!fcmMessaging.isEnabled() || fixtureArg == null) return;
        final Fixture fixture =
                fixtureRepository.findById(fixtureArg.getId()).orElse(null);
        if (fixture == null) return;
        _dispatchMatchStatus(fixture, "bitti");
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

        final var msg = "basladi".equals(mobileType)
                ? messageBuilder.buildKickoffMessage(fixture)
                : messageBuilder.buildFinalMessage(fixture);
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
            default -> List.of();
        };
    }
}
