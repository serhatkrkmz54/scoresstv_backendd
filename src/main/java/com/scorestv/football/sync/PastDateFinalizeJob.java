package com.scorestv.football.sync;

import com.scorestv.football.FootballProperties;
import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Gecmis tarihlerde takili kalmis (status hala NS, 1H, HT, 2H, ET vb.) maclari
 * tespit edip o tarihleri yeniden senkronlar — kullanici ziyaret etmesini
 * beklemeden.
 *
 * <p><b>Niye gerekli?</b> {@link FinishedMatchFinalSyncJob} tek mac bazinda
 * 24sa sonra finalize eder. Ama eger maç beklenmedik sebeple "1H" gibi takili
 * kalmissa (API-Football'a sonradan FT yazilmis ama bizim cron arasinda
 * kacirilmis), bu maç ekrandа saatlerce yanlis gosterilir. Bu job o ihtimali
 * gunluk taramayla kapatir.
 *
 * <p><b>Strateji:</b> son 14 gun icinde kickoff'u olan ve hala "supheli"
 * statude (NS/1H/HT/2H/ET/BT/P/LIVE) olan maclari bul. Tarihlerini topla.
 * Her benzersiz tarih icin {@link FixtureSyncService#syncDate} cagir.
 * Boylece o gunun TUM maclarinin statusleri API'den fresh fetch edilir.
 *
 * <p><b>Quota:</b> uygulamada normalde 0-2 tarih cikar (cron her saat
 * calistigi icin); cogu gunlerde job hicbir sey yapmadan biter. Worst case
 * 14 cagri = ~14 API request. Ultra plan'da %0.02.
 */
@Component
@ConditionalOnProperty(name = "scorestv.football.sync.enabled", havingValue = "true")
public class PastDateFinalizeJob {

    private static final Logger log = LoggerFactory.getLogger(PastDateFinalizeJob.class);

    /** Bizim "henuz bitmemis" sayilan statuler — bunlar gecmis tarihlerde takili kalmamali. */
    private static final Set<String> SUSPICIOUS_PAST_STATUSES = Set.of(
            "NS", "TBD",                              // baslamadi
            "1H", "HT", "2H", "ET", "BT", "P", "LIVE", // canli (gecmiste olmamali)
            "SUSP", "INT"                              // suspended/interrupted
    );

    /** Kac gun geriye bakilacak. */
    private static final int LOOKBACK_DAYS = 14;

    private final FixtureRepository fixtureRepository;
    private final FixtureSyncService fixtureSyncService;
    private final FootballProperties properties;

    public PastDateFinalizeJob(FixtureRepository fixtureRepository,
                               FixtureSyncService fixtureSyncService,
                               FootballProperties properties) {
        this.fixtureRepository = fixtureRepository;
        this.fixtureSyncService = fixtureSyncService;
        this.properties = properties;
    }

    /**
     * Her gece 03:00 — gecmis 14 gunde takili kalmis maclari tespit et,
     * tarihleri topla, her birini syncDate ile finalize et.
     */
    @Scheduled(
            cron = "${scorestv.football.sync.past-date-finalize-cron:0 0 3 * * *}",
            zone = "${scorestv.football.sync.timezone:Europe/Istanbul}")
    public void run() {
        ZoneId zone = ZoneId.of(properties.sync().timezone());
        LocalDate today = LocalDate.now(zone);
        Instant from = today.minusDays(LOOKBACK_DAYS).atStartOfDay(zone).toInstant();
        Instant to = today.atStartOfDay(zone).toInstant(); // bugun haric

        // Pencere icinde suspicious statude olan maclari bul
        List<Fixture> stuck = fixtureRepository
                .findByKickoffAtBetweenOrderByKickoffAtAsc(from, to)
                .stream()
                .filter(f -> SUSPICIOUS_PAST_STATUSES.contains(f.getStatusShort()))
                .toList();

        if (stuck.isEmpty()) {
            log.info("PastDateFinalize: takili kalmis mac yok, atlandi.");
            return;
        }

        // Tarihlerini topla (uniq)
        Set<LocalDate> targetDates = new HashSet<>();
        for (Fixture f : stuck) {
            if (f.getKickoffAt() == null) continue;
            targetDates.add(f.getKickoffAt().atZone(zone).toLocalDate());
        }

        log.info("PastDateFinalize: {} takili mac, {} benzersiz tarih sync edilecek",
                stuck.size(), targetDates.size());

        int totalUpserted = 0;
        int datesFailed = 0;
        for (LocalDate date : targetDates) {
            try {
                int n = fixtureSyncService.syncDate(date);
                totalUpserted += n;
                log.info("PastDateFinalize: {} → {} mac upsert", date, n);
            } catch (RuntimeException ex) {
                datesFailed++;
                log.warn("PastDateFinalize basarisiz: {} — {}", date, ex.getMessage());
            }
        }
        log.info("PastDateFinalize bitti: {} tarih islendi, {} basarisiz, "
                + "toplam {} mac upsert", targetDates.size(), datesFailed, totalUpserted);
    }
}
