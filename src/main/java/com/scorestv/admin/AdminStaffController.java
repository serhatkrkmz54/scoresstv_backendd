package com.scorestv.admin;

import com.scorestv.admin.dto.AdminUserView;
import com.scorestv.admin.dto.CreateEditorRequest;
import com.scorestv.admin.dto.UpdateUserEnabledRequest;
import com.scorestv.admin.dto.UpdateUserRoleRequest;
import com.scorestv.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Editör/yönetici (STAFF) hesap yönetimi — panel "Ayarlar → Editör Yönetimi".
 * SINIF seviyesinde {@code @PreAuthorize("hasRole('ADMIN')")} — tüm uçlar yalniz
 * ADMIN. Sifre hash'leri hicbir yanitta dönmez.
 *
 * NOT: Mevcut {@link com.scorestv.user.AdminUserController} (/api/v1/admin/users
 * — tüm kullanicilar sayfali + logout-all) ile ÇAKIŞMAMASI için AYRI sinif adi
 * (AdminStaffController) ve AYRI taban path (/api/v1/admin/staff) kullanilir.
 */
@RestController
@RequestMapping("/api/v1/admin/staff")
@PreAuthorize("hasRole('ADMIN')")
public class AdminStaffController {

    private final AdminUserService service;

    public AdminStaffController(AdminUserService service) {
        this.service = service;
    }

    /** Staff (EDITOR/ADMIN) hesap listesi. Sifre hash'leri dönmez. */
    @GetMapping
    public List<AdminUserView> list() {
        return service.list();
    }

    /** Yeni editör/yönetici olustur. Ayni e-posta varsa 409. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserView create(@Valid @RequestBody CreateEditorRequest req) {
        return service.create(req);
    }

    /** Rol degistir (EDITOR/ADMIN). Kendi rolünü degistirmek yasak (400). */
    @PatchMapping("/{id}/role")
    public AdminUserView changeRole(@PathVariable Long id,
                                    @Valid @RequestBody UpdateUserRoleRequest req,
                                    @AuthenticationPrincipal CurrentUser currentUser) {
        return service.changeRole(id, req.role(), currentUser.id());
    }

    /** Etkin/pasif degistir. Kendi hesabini devre disi birakmak yasak (400). */
    @PatchMapping("/{id}/enabled")
    public AdminUserView setEnabled(@PathVariable Long id,
                                    @Valid @RequestBody UpdateUserEnabledRequest req,
                                    @AuthenticationPrincipal CurrentUser currentUser) {
        return service.setEnabled(id, req.enabled(), currentUser.id());
    }
}
