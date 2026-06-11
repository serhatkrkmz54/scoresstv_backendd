package com.scorestv.basketball.web;

import com.scorestv.basketball.BasketballImageMirrorService;
import com.scorestv.basketball.BasketballReferenceService;
import com.scorestv.basketball.BasketballSyncService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ADMIN'in basketbol senkronunu elle tetiklemesi için uçlar.
 *
 * <p>{@code scorestv.basketball.enabled=false} olsa bile çalışır — özellik
 * otomatik açılmadan önce tüm boru hattını (fikstür/canlı/referans) test etmek
 * için kullanışlı. Zamanlanmış işler ise yalnızca enabled=true iken çalışır.
 */
@RestController
@RequestMapping("/api/v1/admin/basketball")
@PreAuthorize("hasRole('ADMIN')")
public class BasketballAdminController {

    private final BasketballSyncService sync;
    private final BasketballReferenceService reference;
    private final BasketballImageMirrorService imageMirror;

    public BasketballAdminController(BasketballSyncService sync,
                                     BasketballReferenceService reference,
                                     BasketballImageMirrorService imageMirror) {
        this.sync = sync;
        this.reference = reference;
        this.imageMirror = imageMirror;
    }

    /**
     * Fikstür senkronu — {@code date} verilirse yalnızca o gün (1 API isteği),
     * verilmezse tüm ±gün pencere (~15 istek).
     */
    @PostMapping("/games/sync")
    public Map<String, Object> syncGames(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        var result = new LinkedHashMap<String, Object>();
        if (date != null) {
            result.put("date", date.toString());
            result.put("upserted", sync.syncDate(date));
            return result;
        }
        var dates = sync.windowDates();
        int total = 0;
        for (LocalDate d : dates) {
            total += sync.syncDate(d); // dış çağrı → her tarih kendi transaction'ı
        }
        result.put("dates", dates.size());
        result.put("upserted", total);
        return result;
    }

    /** Canlı skor senkronu (bugünün maçları). */
    @PostMapping("/games/sync-live")
    public Map<String, Object> syncLive() {
        return Map.of("upserted", sync.syncLive());
    }

    /** Referans seed — ülkeler + ligler. */
    @PostMapping("/reference/sync")
    public Map<String, Object> syncReference() {
        var result = new LinkedHashMap<String, Object>();
        result.put("countries", reference.syncCountries());
        result.put("leagues", reference.syncLeagues());
        return result;
    }

    /** Logo/bayrak aynalama — yeni görselleri CDN'e taşır. */
    @PostMapping("/images/mirror")
    public Map<String, Object> mirrorImages() {
        return Map.of("mirrored", imageMirror.mirrorAll());
    }
}
