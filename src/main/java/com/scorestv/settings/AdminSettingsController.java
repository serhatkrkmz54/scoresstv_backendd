package com.scorestv.settings;

import com.scorestv.settings.dto.LoginSecuritySettingsRequest;
import com.scorestv.settings.dto.SettingResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Calisma zamani ayarlarinin ADMIN tarafindan yonetimi.
 */
@RestController
@RequestMapping("/api/v1/admin/settings")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSettingsController {

    private final SettingsService settingsService;

    public AdminSettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /** Tum ayarlari listeler. */
    @GetMapping
    public List<SettingResponse> all() {
        return settingsService.getAll().entrySet().stream()
                .map(e -> new SettingResponse(e.getKey(), e.getValue()))
                .toList();
    }

    /** Brute-force koruma ayarlarini gunceller (deneme sayisi + kilit suresi). */
    @PutMapping("/login-security")
    public List<SettingResponse> updateLoginSecurity(
            @Valid @RequestBody LoginSecuritySettingsRequest request) {
        settingsService.updateLoginSecurity(
                request.maxFailedAttempts(), request.lockoutMinutes());
        return all();
    }
}
