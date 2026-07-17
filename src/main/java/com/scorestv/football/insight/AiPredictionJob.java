package com.scorestv.football.insight;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * AI tahmin kaydı + notlama zamanlanmış işi. Kayıt maçtan önce (her 3 saat),
 * notlama maç bitince (her saat). Hafif işler — sadece covered ligler.
 */
@Component
public class AiPredictionJob {

    private static final Logger log = LoggerFactory.getLogger(AiPredictionJob.class);

    private final AiPredictionRecorder recorder;

    public AiPredictionJob(AiPredictionRecorder recorder) {
        this.recorder = recorder;
    }

    /** Yaklaşan maçların tahminini kaydet. */
    @Scheduled(fixedDelayString = "PT3H", initialDelayString = "PT2M")
    public void record() {
        try {
            recorder.recordUpcoming();
        } catch (Exception e) {
            log.warn("AI tahmin kaydı başarısız: {}", e.toString());
        }
    }

    /** Biten maçları notla. */
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    public void grade() {
        try {
            recorder.gradeFinished();
        } catch (Exception e) {
            log.warn("AI tahmin notlama başarısız: {}", e.toString());
        }
    }
}
