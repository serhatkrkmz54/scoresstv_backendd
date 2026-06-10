package com.scorestv.football.status;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-Football entegrasyonunun sagligini ve kota durumunu gosteren ADMIN
 * endpoint'i.
 *
 * <p>Entegrasyon iskeletinin uctan uca calistigini dogrulamak icin bir "smoke
 * test" gorevi gorur: client -> servis -> yanit zarfi -> hata yonetimi zinciri
 * burada denenir. {@code /status} cagrisi gunluk kotadan dusmedigi icin guvenle
 * cagrilabilir.
 */
@RestController
@RequestMapping("/api/v1/admin/api-football")
@PreAuthorize("hasRole('ADMIN')")
public class ApiFootballStatusController {

    private final ApiFootballStatusService statusService;

    public ApiFootballStatusController(ApiFootballStatusService statusService) {
        this.statusService = statusService;
    }

    /** API-Football hesap, abonelik ve gunluk kota bilgisini doner. */
    @GetMapping("/status")
    public ApiFootballStatus status() {
        return statusService.getStatus();
    }
}
