package com.scorestv.game;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Dönemi biten yarışmaları periyodik olarak çözer. Yalnızca {@code endAt} + GRACE
 * süresi geçmiş + henüz RESOLVED olmayanları alır. @SchedulerLock ile tek node'da
 * koşar.
 *
 * <p><b>GRACE (gecikme) neden var:</b> maç bitse bile oyuncu istatistikleri
 * (player_stats) canlı/lazy sync ile birkaç dakika-saat SONRA oturur. Çözümleme
 * {@code endAt}'ta hemen yapılırsa eksik/0 istatistikle YANLIŞ kazanan ya da VOID
 * çıkar ve idempotent olduğu için KENDİNİ DÜZELTMEZ. Bu yüzden çözümlemeyi
 * {@code endAt + grace} kadar geciktiririz (varsayılan 6 saat) → tüm maçların
 * istatistikleri oturur. Admin yine de {@code endAt}'ı maç programına göre
 * makul seçmeli; grace ek güvenlik payıdır.
 */
@Component
public class GameResolutionJob {

    private static final Logger log = LoggerFactory.getLogger(GameResolutionJob.class);

    private final GameCompetitionRepository competitionRepo;
    private final GameResolutionService resolutionService;
    /** endAt sonrası, istatistikler otursun diye beklenecek ek süre (saat). */
    private final long graceHours;

    public GameResolutionJob(GameCompetitionRepository competitionRepo,
                             GameResolutionService resolutionService,
                             @Value("${scorestv.game.resolve-grace-hours:6}") long graceHours) {
        this.competitionRepo = competitionRepo;
        this.resolutionService = resolutionService;
        this.graceHours = Math.max(0, graceHours);
    }

    @Scheduled(fixedDelayString = "${scorestv.game.resolve-interval-ms:300000}")
    @SchedulerLock(name = "gameResolution", lockAtMostFor = "PT10M")
    public void run() {
        // endAt + grace <= now → yani endAt <= now - grace olan yarışmalar.
        final Instant cutoff = Instant.now().minus(Duration.ofHours(graceHours));
        final List<GameCompetition> due = competitionRepo.findByStatusInAndEndAtLessThanEqual(
                List.of(GameStatus.OPEN, GameStatus.LOCKED), cutoff);
        for (GameCompetition c : due) {
            try {
                resolutionService.resolveCompetition(c.getId());
            } catch (RuntimeException ex) {
                log.error("Oyun yarismasi cozumleme hatasi id={}: {}", c.getId(), ex.getMessage(), ex);
            }
        }
    }
}
