package com.scorestv.news;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Haber push outbox worker'i — emniyet agi. Normalde push, yayin commit'i
 * sonrasi {@link NewsPushPublisher} uzerinden ANINDA gider; bu job yalniz
 * su durumlarda devreye girer:
 * <ul>
 *   <li>gonderim sirasinda sunucu coktu (satir PENDING/lease'li SENDING kaldi),</li>
 *   <li>FCM gecici hatasi → backoff'la yeniden deneme zamani geldi.</li>
 * </ul>
 * ShedLock: coklu instance'ta ayni anda tek worker calisir; satir bazinda da
 * atomik claim oldugu icin cift gonderim iki katmanla engellidir.
 */
@Component
public class NewsPushDispatchJob {

    private static final Logger log = LoggerFactory.getLogger(NewsPushDispatchJob.class);

    private final NewsPushLogRepository pushLogRepository;
    private final NewsNotificationService notifier;

    public NewsPushDispatchJob(NewsPushLogRepository pushLogRepository,
                               NewsNotificationService notifier) {
        this.pushLogRepository = pushLogRepository;
        this.notifier = notifier;
    }

    @Scheduled(fixedDelay = 15000)
    @SchedulerLock(name = "newsPushDispatchJob", lockAtMostFor = "PT2M")
    public void tick() {
        List<NewsPushLog> due = pushLogRepository
                .findTop20ByStatusInAndNextAttemptAtLessThanEqualOrderByIdAsc(
                        List.of("PENDING", "SENDING"), Instant.now());
        if (due.isEmpty()) return;
        log.info("Haber push outbox: {} bekleyen satir isleniyor", due.size());
        for (NewsPushLog row : due) {
            // Her satir kendi transaction'inda; hata digerlerini etkilemez.
            notifier.dispatchOne(row.getArticleId());
        }
    }
}
