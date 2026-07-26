package com.scorestv.user;

import com.scorestv.admin.dto.UpdateUserEnabledRequest;
import com.scorestv.admin.dto.UpdateUserRoleRequest;
import com.scorestv.common.PageResponse;
import com.scorestv.security.CurrentUser;
import com.scorestv.user.dto.AdminUserListItem;
import com.scorestv.user.dto.AdminUserStatsResponse;
import com.scorestv.user.dto.CreateUserRequest;
import com.scorestv.user.dto.LogoutAllResponse;
import com.scorestv.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADMIN'in kullanici (uye) hesaplari uzerindeki yonetim islemleri — panel
 * "Üyeler" sayfasini besler. {@code /api/v1/admin/staff} (yalniz EDITOR/ADMIN
 * hesaplari) bolumunden farkli olarak burada TUM uyeler yonetilir.
 *
 * <p>Guardrail: admin kendi hesabini kapatamaz / kendi rolünü degistiremez
 * (service katmaninda). Uye devre disi birakildiginda tum oturumlari da iptal
 * edilir — access token süresi (≤15dk) dolunca tamamen dislanir.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AuthService authService;
    private final UserService userService;

    public AdminUserController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    /**
     * Filtreli/sayfali uye listesi.
     * Ornek: {@code ?query=ali&role=USER&enabled=true&provider=google&page=0&size=20}
     */
    @GetMapping
    public PageResponse<AdminUserListItem> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String provider,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return userService.adminSearch(query, role, enabled, provider, pageable);
    }

    /** Üst istatistik kartlari (toplam/aktif/pasif/yeni + rol + saglayici). */
    @GetMapping("/stats")
    public AdminUserStatsResponse stats() {
        return userService.adminStats();
    }

    /** Yeni kullanici olusturur (ADMIN / EDITOR / USER). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.createUser(request);
    }

    /**
     * Üyeyi etkin/pasif yapar. Pasife alinca tum refresh token'lari da iptal
     * edilir — kullanici en gec access token süresi dolunca (≤15dk) dislanir.
     */
    @PatchMapping("/{userId}/enabled")
    public AdminUserListItem setEnabled(@PathVariable Long userId,
                                        @Valid @RequestBody UpdateUserEnabledRequest req,
                                        @AuthenticationPrincipal CurrentUser currentUser) {
        AdminUserListItem out =
                userService.adminSetEnabled(userId, req.enabled(), currentUser.id());
        if (!req.enabled()) {
            authService.revokeAllSessions(userId);
        }
        return out;
    }

    /** Üye rolünü degistirir (USER/EDITOR/ADMIN). */
    @PatchMapping("/{userId}/role")
    public AdminUserListItem changeRole(@PathVariable Long userId,
                                        @Valid @RequestBody UpdateUserRoleRequest req,
                                        @AuthenticationPrincipal CurrentUser currentUser) {
        return userService.adminChangeRole(userId, req.role(), currentUser.id());
    }

    /**
     * Bir kullanicinin tum oturumlarini sonlandirir: tum refresh token'lar
     * iptal edilir. Kullanici, elindeki access token suresi dolunca (en fazla
     * 15 dk) tamamen cikis yapmis olur.
     */
    @PostMapping("/{userId}/logout-all")
    public LogoutAllResponse logoutAll(@PathVariable Long userId) {
        int revoked = authService.revokeAllSessions(userId);
        return new LogoutAllResponse(userId, revoked);
    }
}
