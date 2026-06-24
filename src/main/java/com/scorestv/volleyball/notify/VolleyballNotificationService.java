package com.scorestv.volleyball.notify;

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
 * <p>Iki recipient kanali dedup ile birlestirilir:
 * <ol>
 *   <li>{@link DeviceVolleyballSubscriptionRepository} — favori mac aboneleri</li>
 *   <li>{@link VolleyballNotificationPrefRepository} — takim takipcileri</li>
 * </ol>
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
    @Async
    @Transactional(readOnly = true)
    public void dispatchStart(Long gameId, Long homeTeamId, Long awayTeamId,
                              String home, String away) {
        send(gameId, "🏐 Maç başladı!",
                "%s - %s başladı".formatted(home, away),
                "vb_start", null, homeTeamId, awayTeamId, EventKind.START);
    }

    /** Set bitti — o ana kadarki set skoruyla birlikte. */
    @Async
    @Transactional(readOnly = true)
    public void dispatchSetEnd(Long gameId, Long homeTeamId, Long awayTeamId,
                               String home, String away,
                               int set, Integer homeSets, Integer awaySets) {
        String title = "🏐 %d. set bitti".formatted(set);
        String body = "%s %d-%d %s".formatted(home, n(homeSets), n(awaySets), away);
        send(gameId, title, body, "vb_set", set,
                homeTeamId, awayTeamId, EventKind.PERIOD);
    }

    /** →FT/AW: mac bitti (final set skoru). */
    @Async
    @Transactional(readOnly = true)
    public void dispatchFinal(Long gameId, Long homeTeamId, Long awayTeamId,
                              String home, String away,
                              Integer homeSets, Integer awaySets) {
        send(gameId, "🏐 Maç bitti",
                "%s %d-%d %s".formatted(home, n(homeSets), n(awaySets), away),
                "vb_final", null, homeTeamId, awayTeamId, EventKind.FINAL);
    }

    private void send(Long gameId, String title, String body,
                      String type, Integer set,
                      Long homeTeamId, Long awayTeamId,
                      EventKind kind) {
        if (!fcm.isEnabled() || gameId == null) return;

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
            Map<String, String> data = new HashMap<>();
            data.put("type", type);
            data.put("gameId", String.valueOf(gameId));
            data.put("sport", "volleyball");
            if (set != null) data.put("set", String.valueOf(set));
            try {
                fcm.sendToConditionOrThrow(FcmTopics.orCondition(topics), title, body, data);
                log.info("FCM voleybol {} topic dispatch: gameId={} topics={}",
                        type, gameId, topics);
            } catch (RuntimeException ex) {
                log.warn("Voleybol topic dispatch hata gameId={}: {}", gameId, ex.getMessage());
            }
            return;
        }

        Set<String> tokens = new LinkedHashSet<>();
        for (DeviceVolleyballSubscription s : subRepo.findRecipientsForGame(gameId)) {
            tokens.add(s.getDeviceToken().getFcmToken());
        }
        addTeamRecipients(tokens, homeTeamId, kind);
        addTeamRecipients(tokens, awayTeamId, kind);

        if (tokens.isEmpty()) {
            log.debug("Voleybol dispatch {}: alici yok gameId={}", type, gameId);
            return;
        }

        Map<String, String> data = new HashMap<>();
        data.put("type", type);
        data.put("gameId", String.valueOf(gameId));
        data.put("sport", "volleyball");
        if (set != null) data.put("set", String.valueOf(set));

        int sent = fcm.sendMulticast(List.copyOf(tokens), title, body, data);
        log.info("FCM voleybol {} dispatch: gameId={} alici={} gonderildi={}",
                type, gameId, tokens.size(), sent);
    }

    private void addTeamRecipients(Set<String> tokens, Long teamId, EventKind kind) {
        if (teamId == null) return;
        List<VolleyballNotificationPref> prefs = switch (kind) {
            case START -> prefRepo.findRecipientsForStart(teamId);
            case PERIOD -> prefRepo.findRecipientsForPeriod(teamId);
            case FINAL -> prefRepo.findRecipientsForFinal(teamId);
        };
        for (VolleyballNotificationPref p : prefs) {
            tokens.add(p.getDeviceToken().getFcmToken());
        }
    }

    private static int n(Integer v) {
        return v == null ? 0 : v;
    }

    private enum EventKind { START, PERIOD, FINAL }
}
