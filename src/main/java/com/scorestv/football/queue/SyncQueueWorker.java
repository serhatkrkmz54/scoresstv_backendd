package com.scorestv.football.queue;

import com.scorestv.common.ApiException;
import com.scorestv.football.ApiQuotaTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Sync queue worker — {@code fixedDelay=2sn} ile bir is alir ve calistirir.
 * Bu bizim "RabbitMQ consumer"imiz.
 *
 * <p><b>Throughput:</b> Default 2sn → 30/dk → 43.2k/gun. {@code worker-delay-ms}
 * ile ayarlanir; daha agresif istersen 1000ms = 60/dk.
 *
 * <p><b>Rate limit recovery:</b> API throttle hatasi alirsa job 5 dk sonraya
 * ertelenir; worker yine 2sn'de bir tick atar ama PENDING+gecmis tarihli is
 * yoksa bos doner.
 *
 * <p><b>Retry stratejisi:</b> exponential backoff — 30sn, 2dk, 8dk, 32dk, 2sa.
 * 5'ten cok attempts → FAILED.
 *
 * <p>Bean yalniz {@code scorestv.football.sync.enabled=true} ile aktif.
 */
@Component
@ConditionalOnProperty(name = "scorestv.football.sync.enabled", havingValue = "true")
public class SyncQueueWorker {

    private static final Logger log = LoggerFactory.getLogger(SyncQueueWorker.class);

    /** API throttle hatasi sonrasi job'u ne kadar geri at. */
    private static final Duration RATE_LIMIT_BACKOFF = Duration.ofMinutes(5);

    /** Maksimum attempt — bunun otesi FAILED. */
    private static final int MAX_ATTEMPTS = 5;

    /** Exponential backoff dizisi: 30sn, 2dk, 8dk, 32dk, 2sa. */
    private static final long[] BACKOFF_SECONDS = {30, 120, 480, 1920, 7200};

    // ============================================================
    // ADAPTIF QUOTA THROTTLING
    // ============================================================
    // Günlük kalan kota → worker'in hangi priority'ye kadar is alabilecegi.
    // Live ticker (15sn) + lazy sync + scheduled job'lar icin yer birakir.
    //
    //   Kalan >50%:  her priority (1-9) — full hiz
    //   Kalan 20-50%: priority ≤5 (urgent + covered + default) — bulk job durur
    //   Kalan 10-20%: priority ≤3 (sadece urgent + covered)
    //   Kalan <10%:   priority ≤1 (sadece urgent)
    //   Kalan 0:      tamamen dur

    /** Bulk (priority ≥6) job'larin durdurulacagi gunluk kalan kota esik. */
    private static final int BULK_PAUSE_PERCENT = 50;
    private static final int DEFAULT_PAUSE_PERCENT = 20;
    private static final int COVERED_PAUSE_PERCENT = 10;

    private final SyncJobRepository repository;
    private final SyncJobExecutor executor;
    private final ApiQuotaTracker quotaTracker;

    /**
     * Self proxy — {@code @Transactional} ve {@code @Async} advice'larinin
     * devreye girmesi icin self-invocation yerine proxy uzerinden cagri sart.
     * Direkt {@code this.claimNext(...)} Spring AOP'i bypass eder ve
     * "No active transaction" hatasiyla cakar.
     */
    private final SyncQueueWorker self;

    public SyncQueueWorker(SyncJobRepository repository,
                           SyncJobExecutor executor,
                           ApiQuotaTracker quotaTracker,
                           @Lazy SyncQueueWorker self) {
        this.repository = repository;
        this.executor = executor;
        this.quotaTracker = quotaTracker;
        this.self = self;
    }

    /**
     * Su anki gunluk kotaya gore worker'in calistirabilecegi maksimum priority.
     * Daha buyuk = daha dusuk oncelik. Sadece <=N priority'ler claim edilebilir.
     *
     * <p>Kota bilinmiyorsa (-1, server ilk istegi atmamis) varsayilan: tamamı.
     */
    private int maxAllowedPriority() {
        int pct = quotaTracker.getDailyRemainingPercent();
        if (pct < 0) return Integer.MAX_VALUE;     // bilinmiyor — full
        if (pct >= BULK_PAUSE_PERCENT) return 9;   // %50+: hepsi
        if (pct >= DEFAULT_PAUSE_PERCENT) return 5; // %20-50: bulk dur
        if (pct >= COVERED_PAUSE_PERCENT) return 3; // %10-20: covered+urgent
        if (pct > 0) return 1;                     // <%10: sadece urgent
        return 0;                                  // 0: tamamen dur
    }

