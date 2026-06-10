package com.scorestv.football.web;

import com.scorestv.football.sync.FixtureSyncService;
import com.scorestv.football.web.dto.FixtureDatesResponse;
import com.scorestv.football.web.dto.FixtureDayResponse;
import com.scorestv.football.web.dto.FixtureSummary;
import com.scorestv.football.web.dto.LiveFixturesResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Anasayfa fikstür listesi için public (kimlik doğrulaması gerektirmeyen)
 * endpoint. Veri tamamen kendi veritabanımızdan gelir; API-Football'a gidilmez.
 */
@RestController
@RequestMapping("/api/v1/fixtures")
public class PublicFixtureController {

    private final FixtureQueryService queryService;
    private final FixtureSyncService syncService;

    public PublicFixtureController(FixtureQueryService queryService,
                                   FixtureSyncService syncService) {
        this.queryService = queryService;
        this.syncService = syncService;
    }

    /**
     * Belirli bir günün maçlarını lige göre gruplu döner.
     *
     * <p>Önceki günden hâlâ canlı oynanan maçlar (örn. 23:00 başlayıp gece
     * yarısını aşan) bu günün listesine misafir olarak eklenir; bitince
     * otomatik düşer.
     *
     * @param date   ISO tarih (yyyy-MM-dd); verilmezse site saatine göre bugün
     * @param status filtre — "all" | "live" | "upcoming" | "finished"
     *               (varsayılan "all"; bilinmeyen değer "all" sayılır)
     * @param lang   görüntüleme dili: "tr" → Türkçe adlar (girilmişse), aksi
     *               halde "en" (varsayılan) → İngilizce kaynak adlar
     */
    @GetMapping
    public FixtureDayResponse byDate(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false, defaultValue = "all") String status,
            @RequestParam(required = false, defaultValue = "en") String lang) {
        LocalDate target = (date != null) ? date : queryService.today();
        LocalDate today = queryService.today();
        // Tarih bazina gore async finalize tetikle:
        // - Gecmis: 30dk debounce (PastDateFinalizeJob da gece tarar)
        // - Bugun: 60sn debounce (pull-to-refresh ile hizli yenileme)
        if (target.isBefore(today)) {
            syncService.ensureDateFinalizedAsync(target);
        } else if (target.equals(today)) {
            syncService.ensureTodayFinalizedAsync(target);
        }
        return queryService.getFixturesByDate(
                target,
                FixtureStatusFilter.parse(status),
                "tr".equalsIgnoreCase(lang));
    }

    /**
     * Anasayfa tarih şeridi: bugün ± {@code days} gün için (toplam, canlı)
     * maç sayıları. Frontend bunu üst tarih şeridini doldurmak için çağırır.
     *
     * @param days bugünün önünden/arkasından kaç gün (1-30 aralığında kırpılır)
     * @param lang "tr" → Türkçe gün adları (Bugün/Yarın/Pzt...), aksi halde EN
     */
    @GetMapping("/dates")
    public FixtureDatesResponse dates(
            @RequestParam(required = false, defaultValue = "7") int days,
            @RequestParam(required = false, defaultValue = "en") String lang) {
        return queryService.getDates(days, "tr".equalsIgnoreCase(lang));
    }

    /**
     * Şu an oynanan tüm canlı maçların kompakt listesi — canlı banner / mobil
     * widget için. Sık polling'e uygun, kısa cache'lidir.
     *
     * @param lang "tr" → adlar/durum/round Türkçe; aksi halde EN
     */
    @GetMapping("/live")
    public LiveFixturesResponse live(
            @RequestParam(required = false, defaultValue = "en") String lang) {
        return queryService.getLive("tr".equalsIgnoreCase(lang));
    }

    /**
     * Belirli ID'lerdeki maclari ozet listesi olarak doner.
     *
     * <p>Mobile <b>Favoriler</b> tabi icin — favori fixture id listesi cihazda
     * lokal tutulur (SharedPreferences); favoriler sayfasi acildiginda bu
     * endpoint'ten tek seferde tum favori maclarin FixtureSummary'si cekilir.
     *
     * <p><b>Maks 50 id</b> — DOS koruma. ID'ler ?ids=1,2,3 formatinda.
     * <p>Siralama: kickoffAt ASC (yaklasan/bugun ust).
     *
     * @param ids virgülle ayrilmis fixture id listesi
     * @param lang "tr" / "en" (varsayilan "en")
     */
    @GetMapping("/by-ids")
    public List<FixtureSummary> byIds(
            @RequestParam("ids") List<Long> ids,
            @RequestParam(required = false, defaultValue = "en") String lang) {
        return queryService.byIds(ids, "tr".equalsIgnoreCase(lang));
    }
}
