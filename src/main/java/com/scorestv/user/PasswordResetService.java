package com.scorestv.user;

import com.scorestv.common.ApiException;
import com.scorestv.config.ScorestvProperties;
import com.scorestv.email.EmailService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

/**
 * "Sifremi unuttum" akisi.
 * - Sifirlama token'i Redis'te tutulur: kisa omurlu, tek kullanimlik;
 *   TTL ile kendiliginden silinir (ayri temizlik job'i gerekmez).
 * - forgotPassword hesap varligini sizdirmamak icin her durumda sessizce doner.
 * - Google ile olusturulmus (sifresiz) hesaplara sifirlama e-postasi gonderilmez.
 *
 * Iki varyant:
 * 1. LINK tabanli (web): {@link #forgotPassword} + {@link #resetPassword} —
 *    UUID token mail'deki baglantida tasinir, web'de /sifre-sifirla acilir.
 * 2. KOD tabanli/OTP (mobil): {@link #requestResetCode} +
 *    {@link #resetPasswordWithCode} — 6 haneli kod mail'le gider, kullanici
 *    uygulamada girer. Deep link gerekmez.
 */
@Service
public class PasswordResetService {

    private static final String TOKEN_PREFIX = "pwreset:token:";
    private static final String COOLDOWN_PREFIX = "pwreset:cooldown:";
    private static final Duration TOKEN_TTL = Duration.ofHours(1);
    private static final Duration COOLDOWN = Duration.ofMinutes(2);
    private static final String IP_PREFIX = "pwreset:ip:";
    private static final int MAX_REQUESTS_PER_IP = 10;
    private static final Duration IP_WINDOW = Duration.ofHours(1);

    // --- Kod tabanli (OTP) akis (mobil) ---
    private static final String CODE_PREFIX = "pwreset:code:";
    private static final String CODE_ATTEMPTS_PREFIX = "pwreset:codeatt:";
    private static final String CODE_COOLDOWN_PREFIX = "pwreset:codecd:";
    private static final Duration CODE_TTL = Duration.ofMinutes(15);
    // Yeni kod ancak 10 dk sonra istenebilir (mobil UI'da da sayaç gosterilir).
    private static final Duration CODE_COOLDOWN = Duration.ofMinutes(10);
    private static final int MAX_CODE_ATTEMPTS = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redis;
    private final EmailService emailService;
    private final String passwordResetUrl;

