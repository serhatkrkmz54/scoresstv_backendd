package com.scorestv.game;

import com.scorestv.mobile.domain.MobileDeviceToken;
import com.scorestv.mobile.domain.MobileDeviceTokenRepository;
import com.scorestv.mobile.fcm.FcmMessagingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Oyun yarismasi cozuldugunde her KAZANAN kullaniciya kisisel FCM push gonderir
 * ("3/5 tahminin tuttu, +820 Scores Puani kazandin").
 *
 * <p><b>Async + commit-sonrasi:</b> {@link GameResolutionService} coin dagitimini
 * tx icinde yapar, sonunda {@link GameResolvedNotificationEvent} yayinlar. Bu
 * dinleyici {@code AFTER_COMMIT} fazinda + {@code @Async} calisir → FCM I/O
 * cozumleme transaction'ini bloke etmez ve rollback'te yanlis bildirim gitmez.
 *
 * <p><b>Hedefleme:</b> her kullanicinin {@code app_user_id} ile eslesen, master
 * bildirimi acik cihazlari. Cihazlar {@code locale}'e (tr/en) gore gruplanir.
 */
@Service
public class GameResultNotificationService {

    private static final Logger log =
            LoggerFactory.getLogger(GameResultNotificationService.class);

    private final MobileDeviceTokenRepository deviceRepository;
    private final FcmMessagingService fcm;

    public GameResultNotificationService(MobileDeviceTokenRepository deviceRepository,
                                         FcmMessagingService fcm) {
        this.deviceRepository = deviceRepository;
        this.fcm = fcm;
    }

    @Async
    // AFTER_COMMIT dinleyicide @Transactional yalnizca REQUIRES_NEW / NOT_SUPPORTED
    // olabilir (orijinal tx zaten commit oldu). Token okumasi icin taze read-only tx.
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGameResolved(GameResolvedNotificationEvent event) {
        if (!fcm.isEnabled() || event.results() == null || event.results().isEmpty()) {
            return;
        }
        int sent = 0;
        for (GameResolvedNotificationEvent.UserResult r : event.results()) {
            try {
                sent += notifyUser(event, r);
            } catch (Exception ex) {
                log.warn("Oyun sonucu bildirim hatasi (user={}): {}",
                        r.userId(), ex.getMessage());
            }
        }
        log.info("Oyun sonucu bildirimleri: comp={} kazanan={} gonderilen-grup={}",
                event.competitionId(), event.results().size(), sent);
    }

    /** Tek kullanicinin cihazlarina (locale gruplu) push. Gonderilen grup sayisi. */
    private int notifyUser(GameResolvedNotificationEvent event,
                           GameResolvedNotificationEvent.UserResult r) {
        if (r.userId() == null) return 0;
        List<MobileDeviceToken> devices =
                deviceRepository.findByAppUserIdAndNotificationsEnabledTrue(r.userId());
        if (devices.isEmpty()) return 0;

        Map<String, Set<String>> byLocale = new HashMap<>();
        for (MobileDeviceToken t : devices) {
            if (t.getFcmToken() == null) continue;
            byLocale.computeIfAbsent(localeOf(t.getLocale()), k -> new LinkedHashSet<>())
                    .add(t.getFcmToken());
        }

        final Map<String, String> data = data(event.competitionId());
        int groups = 0;
        for (Map.Entry<String, Set<String>> e : byLocale.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            final boolean en = "en".equals(e.getKey());
            final String title = title(event, en);
            final String body = body(r, en);
            fcm.sendMulticast(new ArrayList<>(e.getValue()), title, body, data);
            groups++;
        }
        return groups;
    }

    private String title(GameResolvedNotificationEvent event, boolean en) {
        String name = en
                ? (event.titleEn() != null && !event.titleEn().isBlank()
                        ? event.titleEn() : event.titleTr())
                : event.titleTr();
        if (name == null || name.isBlank()) {
            return en ? "🎯 Results are in!" : "🎯 Sonuçlar açıklandı!";
        }
        return "🎯 " + name;
    }

    private String body(GameResolvedNotificationEvent.UserResult r, boolean en) {
        if (en) {
            return String.format(Locale.ROOT,
                    "%d/%d picks correct · +%d Scores Points earned! 🎉",
                    r.correct(), r.graded(), r.coins());
        }
        return String.format(Locale.ROOT,
                "%d/%d tahminin tuttu · +%d Scores Puanı kazandın! 🎉",
                r.correct(), r.graded(), r.coins());
    }

    /** FCM data payload — tap edilince mobile oyun ekranina yonlendirir. */
    private Map<String, String> data(Long competitionId) {
        Map<String, String> d = new HashMap<>();
        d.put("type", "game_result");
        if (competitionId != null) {
            d.put("competitionId", String.valueOf(competitionId));
        }
        return d;
    }

    /** Cihaz locale'ini "tr" ya da "en"e indirger (default tr). */
    private static String localeOf(String raw) {
        if (raw == null || raw.isBlank()) return "tr";
        return raw.toLowerCase(Locale.ROOT).startsWith("en") ? "en" : "tr";
    }
}
