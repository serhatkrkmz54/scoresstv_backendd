package com.scorestv.admin;

import com.scorestv.admin.dto.AdminUserView;
import com.scorestv.admin.dto.CreateEditorRequest;
import com.scorestv.common.ApiException;
import com.scorestv.user.Role;
import com.scorestv.user.User;
import com.scorestv.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Editör/yönetici hesap yönetimi (yalniz ADMIN). Panel "Ayarlar → Editör
 * Yönetimi" bölümünü besler.
 *
 * <p>Yalniz STAFF (EDITOR/ADMIN) hesaplari listelenir/yönetilir; mobil USER
 * kitlesi bu bölümde GÖRÜNMEZ. Sifreler uygulamanin {@link PasswordEncoder}'i
 * (BCrypt) ile hash'lenir; hash disari asla sizmaz ({@link AdminUserView}).
 *
 * <p>Guardrail: bir admin kendi rolünü degistiremez / kendi hesabini devre disi
 * birakamaz (kendini kilitlemeyi önler).
 */
@Service
public class AdminUserService {

    /** Panelden yönetilebilen roller — mobil USER hesaplari haric. */
    private static final Set<Role> STAFF_ROLES = Set.of(Role.EDITOR, Role.ADMIN);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<AdminUserView> list() {
        return userRepository.findByRoleInOrderByEmailAsc(STAFF_ROLES).stream()
                .map(AdminUserView::from)
                .toList();
    }

    @Transactional
    public AdminUserView create(CreateEditorRequest req) {
        requireStaffRole(req.role());
        String email = req.email().toLowerCase().trim();
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("Bu e-posta zaten kayıtlı");
        }
        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(req.password()))
                .displayName(req.displayName().trim())
                .role(req.role())
                .enabled(true)
                .build();
        return AdminUserView.from(userRepository.save(user));
    }

    @Transactional
    public AdminUserView changeRole(Long targetId, Role role, Long currentUserId) {
        requireStaffRole(role);
        if (targetId.equals(currentUserId)) {
            throw ApiException.badRequest("Kendi rolünüzü değiştiremezsiniz.");
        }
        User user = userRepository.findById(targetId)
                .orElseThrow(() -> ApiException.notFound("Kullanıcı bulunamadı"));
        requireStaffRole(user.getRole()); // yalniz staff hesaplari panelden yönetilir
        user.setRole(role);
        return AdminUserView.from(userRepository.save(user));
    }

    @Transactional
    public AdminUserView setEnabled(Long targetId, boolean enabled, Long currentUserId) {
        if (targetId.equals(currentUserId)) {
            throw ApiException.badRequest("Kendi hesabınızı devre dışı bırakamazsınız.");
        }
        User user = userRepository.findById(targetId)
                .orElseThrow(() -> ApiException.notFound("Kullanıcı bulunamadı"));
        requireStaffRole(user.getRole());
        user.setEnabled(enabled);
        return AdminUserView.from(userRepository.save(user));
    }

    private void requireStaffRole(Role role) {
        if (!STAFF_ROLES.contains(role)) {
            throw ApiException.badRequest(
                    "Bu bölümden yalnızca EDITOR veya ADMIN yönetilebilir.");
        }
    }
}
