package com.scorestv.volleyball.notify;

import com.scorestv.mobile.domain.MobileDeviceToken;
import com.scorestv.mobile.domain.VolleyballNotificationPref;
import com.scorestv.mobile.domain.VolleyballNotificationPrefRepository;
import com.scorestv.mobile.fcm.FcmMessagingService;
import com.scorestv.mobile.fcm.FcmTopics;
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
 * Voleybol maclari icin FCM push gonderir: mac basladi, set bitti (skorlu),
 * mac bitti. Basketbol {@code BasketballNotificationService}'in voleybol esi.
 *
 * <p><b>TR + EN:</b> her bildirim iki dilde uretilir; token-multicast yolunda
 * alicilar cihaz locale'ine gore ({@code MobileDeviceToken.locale}) ayrilip
 * dogru dil gonderilir. (Topic yolu locale ayirmaz → TR gider.)
 *
 * <p>Tum dispatch'ler {@code @Async}. Set bazli escalation: {@code dispatchSetEnd}
 * her tamamlanan set icin tek seferlik bildirim gonderir (last_notified_period).
 */
@Service
public class VolleyballNotificationService {

    private static final Logger log =
            LoggerFactory.getLogger(VolleyballNotificationService.class);

    private final FcmMessagingService fcm;
    private final DeviceVolleyballSubscriptionRepository subRepo;
    private final VolleyballNotificationPrefRepository prefRepo;
    private final boolean useFcmTopics;

    public VolleyballNotificationService(
            FcmMessagingService fcm,
            DeviceVolleyballSubscriptionRepository subRepo,
            VolleyballNotificationPrefRepository prefRepo,
            @Value("${scorestv.notify.use-fcm-topics:false}") boolean useFcmTopics) {
        this.fcm = fcm;
        this.subRepo = subRepo;
        this.prefRepo = prefRepo;
        this.useFcmTopics = useFcmTopics;
    }

    /** NS→canli: mac basladi. */
    @Async("notifyExecutor")
    @Transactional(readOnly = true)
    public void dispatchStart(Long gameId, Long homeTeamId, Long awayTeamId,
                              String home, String away) {
        send(gameId,
                "🏐 Maç başladı!", "%s - %s başladı".formatted(home, away),
                "🏐 Game started!", "%s vs %s has started".formatted(home, away),
                "vb_start", null, homeTeamId, awayTeamId, EventKind.START);
    }

    /** Set bitti — o ana kadarki set skoruyla birlikte. */
    @Async("notifyExecutor")
    @Transactional(readOnly = true)
    public void dispatchSetEnd(Long gameId, Long homeTeamId, Long awayTeamId,
                               String home, String away,
                               int set, Integer homeSets, Integer awaySets) {
        String body = "%s %d-%d %s".formatted(home, n(homeSets), n(awaySets), away);
        send(gameId,
                "🏐 %d. set bitti".formatted(set), body,
                "🏐 End of set %d".formatted(set), body,
                "vb_set", set, homeTeamId, awayTeamId, EventKind.PERIOD);
    }

    /** →FT/AW: mac bitti (final set skoru). */
    @Async("notifyExecutor")
    @Transactional(readOnly = true)
    public void dispatchFinal(Long gameId, Long homeTeamId, Long awayTeamId,
                              String home, String away,
                              Integer homeSets, Integer awaySets) {
        String body = "%s %d-%d %s".formatted(home, n(homeSets), n(awaySets), away);
        send(gameId,
                "🏐 Maç bitti", body,
                "🏐 Final", body,
                "vb_final", null, homeTeamId, awayTeamId, EventKind.FINAL);
    }

    private void send(Long gameId, String titleTr, String bodyTr,
                      String titleEn, String bodyEn,
                      String type, Integer set,
                      Long homeTeamId, Long awayTeamId,
                      EventKind kind) {
        if (!fcm.isEnabled() || gameId == null) return;

        final Map<String, String> data = new HashMap<>();
        data.put("type", type);
        data.put("gameId", String.valueOf(gameId));
        data.put("sport", "volleyball");
        if (set != null) data.put("set", String.valueOf(set));

        if (useFcmTopics) {
            final String suffix = switch (kind) {
                case START -> "basladi";
                case PERIOD -> "set";
                case FINAL -> "bitti";
            };
            final List<String> topics = new ArrayList<>();
            if (homeTeamId != null) topics.add(FcmTopics.volleyballTeamEvent(homeTeamId, suffix));
            if (awayTeamId != null) topics.add(FcmTopics.volleyballTeamEvent(awayTeamId, suffix));
            topics.add(FcmTopics.volleyballGame(gameId));
            try {
                fcm.sendToConditionOrThrow(FcmTopics.orCondition(topics), titleTr, bodyTr, data);
                log.info("FCM voleybol {} topic dispatch: gameId={} topics={}",
                        type, gameId, topics);
            } catch (RuntimeException ex) {
                log.warn("Voleybol topic dispatch hata gameId={}: {}", gameId, ex.getMessage());
            }
            return;
        }

        final Set<String> tr = new LinkedHashSet<>();
        final Set<String> en = new LinkedHashSet<>();
        for (DeviceVolleyballSubscription s : subRepo.findRecipientsForGame(gameId)) {
            addByLocale(s.getDeviceToken(), tr, en);
        }
        addTeamRecipients(tr, en, homeTeamId, kind);
        addTeamRecipients(tr, en, awayTeamId, kind);
        en.removeAll(tr);

        if (tr.isEmpty() && en.isEmpty()) {
            log.debug("Voleybol dispatch {}: alici yok gameId={}", type, gameId);
            return;
        }

        int sent = 0;
        if (!tr.isEmpty()) sent += fcm.sendMulticast(List.copyOf(tr), titleTr, bodyTr, data);
        if (!en.isEmpty()) sent += fcm.sendMulticast(List.copyOf(en), titleEn, bodyEn, data);
        log.info("FCM voleybol {} dispatch: gameId={} tr={} en={} gonderildi={}",
                type, gameId, tr.size(), en.size(), sent);
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
