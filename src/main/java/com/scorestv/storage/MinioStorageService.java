package com.scorestv.storage;

import io.minio.BucketExistsArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.SetBucketPolicyArgs;
import io.minio.messages.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * Bir nesneyi <b>akış (stream)</b> ile yükler — içeriği tümüyle belleğe
     * (byte[]) okumadan doğrudan MinIO'ya aktarır. Büyük dosyalar (video) için
     * heap baskısını/OOM riskini önler.
     *
     * @param objectKey   bucket içindeki yol/anahtar
     * @param stream      içerik akışı (çağıran kapatır)
     * @param size        içerik boyutu (byte); bilinmiyorsa &le; 0 verin
     * @param contentType MIME tipi
     * @return nesnenin herkese açık URL'si
     * @throws StorageException yükleme başarısız olursa
     */
    public String upload(String objectKey, InputStream stream, long size, String contentType) {
        // Boyut biliniyorsa partSize -1 (MinIO otomatik hesaplar); bilinmiyorsa
        // objectSize -1 + sabit 10MB parça gerekir.
        long objectSize = size > 0 ? size : -1L;
        long partSize = size > 0 ? -1L : 10L * 1024 * 1024;
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .stream(stream, objectSize, partSize)
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

    /**
     * Verilen önek (prefix) altındaki nesneleri listeler — en yeni en üstte.
     * Klasör girdileri atlanır. MinIO erişilemezse BOŞ liste döner (yumuşak
     * bağımlılık — çağıran çökmez). {@code limit} ile üst sınır uygulanır.
     */
    public List<StoredObject> list(String prefix, int limit) {
        List<StoredObject> out = new ArrayList<>();
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(properties.bucket())
                            .prefix(prefix)
                            .recursive(true)
                            .build());
            for (Result<Item> r : results) {
                Item item = r.get();
                if (item.isDir()) {
                    continue;
                }
                String key = item.objectName();
                Instant lm = null;
                try {
                    if (item.lastModified() != null) {
                        lm = item.lastModified().toInstant();
                    }
                } catch (Exception ignored) {
                    // bazı sürümler lastModified vermez — önemli değil
                }
                out.add(new StoredObject(key, publicUrl(key), item.size(), lm));
            }
        } catch (Exception ex) {
            log.warn("MinIO listeleme başarısız (prefix={}): {}", prefix, ex.getMessage());
            return List.of();
        }
        // En yeni önce (lastModified boşsa sona)
        out.sort((x, y) -> {
            if (x.lastModified() == null && y.lastModified() == null) {
                return 0;
            }
            if (x.lastModified() == null) {
                return 1;
            }
            if (y.lastModified() == null) {
                return -1;
            }
            return y.lastModified().compareTo(x.lastModified());
        });
        if (limit > 0 && out.size() > limit) {
            return new ArrayList<>(out.subList(0, limit));
        }
        return out;
    }

    /** Depodaki bir nesnenin hafif özeti (medya kütüphanesi için). */
    public record StoredObject(String key, String url, long size, Instant lastModified) {
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
