package com.scorestv.user;

import com.scorestv.common.ApiException;
import com.scorestv.config.ScorestvProperties;
import com.scorestv.security.AppleTokenVerifier;
import com.scorestv.security.GoogleTokenVerifier;
import com.scorestv.security.JwtService;
import com.scorestv.security.LoginAttemptService;
import com.scorestv.user.dto.AppleLoginRequest;
import com.scorestv.user.dto.AuthResponse;
import com.scorestv.user.dto.ChangePasswordRequest;
import com.scorestv.user.dto.GoogleLoginRequest;
import com.scorestv.user.dto.LoginRequest;
import com.scorestv.user.dto.RegisterRequest;
import com.scorestv.user.dto.TokenRequest;
import com.scorestv.user.dto.UpdateProfileRequest;
import com.scorestv.user.dto.UserResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final AppleTokenVerifier appleTokenVerifier;
    private final Duration refreshTtl;
    private final ApplicationEventPublisher eventPublisher;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       LoginAttemptService loginAttemptService,
                       GoogleTokenVerifier googleTokenVerifier,
                       AppleTokenVerifier appleTokenVerifier,
                       ScorestvProperties props,
                       ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
        this.googleTokenVerifier = googleTokenVerifier;
        this.appleTokenVerifier = appleTokenVerifier;
        this.refreshTtl = props.security().jwt().refreshTokenTtl();
        this.eventPublisher = eventPublisher;
    }

    /** Herkese acik kayit. Yeni kullanici her zaman USER rolu ile olusur. */
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().toLowerCase().trim();
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("Bu e-posta zaten kayıtlı");
        }
        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(req.password()))
                .displayName(req.displayName().trim())
                .birthDate(req.birthDate())
                .country(req.country() == null ? null : req.country().trim())
                .role(Role.USER)
                .enabled(true)
                .build();
        userRepository.save(user);
        eventPublisher.publishEvent(new UserRegisteredEvent(user.getId()));
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        String email = req.email().toLowerCase().trim();

        long lockSeconds = loginAttemptService.lockSecondsRemaining(email);
        if (lockSeconds > 0) {
            long minutes = (lockSeconds / 60) + 1;
            throw ApiException.tooManyRequests(
                    "Çok fazla hatalı giriş denemesi. " + minutes
                            + " dakika sonra tekrar deneyin.");
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user != null && user.getPassword() == null) {
            String provider = socialProviderHint(user);
            throw ApiException.unauthorized(
                    "Bu hesap " + provider + " ile oluşturulmuş. Lütfen "
                            + provider + " ile giriş yapın.");
        }
        if (user == null || !passwordEncoder.matches(req.password(), user.getPassword())) {
            loginAttemptService.recordFailure(email);
            throw ApiException.unauthorized("E-posta veya şifre hatalı");
        }
        if (!user.isEnabled()) {
            throw ApiException.unauthorized("Hesabınız devre dışı bırakılmıştır.");
        }

        loginAttemptService.recordSuccess(email);
        return issueTokens(user);
    }

    /**
     * Google ID token ile giris/kayit.
     * - Daha once Google'a baglanmis kullanici varsa: dogrudan giris.
     * - Ayni e-postayla local hesap varsa: o hesaba Google baglanir (linking).
     * - Hicbiri yoksa: yeni kayit; dogum tarihi + ulke opsiyonel (App Store
     *   5.1.1(v)) — verilirse kaydedilir, verilmezse null gecilir.
     */
    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest req) {
        GoogleTokenVerifier.GoogleUser google = googleTokenVerifier.verify(req.idToken());
        String email = google.email().toLowerCase().trim();

        User user = userRepository.findByGoogleId(google.googleId()).orElse(null);
        if (user == null) {
            user = userRepository.findByEmail(email).orElse(null);
            if (user != null) {
                // Mevcut hesabi Google ile esle.
                user.setGoogleId(google.googleId());
                userRepository.save(user);
            } else {
                // Yeni Google kaydi — dogum tarihi ve ulke opsiyonel.
                user = User.builder()
                        .email(email)
                        .displayName(resolveDisplayName(google, email))
                        .googleId(google.googleId())
                        .birthDate(req.birthDate())
                        .country(req.country() == null ? null : req.country().trim())
                        .role(Role.USER)
                        .enabled(true)
                        .build();
                userRepository.save(user);
                eventPublisher.publishEvent(new UserRegisteredEvent(user.getId()));
            }
        }

        if (!user.isEnabled()) {
            throw ApiException.unauthorized("Hesabınız devre dışı bırakılmıştır.");
        }
        return issueTokens(user);
    }

    private String resolveDisplayName(GoogleTokenVerifier.GoogleUser google, String email) {
        String name = (google.name() != null && !google.name().isBlank())
                ? google.name().trim() : email;
        return name.length() > 100 ? name.substring(0, 100) : name;
    }

    /**
     * Apple ile giriş/kayıt — {@link #loginWithGoogle} ile aynı desen.
     * - Daha önce Apple'a bağlanmış kullanıcı varsa: doğrudan giriş.
     * - Aynı e-postayla local/Google hesap varsa: o hesaba Apple bağlanır.
     * - Hiçbiri yoksa: yeni kayıt; doğum tarihi + ülke opsiyonel (App Store
     *   5.1.1(v)). E-posta ise hesap oluşturmak için gereklidir.
     *
     * <p>Apple özellikleri: {@code email} gizli relay olabilir; {@code name}
     * yalnız ilk girişte client'tan gelir (token'da yoktur).
     */
    @Transactional
    public AuthResponse loginWithApple(AppleLoginRequest req) {
        AppleTokenVerifier.AppleUser apple = appleTokenVerifier.verify(req.identityToken());
        String email = apple.email() != null ? apple.email().toLowerCase().trim() : null;

        User user = userRepository.findByAppleId(apple.appleId()).orElse(null);
        if (user == null) {
            // Aynı e-postayla mevcut hesap varsa Apple ile eşle.
            if (email != null) {
                user = userRepository.findByEmail(email).orElse(null);
            }
            if (user != null) {
                user.setAppleId(apple.appleId());
                userRepository.save(user);
            } else {
                // Yeni Apple kaydı — doğum tarihi ve ülke opsiyonel; e-posta ise
                // hesap oluşturmak için gereklidir.
                if (email == null) {
                    throw ApiException.badRequest(
                            "Apple hesabından e-posta alınamadı; lütfen e-postayı paylaşmayı seçin.");
                }
                user = User.builder()
                        .email(email)
                        .displayName(resolveAppleDisplayName(req.name(), email))
                        .appleId(apple.appleId())
                        .birthDate(req.birthDate())
                        .country(req.country() == null ? null : req.country().trim())
                        .role(Role.USER)
                        .enabled(true)
                        .build();
                userRepository.save(user);
                eventPublisher.publishEvent(new UserRegisteredEvent(user.getId()));
            }
        }

        if (!user.isEnabled()) {
            throw ApiException.unauthorized("Hesabınız devre dışı bırakılmıştır.");
        }
        return issueTokens(user);
    }

    private String resolveAppleDisplayName(String name, String email) {
        String n = (name != null && !name.isBlank()) ? name.trim() : email;
        return n.length() > 100 ? n.substring(0, 100) : n;
    }

    /**
     * Sifresiz (sosyal) bir hesabin hangi saglayici(lar) ile olusturuldugunu
     * kullaniciya gosterilecek metne cevirir — Google / Apple / "Google veya Apple".
     */
    private static String socialProviderHint(User user) {
        boolean google = user.getGoogleId() != null && !user.getGoogleId().isBlank();
        boolean apple = user.getAppleId() != null && !user.getAppleId().isBlank();
        if (google && apple) return "Google veya Apple";
        if (apple) return "Apple";
        return "Google";
    }

    /**
     * Refresh token rotasyonu + reuse detection.
     * Her kullanimda eski token iptal edilir, yeni cift uretilir. Iptal edilmis
     * (kullanilmis) bir token tekrar sunulursa bu token'in calindigi anlamina
     * gelebilir; bu durumda kullanicinin TUM oturumlari kapatilir.
     *
     * noRollbackFor: reuse durumunda once tum oturumlar iptal edilir, sonra
     * hata firlatilir; istisna islemi geri almasin diye rollback kapatildi.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(TokenRequest req) {
        RefreshToken token = refreshTokenRepository.findByToken(req.refreshToken())
                .orElseThrow(() -> ApiException.unauthorized("Geçersiz refresh token"));

        if (token.isRevoked()) {
            // Kullanilmis bir token yeniden geldi -> olasi token hirsizligi.
            refreshTokenRepository.revokeAllByUserId(token.getUserId());
            throw ApiException.unauthorized(
                    "Refresh token yeniden kullanıldı; güvenlik için tüm oturumlar "
                            + "kapatıldı. Lütfen tekrar giriş yapın.");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.unauthorized("Refresh token süresi dolmuş");
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> ApiException.unauthorized("Kullanıcı bulunamadı!"));
        token.setRevoked(true);
        refreshTokenRepository.save(token);
        return issueTokens(user);
    }

    @Transactional
    public void logout(TokenRequest req) {
        refreshTokenRepository.findByToken(req.refreshToken()).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(Long userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> ApiException.unauthorized("Kullanıcı bulunamadı!"));
    }

    /** Kullanicinin kendi profilini gunceller (ad, dogum tarihi, ulke). */
    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Kullanıcı bulunamadı"));
        user.setDisplayName(req.displayName().trim());
        user.setBirthDate(req.birthDate());
        user.setCountry(req.country().trim());
        return UserResponse.from(userRepository.save(user));
    }

    /**
     * Kullanicinin sifresini degistirir. Google ile olusturulmus (sifresiz)
     * hesaplarda calismaz. Basarili olunca guvenlik geregi diger tum oturumlar
     * kapatilir ve cagriyi yapan istemciye yeni token cifti dondurulur.
     */
    @Transactional
    public AuthResponse changePassword(Long userId, ChangePasswordRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Kullanıcı bulunamadı"));
        if (user.getPassword() == null) {
            throw ApiException.badRequest(
                    "Bu hesap " + socialProviderHint(user)
                            + " ile oluşturulmuş; şifresi yok, değiştirilemez.");
        }
        if (!passwordEncoder.matches(req.currentPassword(), user.getPassword())) {
            throw ApiException.unauthorized("Mevcut şifre hatalı");
        }
        if (req.newPassword().equals(req.currentPassword())) {
            throw ApiException.badRequest("Yeni şifre mevcut şifreyle aynı olamaz");
        }
        user.setPassword(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);
        // Guvenlik: sifre degisince mevcut tum refresh token'lar iptal edilir.
        refreshTokenRepository.revokeAllByUserId(userId);
        return issueTokens(user);
    }

    /**
     * Kullanicinin hesabini ve TUM kisisel verilerini KALICI olarak siler
     * (App Store 5.1.1(v) ve Google Play "hesap silme" zorunlulugu).
     *
     * <p>{@code users} satiri silinince veritabanindaki {@code ON DELETE CASCADE}
     * yabanci anahtarlari sayesinde su tablolardaki kayitlar da otomatik silinir:
     * <ul>
     *   <li>{@code refresh_tokens} — tum oturumlar</li>
     *   <li>{@code fixture_comments} — kullanicinin yorumlari</li>
     *   <li>{@code fixture_comment_likes} — yorum begenileri</li>
     * </ul>
     * Sifre sifirlama token'lari Redis'te TTL ile kendiliginden gecer (kullanici
     * degil token bazli). Cihaz push token'lari kullanici degil CIHAZ bazli
     * (anonim) oldugu icin bilincli olarak dokunulmaz.
     *
     * <p>Geri donusu yoktur; cagirmadan once istemci tarafinda kullanici onayi
     * alinmalidir.
     */
    @Transactional
    public void deleteAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Kullanıcı bulunamadı"));
        userRepository.delete(user);
    }

    /** ADMIN: bir kullanicinin tum oturumlarini (refresh token'larini) sonlandirir. */
    @Transactional
    public int revokeAllSessions(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw ApiException.notFound("Kullanıcı bulunamadı");
        }
        return refreshTokenRepository.revokeAllByUserId(userId);
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        RefreshToken refreshToken = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .userId(user.getId())
                .expiresAt(Instant.now().plus(refreshTtl))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);
        return AuthResponse.of(
                accessToken,
                refreshToken.getToken(),
                jwtService.getAccessTtlSeconds(),
                UserResponse.from(user));
    }
}
