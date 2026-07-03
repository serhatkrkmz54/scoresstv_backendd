package com.scorestv.football.web;

import com.scorestv.football.web.dto.TvGuideResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * "Canlı Maç Programı / Hangi Kanalda" hub'ı ucu — bir günün futbol maçlarını
 * lige gruplu + her lig için varsayılan TV kanal(lar)ı ile döner.
 *
 * <p>Web'deki haftalık {@code /canli-mac-programi} SEO sayfası buna bağlanır;
 * bugün + sonraki 7 gün, gün gün ({@code date}) çekilir.
 *
 * <p><b>Örnek:</b>
 * <pre>
 *   GET /api/v1/football/tv-guide
 *   GET /api/v1/football/tv-guide?date=2026-07-05&lang=tr&country=TR
 * </pre>
 *
 * <p><b>Auth:</b> public — {@code SecurityConfig} permitAll. Elasticsearch
 * gerektirmez (FixtureLookupController'dan farklı olarak {@code @ConditionalOnProperty}
 * YOKTUR).
 */
@RestController
@RequestMapping("/api/v1/football/tv-guide")
public class TvGuideController {

    private final TvGuideService tvGuideService;

    public TvGuideController(TvGuideService tvGuideService) {
        this.tvGuideService = tvGuideService;
    }

    /**
     * Verilen günün TV programı.
     *
     * @param date    gün (ISO yyyy-MM-dd); verilmezse site saatine göre bugün
     * @param country kullanıcı ülke kodu (kanal çözümü); verilmezse "TR"
     * @param lang    "tr" → takım/lig/kanal adları Türkçe (girilmişse); yoksa "en"
     */
    @GetMapping
    public TvGuideResponse tvGuide(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false, defaultValue = "TR") String country,
            @RequestParam(required = false, defaultValue = "en") String lang) {

        boolean turkish = "tr".equalsIgnoreCase(lang);
        LocalDate day = (date != null) ? date : tvGuideService.today();
        return tvGuideService.getTvGuide(day, country, turkish);
    }
}
