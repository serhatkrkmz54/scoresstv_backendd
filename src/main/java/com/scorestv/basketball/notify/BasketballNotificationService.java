package com.scorestv.basketball.notify;

import com.scorestv.basketball.domain.DeviceBasketballSubscription;
import com.scorestv.basketball.domain.DeviceBasketballSubscriptionRepository;
import com.scorestv.mobile.domain.BasketballNotificationPref;
import com.scorestv.mobile.domain.BasketballNotificationPrefRepository;
import com.scorestv.mobile.domain.MobileDeviceToken;
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
 * Basketbol maçları için FCM push gönderir: maç başladı, çeyrek bitti
 * (skorlu), maç bitti. Football'un {@code NotificationDispatcherService}'inden
 * tamamen ayrı (ayrı abonelik tablosu, ayrı topic/type).
 *
 * <p><b>TR + EN:</b> her bildirim iki dilde üretilir; token-multicast yolunda
 * alıcılar cihaz locale'ine göre ({@code MobileDeviceToken.locale}) ayrılıp
 * doğru dil gönderilir. (Topic yolu locale ayırmaz → TR gider.)
 *
 * <p>Tüm dispatch'ler {@code @Async} — sync tick'ini bekletmez.
 */
@Service
public class BasketballNotificationService {

    private static final Logger log =
            LoggerFactory.getLogger(BasketballNotificationService.class);

    private final FcmMessagingService fcm;
    private final DeviceBasketballSubscriptionRepository subRepo;
    private final BasketballNotificationPrefRepository prefRepo;
    /** FCM Topics yolu acik mi? (scorestv.notify.use-fcm-topics) — futbolla ayni flag. */
    private final boolean useFcmTopics;

    public BasketballNotificationService(
            FcmMessagingService fcm,
            DeviceBasketballSubscriptionRepository subRepo,
            BasketballNotificationPrefRepository prefRepo,
            @Value("${scorestv.notify.use-fcm-topics:false}") boolean useFcmTopics) {
        this.fcm = fcm;
        this.subRepo = subRepo;
        this.prefRepo = prefRepo;
        this.useFcmTopics = useFcmTopics;
    }

    /** NS→canlı: maç başladı. */
    @Async
    @Transactional(readOnly = true)
    public void dispatchStart(Long gameId, Long homeTeamId, Long awayTeamId,
                              String home, String away) {
        send(gameId,
                "🏀 Maç başladı!", "%s - %s başladı".formatted(home, away),
                "🏀 Game started!", "%s vs %s has started".formatted(home, away),
                "bk_start", null, homeTeamId, awayTeamId, EventKind.START);
    }

    /** Çeyrek bitti — o ana kadarki toplam skorla birlikte. */
    @Async
    @Transactional(readOnly = true)
    public void dispatchPeriodEnd(Long gameId, Long homeTeamId, Long awayTeamId,
                                  String home, String away,
                                  int quarter, Integer homeTotal,
                                  Integer awayTotal) {
        String body = "%s %d-%d %s".formatted(home, n(homeTotal), n(awayTotal), away);
        send(gameId,
                "🏀 %d. çeyrek bitti".formatted(quarter), body,
                "🏀 End of Q%d".formatted(quarter), body,
                "bk_period", quarter, homeTeamId, awayTeamId, EventKind.PERIOD);
    }

    /** →FT/AOT: maç bitti (final skor). */
    @Async
    @Transactional(readOnly = true)
    public void dispatchFinal(Long gameId, Long homeTeamId, Long awayTeamId,
                              String home, String away,
                              Integer homeTotal, Integer awayTotal) {
        String body = "%s %d-%d %s".formatted(home, n(homeTotal), n(awayTotal), away);
        send(gameId,
                "🏀 Maç bitti", body,
                "🏀 Final", body,
                "bk_final", null, homeTeamId, awayTeamId, EventKind.FINAL);
    }

    private void send(Long gameId, String titleTr, String bodyTr,
                      String titleEn, String bodyEn,
                      String type, Integer quarter,
                      Long homeTeamId, Long awayTeamId,
                      EventKind kind) {
        if (!fcm.isEnabled() || gameId == null) return;

        final Map<String, String> data = new HashMap<>();
        data.put("type", type);
        data.put("gameId", String.valueOf(gameId));
        data.put("sport", "basketball");
        if (quarter != null) data.put("quarter", String.valueOf(quarter));

        // ---- FCM Topics yolu (flag ACIK) — locale ayirimi yok, TR gonderilir.
        if (useFcmTopics) {
            final String suffix = switch (kind) {
                case START -> "basladi";
                case PERIOD -> "ceyrek";
                case FINAL -> "bitti";
            };
            final List<String> topics = new ArrayList<>();
            if (homeTeamId != null) topics.add(FcmTopics.basketballTeamEvent(homeTeamId, suffix));
            if (awayTeamId != null) topics.add(FcmTopics.basketballTeamEvent(awayTeamId, suffix));
            topics.add(FcmTopics.basketballGame(gameId));
            try {
                fcm.sendToConditionOrThrow(FcmTopics.orCondition(topics), titleTr, bodyTr, data);
                log.info("FCM basketbol {} topic dispatch: gameId={} topics={}",
                        type, gameId, topics);
            } catch (RuntimeException ex) {
                log.warn("Basketbol topic dispatch hata gameId={}: {}", gameId, ex.getMessage());
            }
            return;
        }

        // ---- Token-multicast yolu (varsayilan) — locale'e gore TR/EN ayrimi.
        final Set<String> tr = new LinkedHashSet<>();
        final Set<String> en = new LinkedHashSet<>();
        for (DeviceBasketballSubscription s : subRepo.findRecipientsForGame(gameId)) {
            addByLocale(s.getDeviceToken(), tr, en);
        }
        addTeamRecipients(tr, en, homeTeamId, kind);
        addTeamRecipients(tr, en, awayTeamId, kind);
        en.removeAll(tr);

        if (tr.isEmpty() && en.isEmpty()) {
            log.debug("Basketbol dispatch {}: alıcı yok gameId={}", type, gameId);
            return;
        }

        int sent = 0;
        if (!tr.isEmpty()) sent += fcm.sendMulticast(List.copyOf(tr), titleTr, bodyTr, data);
        if (!en.isEmpty()) sent += fcm.sendMulticast(List.copyOf(en), titleEn, bodyEn, data);
        log.info("FCM basketbol {} dispatch: gameId={} tr={} en={} gönderildi={}",
                type, gameId, tr.size(), en.size(), sent);
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
