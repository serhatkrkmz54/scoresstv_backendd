package com.scorestv.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;

/**
 * MinIO (S3 uyumlu) nesne depolama servisi.
 *
 * <p>Logo/görsel gibi statik dosyalar buraya yüklenir ve herkese açık URL ile
 * servis edilir. MinIO <b>yumuşak bağımlılıktır</b>: erişilemezse uygulama
 * çökmez, yalnızca uyarı loglanır — görsel aynalama o süre çalışmaz, gerisi çalışır.
 */
@Service
public class MinioStorageService {

    private static final Logger log = LoggerFactory.getLogger(MinioStorageService.class);

    private final StorageProperties properties;
    private final MinioClient minioClient;

    public MinioStorageService(StorageProperties properties) {
        this.properties = properties;
        this.minioClient = MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    /**
     * Uygulama hazır olduğunda bucket'ın varlığından emin olur; yoksa oluşturup
     * herkese-açık-okuma politikası uygular. MinIO erişilemezse yalnızca uyarı
     * loglanır.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureBucket() {
        String bucket = properties.bucket();
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (exists) {
                log.info("MinIO bucket hazır: {}", bucket);
                return;
            }
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                    .bucket(bucket)
                    .config(publicReadPolicy(bucket))
                    .build());
            log.info("MinIO bucket oluşturuldu (herkese açık okuma): {}", bucket);
        } catch (Exception ex) {
            log.warn("MinIO'ya ulaşılamadı, nesne depolama şu an devre dışı: {}",
                    ex.getMessage());
        }
    }

    /**
     * Bir nesneyi bucket'a yükler ve herkese açık URL'sini döner.
     *
     * @param objectKey   bucket içindeki yol/anahtar, örn. "teams/33.png"
     * @param data        dosya içeriği
     * @param contentType MIME tipi, örn. "image/png"
     * @return nesnenin herkese açık URL'si
     * @throws StorageException yükleme başarısız olursa
     */
    public String upload(String objectKey, byte[] data, String contentType) {
        try (ByteArrayInputStream stream = new ByteArrayInputStream(data)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .stream(stream, data.length, -1)
                    .contentType(contentType)
                    .build());
            return publicUrl(objectKey);
        } catch (Exception ex) {
            throw new StorageException("Nesne yüklenemedi: " + objectKey, ex);
        }
    }

    /** Bir nesneyi siler. Hata olursa StorageException firlatir. */
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .build());
        } catch (Exception ex) {
            throw new StorageException("Nesne silinemedi: " + objectKey, ex);
        }
    }

    /** Bir nesne anahtarının herkese açık URL'sini üretir. */
    public String publicUrl(String objectKey) {
        String base = properties.publicUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + objectKey;
    }

    /** Verilen bucket için "herkese açık okuma" S3 politikası (JSON). */
    private static String publicReadPolicy(String bucket) {
        return """
                {
                  "Version": "2012-10-17",
                  "Statement": [
                    {
                      "Effect": "Allow",
                      "Principal": {"AWS": ["*"]},
                      "Action": ["s3:GetObject"],
                      "Resource": ["arn:aws:s3:::%s/*"]
                    }
                  ]
                }""".formatted(bucket);
    }
}
