package com.scorestv.mobile.fcm;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApsAlert;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
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
 */
@Service
public class FcmMessagingService {

    private static final Logger log = LoggerFactory.getLogger(FcmMessagingService.class);

    /** FCM tek istek maksimum recipient sayisi (multicast limit). */
    private static final int FCM_MAX_TOKENS_PER_REQUEST = 500;

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
            totalSent += sendChunk(chunk, title, body, data);
        }
        return totalSent;
    }

    private int sendChunk(List<String> tokens, String title, String body,
                          Map<String, String> data) {
        try {
            MulticastMessage.Builder builder = MulticastMessage.builder()
                    .addAllTokens(tokens)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .setNotification(AndroidNotification.builder()
                                    .setChannelId("scorestv_default")
                                    .build())
                            .build())
                    .setApnsConfig(com.google.firebase.messaging.ApnsConfig.builder()
                            .setAps(com.google.firebase.messaging.Aps.builder()
                                    .setAlert(ApsAlert.builder()
                                            .setTitle(title)
                                            .setBody(body)
                                            .build())
                                    .setSound("default")
                                    .build())
                            .build());
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

            log.info("FCM multicast: gonderildi={}, hata={}, gecersiz_silindi={}",
                    response.getSuccessCount(), response.getFailureCount(),
                    invalidTokens.size());
            return response.getSuccessCount();
        } catch (FirebaseMessagingException ex) {
            log.error("FCM multicast basarisiz: {}", ex.getMessage(), ex);
            return 0;
        }
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
