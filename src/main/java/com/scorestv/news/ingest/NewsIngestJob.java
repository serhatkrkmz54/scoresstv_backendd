package com.scorestv.news.ingest;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Haber içe aktarmayı periyodik tetikler (varsayılan 20 dk). Yalnız
 * {@code scorestv.news-ingest.enabled=true} iken iş yapar (guard servistedir).
 * Çok-instance kurulumda {@code @SchedulerLock} ile TEK node'da koşar.
 */
@Component
public class NewsIngestJob {

    private final NewsIngestService service;

    public NewsIngestJob(NewsIngestService service) {
        this.service = service;
    }

    @Scheduled(cron = "${scorestv.news-ingest.cron:0 */30 * * * *}")
    @SchedulerLock(name = "newsIngest", lockAtMostFor = "PT10M")
    public void run() {
        service.runOnce();
    }
}
