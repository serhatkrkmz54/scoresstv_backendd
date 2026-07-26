package com.scorestv.reporter;

import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.football.live.LiveBroadcaster;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Manuel (muhabir) canlı maçların dakikasını OTOMATİK işletir — API maçlarında
 * bu işi ticker yapar, manuel maçlarda kimse yapmıyordu (dakika 1'de kalıyordu).
 *
 * <p>Her 30 sn'de: source=manual + 1H/2H maçlarda elapsed, yarının başlama
 * anından ({@code manualPhaseStart}) hesaplanır (1H: 1+geçen dk, max 45;
 * 2H: 46+geçen dk, max 90). Değiştiyse kaydedilir ve canlı topic'ten yayılır —
 * ana sayfa listesi ve maç detayı otomatik akar.
 */
@Component
public class ManualLiveClockJob {

    private static final Logger log = LoggerFactory.getLogger(ManualLiveClockJob.class);

    private static final Set<String> RUNNING = Set.of("1H", "2H");

    private final FixtureRepository fixtureRepo;
    private final LiveBroadcaster liveBroadcaster;

    public ManualLiveClockJob(FixtureRepository fixtureRepo, LiveBroadcaster liveBroadcaster) {
        this.fixtureRepo = fixtureRepo;
        this.liveBroadcaster = liveBroadcaster;
    }

    @Scheduled(fixedDelay = 30000)
    @SchedulerLock(name = "manualLiveClockJob", lockAtMostFor = "PT1M")
    @Transactional
    public void tick() {
        List<Fixture> live = fixtureRepo.findBySourceAndStatusShortIn("manual", RUNNING);
        if (live.isEmpty()) return;
        Instant now = Instant.now();
        for (Fixture f : live) {
            Instant phaseStart = f.getManualPhaseStart();
            if (phaseStart == null) continue; // eski kayıt — muhabir dakikayı elle yönetir
            long passed = Duration.between(phaseStart, now).toMinutes();
            // Normal süre biterse dakika 45/90'da durur, UZATMA statusExtra'ya
            // yazılır (45+2, 90+4 gibi) — frontend'ler API maçlarıyla aynı
            // sekilde gosterir. Uzatma tavanı 15dk (muhabir bitirmeyi unutursa
            // sonsuz saymasın).
            final boolean firstHalf = "1H".equals(f.getStatusShort());
            final int cap = firstHalf ? 45 : 90;
            final long total = (firstHalf ? 1 : 46) + passed;
            final int computed = (int) Math.min(cap, total);
            final Integer extra = total > cap
                    ? (int) Math.min(15, total - cap)
                    : null;
            final boolean elapsedChanged =
                    f.getElapsed() == null || computed > f.getElapsed();
            final boolean extraChanged =
                    (extra == null) != (f.getStatusExtra() == null)
                            || (extra != null && !extra.equals(f.getStatusExtra()));
            if (elapsedChanged || extraChanged) {
                f.setElapsed(computed);
                f.setStatusExtra(extra);
                f.setLastSyncedAt(now);
                fixtureRepo.save(f);
                try {
                    liveBroadcaster.broadcast(f);
                } catch (RuntimeException ex) {
                    log.warn("Manuel saat yayını başarısız id={}: {}", f.getId(), ex.getMessage());
                }
            }
        }
    }
}
