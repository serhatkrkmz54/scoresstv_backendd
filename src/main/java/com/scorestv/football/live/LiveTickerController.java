package com.scorestv.football.live;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADMIN'in canlı tick'i elle çalıştırması için endpoint — debug/test ve
 * zamanlanmış iş kapalıyken manuel yoklama için.
 */
@RestController
@RequestMapping("/api/v1/admin/api-football/live")
@PreAuthorize("hasRole('ADMIN')")
public class LiveTickerController {

    private final LiveTickerService tickerService;

    public LiveTickerController(LiveTickerService tickerService) {
        this.tickerService = tickerService;
    }

    /**
     * Bir kerelik canlı tick: API'den çek, değişenleri yay, sonucu özet olarak
     * döndür. Loglara da yazılır.
     */
    @PostMapping("/tick")
    public LiveTickerResult tick() {
        return tickerService.tick();
    }
}
