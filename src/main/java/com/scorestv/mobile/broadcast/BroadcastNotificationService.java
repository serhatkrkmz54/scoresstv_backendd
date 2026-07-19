package com.scorestv.mobile.broadcast;

import com.scorestv.common.ApiException;
import com.scorestv.mobile.domain.MobileDeviceToken;
import com.scorestv.mobile.domain.MobileDeviceTokenRepository;
import com.scorestv.mobile.fcm.FcmMessagingService;
import com.scorestv.user.User;
import com.scorestv.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Genel (habere/maca bagli OLMAYAN) push bildirim gonderimi — GARANTILI teslim.
 *
 * <p>Akis (cok kullaniciya guvenli):
 * <ol>
 *   <li>{@link #enqueue} — istek aninda yalniz QUEUED satiri yazar ve DONER
 *       (HTTP istegi FCM'i beklemez). Hedef cihaz sayisi hizli bir COUNT ile
 *       doldurulur.</li>
 *   <li>{@link BroadcastNotificationWorker} periyodik olarak QUEUED satirlari
 *       alir ve {@link #deliver} ile gonderir; hata olursa backoff + tekrar.</li>
 * </ol>
 *
 * <p>Alicilar {@link MobileDeviceTokenRepository#findBroadcastRecipients} ile
 * opsiyonel platform (ios/android) + opsiyonel dil (locale on eki) filtresine
 * gore secilir; master {@code notificationsEnabled} kapali cihazlar HARIC.
 */
@Service
public class BroadcastNotificationService {

    private static final Logger log =
            LoggerFactory.getLogger(BroadcastNotificationService.class);

    private final MobileDeviceTokenRepository deviceRepository;
    private final FcmMessagingService fcm;
    private final BroadcastNotificationRepository historyRepository;
    private final UserRepository userRepository;

    public BroadcastNotificationService(MobileDeviceTokenRepository deviceRepository,
                                        FcmMessagingService fcm,
                                        BroadcastNotificationRepository historyRepository,
                                        UserRepository userRepository) {
        this.deviceRepository = deviceRepository;
        this.fcm = fcm;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
    }

    /**
     * TEST gonderimi — yalnizca verilen e-postaya ait hesabin (app_user_id)
     * cihazlarina, SENKRON gonderir (kuyruk yok; tek kullanici birkac cihaz).
     * Genel broadcast'ten farki: herkese GITMEZ, gecmise yazilmaz. Push'un
     * telefonda dogru geldigini test etmek icin.
     *
     * @throws ApiException e-posta ile hesap yoksa ya da hesaba bagli bildirimi
     *                      acik cihaz yoksa (400).
     */
    public TestNotificationResult sendTest(String email, String title, String body,
                                           String link) {
        String normEmail = email == null ? "" : email.trim();
        User user = userRepository.findByEmail(normEmail).orElseThrow(
                () -> ApiException.badRequest(
                        "Bu e-posta ile kayitli kullanici yok: " + normEmail));

        List<String> tokens = deviceRepository
                .findByAppUserIdAndNotificationsEnabledTrue(user.getId())
                .stream()
                .map(MobileDeviceToken::getFcmToken)
                .filter(t -> t != null && !t.isBlank())
                .distinct()
                .toList();

        if (tokens.isEmpty()) {
            throw ApiException.badRequest(
                    "Bu hesaba bagli, bildirimi acik cihaz bulunamadi. Telefonda "
                    + "bu hesapla giris yapip bildirim iznini verdiginden emin ol "
                    + "(gerekirse cikis/giris yapip uygulamayi bir kez ac).");
        }

        Map<String, String> data = new HashMap<>();
        data.put("type", "broadcast");
        if (link != null && !link.isBlank()) {
            data.put("link", link.trim());
        }

        int sent = fcm.sendMulticast(tokens, title.trim(), body.trim(), data);
        log.info("Test bildirimi gonderildi: email={} cihaz={} iletilen={}",
                normEmail, tokens.size(), sent);
        return new TestNotificationResult(normEmail, tokens.size(), sent, fcm.isEnabled());
    }

    /**
     * Bildirimi KUYRUGA alir ve hemen doner (gonderim arka planda yapilir).
     *
     * @param platform ALL ise platform filtresi yok; aksi halde ios/android
     * @param lang     ALL ise dil filtresi yok; aksi halde locale on eki (tr/en)
     */
    @Transactional
    public BroadcastNotification enqueue(String title, String body, String link,
                                         BroadcastPlatform platform, BroadcastLang lang,
                                         Long authorId) {
        BroadcastPlatform p = platform != null ? platform : BroadcastPlatform.ALL;
        BroadcastLang l = lang != null ? lang : BroadcastLang.ALL;

        long targetCount = deviceRepository.countBroadcastRecipients(
                platformFilter(p), langFilter(l));

        BroadcastNotification row = new BroadcastNotification();
        row.setTitle(title);
        row.setBody(body);
        row.setLink(link != null && !link.isBlank() ? link.trim() : null);
        row.setPlatformTarget(p);
        row.setLangTarget(l);
        row.setRecipientCount((int) Math.min(targetCount, Integer.MAX_VALUE));
        row.setSentCount(0);
        row.setStatus(BroadcastNotification.STATUS_QUEUED);
        row.setAttempts(0);
        row.setNextAttemptAt(Instant.now());
        row.setCreatedBy(authorId);
        row.setCreatedAt(Instant.now());
        BroadcastNotification saved = historyRepository.save(row);

        log.info("Broadcast kuyruga alindi: id={} platform={} lang={} hedef~{}",
                saved.getId(), p, l, targetCount);
        return saved;
    }

    /**
     * Bir kuyruk satirini FCM'e gonderir (worker cagirir). Satirin
     * {@code recipientCount}/{@code sentCount} alanlarini gunceller ama KAYDETMEZ
     * (worker durum + kaydi yonetir).
     *
     * @throws RuntimeException gecici toplam basarisizlikta (hicbir cihaza
     *         iletilemedi) — worker backoff'la TEKRAR denesin diye.
     */
    public void deliver(BroadcastNotification row) {
        List<String> tokens = deviceRepository
                .findBroadcastRecipients(
                        platformFilter(row.getPlatformTarget()),
                        langFilter(row.getLangTarget()))
                .stream()
                .map(MobileDeviceToken::getFcmToken)
                .filter(t -> t != null && !t.isBlank())
                .distinct()
                .toList();

        Map<String, String> data = new HashMap<>();
        data.put("type", "broadcast");
        if (row.getLink() != null && !row.getLink().isBlank()) {
            data.put("link", row.getLink().trim());
        }

        int sent = fcm.sendMulticast(tokens, row.getTitle(), row.getBody(), data);
        row.setRecipientCount(tokens.size());
        row.setSentCount(sent);

        // Alici VAR ama hicbirine iletilemediyse: gecici FCM/ag hatasi kabul et
        // ve tekrar denenmesi icin hata firlat (hicbir sey gitmedigi icin
        // yeniden gonderim COPYA yaratmaz). Kismi basari (sent>0) = basarili.
        if (!tokens.isEmpty() && sent == 0) {
            throw new IllegalStateException(
                    "FCM'e hicbir bildirim iletilemedi (gecici hata olabilir)");
        }
    }

    /** En yeni gonderimler (panel gecmisi + durum). */
    @Transactional(readOnly = true)
    public List<BroadcastNotification> history(int limit) {
        int capped = Math.min(Math.max(limit, 1), 200);
        return historyRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, capped));
    }

    /** ALL → null (filtre yok); aksi halde "ios"/"android". */
    private static String platformFilter(BroadcastPlatform p) {
        return p == null || p == BroadcastPlatform.ALL ? null : p.name().toLowerCase();
    }

    /** ALL → null (filtre yok); aksi halde "tr"/"en" (locale on eki). */
    private static String langFilter(BroadcastLang l) {
        return l == null || l == BroadcastLang.ALL ? null : l.name().toLowerCase();
    }
}
