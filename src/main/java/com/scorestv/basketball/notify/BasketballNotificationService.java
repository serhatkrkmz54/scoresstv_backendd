package com.scorestv.basketball.notify;

import com.scorestv.basketball.domain.DeviceBasketballSubscription;
import com.scorestv.basketball.domain.DeviceBasketballSubscriptionRepository;
import com.scorestv.mobile.domain.BasketballNotificationPref;
import com.scorestv.mobile.domain.BasketballNotificationPrefRepository;
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
 * Basketbol maçları için FCM push gönderir: maç başladı, çeyrek bitti
 * (skorlu), maç bitti. Football'un {@code NotificationDispatcherService}'inden
 * tamamen ayrı (ayrı abonelik tablosu, ayrı topic/type).
 *
 * <p>A-Faz5: Iki recipient kanalı dedup ile birleştirilir:
 * <ol>
 *   <li>{@link DeviceBasketballSubscriptionRepository} — favori maç aboneleri</li>
 *   <li>{@link BasketballNotificationPrefRepository} — takım takipçileri
 *       (basketbol notif prefs, olay tipine göre)</li>
 * </ol>
 * Aynı cihaz iki kanalda da varsa tek bildirim alır (LinkedHashSet token bazlı
 * dedup).
 *
 * <p>Tüm dispatch'ler {@code @Async} — sync tick'ini bekletmez. Mesaj metni
 * çağıran tarafından çıkarılan primitive verilerden kurulur (entity geçirilmez
 * → async thread'de lazy-init sorunu olmaz).
 */
@Service
public class BasketballNotificationService {

    private static final Logger log =
            LoggerFactory.getLogger(BasketballNotificationService.class);

    private final FcmMessagingService fcm;
    private final DeviceBasketballSubscriptionRepository subRepo;
    private final BasketballNotificationPrefRepository prefRepo;

    public BasketballNotificationService(
            FcmMessagingService fcm,
            DeviceBasketballSubscriptionRepository subRepo,
            BasketballNotificationPrefRepository prefRepo) {
        this.fcm = fcm;
        this.subRepo = subRepo;
        this.prefRepo = prefRepo;
    }

    /** NS→canlı: maç başladı. */
    @Async
    @Transactional(readOnly = true)
    public void dispatchStart(Long gameId, Long homeTeamId, Long awayTeamId,
                              String home, String away) {
        send(gameId, "🏀 Maç başladı!",
                "%s - %s başladı".formatted(home, away),
                "bk_start", null, homeTeamId, awayTeamId, EventKind.START);
    }

    /** Çeyrek bitti — o ana kadarki toplam skorla birlikte. */
    @Async
    @Transactional(readOnly = true)
    public void dispatchPeriodEnd(Long gameId, Long homeTeamId, Long awayTeamId,
                                  String home, String away,
                                  int quarter, Integer homeTotal,
                                  Integer awayTotal) {
        String title = "🏀 %d. çeyrek bitti".formatted(quarter);
        String body = "%s %d-%d %s".formatted(home, n(homeTotal), n(awayTotal), away);
        send(gameId, title, body, "bk_period", quarter,
                homeTeamId, awayTeamId, EventKind.PERIOD);
    }

    /** →FT/AOT: maç bitti (final skor). */
    @Async
    @Transactional(readOnly = true)
    public void dispatchFinal(Long gameId, Long homeTeamId, Long awayTeamId,
                              String home, String away,
                              Integer homeTotal, Integer awayTotal) {
        send(gameId, "🏀 Maç bitti",
                "%s %d-%d %s".formatted(home, n(homeTotal), n(awayTotal), away),
                "bk_final", null, homeTeamId, awayTeamId, EventKind.FINAL);
    }

    private void send(Long gameId, String title, String body,
                      String type, Integer quarter,
                      Long homeTeamId, Long awayTeamId,
                      EventKind kind) {
        if (!fcm.isEnabled() || gameId == null) return;

        // 1) Favori MAÇ aboneleri.
        Set<String> tokens = new LinkedHashSet<>();
        for (DeviceBasketballSubscription s : subRepo.findRecipientsForGame(gameId)) {
            tokens.add(s.getDeviceToken().getFcmToken());
        }

        // 2) Takım takipçileri — home + away için olay tipine göre.
        addTeamRecipients(tokens, homeTeamId, kind);
        addTeamRecipients(tokens, awayTeamId, kind);

        if (tokens.isEmpty()) {
            log.debug("Basketbol dispatch {}: alıcı yok gameId={}", type, gameId);
            return;
        }

        Map<String, String> data = new HashMap<>();
        data.put("type", type);
        data.put("gameId", String.valueOf(gameId));
        data.put("sport", "basketball");
        if (quarter != null) data.put("quarter", String.valueOf(quarter));

        int sent = fcm.sendMulticast(List.copyOf(tokens), title, body, data);
        log.info("FCM basketbol {} dispatch: gameId={} alıcı={} gönderildi={}",
                type, gameId, tokens.size(), sent);
    }

    /** Takım id null değilse, olay tipine göre uygun pref query'sini çalıştır. */
    private void addTeamRecipients(Set<String> tokens, Long teamId, EventKind kind) {
        if (teamId == null) return;
        List<BasketballNotificationPref> prefs = switch (kind) {
            case START -> prefRepo.findRecipientsForStart(teamId);
            case PERIOD -> prefRepo.findRecipientsForPeriod(teamId);
            case FINAL -> prefRepo.findRecipientsForFinal(teamId);
        };
        for (BasketballNotificationPref p : prefs) {
            tokens.add(p.getDeviceToken().getFcmToken());
        }
    }

    private static int n(Integer v) {
        return v == null ? 0 : v;
    }

    /** Olay tipine göre pref query seçimi (switch dispatch için). */
    private enum EventKind { START, PERIOD, FINAL }
}