    /**
     * Her {@code worker-delay-ms} (default 2000ms) bir tick. Tek is alir,
     * calistirir, durum gunceller. Yari is yoksa hemen doner.
     */
    @Scheduled(
            fixedDelayString = "${scorestv.football.sync.queue-worker-delay-ms:2000}",
            timeUnit = TimeUnit.MILLISECONDS)
    public void tick() {
        // 429 cooldown aktifse hic is alma — kisa rate-limit penceresinde job'lari
        // bos yere 5dk backoff'a atmaktansa worker'i tamamen duraklat.
        if (quotaTracker.isInCooldown()) {
            return;
        }
        int maxPriority = maxAllowedPriority();
        if (maxPriority <= 0) {
            // Kota tukenmis — tamamen dur
            return;
        }
        SyncJob job = self.claimNext(maxPriority);
        if (job == null) return;
        log.debug("Sync job basliyor: id={} type={} priority={} attempt={} (kota %{})",
                job.getId(), job.getJobType(), job.getPriority(),
                job.getAttempts(), quotaTracker.getDailyRemainingPercent());
        try {
            int written = executor.execute(job);
            self.markCompleted(job, written);
        } catch (ApiException ex) {
            // API hatasi — rate limit veya yetkilendirme. Rate limit ise geri at.
            if (isRateLimit(ex)) {
                self.scheduleRetry(job, RATE_LIMIT_BACKOFF, ex.getMessage());
                log.warn("Sync job rate limit ile geri atildi: id={} → 5dk sonra",
                        job.getId());
            } else {
                handleFailure(job, ex);
            }
        } catch (RuntimeException ex) {
            handleFailure(job, ex);
        }
    }

    /**
     * Bir PENDING + zamani gelmis + priority<=maxAllowed is'i atomic claim eder.
     * SELECT FOR UPDATE SKIP LOCKED + UPDATE tek tx'te. Quota tukeniyorsa
     * maxAllowedPriority dusururek bulk job'lari atlar.
     */
    @Transactional
    public SyncJob claimNext(int maxAllowedPriority) {
        List<SyncJob> candidates = repository.findClaimable(
                Instant.now(), maxAllowedPriority, PageRequest.of(0, 1));
        if (candidates.isEmpty()) return null;
        SyncJob job = candidates.get(0);
        job.setStatus(SyncJobStatus.IN_PROGRESS);
        job.setAttempts(job.getAttempts() + 1);
        return repository.save(job);
    }

    @Transactional
    public void markCompleted(SyncJob job, int written) {
        job.setStatus(SyncJobStatus.COMPLETED);
        job.setLastError(null);
        repository.save(job);
        log.info("Sync job tamamlandi: id={} type={} attempts={} written={}",
                job.getId(), job.getJobType(), job.getAttempts(), written);
    }

    @Transactional
    public void scheduleRetry(SyncJob job, Duration delay, String errorMsg) {
        job.setStatus(SyncJobStatus.PENDING);
        job.setNextAttemptAt(Instant.now().plus(delay));
        job.setLastError(truncate(errorMsg, 1000));
        repository.save(job);
    }

    @Transactional
    public void markFailed(SyncJob job, String errorMsg) {
        job.setStatus(SyncJobStatus.FAILED);
        job.setLastError(truncate(errorMsg, 1000));
        repository.save(job);
        log.error("Sync job basarisiz oldu (max attempts): id={} type={} error={}",
                job.getId(), job.getJobType(), errorMsg);
    }

    private void handleFailure(SyncJob job, Throwable ex) {
        String msg = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        if (job.getAttempts() >= MAX_ATTEMPTS) {
            self.markFailed(job, msg);
            return;
        }
        // Exponential backoff
        long secs = BACKOFF_SECONDS[Math.min(job.getAttempts() - 1, BACKOFF_SECONDS.length - 1)];
        self.scheduleRetry(job, Duration.ofSeconds(secs), msg);
        log.warn("Sync job hata, retry planlandi: id={} attempt={}/{} backoff={}sn — {}",
                job.getId(), job.getAttempts(), MAX_ATTEMPTS, secs, msg);
    }

    private static boolean isRateLimit(ApiException ex) {
        // Once status: ApiFootballException.quotaExceeded HTTP 429 tasir — mesaj
        // sanitize edilmis olsa bile bu yakalanir (asil hatanin kaynagi buydu).
        if (ex.getStatus() == HttpStatus.TOO_MANY_REQUESTS) {
            return true;
        }
        if (ex.getMessage() == null) return false;
        String msg = ex.getMessage().toLowerCase();
        return msg.contains("rate limit") || msg.contains("too many requests")
                || msg.contains("429");
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
