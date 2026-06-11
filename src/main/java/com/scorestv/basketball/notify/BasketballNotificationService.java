package com.scorestv.basketball.notify;

import com.scorestv.basketball.domain.DeviceBasketballSubscription;
import com.scorestv.basketball.domain.DeviceBasketballSubscriptionRepository;
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
 * Favori basketbol maçları için FCM push gönderir: maç başladı, çeyrek bitti
 * (skorlu), maç bitti. Football'un {@code NotificationDispatcherService}'inden
 * tamamen ayrı (ayrı abonelik tablosu, ayrı topic/type).
 *
 * <p>Tüm dispatch'ler {@code @Async} — sync tick'ini bekletmez. Recipient
 * lookup'ı {@link DeviceBasketballSubscriptionRepository}'den (favori maçlar).
 * Mesaj metni çağıran tarafından çıkarılan primitive verilerden kurulur
 * (entity geçirilmez → async thread'de lazy-init sorunu olmaz).
 */
@Service
public class BasketballNotificationService {

    private static final Logger log =
            LoggerFactory.getLogger(BasketballNotificationService.class);

    private final FcmMessagingService fcm;
    private final DeviceBasketballSubscriptionRepository subRepo;

    public BasketballNotificationService(FcmMessagingService fcm,
                                         DeviceBasketballSubscriptionRepository subRepo) {
        this.fcm = fcm;
        this.subRepo = subRepo;
    }

    /** NS→canlı: maç başladı. */
    @Async
    @Transactional(readOnly = true)
    public void dispatchStart(Long gameId, String home, String away) {
        send(gameId, "🏀 Maç başladı!",
                "%s - %s başladı".formatted(home, away),
                "bk_start", null);
    }

    /** Çeyrek bitti — o ana kadarki toplam skorla birlikte. */
    @Async
    @Transactional(readOnly = true)
    public void dispatchPeriodEnd(Long gameId, String home, String away,
                                  int quarter, Integer homeTotal, Integer awayTotal) {
        String title = "🏀 %d. çeyrek bitti".formatted(quarter);
        String body = "%s %d-%d %s".formatted(home, n(homeTotal), n(awayTotal), away);
        send(gameId, title, body, "bk_period", quarter);
    }

    /** →FT/AOT: maç bitti (final skor). */
    @Async
    @Transactional(readOnly = true)
    public void dispatchFinal(Long gameId, String home, String away,
                              Integer homeTotal, Integer awayTotal) {
        send(gameId, "🏀 Maç bitti",
                "%s %d-%d %s".formatted(home, n(homeTotal), n(awayTotal), away),
                "bk_final", null);
    }

    private void send(Long gameId, String title, String body,
                      String type, Integer quarter) {
        if (!fcm.isEnabled() || gameId == null) return;

        List<DeviceBasketballSubscription> recipients = subRepo.findRecipientsForGame(gameId);
        if (recipients.isEmpty()) {
            log.debug("Basketbol dispatch {}: alıcı yok gameId={}", type, gameId);
            return;
        }

        Set<String> tokens = new LinkedHashSet<>();
        for (DeviceBasketballSubscription s : recipients) {
            tokens.add(s.getDeviceToken().getFcmToken());
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

    private static int n(Integer v) {
        return v == null ? 0 : v;
    }
}
