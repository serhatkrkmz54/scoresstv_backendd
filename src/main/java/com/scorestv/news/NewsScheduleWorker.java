package com.scorestv.news;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Zamanlanmis haber yayin worker'i.
 *
 * <p>Periyodik olarak SCHEDULED durumundaki, {@code publishedAt <= now} olan
 * haberleri PUBLISHED'e cevirir — public sorgular yalniz PUBLISHED gordugu icin
 * bu is olmadan zamanlanmis haber kendiliginden yayina gecmezdi.
 *
 * <p>@SchedulerLock ile cok-instance'ta yalniz bir kopya calisir; fixedDelay
 * oldugundan kendisiyle cakismaz. Broadcast/outbox worker'lari ile ayni desen.
 *
 * <p>NOT: Bu is DEVREYE GIRDIGINDE, zamani gecmis TUM mevcut SCHEDULED haberler
 * bir sonraki tik'te yayinlanir. Ilk deploy oncesi elde bekleyen SCHEDULED haber
 * olup olmadigi kontrol edilmelidir.
 */
@Component
public class NewsScheduleWorker {

    private static final Logger log = LoggerFactory.getLogger(NewsScheduleWorker.class);

    private final NewsService newsService;

    public NewsScheduleWorker(NewsService newsService) {
        this.newsService = newsService;
    }

    @Scheduled(fixedDelayString = "${scorestv.news.schedule-interval-ms:60000}")
    @SchedulerLock(name = "newsScheduleWorker", lockAtMostFor = "PT5M")
    public void publishDue() {
        try {
            int n = newsService.publishDueScheduled();
            if (n > 0) {
                log.info("Zamanlanmis haber otomatik yayinlandi: {}", n);
            }
        } catch (Exception ex) {
            log.warn("Zamanli haber yayinlama hatasi: {}", ex.getMessage());
        }
    }
}
