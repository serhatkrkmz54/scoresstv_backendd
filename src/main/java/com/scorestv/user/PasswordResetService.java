package com.scorestv.user;

import com.scorestv.common.ApiException;
import com.scorestv.config.ScorestvProperties;
import com.scorestv.email.EmailService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

/**
 * "Sifremi unuttum" akisi.
 * - Sifirlama token'i Redis'te tutulur: kisa omurlu, tek kullanimlik;
 *   TTL ile kendiliginden silinir (ayri temizlik job'i gerekmez).
 * - forgotPassword hesap varligini sizdirmamak icin her durumda sessizce doner.
 * - Google ile olusturulmus (sifresiz) hesaplara sifirlama e-postasi gonderilmez.
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
}
