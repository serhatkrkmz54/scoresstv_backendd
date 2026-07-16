package com.scorestv.mobile.notify;

import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureEvent;
import com.scorestv.football.domain.FixtureEventRepository;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.mobile.domain.DeviceMatchSubscription;
import com.scorestv.mobile.domain.DeviceMatchSubscriptionRepository;
import com.scorestv.mobile.domain.MobileDeviceToken;
import com.scorestv.mobile.domain.UserNotificationPref;
import com.scorestv.mobile.domain.UserNotificationPrefRepository;
import com.scorestv.mobile.fcm.FcmMessagingService;
import com.scorestv.mobile.fcm.FcmTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Canli mac olaylarini (gol, kart, penalti) ilgili kullanicilara FCM push
 * notification olarak gonderir. (Kickoff/final/HT/2H {@link
 * com.scorestv.football.live.FixtureNotifyGate} → outbox uzerinden gider.)
 *
 * <p><b>İki fazlı canlı bildirim (Maçkolik deseni):</b> gol/kırmızı kart önce
 * ISIMSIZ (anında), sonra oyuncu adıyla AYNI bildirim collapse_key ile SESSİZCE
 * güncellenir.
 *
 * <p><b>TR + EN:</b> mesaj enqueue anında iki dilde render edilir; gönderimde
 * ({@link #sendOutboxRow}) alıcılar cihaz locale'ine göre ayrılıp doğru dil
 * gönderilir.
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
    private final NotificationOutboxEnqueuer enqueuer;
    private final NotificationOutboxRepository outboxRepository;
    /** FCM Topics yolu acik mi? (scorestv.notify.use-fcm-topics) */
    private final boolean useFcmTopics;
    /** Iki fazli bildirimin FAZ-2'si (isimli SESSIZ guncelleme) acik mi?
     * (scorestv.notify.enrich-updates) — magaza surumu cikana dek FALSE; boylece
     * eski app kullanicilari fazla titresim yasamaz (davranis bugunkuyle ayni). */
    private final boolean enrichUpdates;
    /** Topic + token AYNI ANDA gonderilsin mi? (scorestv.notify.dual-send) —
     * gecis doneminde topic'e abone OLMAYAN (eski build / henuz reconcile
     * olmamis) cihazlar token yoluyla da yakalanir; collapse_key ile cihazda
     * TEK bildirime iner. Rollout tamamlaninca FALSE → sadece topic (olcek). */
    private final boolean dualSend;

    public NotificationDispatcherService(
            UserNotificationPrefRepository prefRepository,
            DeviceMatchSubscriptionRepository matchSubRepository,
            FcmMessagingService fcmMessaging,
            NotificationMessageBuilder messageBuilder,
            FixtureRepository fixtureRepository,
            FixtureEventRepository fixtureEventRepository,
            NotificationOutboxEnqueuer enqueuer,
            NotificationOutboxRepository outboxRepository,
            @Value("${scorestv.notify.use-fcm-topics:false}") boolean useFcmTopics,
            @Value("${scorestv.notify.enrich-updates:false}") boolean enrichUpdates,
            @Value("${scorestv.notify.dual-send:false}") boolean dualSend) {
        this.useFcmTopics = useFcmTopics;
        this.enrichUpdates = enrichUpdates;
        this.dualSend = dualSend;
        this.prefRepository = prefRepository;
        this.matchSubRepository = matchSubRepository;
        this.fcmMessaging = fcmMessaging;
        this.messageBuilder = messageBuilder;
        this.fixtureRepository = fixtureRepository;
        this.fixtureEventRepository = fixtureEventRepository;
        this.enqueuer = enqueuer;
        this.outboxRepository = outboxRepository;
    }

    // ============================================================
    // Collapse anahtarları (OS bildirim slotu — faz-1 ve faz-2 AYNI olmalı)
    // ============================================================

    private static String _goalCollapse(Long fixtureId, Long teamId, int teamGoalNo) {
        return "g-" + fixtureId + "-" + (teamId == null ? 0 : teamId) + "-" + teamGoalNo;
    }

    private static String _eventCollapse(Long fixtureId, String mobileType, Long teamId,
                                         int elapsed, int extra) {
        return "e-" + fixtureId + "-" + mobileType + "-" + teamId + "-" + elapsed + "-" + extra;
    }

    private static String _hash(String s) {
        return s == null ? "0" : Integer.toHexString(s.hashCode());
    }

    /** Bu golün, atan takımın kaçıncı golü olduğu (1-tabanlı). Kaçan penaltı hariç. */
    private int _goalOrdinal(Long fixtureId, Long teamId, FixtureEvent current) {
        List<FixtureEvent> all =
                fixtureEventRepository.findByFixtureIdOrderByTimeElapsedAsc(fixtureId);
        int n = 0;
        for (FixtureEvent e : all) {
            if (e.getType() == null || !"goal".equalsIgnoreCase(e.getType())) continue;
            String d = e.getDetail() == null ? "" : e.getDetail().toLowerCase();
            if (d.contains("missed")) continue;
            if (e.getTeam() == null || !teamId.equals(e.getTeam().getId())) continue;
            n++;
            if (current.getId() != null && current.getId().equals(e.getId())) return n;
        }
        return n == 0 ? 1 : n;
    }

    /** collapse/silent'i data'ya koy (foreground local-notif yerinde güncelleme icin). */
    private static void _putCollapse(Map<String, String> data, String collapseKey, boolean silent) {
        if (collapseKey != null) data.put("collapseKey", collapseKey);
        data.put("silent", silent ? "true" : "false");
    }

    // ============================================================
    // EVENT (gol faz-2 / kart / penalti) — FixtureEventsLiveProcessor'dan
    // ============================================================

    @Async
    @Transactional
    public void dispatchEvent(Fixture fixtureArg, FixtureEvent eventArg) {
        if (!fcmMessaging.isEnabled()) return;
        if (fixtureArg == null || eventArg == null) return;

        final Fixture fixture =
                fixtureRepository.findById(fixtureArg.getId()).orElse(null);
        if (fixture == null) return;
        final FixtureEvent event = eventArg.getId() != null
                ? fixtureEventRepository.findById(eventArg.getId()).orElse(eventArg)
                : eventArg;

        final String mobileType = _mapEventType(event);
        if (mobileType == null) return;

        final Long teamId = event.getTeam() != null ? event.getTeam().getId() : null;
        if (teamId == null) return;

        final String playerName = event.getPlayerName();
        final boolean hasName = playerName != null && !playerName.isBlank();

        // ---- GOL: faz-1 (isimsiz/anında) dispatchGoal'da. Burada YALNIZ golcü
        //      ADI gelince faz-2 SESSİZ güncelleme (aynı collapse slotu).
        if ("gol".equals(mobileType)) {
            if (!hasName) return;
            // Magaza surumu cikana dek isimli SESSIZ guncelleme kapali (eski app
            // uyumu: gol skor-only kalir, tam bugunku davranis).
            if (!enrichUpdates) return;
            final int teamGoalNo = _goalOrdinal(fixture.getId(), teamId, event);
            final String collapseKey = _goalCollapse(fixture.getId(), teamId, teamGoalNo);
            final Integer minute = event.getTimeElapsed();
            final var gmsg = messageBuilder.scoreGoal(fixture, playerName, minute);
            final Map<String, String> gdata = new HashMap<>();
            gdata.put("type", "gol");
            gdata.put("fixtureId", String.valueOf(fixture.getId()));
            gdata.put("teamId", String.valueOf(teamId));
            if (minute != null) gdata.put("eventMinute", String.valueOf(minute));
            _putCollapse(gdata, collapseKey, true);
            final String gdedup = String.format("GOALNAME:%d:%d:%d:%s",
                    fixture.getId(), teamId, teamGoalNo,
                    event.getPlayerId() == null
                            ? _hash(playerName) : String.valueOf(event.getPlayerId()));
            enqueuer.enqueue(NotificationOutbox.KIND_GOAL, "gol", fixture.getId(),
                    teamId, gmsg, gdata, gdedup, collapseKey, true);
            return;
        }

        // ---- KIRMIZI / PENALTI: event-tetikli, iki fazlı (isimsiz→isimli).
        final int evExtra = event.getTimeExtra() == null ? 0 : event.getTimeExtra();
        final int evElapsed = event.getTimeElapsed() == null ? 0 : event.getTimeElapsed();
        final String collapseKey =
                _eventCollapse(fixture.getId(), mobileType, teamId, evElapsed, evExtra);
        final boolean silent = outboxRepository.existsByCollapseKey(collapseKey);
        // SESSIZ guncelleme (isimli faz-2) magaza surumu cikana dek kapali.
        // Isimsiz faz-1 (silent=false) her zaman gider → eski app uyumu korunur.
        if (silent && !enrichUpdates) return;
        final String phase = hasName
                ? "NAME:" + (event.getPlayerId() == null
                        ? _hash(playerName) : String.valueOf(event.getPlayerId()))
                : "BASE";
        final String evType = event.getType() == null ? "" : event.getType().toLowerCase();
        final String dedupKey = String.format("EVENT:%d:%s:%s:%d:%d:%d:%s",
                fixture.getId(), mobileType, evType, teamId, evElapsed, evExtra, phase);

        final var msg = messageBuilder.event(fixture, event, mobileType);
        final Map<String, String> data = new HashMap<>();
        data.put("type", mobileType);
        data.put("fixtureId", String.valueOf(fixture.getId()));
        data.put("teamId", String.valueOf(teamId));
        if (event.getTimeElapsed() != null) {
            data.put("eventMinute", String.valueOf(event.getTimeElapsed()));
        }
        _putCollapse(data, collapseKey, silent);
        enqueuer.enqueue(NotificationOutbox.KIND_EVENT, mobileType, fixture.getId(),
                teamId, msg, data, dedupKey, collapseKey, silent);
    }

    // ============================================================
    // GOL faz-1 — LiveTickerService SKOR degisimini gorunce ANINDA cagrilir.
    // ============================================================

    @Async
    @Transactional
    public void dispatchGoal(Long fixtureId, Long scoringTeamId) {
        if (!fcmMessaging.isEnabled() || fixtureId == null) return;
        final Fixture fixture =
                fixtureRepository.findById(fixtureId).orElse(null);
        if (fixture == null) return;

        final int h = fixture.getHomeGoals() == null ? 0 : fixture.getHomeGoals();
        final int a = fixture.getAwayGoals() == null ? 0 : fixture.getAwayGoals();

        final int teamGoalNo;
        if (scoringTeamId != null && fixture.getHomeTeam() != null
                && scoringTeamId.equals(fixture.getHomeTeam().getId())) {
            teamGoalNo = h;
        } else if (scoringTeamId != null && fixture.getAwayTeam() != null
                && scoringTeamId.equals(fixture.getAwayTeam().getId())) {
            teamGoalNo = a;
        } else {
            teamGoalNo = h + a;
        }
        final String collapseKey = _goalCollapse(fixtureId, scoringTeamId, teamGoalNo);

        final String dedupKey = String.format("GOAL:%d:%d:%d-%d",
                fixtureId, scoringTeamId == null ? 0L : scoringTeamId, h, a);

        final Integer minute = fixture.getElapsed();
        final var msg = messageBuilder.scoreGoal(fixture, null, minute);
        final Map<String, String> data = new HashMap<>();
        data.put("type", "gol");
        data.put("fixtureId", String.valueOf(fixtureId));
        if (minute != null) data.put("eventMinute", String.valueOf(minute));
        _putCollapse(data, collapseKey, false);
        enqueuer.enqueue(NotificationOutbox.KIND_GOAL, "gol", fixtureId,
                scoringTeamId, msg, data, dedupKey, collapseKey, false);
    }

    // ============================================================
    // KADRO (İlk 11) — ImminentLineupsJob kadro İLK açıklandığında cagrilir.
    // ============================================================

    @Async
    @Transactional
    public void dispatchLineup(Long fixtureId) {
        if (!fcmMessaging.isEnabled() || fixtureId == null) return;
        final Fixture fixture =
                fixtureRepository.findById(fixtureId).orElse(null);
        if (fixture == null) return;
        final var msg = messageBuilder.lineup(fixture);
        final Map<String, String> data = new HashMap<>();
        data.put("type", "kadro");
        data.put("fixtureId", String.valueOf(fixtureId));
        enqueuer.enqueue(NotificationOutbox.KIND_LINEUP, "kadro", fixtureId, null,
                msg, data, "LINEUP:" + fixtureId);
    }

    /**
     * SENKRON outbox gönderimi — {@link NotificationOutboxWorker} çağırır.
     * Sert FCM hatasında EXCEPTION fırlatır (worker retry).
     *
     * <p><b>Gönderim modları</b> (flag'lerle):
     * <ul>
     *   <li><b>TOKEN</b> (use-fcm-topics=false): DB'deki cihaz token'larına
     *       multicast; locale'e göre TR/EN. Herkese ulaşır, teslim görünür.</li>
     *   <li><b>TOPIC</b> (use-fcm-topics=true, dual-send=false): FCM condition'a
     *       tek gönderim; fan-out'u Google yapar (ölçek). Dil {@code lang_tr}/
     *       {@code lang_en} topic'iyle ayrılır.</li>
     *   <li><b>DUAL</b> (ikisi de açık): topic + token birlikte; geçiş döneminde
     *       abone olmayan cihazlar da yakalanır, collapse_key cihazda tek
     *       bildirime indirir.</li>
     * </ul>
     *
     * @return gönderim sonucu (mod + token alıcı + teslim sayısı) — admin takip.
     */
    @Transactional(readOnly = true)
    public SendResult sendOutboxRow(Long fixtureId, Long teamId, String notifType,
                             String titleTr, String bodyTr, String titleEn, String bodyEn,
                             Map<String, String> data, String collapseKey, boolean silent) {
        if (!fcmMessaging.isEnabled()) {
            throw new IllegalStateException("FCM devre disi");
        }
        final String enTitle = (titleEn != null && !titleEn.isBlank()) ? titleEn : titleTr;
        final String enBody = (bodyEn != null && !bodyEn.isBlank()) ? bodyEn : bodyTr;

        // Olay tipine göre ÖZEL SES/KANAL (gol/kırmızı/penaltı/başladı/bitti).
        // Android: kanal id (kanal sesi app'te kurulu); iOS: aps.sound dosya adı.
        // Eski app'te kanal/dosya yoksa FCM/iOS varsayılana düşer → geriye uyumlu.
        final Map<String, String> d = new HashMap<>(data == null ? Map.of() : data);
        final String _ch = _androidChannelFor(notifType);
        final String _snd = _iosSoundFor(notifType);
        if (_ch != null) d.put("androidChannel", _ch);
        if (_snd != null) d.put("iosSound", _snd);
        // Foreground local-notif'in aynı slotu (tag) kullanabilmesi için
        // collapse_key'i data'ya da koy → DUAL-SEND'de foreground kopyaları da
        // cihazda tek bildirime iner. (Arka planda mesaj tag'i zaten set edilir.)
        if (collapseKey != null && !collapseKey.isBlank()) {
            d.putIfAbsent("collapseKey", collapseKey);
        }

        // ---- TOPIC yolu (flag ACIK) — dil ayrimi lang_tr/lang_en ile.
        boolean topicSent = false;
        if (useFcmTopics) {
            final String suffix = FcmTopics.suffixFor(notifType);
            final List<String> topics = new ArrayList<>();
            if (teamId != null) {
                topics.add(FcmTopics.teamEvent(teamId, suffix));
            } else {
                final Fixture fx = fixtureRepository.findById(fixtureId).orElse(null);
                if (fx != null) {
                    if (fx.getHomeTeam() != null) {
                        topics.add(FcmTopics.teamEvent(fx.getHomeTeam().getId(), suffix));
                    }
                    if (fx.getAwayTeam() != null) {
                        topics.add(FcmTopics.teamEvent(fx.getAwayTeam().getId(), suffix));
                    }
                }
            }
            topics.add(FcmTopics.favoriteFixture(fixtureId));
            if (!topics.isEmpty()) {
                final String orCond = FcmTopics.orCondition(topics);
                // TR ve EN alicilar ayri condition (dil topic'iyle) → dogru dil.
                fcmMessaging.sendToConditionOrThrow(
                        FcmTopics.andLang(orCond, FcmTopics.lang("tr")),
                        titleTr, bodyTr, d, collapseKey, silent);
                fcmMessaging.sendToConditionOrThrow(
                        FcmTopics.andLang(orCond, FcmTopics.lang("en")),
                        enTitle, enBody, d, collapseKey, silent);
                topicSent = true;
            }
            // dual-send KAPALI → sadece topic (ölçek). Aksi halde token'a devam.
            if (!dualSend) {
                return new SendResult(topicSent ? "TOPIC" : "NONE", 0, 0);
            }
        }

        // ---- TOKEN yolu (varsayilan VEYA dual-send) — locale'e gore TR/EN.
        final Set<String> trTokens = new LinkedHashSet<>();
        final Set<String> enTokens = new LinkedHashSet<>();
        if (teamId != null) {
            for (UserNotificationPref p : _recipientsFor(notifType, teamId)) {
                _addByLocale(p.getDeviceToken(), trTokens, enTokens);
            }
        } else {
            final Fixture fixture = fixtureRepository.findById(fixtureId).orElse(null);
            if (fixture != null) {
                if (fixture.getHomeTeam() != null) {
                    for (UserNotificationPref p :
                            _recipientsFor(notifType, fixture.getHomeTeam().getId())) {
                        _addByLocale(p.getDeviceToken(), trTokens, enTokens);
                    }
                }
                if (fixture.getAwayTeam() != null) {
                    for (UserNotificationPref p :
                            _recipientsFor(notifType, fixture.getAwayTeam().getId())) {
                        _addByLocale(p.getDeviceToken(), trTokens, enTokens);
                    }
                }
            }
        }
        for (DeviceMatchSubscription s :
                matchSubRepository.findRecipientsForFixture(fixtureId)) {
            _addByLocale(s.getDeviceToken(), trTokens, enTokens);
        }
        // Ayni cihaz iki listede olmasin (locale tekildir ama garanti icin).
        enTokens.removeAll(trTokens);
        final int recipients = trTokens.size() + enTokens.size();
        final String mode = useFcmTopics ? "DUAL" : "TOKEN";
        if (recipients == 0) {
            return new SendResult(topicSent ? "TOPIC" : mode, 0, 0);
        }

        int sent = 0;
        if (!trTokens.isEmpty()) {
            sent += fcmMessaging.sendMulticastOrThrow(
                    List.copyOf(trTokens), titleTr, bodyTr, d, collapseKey, silent);
        }
        if (!enTokens.isEmpty()) {
            sent += fcmMessaging.sendMulticastOrThrow(
                    List.copyOf(enTokens), enTitle, enBody, d, collapseKey, silent);
        }
        log.info("FCM outbox dispatch: fixtureId={} type={} mode={} tr={} en={} teslim={} collapse={} silent={}",
                fixtureId, notifType, mode, trTokens.size(), enTokens.size(), sent, collapseKey, silent);
        return new SendResult(mode, recipients, sent);
    }

    /**
     * Gönderim sonucu — admin panelde teslim takibi için.
     *
     * @param mode       "TOKEN" | "TOPIC" | "DUAL" | "NONE"
     * @param recipients token yolunda hedeflenen cihaz sayısı (topic fan-out
     *                   sayısı FCM'de görünmez → 0)
     * @param delivered  token yolunda FCM'in başarılı bildirdiği sayı
     */
    public record SendResult(String mode, int recipients, int delivered) {}

    /** Cihazı locale'ine göre TR ya da EN token listesine ekle (tr* → TR, diğer → EN). */
    private void _addByLocale(MobileDeviceToken token, Set<String> tr, Set<String> en) {
        if (token == null) return;
        final String fcm = token.getFcmToken();
        if (fcm == null || fcm.isBlank()) return;
        final String loc = token.getLocale();
        if (loc != null && loc.toLowerCase().startsWith("tr")) {
            tr.add(fcm);
        } else {
            en.add(fcm);
        }
    }

    // ============================================================
    // Yardimcilar
    // ============================================================

    /** Olay tipi → Android bildirim kanalı (özel ses). ht/2yari/kadro → null (varsayılan). */
    private static String _androidChannelFor(String notifType) {
        return switch (notifType) {
            case "gol" -> "scorestv_goal";
            case "kirmizi" -> "scorestv_redcard";
            case "penalti" -> "scorestv_penalty";
            case "basladi" -> "scorestv_start";
            case "bitti" -> "scorestv_end";
            default -> null;
        };
    }

    /** Olay tipi → iOS bildirim sesi dosyası (bundle'da). Yoksa null (varsayılan). */
    private static String _iosSoundFor(String notifType) {
        return switch (notifType) {
            case "gol" -> "goal.wav";
            case "kirmizi" -> "red_card.wav";
            case "penalti" -> "penalty.wav";
            case "basladi" -> "match_start.wav";
            case "bitti" -> "match_end.wav";
            default -> null;
        };
    }

    private String _mapEventType(FixtureEvent e) {
        if (e.getType() == null) return null;
        final String type = e.getType().toLowerCase();
        final String detail = e.getDetail() == null ? "" : e.getDetail().toLowerCase();

        if ("goal".equals(type)) {
            if (detail.contains("missed")) return "penalti";
            return "gol";
        }
        if ("card".equals(type)) {
            if (detail.contains("red") || detail.contains("second yellow")) {
                return "kirmizi";
            }
            return null;
        }
        if ("var".equals(type)) {
            if (detail.contains("penalty") || detail.contains("goal")) return "penalti";
            return null;
        }
        return null;
    }

    private List<UserNotificationPref> _recipientsFor(String mobileType, Long teamId) {
        return switch (mobileType) {
            case "gol" -> prefRepository.findRecipientsForGoal(teamId);
            case "kirmizi" -> prefRepository.findRecipientsForRedCard(teamId);
            case "penalti" -> prefRepository.findRecipientsForPenalty(teamId);
            case "basladi" -> prefRepository.findRecipientsForKickoff(teamId);
            case "bitti" -> prefRepository.findRecipientsForFinal(teamId);
            case "kadro" -> prefRepository.findRecipientsForLineup(teamId);
            case "ht" -> prefRepository.findRecipientsForHalftime(teamId);
            case "2yari" -> prefRepository.findRecipientsForSecondHalf(teamId);
            default -> List.of();
        };
    }
}
