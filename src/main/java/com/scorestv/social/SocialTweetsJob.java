package com.scorestv.social;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tweet cache'ini düzenli olarak (varsayılan 30 dk) arka planda tazeler.
 *
 * <p>{@code @ConditionalOnProperty} sayesinde yalnızca
 * {@code scorestv.social.enabled=true} ise bean oluşur. İlk çekim açılıştan
 * ~8 sn sonra yapılır; sonra {@code refresh-ms} aralığıyla devam eder.
 */
@Component
@ConditionalOnProperty(name = "scorestv.social.enabled", havingValue = "true")
public class SocialTweetsJob {

    private final SocialTweetsService service;

    public SocialTweetsJob(SocialTweetsService service) {
        this.service = service;
    }

    @Scheduled(
            fixedDelayString = "${scorestv.social.refresh-ms:1800000}",
            initialDelayString = "${scorestv.social.initial-delay-ms:8000}")
    public void run() {
        service.refresh();
    }
}
