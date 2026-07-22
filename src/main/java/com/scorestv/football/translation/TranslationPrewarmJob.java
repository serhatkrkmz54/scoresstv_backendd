package com.scorestv.football.translation;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Tek seferlik ön-ısıtma: açılıştan sonra bir kez, DB'de zaten var olan
 * İngilizce metinlerin (sakatlık sebebi/türü, istatistik adı, puan durumu
 * açıklaması, round, transfer türü, kupa yerleşimi) distinct listesini çıkarıp
 * {@link AutoTranslateService#prewarm} ile DeepL cache'ini toplu doldurur.
 *
 * <p>Böylece kullanıcı hiçbir sayfayı beklemeden, gelmiş geçmiş metinler
 * Türkçeleşir. Sözlük+parser'ın zaten çevirdiği değerler de gönderilir ama
 * cache'ten ASLA okunmaz (FootballMessages önce sözlüğe bakar) — yalnız zararsız
 * fazladan satır olur. Sweep kendi arka plan thread'inde, throttle'lı çalışır;
 * DeepL kotası dolarsa erken durur.
 *
 * <p>{@code translate.auto.prewarm=false} ile kapatılabilir (o zaman boşluklar
 * yalnız sayfa görüldükçe dolar).
 */
@Component
public class TranslationPrewarmJob {

    private static final Logger log = LoggerFactory.getLogger(TranslationPrewarmJob.class);

    /** Açılış yoğunluğu (sync/live job'lar) geçsin diye ilk beklemesi. */
    private static final long INITIAL_DELAY_MS = 30_000;

    @PersistenceContext
    private EntityManager em;

    private final AutoTranslateService autoTranslate;
    private final TransactionTemplate txTemplate;
    private final boolean prewarmEnabled;

    public TranslationPrewarmJob(
            AutoTranslateService autoTranslate,
            PlatformTransactionManager txManager,
            @Value("${translate.auto.prewarm:true}") boolean prewarmEnabled) {
        this.autoTranslate = autoTranslate;
        this.txTemplate = new TransactionTemplate(txManager);
        this.txTemplate.setReadOnly(true);
        this.prewarmEnabled = prewarmEnabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!prewarmEnabled || !autoTranslate.isEnabled()) {
            return;
        }
        Thread t = new Thread(this::runSweep, "stv-autotr-prewarm");
        t.setDaemon(true);
        t.start();
    }

    private void runSweep() {
        try {
            Thread.sleep(INITIAL_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        log.info("AutoTranslate prewarm sweep basliyor");
        sweep(AutoTranslateService.CAT_INJURY_REASON,
                "SELECT DISTINCT i.reason FROM Injury i WHERE i.reason IS NOT NULL");
        sweep(AutoTranslateService.CAT_INJURY_TYPE,
                "SELECT DISTINCT i.type FROM Injury i WHERE i.type IS NOT NULL");
        // Oyuncu sayfasi sakatlik sekmesi: player_sidelined.type de injuryReason'dan
        // gecer (ayni CAT_INJURY_REASON kategorisi/cache'i) — onu da on-isit.
        sweep(AutoTranslateService.CAT_INJURY_REASON,
                "SELECT DISTINCT ps.type FROM PlayerSidelined ps WHERE ps.type IS NOT NULL");
        sweep(AutoTranslateService.CAT_STATISTIC,
                "SELECT DISTINCT s.statType FROM FixtureStatistic s WHERE s.statType IS NOT NULL");
        sweep(AutoTranslateService.CAT_STANDING,
                "SELECT DISTINCT st.description FROM Standing st WHERE st.description IS NOT NULL");
        sweep(AutoTranslateService.CAT_ROUND,
                "SELECT DISTINCT f.round FROM Fixture f WHERE f.round IS NOT NULL");
        sweep(AutoTranslateService.CAT_TRANSFER,
                "SELECT DISTINCT t.transferType FROM Transfer t WHERE t.transferType IS NOT NULL");
        sweep(AutoTranslateService.CAT_TROPHY,
                "SELECT DISTINCT c.place FROM CoachTrophy c WHERE c.place IS NOT NULL");
        sweep(AutoTranslateService.CAT_SURFACE,
                "SELECT DISTINCT v.surface FROM Venue v WHERE v.surface IS NOT NULL");
        sweep(AutoTranslateService.CAT_GROUP,
                "SELECT DISTINCT st.groupName FROM Standing st WHERE st.groupName IS NOT NULL");
        sweep(AutoTranslateService.CAT_PREDICTION_COMMENT,
                "SELECT DISTINCT p.winnerComment FROM Prediction p WHERE p.winnerComment IS NOT NULL");
        sweep(AutoTranslateService.CAT_EVENT_COMMENT,
                "SELECT DISTINCT e.comments FROM FixtureEvent e WHERE e.comments IS NOT NULL");
        // NOT: prediction.advice ve player position on-isitilmaz — advice yuksek
        // cesitlilikte (takim adi+sayi gomulu) oldugu icin kotayi yakabilir; ikisi de
        // yalniz gorulduce (serve-path) doldurulur.
        log.info("AutoTranslate prewarm sweep tamamlandi");
    }

    private void sweep(String category, String jpql) {
        if (Thread.currentThread().isInterrupted()) {
            return;
        }
        List<String> values;
        try {
            values = txTemplate.execute(status ->
                    em.createQuery(jpql, String.class).getResultList());
        } catch (Exception e) {
            log.warn("AutoTranslate prewarm okuma hatasi ({}): {}", category, e.getMessage());
            return;
        }
        if (values == null || values.isEmpty()) {
            return;
        }
        int done = autoTranslate.prewarm(category, values);
        log.info("AutoTranslate prewarm {}: distinct={} yeni-ceviri={}",
                category, values.size(), done);
    }
}
