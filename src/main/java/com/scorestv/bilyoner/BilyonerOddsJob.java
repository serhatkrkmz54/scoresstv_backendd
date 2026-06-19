package com.scorestv.bilyoner;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Bilyoner oran snapshot'ını düzenli olarak tazeler — TAM OTOMASYON.
 *
 * <p>{@code @ConditionalOnProperty} sayesinde yalnızca
 * {@code scorestv.bilyoner.enabled=true} ise bean oluşur (kapalıyken iş yok,
 * fetch yok). Snapshot kullanıcı trafiğine bağlı kalmadan {@code refresh-ms}
 * aralığıyla (varsayılan 60 sn) güncel tutulur.
 */
@Component
@ConditionalOnProperty(name = "scorestv.bilyoner.enabled", havingValue = "true")
public class BilyonerOddsJob {

    private final BilyonerOddsService service;

    public BilyonerOddsJob(BilyonerOddsService service) {
        this.service = service;
    }

    @Scheduled(
            fixedDelayString = "${scorestv.bilyoner.refresh-ms:60000}",
            initialDelayString = "${scorestv.bilyoner.initial-delay-ms:10000}")
    @SchedulerLock(name = "bilyonerOdds", lockAtMostFor = "PT5M")
    public void run() {
        service.refresh();
    }
}
