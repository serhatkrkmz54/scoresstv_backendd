package com.scorestv.user;

import com.scorestv.common.ApiException;
import com.scorestv.storage.MinioStorageService;
import com.scorestv.user.dto.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

/**
 * Kullanici profil resmi (avatar) yukleme/kaldirma servisi.
 *
 * <p>Yuklenen gorsel sunucu tarafinda islenir: merkezden KARE kirpilir ve
 * {@value #TARGET_SIZE}px'e olceklenip JPEG olarak MinIO'ya yuklenir. Boylece
 * istemcinin kirpma yapmasi gerekmez, depolama ve bant genisligi sabit kalir.
 * Onceki avatar (varsa) yeni yukleme/kaldirma sirasinda MinIO'dan silinir.
 *
 * <p>Nesne anahtari: {@code avatars/{userId}-{uuid}.jpg}. Herkese acik URL
 * {@link MinioStorageService#publicUrl(String)} ile turetilir ve
 * {@link UserResponse#avatarUrl()} icinde istemciye doner.
 */
@Service
public class AvatarService {

    private static final Logger log = LoggerFactory.getLogger(AvatarService.class);

    /** Cikti avatar kenar uzunlugu (kare). */
    private static final int TARGET_SIZE = 256;
    /** Kabul edilen en buyuk ham dosya boyutu (8 MB). */
    private static final long MAX_BYTES = 8L * 1024 * 1024;

    private final UserRepository userRepository;
    private final MinioStorageService storage;

    public AvatarService(UserRepository userRepository, MinioStorageService storage) {
        this.userRepository = userRepository;
        this.storage = storage;
    }

    /**
     * Kullanicinin avatarini gunceller. Gorsel karelestirilir + boyutlandirilir,
     * MinIO'ya yuklenir, eski avatar (varsa) silinir ve guncel kullanici doner.
     */
    // NOT: BİLEREK @Transactional YOK. Bu metot yavaş bir ağ I/O'su
    // (storage.upload → MinIO PUT) içeriyor; @Transactional olsaydı DB
    // bağlantısı bu PUT boyunca havuzda TUTULURDU. Eşzamanlı/yavaş
    // yüklemelerde havuz tükenip yeni istekler ilk DB erişiminde (findById)
    // bloklanır ve zaman aşımına uğrardı. Burada findById ve save kendi kısa
    // transaction'larını alır; MinIO PUT sırasında hiçbir bağlantı tutulmaz.
    public UserResponse upload(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Dosya boş olamaz.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.badRequest("Görsel en fazla 8 MB olabilir.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw ApiException.badRequest("Yalnızca görsel dosyası yükleyebilirsiniz.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Kullanıcı bulunamadı."));

        byte[] jpeg = toSquareJpeg(file);

        String oldKey = user.getAvatarKey();
        String key = "avatars/" + userId + "-" + UUID.randomUUID() + ".jpg";
        // Anahtar UUID ile benzersiz (her yuklemede yeni) → kalici onbellek
        // guvenli. Cache-Control ile tarayici/CDN avatari bir daha indirmez;
        // gorüntüleme aninda gelir. Yeni yukleme yeni anahtar urettiginden
        // eski onbellek asla eski resmi gostermez.
        String url = storage.upload(key, jpeg, "image/jpeg",
                "public, max-age=31536000, immutable");

        user.setAvatarKey(key);
        userRepository.save(user);

        // Eski avatari en son sil — yeni yukleme basarili olduktan sonra.
        deleteQuietly(oldKey);

        return UserResponse.from(user, url);
    }

    /** Avatari kaldirir — DB anahtarini temizler ve MinIO nesnesini siler.
     *  @Transactional yok — deleteQuietly (MinIO) bağlantıyı tutmasın diye. */
    public UserResponse remove(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Kullanıcı bulunamadı."));
        String oldKey = user.getAvatarKey();
        user.setAvatarKey(null);
        userRepository.save(user);
        deleteQuietly(oldKey);
        return UserResponse.from(user, null);
    }

    /**
     * Ham gorseli merkezden kare kirpip {@value #TARGET_SIZE}px JPEG'e cevirir.
     * Seffaf (PNG) gorsellerde alfa kaybolacagi icin beyaz zemin doldurulur.
     */
    private byte[] toSquareJpeg(MultipartFile file) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(file.getBytes()));
            if (src == null) {
                throw ApiException.badRequest("Görsel çözümlenemedi (geçersiz veya desteklenmeyen format).");
            }
            int side = Math.min(src.getWidth(), src.getHeight());
            int x = (src.getWidth() - side) / 2;
            int y = (src.getHeight() - side) / 2;
            BufferedImage cropped = src.getSubimage(x, y, side, side);

            BufferedImage out = new BufferedImage(TARGET_SIZE, TARGET_SIZE, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, TARGET_SIZE, TARGET_SIZE);
            g.drawImage(cropped, 0, 0, TARGET_SIZE, TARGET_SIZE, null);
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            if (!ImageIO.write(out, "jpg", baos)) {
                throw ApiException.badRequest("Görsel JPEG'e dönüştürülemedi.");
            }
            return baos.toByteArray();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("Görsel işlenemedi: " + e.getMessage());
        }
    }

    /** Nesneyi siler; hata olursa yalniz loglar (akisi bozmaz). */
    private void deleteQuietly(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            storage.delete(key);
        } catch (Exception e) {
            log.warn("Eski avatar silinemedi (key={}): {}", key, e.toString());
        }
    }
}
