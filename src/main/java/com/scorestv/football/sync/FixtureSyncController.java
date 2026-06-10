package com.scorestv.football.sync;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * ADMIN'in fikstür senkronunu elle tetiklemesi için endpoint.
 *
 * <p>Özellikle geliştirme aşamasında (zamanlanmış işler kapalıyken) kullanışlıdır:
 * tek bir tarih verilerek yalnızca 1 API isteğiyle tüm boru hattı test edilebilir.
 */
@RestController
@RequestMapping("/api/v1/admin/api-football")
@PreAuthorize("hasRole('ADMIN')")
public class FixtureSyncController {

    private final FixtureSyncService syncService;

    public FixtureSyncController(FixtureSyncService syncService) {
        this.syncService = syncService;
    }

    /**
     * Fikstür senkronunu elle çalıştırır.
     *
     * @param date verilirse yalnızca o tarih senkronlanır (1 API isteği);
     *             verilmezse tüm pencere senkronlanır (~15 API isteği, uzun sürebilir)
     * @return senkron özeti
     */
    @PostMapping("/fixtures/sync")
    public FixtureSyncResult syncFixtures(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (date != null) {
            int upserted = syncService.syncDate(date);
            return new FixtureSyncResult(1, 0, upserted);
        }
        return syncService.syncWindow();
    }
}
