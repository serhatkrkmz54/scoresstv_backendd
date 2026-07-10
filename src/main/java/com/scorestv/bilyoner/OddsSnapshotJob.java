package com.scorestv.bilyoner;

import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Maç öncesi Bilyoner oranlarını kalıcı arşive yazar — value/backtest analizi için.
 *
 * <p>Her ~10 dk çalışır; iki pencere:
 * <ul>
 *   <li><b>opening</b> — kickoff'a ~24 saat kala (çizgi açılışı),</li>
 *   <li><b>closing</b> — kickoff'a ~90 dk kala (piyasanın en keskin tahmini).</li>
 * </ul>
 *
 * <p>Hiç API-Football çağrısı YOK — yalnızca zaten bellekte olan Bilyoner
 * snapshot'ından okur, kota harcamaz. Idempotent: her (fixture, tür) için tek satır.
 *
 * <p><b>Gizlilik:</b> bu satırlar SADECE içeride (model doğrulama) kullanılır;
 * hiçbir public uçtan servis edilmez, kullanıcıya gösterilmez.
 *
 * <p>@SchedulerLock ile çok-instance'ta yalnız bir kopya çalışır.
 */
@Component
public class OddsSnapshotJob {

    private static final Logger log = LoggerFactory.getLogger(OddsSnapshotJob.class);
    private static final String SOURCE = "bilyoner";

    private final FixtureRepository fixtureRepo;
    private final FixtureOddsSnapshotRepository snapRepo;
    private final BilyonerOddsService bilyoner;

    @Value("${scorestv.odds.snapshot-enabled:true}")
    private boolean enabled;

    public OddsSnapshotJob(FixtureRepository fixtureRepo,
                           FixtureOddsSnapshotRepository snapRepo,
                           BilyonerOddsService bilyoner) {
        this.fixtureRepo = fixtureRepo;
        this.snapRepo = snapRepo;
        this.bilyoner = bilyoner;
    }

    @Scheduled(fixedDelayString = "${scorestv.odds.snapshot-interval-ms:600000}",
            initialDelayString = "${scorestv.odds.snapshot-initial-ms:120000}")
    @SchedulerLock(name = "oddsSnapshotJob", lockAtMostFor = "PT9M", lockAtLeastFor = "PT10S")
    public void run() {
        if (!enabled) {
            return;
        }
        Instant now = Instant.now();
        int opening = snapshotWindow(
                now.plus(22, ChronoUnit.HOURS), now.plus(26, ChronoUnit.HOURS), "opening", now);
        int closing = snapshotWindow(
                now, now.plus(95, ChronoUnit.MINUTES), "closing", now);
        if (opening + closing > 0) {
            log.info("Oran snapshot yazıldı — açılış: {}, kapanış: {}", opening, closing);
        }
    }

    private int snapshotWindow(Instant start, Instant end, String kind, Instant now) {
        List<Fixture> fixtures = fixtureRepo.findUpcomingWithTeamsBetween(start, end);
        int written = 0;
        for (Fixture f : fixtures) {
            if (snapRepo.existsByFixtureIdAndSourceAndSnapshotKind(f.getId(), SOURCE, kind)) {
                continue; // zaten var — idempotent
            }
            MatchOdds odds = bilyoner.forFixture(f.getHomeTeam(), f.getAwayTeam(), f.getKickoffAt());
            if (odds == null) {
                continue; // Bilyoner'de eşleşme yok / kapalı
            }
            FixtureOddsSnapshot snap = build(f, odds, kind, now);
            if (snap == null) {
                continue; // kullanılabilir market yok
            }
            try {
                snapRepo.save(snap);
                written++;
            } catch (DataIntegrityViolationException dup) {
                // Eşzamanlı yazım (unique çakışması) — güvenle yok say.
            }
        }
        return written;
    }

    private FixtureOddsSnapshot build(Fixture f, MatchOdds o, String kind, Instant now) {
        FixtureOddsSnapshot s = new FixtureOddsSnapshot();
        s.setFixtureId(f.getId());
        s.setSource(SOURCE);
        s.setSnapshotKind(kind);
        s.setCapturedAt(now);
        if (f.getKickoffAt() != null) {
            s.setMinutesToKickoff((int) Duration.between(now, f.getKickoffAt()).toMinutes());
        }
        s.setOddHome(odd(o, "Maç Sonucu", "1"));
        s.setOddDraw(odd(o, "Maç Sonucu", "X"));
        s.setOddAway(odd(o, "Maç Sonucu", "2"));
        s.setOddOver25(odd(o, "2.5 Alt/Üst", "Üst"));
        s.setOddUnder25(odd(o, "2.5 Alt/Üst", "Alt"));
        s.setOddBttsYes(odd(o, "Karşılıklı Gol", "Var"));
        s.setOddBttsNo(odd(o, "Karşılıklı Gol", "Yok"));
        // Hiçbir ana market yoksa satır yazmaya değmez.
        if (s.getOddHome() == null && s.getOddOver25() == null && s.getOddBttsYes() == null) {
            return null;
        }
        return s;
    }

    private static Double odd(MatchOdds o, String market, String label) {
        if (o == null || o.markets() == null) {
            return null;
        }
        for (MatchOdds.Market m : o.markets()) {
            if (!market.equals(m.name())) {
                continue;
            }
            for (MatchOdds.Outcome oc : m.outcomes()) {
                if (label.equals(oc.label())) {
                    return parse(oc.odd());
                }
            }
        }
        return null;
    }

    private static Double parse(String v) {
        if (v == null) {
            return null;
        }
        try {
            return Double.parseDouble(v.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
