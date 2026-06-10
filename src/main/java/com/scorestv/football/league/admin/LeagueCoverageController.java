package com.scorestv.football.league.admin;

import com.scorestv.common.ApiException;
import com.scorestv.football.domain.League;
import com.scorestv.football.domain.LeagueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ADMIN'in liglerin {@code covered} (kapsam) bayragini yonetmesi icin
 * endpoint'ler.
 *
 * <p><b>"covered" ne demek?</b> Periyodik joblarin (HourlyStandingsJob,
 * DailyLeagueRefreshJob, DailyH2hPrefetchJob, DailyInjuriesJob,
 * DailyPredictionsJob, FinishedMatchFinalSyncJob) hangi ligler icin
 * calisacagini belirler. Ayrica {@code SyncRateLimiter} canli joblari
 * non-covered liglerde 4x yavas tetikler.
 *
 * <p>{@code covered=true} olan ligler "oncelikli" sayilir — verisi her
 * zaman taze, ana sayfada one cikan listede yer alir. {@code covered=false}
 * ligler lazy sync ile karsilanir (kullanici sayfayi acmadikca sync yok).
 *
 * <p>Tipik kullanim: Acilis (seed) sonrasi 20-30 onemli ligi (Super Lig,
 * Premier League, La Liga, CL, EL, vb.) covered isaretle.
 */
@RestController
@RequestMapping("/api/v1/admin/api-football/leagues")
@PreAuthorize("hasRole('ADMIN')")
public class LeagueCoverageController {

    private static final Logger log = LoggerFactory.getLogger(LeagueCoverageController.class);

    private final LeagueRepository leagueRepository;

    public LeagueCoverageController(LeagueRepository leagueRepository) {
        this.leagueRepository = leagueRepository;
    }

    /** Tek bir ligin covered bayragini set eder. */
    @PutMapping("/{id}/coverage")
    @Transactional
    public CoverageToggleResult setCoverage(@PathVariable Long id,
                                            @RequestParam boolean covered) {
        League league = leagueRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Lig bulunamadi: " + id));
        boolean previous = league.isCovered();
        league.setCovered(covered);
        leagueRepository.save(league);
        log.info("Lig coverage degistirildi: leagueId={} '{}' {} → {}",
                id, league.getName(), previous, covered);
        return new CoverageToggleResult(id, league.getName(), previous, covered);
    }

    /**
     * Birden cok ligi tek istekte covered=true isaretler. Acilis seed sonrasi
     * "30 onemli lig" listesini tek seferde set etmek icin kullanisli.
     *
     * <p>Ornek: {@code PUT /admin/.../leagues/coverage/bulk?ids=2,3,39,140,78,135,61,203,144}
     */
    @PutMapping("/coverage/bulk")
    @Transactional
    public BulkCoverageResult setCoverageBulk(@RequestParam List<Long> ids,
                                              @RequestParam(defaultValue = "true") boolean covered) {
        int updated = 0;
        int notFound = 0;
        for (Long id : ids) {
            League league = leagueRepository.findById(id).orElse(null);
            if (league == null) {
                notFound++;
                continue;
            }
            if (league.isCovered() != covered) {
                league.setCovered(covered);
                leagueRepository.save(league);
            }
            updated++;
        }
        log.info("Toplu coverage degisikligi: {} lig {} → {} (eksik: {})",
                updated, !covered, covered, notFound);
        return new BulkCoverageResult(updated, notFound, covered);
    }

    /** Su an covered olan tum ligleri listele. */
    @GetMapping("/coverage")
    public List<LeagueCoverageRow> listCovered() {
        return leagueRepository.findByCoveredTrue().stream()
                .map(l -> new LeagueCoverageRow(
                        l.getId(), l.getName(), l.getType(),
                        l.getCountryName(), l.getCurrentSeason()))
                .toList();
    }

    /**
     * <b>Tum ligleri</b> tek istekte covered isaretler. Manuel id listesi
     * gerekmez. AutoEnqueueScheduler bu ligler icin /teams sync'i yavas yavas
     * kuyruga ekler — junction tablosu kademeli dolar, mobile favori takim
     * secimi tum liglerde tam liste alir.
     *
     * <p>Kullanim: {@code PUT /admin/.../leagues/coverage/all?covered=true}
     *
     * <p>Uyarı: {@code covered=false} ile cagrilirsa TUM ligler kapsam disi
     * birakilir — joblar duser. Ihtiyatla kullan.
     */
    @PutMapping("/coverage/all")
    @Transactional
    public BulkAllCoverageResult setCoverageAll(
            @RequestParam(defaultValue = "true") boolean covered) {
        // Native update — tek query, hizli.
        int updated = leagueRepository.setCoveredForAll(covered);
        log.info("TUM ligler coverage degistirildi: {} satir → covered={}", updated, covered);
        return new BulkAllCoverageResult(updated, covered);
    }

    public record CoverageToggleResult(
            Long leagueId, String leagueName,
            boolean previous, boolean current) {}

    public record BulkCoverageResult(
            int updated, int notFound, boolean covered) {}

    public record BulkAllCoverageResult(
            int updated, boolean covered) {}

    public record LeagueCoverageRow(
            Long id, String name, String type,
            String country, Integer currentSeason) {}
}
