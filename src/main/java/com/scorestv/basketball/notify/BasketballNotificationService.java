package com.scorestv.basketball.notify;

import com.scorestv.basketball.domain.DeviceBasketballSubscription;
import com.scorestv.basketball.domain.DeviceBasketballSubscriptionRepository;
import com.scorestv.mobile.domain.BasketballNotificationPref;
import com.scorestv.mobile.domain.BasketballNotificationPrefRepository;
import com.scorestv.mobile.domain.MobileDeviceToken;
import com.scorestv.mobile.fcm.FcmMessagingService;
import com.scorestv.mobile.fcm.FcmTopics;
import com.scorestv.mobile.notify.NotificationDispatcherService.SendResult;
import com.scorestv.mobile.notify.NotificationMessageBuilder.Localized;
import com.scorestv.mobile.notify.NotificationOutbox;
import com.scorestv.mobile.notify.NotificationOutboxEnqueuer;
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
 * Basketbol maçları için FCM push: maç başladı, çeyrek bitti (skorlu), maç
 * bitti. Football'un {@code NotificationDispatcherService}'inden ayrı abonelik
 * tablosu/topic kullanır ama TESLİM aynı OUTBOX'tan geçer: dispatch metodları
 * mesajı render edip {@code sport=basketball} satırı olarak kuyruğa yazar;
 * {@link com.scorestv.mobile.notify.NotificationOutboxWorker} backoff'lu retry
 * ile {@link #sendOutboxRow} üzerinden gönderir. Böylece FCM'in geçici
 * INTERNAL/UNKNOWN hataları bildirimi düşürmez (eskiden at-ve-unut idi).
 *
 * <p><b>TR + EN:</b> her bildirim iki dilde üretilir; token-multicast yolunda
 * alıcılar cihaz locale'ine göre ({@code MobileDeviceToken.locale}) ayrılır,
 * topic yolunda ise futboldaki gibi {@code lang_tr}/{@code lang_en} dil
 * topic'iyle iki ayrı condition gönderilir — herkes kendi dilinde alır.
 */
@Service
public class BasketballNotificationService {

    private static final Logger log =
            LoggerFactory.getLogger(BasketballNotificationService.class);

    private final FcmMessagingService fcm;
    private final DeviceBasketballSubscriptionRepository subRepo;
    private final BasketballNotificationPrefRepository prefRepo;
    private final NotificationOutboxEnqueuer enqueuer;
    /** FCM Topics yolu acik mi? (scorestv.notify.use-fcm-topics) — futbolla ayni flag. */
    private final boolean useFcmTopics;

    public BasketballNotificationService(
            FcmMessagingService fcm,
            DeviceBasketballSubscriptionRepository subRepo,
            BasketballNotificationPrefRepository prefRepo,
            NotificationOutboxEnqueuer enqueuer,
            @Value("${scorestv.notify.use-fcm-topics:false}") boolean useFcmTopics) {
        this.fcm = fcm;
        this.subRepo = subRepo;
        this.prefRepo = prefRepo;
        this.enqueuer = enqueuer;
        this.useFcmTopics = useFcmTopics;
    }

    /** NS→canlı: maç başladı. */
    @Async("notifyExecutor")
    public void dispatchStart(Long gameId, Long homeTeamId, Long awayTeamId,
                              String home, String away) {
        enqueue(gameId, NotificationOutbox.KIND_START,
                "🏀 Maç başladı!", "%s - %s başladı".formatted(home, away),
                "🏀 Game started!", "%s vs %s has started".formatted(home, away),
                "bk_start", null, homeTeamId, awayTeamId);
    }

    /** Çeyrek bitti — o ana kadarki toplam skorla birlikte. */
    @Async("notifyExecutor")
    public void dispatchPeriodEnd(Long gameId, Long homeTeamId, Long awayTeamId,
                                  String home, String away,
                                  int quarter, Integer homeTotal,
                                  Integer awayTotal) {
        String body = "%s %d-%d %s".formatted(home, n(homeTotal), n(awayTotal), away);
        enqueue(gameId, NotificationOutbox.KIND_PERIOD,
                "🏀 %d. çeyrek bitti".formatted(quarter), body,
                "🏀 End of Q%d".formatted(quarter), body,
                "bk_period", quarter, homeTeamId, awayTeamId);
    }

    /** →FT/AOT: maç bitti (final skor). */
    @Async("notifyExecutor")
    public void dispatchFinal(Long gameId, Long homeTeamId, Long awayTeamId,
                              String home, String away,
                              Integer homeTotal, Integer awayTotal) {
        String body = "%s %d-%d %s".formatted(home, n(homeTotal), n(awayTotal), away);
        enqueue(gameId, NotificationOutbox.KIND_FINAL,
                "🏀 Maç bitti", body,
                "🏀 Final", body,
                "bk_final", null, homeTeamId, awayTeamId);
    }

    /** Mesajı dondurup outbox'a yazar — gerçek gönderim worker'da. */
    private void enqueue(Long gameId, String kind,
                         String titleTr, String bodyTr,
                         String titleEn, String bodyEn,
                         String type, Integer quarter,
                         Long homeTeamId, Long awayTeamId) {
        if (!fcm.isEnabled() || gameId == null) return;

        final Map<String, String> data = new HashMap<>();
        data.put("type", type);
        data.put("gameId", String.valueOf(gameId));
        data.put("sport", "basketball");
        if (quarter != null) data.put("quarter", String.valueOf(quarter));

        // PERIOD'da çeyrek no dedup'a girer (her çeyrek ayrı bildirim).
        final String dedup = "bk:" + gameId + ":" + kind
                + (quarter != null ? ":" + quarter : "");
        enqueuer.enqueueSport(NotificationOutbox.SPORT_BASKETBALL, kind, type,
                gameId, homeTeamId, awayTeamId,
                new Localized(titleTr, bodyTr, titleEn, bodyEn),
                data, dedup, dedup, false);
    }

    /**
     * Outbox worker'ın çağırdığı GERÇEK gönderim — hata durumunda fırlatır ki
     * worker backoff'la tekrar denesin (eski at-ve-unut davranışının tersi).
     */
    @Transactional(readOnly = true)
    public SendResult sendOutboxRow(String kind, Long gameId,
                                    Long homeTeamId, Long awayTeamId,
                                    String titleTr, String bodyTr,
                                    String titleEn, String bodyEn,
                                    Map<String, String> data) {
        if (!fcm.isEnabled()) {
            throw new IllegalStateException("FCM devre disi");
        }
        final EventKind ek = eventKind(kind);

        // ---- FCM Topics yolu (flag ACIK) — futboldaki gibi dil topic'iyle
        // (lang_tr / lang_en) iki ayri condition: TR alici Turkce, EN alici
        // Ingilizce metin alir. (Eskiden tek TR mesaj herkese gidiyordu.)
        if (useFcmTopics) {
            final String suffix = switch (ek) {
                case START -> "basladi";
                case PERIOD -> "ceyrek";
                case FINAL -> "bitti";
            };
            final List<String> topics = new ArrayList<>();
            if (homeTeamId != null) topics.add(FcmTopics.basketballTeamEvent(homeTeamId, suffix));
            if (awayTeamId != null) topics.add(FcmTopics.basketballTeamEvent(awayTeamId, suffix));
            topics.add(FcmTopics.basketballGame(gameId));
            final String orCond = FcmTopics.orCondition(topics);
            final String tEnTitle = (titleEn != null && !titleEn.isBlank()) ? titleEn : titleTr;
            final String tEnBody = (bodyEn != null && !bodyEn.isBlank()) ? bodyEn : bodyTr;
            fcm.sendToConditionOrThrow(
                    FcmTopics.andLang(orCond, FcmTopics.lang("tr")), titleTr, bodyTr, data);
            fcm.sendToConditionOrThrow(
                    FcmTopics.andLang(orCond, FcmTopics.lang("en")), tEnTitle, tEnBody, data);
            log.info("FCM basketbol {} topic dispatch: gameId={} topics={}", kind, gameId, topics);
            return new SendResult("TOPIC", 0, 0);
        }

        // ---- Token-multicast yolu (varsayilan) — locale'e gore TR/EN ayrimi.
        final Set<String> tr = new LinkedHashSet<>();
        final Set<String> en = new LinkedHashSet<>();
        for (DeviceBasketballSubscription s : subRepo.findRecipientsForGame(gameId)) {
            addByLocale(s.getDeviceToken(), tr, en);
        }
        addTeamRecipients(tr, en, homeTeamId, ek);
        addTeamRecipients(tr, en, awayTeamId, ek);
        en.removeAll(tr);

        if (tr.isEmpty() && en.isEmpty()) {
            log.debug("Basketbol dispatch {}: alıcı yok gameId={}", kind, gameId);
            return new SendResult("NONE", 0, 0);
        }

        final String enTitle = (titleEn != null && !titleEn.isBlank()) ? titleEn : titleTr;
        final String enBody = (bodyEn != null && !bodyEn.isBlank()) ? bodyEn : bodyTr;
        int sent = 0;
        if (!tr.isEmpty()) sent += fcm.sendMulticast(List.copyOf(tr), titleTr, bodyTr, data);
        if (!en.isEmpty()) sent += fcm.sendMulticast(List.copyOf(en), enTitle, enBody, data);
        log.info("FCM basketbol {} dispatch: gameId={} tr={} en={} gönderildi={}",
                kind, gameId, tr.size(), en.size(), sent);
        return new SendResult("TOKEN", tr.size() + en.size(), sent);
    }

    private static EventKind eventKind(String kind) {
        return switch (kind) {
            case NotificationOutbox.KIND_PERIOD -> EventKind.PERIOD;
            case NotificationOutbox.KIND_FINAL -> EventKind.FINAL;
            default -> EventKind.START;
        };
    }

    /** Takım id null değilse, olay tipine göre uygun pref query'sini çalıştır. */
    private void addTeamRecipients(Set<String> tr, Set<String> en, Long teamId, EventKind kind) {
        if (teamId == null) return;
        List<BasketballNotificationPref> prefs = switch (kind) {
            case START -> prefRepo.findRecipientsForStart(teamId);
            case PERIOD -> prefRepo.findRecipientsForPeriod(teamId);
            case FINAL -> prefRepo.findRecipientsForFinal(teamId);
        };
        for (BasketballNotificationPref p : prefs) {
            addByLocale(p.getDeviceToken(), tr, en);
        }
    }

    /** Cihazı locale'ine göre TR ya da EN token listesine ekle (tr* → TR, diğer → EN). */
    private static void addByLocale(MobileDeviceToken t, Set<String> tr, Set<String> en) {
        if (t == null) return;
        final String fcmToken = t.getFcmToken();
        if (fcmToken == null || fcmToken.isBlank()) return;
        final String loc = t.getLocale();
        if (loc != null && loc.toLowerCase().startsWith("tr")) {
            tr.add(fcmToken);
        } else {
            en.add(fcmToken);
        }
    }

    private static int n(Integer v) {
        return v == null ? 0 : v;
    }

    /** Olay tipine göre pref query seçimi (switch dispatch için). */
    private enum EventKind { START, PERIOD, FINAL }
}
