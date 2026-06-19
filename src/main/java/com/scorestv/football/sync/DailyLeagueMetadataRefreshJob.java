package com.scorestv.football.sync;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * TÜM liglerin metadata'sını (özellikle {@code current_season} + sezon listesi +
 * coverage) GÜNLÜK olarak TEK API çağrısıyla tazeler.
 *
 * <p><b>Neden gerekli:</b> {@code current_season} sezon devrinde güncel kalmazsa
 * onboarding takım listesi, puan durumu ve fikstür gösterimi yanlış/boş sezona
 * bakar. {@link DailyLeagueRefreshJob} bunu lig-başına {@code syncOne} ile yapıyordu
 * ama covered lig sayısı çok büyük (binlerce) olduğundan kota/süre yetmiyor ve
 * birçok lig bayat kalıyordu.
 *
 * <p><b>Çözüm:</b> {@code GET /leagues} (filtresiz) TEK çağrıda TÜM ligleri ve
 * her birinin {@code current:true} sezonunu döndürür; {@link ReferenceUpserter}
 * her lig için {@code current_season}'ı buna göre yazar. Kaç lig olursa olsun
 * maliyet sabit (1 çağrı/gün). {@code covered} ve elle girilen {@code nameTr}
 * alanlarına DOKUNULMAZ.
 *
 * <p>Bean yalnız {@code scorestv.football.sync.enabled=true} ile aktif;
 * {@code @SchedulerLock} ile çoklu instance'ta tek node'da koşar.
 */
@Component
@ConditionalOnProperty(name = "scorestv.football.sync.enabled", havingValue = "true")
public class DailyLeagueMetadataRefreshJob {

    private static final Logger log =
            LoggerFactory.getLogger(DailyLeagueMetadataRefreshJob.class);

    private final ReferenceSyncService referenceSyncService;

    public DailyLeagueMetadataRefreshJob(ReferenceSyncService referenceSyncService) {
        this.referenceSyncService = referenceSyncService;
    }

    @Scheduled(
            cron = "${scorestv.football.sync.leagues-metadata-cron:0 15 3 * * *}",
            zone = "${scorestv.football.sync.timezone:Europe/Istanbul}")
    @SchedulerLock(name = "dailyLeagueMetadataRefresh", lockAtMostFor = "PT30M")
    public void run() {
        try {
            var result = referenceSyncService.syncLeagues();
            log.info("Günlük lig metadata tazeleme bitti: {} lig, {} sezon upsert; {} başarısız",
                    result.leaguesUpserted(), result.seasonsUpserted(), result.leaguesFailed());
        } catch (RuntimeException ex) {
            log.warn("Günlük lig metadata tazeleme hata: {}", ex.getMessage(), ex);
        }
    }
}
