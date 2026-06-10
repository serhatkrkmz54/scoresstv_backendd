package com.scorestv.mobile.fcm;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;

/**
 * Firebase Admin SDK init.
 *
 * <p>Service account JSON path'i config'ten alinir:
 * {@code scorestv.firebase.service-account-path}
 *
 * <p>Path classpath: ile baslarsa resource'tan, aksi takdirde file:'tan
 * (production'da gizli dosya disinda mount edilebilir).
 *
 * <p>Yapilandirma yoksa veya dosya bulunamazsa graceful degrade — backend
 * baslatilir ama FCM gonderim devre disi. Loglar uyari verir.
 */
@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${scorestv.firebase.service-account-path:}")
    private String serviceAccountPath;

    private final ResourceLoader resourceLoader;

    public FirebaseConfig(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * FirebaseApp singleton — Spring tarafindan tek instance.
     *
     * <p>Sadece {@code scorestv.firebase.service-account-path} property'si
     * tanimli VE bos degil ise bean register edilir. Bos veya tanimsizsa
     * bean hic olusturulmaz; FcmMessagingService constructor null alir ve
     * gonderim metodlari no-op olur.
     */
    @Bean
    @ConditionalOnProperty(
            name = "scorestv.firebase.service-account-path",
            matchIfMissing = false)
    public FirebaseApp firebaseApp() throws IOException {
        Resource resource = resourceLoader.getResource(serviceAccountPath);
        if (!resource.exists()) {
            throw new IOException("Firebase service account dosyasi bulunamadi: "
                    + serviceAccountPath
                    + " (path config: scorestv.firebase.service-account-path)");
        }
        try (InputStream is = resource.getInputStream()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(is))
                    .build();
            // Tek instance — multi-init exception'i icin kontrol.
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp app = FirebaseApp.initializeApp(options);
                log.info("Firebase Admin SDK basariyla init edildi: project={}",
                        app.getOptions().getProjectId());
                return app;
            }
            return FirebaseApp.getInstance();
        }
    }

    /**
     * FirebaseMessaging bean — sadece FirebaseApp bean'i varsa olusturulur.
     * {@code @ConditionalOnBean} ile transitive: path yoksa bu da yok.
     */
    @Bean
    @ConditionalOnBean(FirebaseApp.class)
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}
