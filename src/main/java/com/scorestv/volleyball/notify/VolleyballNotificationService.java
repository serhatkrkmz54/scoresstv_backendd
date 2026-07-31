package com.scorestv.volleyball.notify;

import com.scorestv.mobile.domain.MobileDeviceToken;
import com.scorestv.mobile.domain.VolleyballNotificationPref;
import com.scorestv.mobile.domain.VolleyballNotificationPrefRepository;
import com.scorestv.mobile.fcm.FcmMessagingService;
import com.scorestv.mobile.fcm.FcmTopics;
import com.scorestv.mobile.notify.NotificationDispatcherService.SendResult;
import com.scorestv.mobile.notify.NotificationMessageBuilder.Localized;
import com.scorestv.mobile.notify.NotificationOutbox;
import com.scorestv.mobile.notify.NotificationOutboxEnqueuer;
import com.scorestv.volleyball.domain.DeviceVolleyballSubscription;
import com.scorestv.volleyball.domain.DeviceVolleyballSubscriptionRepository;
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
 * Voleybol maclari icin FCM push: mac basladi, set bitti (skorlu), mac bitti.
 * Basketbol {@code BasketballNotificationService}'in voleybol esi — teslim
 * ayni OUTBOX'tan gecer: dispatch metodlari mesaji render edip
 * {@code sport=volleyball} satiri olarak kuyruga yazar; worker backoff'lu
 * retry ile {@link #sendOutboxRow} uzerinden gonderir (eskiden at-ve-unut idi).
 *
 * <p><b>TR + EN:</b> her bildirim iki dilde uretilir; token-multicast yolunda
 * alicilar cihaz locale'ine gore ayrilip dogru dil gonderilir. (Topic yolu
 * locale ayirmaz → TR gider.)
 */
@Service
public class VolleyballNotificationService {

    private static final Logger log =
            LoggerFactory.getLogger(VolleyballNotificationService.class);

    private final FcmMessagingService fcm;
    private final DeviceVolleyballSubscriptionRepository subRepo;
    private final VolleyballNotificationPrefRepository prefRepo;
    private final NotificationOutboxEnqueuer enqueuer;
    private final boolean useFcmTopics;

    public VolleyballNotificationService(
            FcmMessagingService fcm,
            DeviceVolleyballSubscriptionRepository subRepo,
            VolleyballNotificationPrefRepository prefRepo,
            NotificationOutboxEnqueuer enqueuer,
            @Value("${scorestv.notify.use-fcm-topics:false}") boolean useFcmTopics) {
        this.fcm = fcm;
        this.subRepo = subRepo;
        this.prefRepo = prefRepo;
        this.enqueuer = enqueuer;
        this.useFcmTopics = useFcmTopics;
    }

    /** NS→canli: mac basladi. */
    @Async("notifyExecutor")
    public void dispatchStart(Long gameId, Long homeTeamId, Long awayTeamId,
                              String home, String away) {
        enqueue(gameId, NotificationOutbox.KIND_START,
                "🏐 Maç başladı!", "%s - %s başladı".formatted(home, away),
                "🏐 Game started!", "%s vs %s has started".formatted(home, away),
                "vb_start", null, homeTeamId, awayTeamId);
    }

    /** Set bitti — o ana kadarki set skoruyla birlikte. */
    @Async("notifyExecutor")
    public void dispatchSetEnd(Long gameId, Long homeTeamId, Long awayTeamId,
                               String home, String away,
                               int set, Integer homeSets, Integer awaySets) {
        String body = "%s %d-%d %s".formatted(home, n(homeSets), n(awaySets), away);
        enqueue(gameId, NotificationOutbox.KIND_PERIOD,
                "🏐 %d. set bitti".formatted(set), body,
                "🏐 End of set %d".formatted(set), body,
                "vb_set", set, homeTeamId, awayTeamId);
    }

    /** →FT/AW: mac bitti (final set skoru). */
    @Async("notifyExecutor")
    public void dispatchFinal(Long gameId, Long homeTeamId, Long awayTeamId,
                              String home, String away,
                              Integer homeSets, Integer awaySets) {
        String body = "%s %d-%d %s".formatted(home, n(homeSets), n(awaySets), away);
        enqueue(gameId, NotificationOutbox.KIND_FINAL,
                "🏐 Maç bitti", body,
                "🏐 Final", body,
                "vb_final", null, homeTeamId, awayTeamId);
    }

