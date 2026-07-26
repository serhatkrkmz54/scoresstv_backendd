package com.scorestv.user;

import com.scorestv.common.ApiException;
import com.scorestv.common.PageResponse;
import com.scorestv.user.dto.AdminUserListItem;
import com.scorestv.user.dto.AdminUserStatsResponse;
import com.scorestv.user.dto.CreateUserRequest;
import com.scorestv.user.dto.UserResponse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ADMIN'in kullanici hesaplari uzerindeki yonetim islemleri
 * (olusturma, sayfali listeleme).
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Yeni kullanici olusturur. Rol (ADMIN/EDITOR/USER) cagiran ADMIN tarafindan secilir. */
    @Transactional
    public UserResponse createUser(CreateUserRequest req) {
        String email = req.email().toLowerCase().trim();
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("Bu e-posta zaten kayıtlı");
        }
        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(req.password()))
                .displayName(req.displayName().trim())
                .role(req.role())
                .birthDate(req.birthDate())
                .country(req.country() != null ? req.country().trim() : null)
                .enabled(true)
                .build();
        return UserResponse.from(userRepository.save(user));
    }

    /** Kullanicilari sayfali doner. */
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> listUsers(Pageable pageable) {
        return PageResponse.from(
                userRepository.findAll(pageable).map(UserResponse::from));
    }

    // ================================================================
    // Panel "Üyeler" — filtreli liste + istatistik + yönetim islemleri
    // ================================================================

    /**
     * Filtreli/sayfali üye listesi. Tum filtreler opsiyonel; null olan
     * uygulanmaz. Specification API — null-parametreli JPQL'in Postgres tip
     * cikarim sorunlarina girmeden dinamik WHERE kurar.
     *
     * @param query    e-posta VEYA gorunen ad icinde arama (case-insensitive)
     * @param role     USER | EDITOR | ADMIN
     * @param enabled  true=aktif, false=pasif
     * @param provider google | apple | local
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminUserListItem> adminSearch(String query, Role role,
                                                       Boolean enabled, String provider,
                                                       Pageable pageable) {
        Specification<User> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (query != null && !query.isBlank()) {
                String like = "%" + query.trim().toLowerCase() + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(root.get("email")), like),
                        cb.like(cb.lower(root.get("displayName")), like)));
            }
            if (role != null) ps.add(cb.equal(root.get("role"), role));
            if (enabled != null) ps.add(cb.equal(root.get("enabled"), enabled));
            if (provider != null && !provider.isBlank()) {
                switch (provider) {
                    case "google" -> ps.add(cb.isNotNull(root.get("googleId")));
                    case "apple" -> ps.add(cb.and(
                            cb.isNull(root.get("googleId")),
                            cb.isNotNull(root.get("appleId"))));
                    case "local" -> ps.add(cb.and(
                            cb.isNull(root.get("googleId")),
                            cb.isNull(root.get("appleId"))));
                    default -> { /* bilinmeyen provider filtresi yok sayilir */ }
                }
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return PageResponse.from(
                userRepository.findAll(spec, pageable).map(AdminUserListItem::from));
    }

    /** Üst istatistik kartlari. */
    @Transactional(readOnly = true)
    public AdminUserStatsResponse adminStats() {
        Map<String, Long> byRole = new LinkedHashMap<>();
        for (Object[] row : userRepository.countGroupByRole()) {
            byRole.put(String.valueOf(row[0]), (Long) row[1]);
        }
        return new AdminUserStatsResponse(
                userRepository.count(),
                userRepository.countByEnabledTrue(),
                userRepository.countByEnabledFalse(),
                userRepository.countByCreatedAtAfter(Instant.now().minus(Duration.ofDays(7))),
                byRole,
                userRepository.countByGoogleIdNotNull(),
                userRepository.countByAppleIdNotNull(),
                userRepository.countByPasswordNotNull());
    }

    /**
     * Üye etkin/pasif degistir (her rol). Admin kendi hesabini kapatamaz.
     * Oturum iptali (refresh token revoke) cagiran controller'da yapilir.
     */
    @Transactional
    public AdminUserListItem adminSetEnabled(Long targetId, boolean enabled, Long currentUserId) {
        if (targetId.equals(currentUserId)) {
            throw ApiException.badRequest("Kendi hesabınızı devre dışı bırakamazsınız.");
        }
        User user = userRepository.findById(targetId)
                .orElseThrow(() -> ApiException.notFound("Kullanıcı bulunamadı"));
        user.setEnabled(enabled);
        return AdminUserListItem.from(userRepository.save(user));
    }

    /** Üye rolü degistir (USER/EDITOR/ADMIN). Admin kendi rolünü degistiremez. */
    @Transactional
    public AdminUserListItem adminChangeRole(Long targetId, Role role, Long currentUserId) {
        if (targetId.equals(currentUserId)) {
            throw ApiException.badRequest("Kendi rolünüzü değiştiremezsiniz.");
        }
        User user = userRepository.findById(targetId)
                .orElseThrow(() -> ApiException.notFound("Kullanıcı bulunamadı"));
        user.setRole(role);
        return AdminUserListItem.from(userRepository.save(user));
    }
}