    public PasswordResetService(UserRepository userRepository,
                                RefreshTokenRepository refreshTokenRepository,
                                PasswordEncoder passwordEncoder,
                                StringRedisTemplate redis,
                                EmailService emailService,
                                ScorestvProperties properties) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.redis = redis;
        this.emailService = emailService;
        this.passwordResetUrl = properties.mail().passwordResetUrl();
    }

    /** Sifre sifirlama baglantisi uretir ve e-posta ile gonderir. */
    @Transactional(readOnly = true)
    public void forgotPassword(String rawEmail, String clientIp) {
        enforceIpLimit(clientIp);
        String email = rawEmail.toLowerCase().trim();
        User user = userRepository.findByEmail(email).orElse(null);
        // Hesap yok ya da Google ile olusturulmus (sifresiz) -> sessizce cik.
        if (user == null || user.getPassword() == null) {
            return;
        }
        // Kisa surede tekrar istek geldiyse yeni e-posta gonderme (spam korumasi).
        if (Boolean.TRUE.equals(redis.hasKey(COOLDOWN_PREFIX + email))) {
            return;
        }
        String token = UUID.randomUUID().toString();
        redis.opsForValue().set(TOKEN_PREFIX + token, String.valueOf(user.getId()), TOKEN_TTL);
        redis.opsForValue().set(COOLDOWN_PREFIX + email, "1", COOLDOWN);
        emailService.sendPasswordResetEmail(email, passwordResetUrl + "?token=" + token);
    }

    /** Ayni IP'den saatte izin verilenden fazla istek gelirse 429 doner. */
    private void enforceIpLimit(String clientIp) {
        String ipKey = IP_PREFIX + clientIp;
        Long count = redis.opsForValue().increment(ipKey);
        if (count != null && count == 1L) {
            redis.expire(ipKey, IP_WINDOW);
        }
        if (count != null && count > MAX_REQUESTS_PER_IP) {
            throw ApiException.tooManyRequests(
                    "Çok fazla şifre sıfırlama isteği. Lütfen bir süre sonra tekrar deneyin.");
        }
    }

    /** Token'i dogrular, sifreyi gunceller, tum oturumlari kapatir. */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        String key = TOKEN_PREFIX + token;
        String userIdValue = redis.opsForValue().get(key);
        if (userIdValue == null) {
            throw ApiException.badRequest("Sıfırlama bağlantısı geçersiz veya süresi dolmuş");
        }
        User user = userRepository.findById(Long.valueOf(userIdValue))
                .orElseThrow(() -> ApiException.badRequest("Kullanıcı bulunamadı"));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        redis.delete(key);                                     // tek kullanimlik
        refreshTokenRepository.revokeAllByUserId(user.getId()); // guvenlik: oturumlar kapanir
    }

    // =====================================================================
    // KOD tabanli (OTP) akis — mobil. Mail'de 6 haneli kod gider; kullanici
    // uygulamadaki ekrana girer. Deep link / web sayfasi gerekmez.
    // =====================================================================

    /** 6 haneli dogrulama kodu uretir ve e-posta ile gonderir (mobil akis). */
    @Transactional(readOnly = true)
    public void requestResetCode(String rawEmail, String clientIp) {
        enforceIpLimit(clientIp);
        String email = rawEmail.toLowerCase().trim();
        User user = userRepository.findByEmail(email).orElse(null);
        // Hesap yok ya da Google/Apple ile olusturulmus (sifresiz) -> sessizce cik.
        if (user == null || user.getPassword() == null) {
            return;
        }
        // Kisa surede tekrar istek geldiyse yeni kod gonderme (spam korumasi).
        if (Boolean.TRUE.equals(redis.hasKey(CODE_COOLDOWN_PREFIX + email))) {
            return;
        }
        String code = generateCode();
        redis.opsForValue().set(CODE_PREFIX + email, code, CODE_TTL);
        redis.delete(CODE_ATTEMPTS_PREFIX + email);          // yeni kod -> deneme sayaci sifir
        redis.opsForValue().set(CODE_COOLDOWN_PREFIX + email, "1", CODE_COOLDOWN);
        emailService.sendPasswordResetCodeEmail(email, code);
    }

    /** Kodu dogrular, sifreyi gunceller, tum oturumlari kapatir (mobil akis). */
    @Transactional
    public void resetPasswordWithCode(String rawEmail, String code, String newPassword) {
        String email = rawEmail.toLowerCase().trim();
        String key = CODE_PREFIX + email;
        String stored = redis.opsForValue().get(key);
        if (stored == null) {
            throw ApiException.badRequest("Kodun süresi dolmuş veya geçersiz. Yeni kod isteyin.");
        }
        // Brute-force korumasi: hatali deneme sayisini sayar; limit asilinca kodu iptal eder.
        String attKey = CODE_ATTEMPTS_PREFIX + email;
        Long attempts = redis.opsForValue().increment(attKey);
        if (attempts != null && attempts == 1L) {
            redis.expire(attKey, CODE_TTL);
        }
        if (attempts != null && attempts > MAX_CODE_ATTEMPTS) {
            redis.delete(key);
            redis.delete(attKey);
            throw ApiException.badRequest("Çok fazla hatalı deneme. Yeni kod isteyin.");
        }
        if (!stored.equals(code == null ? null : code.trim())) {
            throw ApiException.badRequest("Kod hatalı.");
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> ApiException.badRequest("Kullanıcı bulunamadı"));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        redis.delete(key);                                     // tek kullanimlik
        redis.delete(attKey);
        refreshTokenRepository.revokeAllByUserId(user.getId()); // oturumlar kapanir
    }

    /** Sifirdan onde sifirli, 6 haneli sayisal kod ("000000"-"999999"). */
    private String generateCode() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }
}
