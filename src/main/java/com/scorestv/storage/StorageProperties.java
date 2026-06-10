package com.scorestv.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * application.yml içindeki "scorestv.storage.*" ayarları — MinIO nesne depolama
 * yapılandırması.
 */
@ConfigurationProperties(prefix = "scorestv.storage")
public record StorageProperties(

        /** MinIO S3 API adresi. Local: localhost:9000; prod app içi: http://minio:9000. */
        @DefaultValue("http://localhost:9000") String endpoint,

        /** MinIO erişim anahtarı (kullanıcı adı). */
        @DefaultValue("minioadmin") String accessKey,

        /** MinIO gizli anahtarı (şifre). */
        @DefaultValue("minioadmin") String secretKey,

        /** Görsellerin yükleneceği bucket adı. */
        @DefaultValue("scorestv-media") String bucket,

        /** Görsellerin herkese açık servis edildiği temel adres. */
        @DefaultValue("http://localhost:9000/scorestv-media") String publicUrl
) {
}
