package com.scorestv.mobile.fcm;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.ApsAlert;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.scorestv.mobile.service.MobileDeviceTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Firebase Admin SDK uzerinden FCM gonderim wrapper'i.
 *
 * <p>Tek mesaj veya bulk (500'e kadar token) gonderim. Gecersiz token
 * tespit edildiginde {@link MobileDeviceTokenService#invalidateToken} ile
 * DB'den silinir (otomatik cleanup).
 *
 * <p>FirebaseMessaging bean'i null ise (service account dosyasi yok)
 * tum metodlar no-op davranir ve loga uyari yazar — backend calismaya
 * devam eder.
 *
 * <p><b>Collapse/replace (iki fazlı bildirim):</b> {@code collapseKey}
 * verilirse Android'de notification {@code tag} + {@code collapse_key},
 * iOS'ta {@code apns-collapse-id} olarak set edilir → aynı anahtarla gelen
 * ikinci bildirim eskisini YERİNDE günceller. {@code silent=true} ise
 * güncelleme sessiz gider (Android düşük öncelik + ses kapalı; iOS
 * {@code apns-priority:5} + ses yok) — kullanıcı ikinci kez titremesin.
 */
@Service
public class FcmMessagingService {

    private static final Logger log = LoggerFactory.getLogger(FcmMessagingService.class);

    /** FCM tek istek maksimum recipient sayisi (multicast limit). */
    private static final int FCM_MAX_TOKENS_PER_REQUEST = 500;

    /** APNs apns-collapse-id header maksimum 64 bayt. */
    private static final int APNS_COLLAPSE_MAX = 64;

    private final FirebaseMessaging messaging; // null olabilir
    private final MobileDeviceTokenService tokenService;

    public FcmMessagingService(
            @Autowired(required = false) FirebaseMessaging messaging,
            MobileDeviceTokenService tokenService) {
        this.messaging = messaging;
        this.tokenService = tokenService;
        if (messaging == null) {
            log.warn("FcmMessagingService olusturuldu ama FirebaseMessaging null — "
                    + "tum gonderim cagrilari no-op olacak.");
        }
    }

    public boolean isEnabled() {
        return messaging != null;
    }

    /**
     * Birden cok cihaza ayni mesaji gonder. 500'lu batch'lere boler.
     *
     * @param tokens cihaz token listesi (anonim cihaz id)
     * @param title  bildirim basligi
     * @param body   bildirim govdesi
     * @param data   ekstra payload — tap edildiginde mobile app route etmek icin
     *               (orn: {"type": "goal", "fixtureId": "12345", "teamId": "549"})
     * @return basariyla gonderilen toplam sayi
     */
    public int sendMulticast(List<String> tokens, String title, String body,
                             Map<String, String> data) {
        if (messaging == null) {
            log.debug("FCM disabled — sendMulticast no-op (recipients={})", tokens.size());
            return 0;
        }
        if (tokens.isEmpty()) return 0;

        int totalSent = 0;
        // 500'lu batch'lere bol
        for (int i = 0; i < tokens.size(); i += FCM_MAX_TOKENS_PER_REQUEST) {
            int end = Math.min(i + FCM_MAX_TOKENS_PER_REQUEST, tokens.size());
            List<String> chunk = tokens.subList(i, end);
            totalSent += sendChunk(chunk, title, body, data, false, null, false);
        }
        return totalSent;
    }

    /**
     * {@link #sendMulticast} ile AYNI, ama sert (batch-seviyesi) FCM hatasında
     * istisnayı YUTMAZ — {@link RuntimeException} olarak fırlatır (outbox worker
     * backoff'la TEKRAR DENEYEBİLSİN diye; FCM UNAVAILABLE/INTERNAL, ağ hatasında
     * bildirim kaybolmaz). Ayrıca collapse/replace: {@code collapseKey} aynı OS
     * bildirimini yerinde günceller, {@code silent=true} sessiz güncellemedir.
     *
     * @param collapseKey OS bildirim slotu (Android tag / APNs collapse-id); null ise normal
     * @param silent      true → sessiz güncelleme (ses yok, düşük öncelik)
     * @return başarıyla gönderilen alıcı sayısı (0 = alıcı yok demek olabilir)
     * @throws RuntimeException sert FCM hatasında (retry sinyali)
     */
    public int sendMulticastOrThrow(List<String> tokens, String title, String body,
                                    Map<String, String> data,
                                    String collapseKey, boolean silent) {
        if (messaging == null) {
            throw new IllegalStateException("FCM devre disi (FirebaseMessaging null)");
        }
        if (tokens.isEmpty()) return 0;
        int totalSent = 0;
        for (int i = 0; i < tokens.size(); i += FCM_MAX_TOKENS_PER_REQUEST) {
            int end = Math.min(i + FCM_MAX_TOKENS_PER_REQUEST, tokens.size());
            List<String> chunk = tokens.subList(i, end);
            totalSent += sendChunk(chunk, title, body, data, true, collapseKey, silent);
        }
        return totalSent;
    }

    /**
     * Bir FCM <b>condition</b>'a (topic kombinasyonu) TEK mesaj gönderir —
     * fan-out'u Google yapar (alıcı DB sorgusu + multicast YOK). FCM Topics
     * yolu için kullanılır.
     *
     * @param condition örn. {@code 't549_gol' in topics || 'fix12345' in topics}
     * @throws RuntimeException sert FCM hatasında (outbox retry sinyali)
     */
    public void sendToConditionOrThrow(String condition, String title, String body,
                                       Map<String, String> data) {
        sendToConditionOrThrow(condition, title, body, data, null, false);
    }

    /** {@link #sendToConditionOrThrow(String, String, String, Map)} + collapse/replace. */
    public void sendToConditionOrThrow(String condition, String title, String body,
                                       Map<String, String> data,
                                       String collapseKey, boolean silent) {
        if (messaging == null) {
            throw new IllegalStateException("FCM devre disi (FirebaseMessaging null)");
        }
        try {
            Message.Builder builder = Message.builder()
                    .setCondition(condition)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .setAndroidConfig(_android(collapseKey, silent, data))
                    .setApnsConfig(_apns(title, body, collapseKey, silent, data));
            if (data != null && !data.isEmpty()) {
                builder.putAllData(data);
            }
            String id = messaging.send(builder.build());
            log.info("FCM condition gonderildi: condition=[{}] id={} collapse={} silent={}",
                    condition, id, collapseKey, silent);
        } catch (FirebaseMessagingException ex) {
            log.error("FCM condition basarisiz (condition=[{}]): {}", condition, ex.getMessage(), ex);
            throw new RuntimeException("FCM condition gonderim hatasi: " + ex.getMessage(), ex);
        }
    }

    private int sendChunk(List<String> tokens, String title, String body,
                          Map<String, String> data, boolean throwOnError,
                          String collapseKey, boolean silent) {
        try {
            MulticastMessage.Builder builder = MulticastMessage.builder()
                    .addAllTokens(tokens)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .setAndroidConfig(_android(collapseKey, silent, data))
                    .setApnsConfig(_apns(title, body, collapseKey, silent, data));
            if (data != null && !data.isEmpty()) {
                builder.putAllData(data);
            }
            BatchResponse response = messaging.sendEachForMulticast(builder.build());

            // Gecersiz token'lari topla ve sil.
            List<String> invalidTokens = collectInvalidTokens(response, tokens);
            for (String invalid : invalidTokens) {
                try {
                    tokenService.invalidateToken(invalid);
                } catch (RuntimeException ex) {
                    log.warn("Token invalidation hatasi: {}", ex.getMessage());
                }
            }

            log.info("FCM multicast: gonderildi={}, hata={}, gecersiz_silindi={} collapse={} silent={}",
                    response.getSuccessCount(), response.getFailureCount(),
                    invalidTokens.size(), collapseKey, silent);
            return response.getSuccessCount();
        } catch (FirebaseMessagingException ex) {
            log.error("FCM multicast basarisiz: {}", ex.getMessage(), ex);
            if (throwOnError) {
                // Outbox worker retry edebilsin diye sinyal ver.
                throw new RuntimeException("FCM gonderim hatasi: " + ex.getMessage(), ex);
            }
            return 0;
        }
    }

    /**
     * Android bildirim yapısı. collapseKey → notification {@code tag} +
     * {@code collapse_key} (aynı tag eskiyi değiştirir). silent → düşük öncelik +
     * ses kapalı (kanal HIGH önemdeyse bile heads-up bastırılır).
     */
    private AndroidConfig _android(String collapseKey, boolean silent, Map<String, String> data) {
        // Kanal seçimi: silent → düşük önemli 'scorestv_updates' (sessiz güncelleme).
        // Değilse data'daki 'androidChannel' (olay sesi olan kanal, örn.
        // scorestv_goal) — yoksa 'scorestv_default'. Eski app'te özel kanal yoksa
        // FCM manifest default'una (scorestv_default) düşer → geriye uyumlu.
        final String channel = silent
                ? "scorestv_updates"
                : _dataOr(data, "androidChannel", "scorestv_default");
        AndroidNotification.Builder n = AndroidNotification.builder()
                .setChannelId(channel);
        if (collapseKey != null) {
            n.setTag(collapseKey);
        }
        // Sessizlik ASIL olarak KANAL ile saglanir (silent → 'scorestv_updates'
        // düşük önemli kanal, ses/titreşim yok). Mesaj önceliği de NORMAL yapılır
        // (heads-up bastırılır). AndroidNotification.Builder'da ayrı bir "notif
        // priority" setter'i firebase-admin 9.x'te bu şekilde yok — kanal yeterli.
        AndroidConfig.Builder b = AndroidConfig.builder()
                .setPriority(silent ? AndroidConfig.Priority.NORMAL : AndroidConfig.Priority.HIGH)
                .setNotification(n.build());
        if (collapseKey != null) {
            b.setCollapseKey(collapseKey);
        }
        return b.build();
    }

    /**
     * iOS/APNs yapısı. collapseKey → {@code apns-collapse-id} header (aynı id
     * eskiyi Notification Center'da değiştirir; max 64 bayt). silent → ses yok +
     * {@code apns-priority:5} (sessiz güncelleme).
     */
    private ApnsConfig _apns(String title, String body, String collapseKey, boolean silent,
                            Map<String, String> data) {
        Aps.Builder aps = Aps.builder()
                .setAlert(ApsAlert.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build());
        if (!silent) {
            // Olay sesi (data'daki 'iosSound', örn. goal.wav) — yoksa 'default'.
            // Dosya bundle'da yoksa (eski app) iOS varsayilana duser → uyumlu.
            aps.setSound(_dataOr(data, "iosSound", "default"));
        }
        ApnsConfig.Builder b = ApnsConfig.builder().setAps(aps.build());
        b.putHeader("apns-push-type", "alert");
        b.putHeader("apns-priority", silent ? "5" : "10");
        if (collapseKey != null) {
            String cid = collapseKey.length() > APNS_COLLAPSE_MAX
                    ? collapseKey.substring(0, APNS_COLLAPSE_MAX)
                    : collapseKey;
            b.putHeader("apns-collapse-id", cid);
        }
        return b.build();
    }

    /** data[key] doluysa onu, degilse def dondurur. */
    private static String _dataOr(Map<String, String> data, String key, String def) {
        if (data == null) return def;
        final String v = data.get(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    /**
     * Gecersiz token'lari (UNREGISTERED / INVALID_ARGUMENT) topla.
     * Bunlar uninstall edilmis ya da yeniden register olmus cihazlar — DB'den
     * silinir.
     */
    private List<String> collectInvalidTokens(BatchResponse response, List<String> tokens) {
        List<String> invalid = new ArrayList<>();
        List<SendResponse> responses = response.getResponses();
        for (int i = 0; i < responses.size(); i++) {
            SendResponse r = responses.get(i);
            if (r.isSuccessful()) continue;
            FirebaseMessagingException ex = r.getException();
            if (ex == null) continue;
            MessagingErrorCode code = ex.getMessagingErrorCode();
            if (code == MessagingErrorCode.UNREGISTERED
                    || code == MessagingErrorCode.INVALID_ARGUMENT) {
                invalid.add(tokens.get(i));
            }
        }
        return invalid;
    }
}