    /** Mesaji dondurup outbox'a yazar — gercek gonderim worker'da. */
    private void enqueue(Long gameId, String kind,
                         String titleTr, String bodyTr,
                         String titleEn, String bodyEn,
                         String type, Integer set,
                         Long homeTeamId, Long awayTeamId) {
        if (!fcm.isEnabled() || gameId == null) return;

        final Map<String, String> data = new HashMap<>();
        data.put("type", type);
        data.put("gameId", String.valueOf(gameId));
        data.put("sport", "volleyball");
        if (set != null) data.put("set", String.valueOf(set));

        // PERIOD'da set no dedup'a girer (her set ayri bildirim).
        final String dedup = "vb:" + gameId + ":" + kind
                + (set != null ? ":" + set : "");
        enqueuer.enqueueSport(NotificationOutbox.SPORT_VOLLEYBALL, kind, type,
                gameId, homeTeamId, awayTeamId,
                new Localized(titleTr, bodyTr, titleEn, bodyEn),
                data, dedup, dedup, false);
    }

    /**
     * Outbox worker'in cagirdigi GERCEK gonderim — hata durumunda firlatir ki
     * worker backoff'la tekrar denesin.
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

        // Futboldaki gibi dil topic'iyle (lang_tr / lang_en) iki ayri condition:
        // TR alici Turkce, EN alici Ingilizce metin alir.
        if (useFcmTopics) {
            final String suffix = switch (ek) {
                case START -> "basladi";
                case PERIOD -> "set";
                case FINAL -> "bitti";
            };
            final List<String> topics = new ArrayList<>();
            if (homeTeamId != null) topics.add(FcmTopics.volleyballTeamEvent(homeTeamId, suffix));
            if (awayTeamId != null) topics.add(FcmTopics.volleyballTeamEvent(awayTeamId, suffix));
            topics.add(FcmTopics.volleyballGame(gameId));
            final String orCond = FcmTopics.orCondition(topics);
            final String tEnTitle = (titleEn != null && !titleEn.isBlank()) ? titleEn : titleTr;
            final String tEnBody = (bodyEn != null && !bodyEn.isBlank()) ? bodyEn : bodyTr;
            fcm.sendToConditionOrThrow(
                    FcmTopics.andLang(orCond, FcmTopics.lang("tr")), titleTr, bodyTr, data);
            fcm.sendToConditionOrThrow(
                    FcmTopics.andLang(orCond, FcmTopics.lang("en")), tEnTitle, tEnBody, data);
            log.info("FCM voleybol {} topic dispatch: gameId={} topics={}", kind, gameId, topics);
            return new SendResult("TOPIC", 0, 0);
        }

        final Set<String> tr = new LinkedHashSet<>();
        final Set<String> en = new LinkedHashSet<>();
        for (DeviceVolleyballSubscription s : subRepo.findRecipientsForGame(gameId)) {
            addByLocale(s.getDeviceToken(), tr, en);
        }
        addTeamRecipients(tr, en, homeTeamId, ek);
        addTeamRecipients(tr, en, awayTeamId, ek);
        en.removeAll(tr);

        if (tr.isEmpty() && en.isEmpty()) {
            log.debug("Voleybol dispatch {}: alici yok gameId={}", kind, gameId);
            return new SendResult("NONE", 0, 0);
        }

        final String enTitle = (titleEn != null && !titleEn.isBlank()) ? titleEn : titleTr;
        final String enBody = (bodyEn != null && !bodyEn.isBlank()) ? bodyEn : bodyTr;
        int sent = 0;
        if (!tr.isEmpty()) sent += fcm.sendMulticast(List.copyOf(tr), titleTr, bodyTr, data);
        if (!en.isEmpty()) sent += fcm.sendMulticast(List.copyOf(en), enTitle, enBody, data);
        log.info("FCM voleybol {} dispatch: gameId={} tr={} en={} gonderildi={}",
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

    private void addTeamRecipients(Set<String> tr, Set<String> en, Long teamId, EventKind kind) {
        if (teamId == null) return;
        List<VolleyballNotificationPref> prefs = switch (kind) {
            case START -> prefRepo.findRecipientsForStart(teamId);
            case PERIOD -> prefRepo.findRecipientsForPeriod(teamId);
            case FINAL -> prefRepo.findRecipientsForFinal(teamId);
        };
        for (VolleyballNotificationPref p : prefs) {
            addByLocale(p.getDeviceToken(), tr, en);
        }
    }

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

    private enum EventKind { START, PERIOD, FINAL }
}
