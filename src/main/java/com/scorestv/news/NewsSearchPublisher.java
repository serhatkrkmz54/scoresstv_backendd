package com.scorestv.news;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Haber Elasticsearch index'ini <b>commit sonrasi</b> gunceller/temizler.
 *
 * <p>{@link NewsService} bir haber mutasyonunun icinde bunu cagirir; aktif
 * transaction varsa islem {@code afterCommit}'e ertelenir — boylece rollback'te
 * yanlis index yazilmaz ve upsert, article + linkler DB'ye yazildiktan SONRA
 * (ayri thread'de {@code @Async}) taze okuyarak calisir. {@link NewsPushPublisher}
 * ile ayni desen.
 *
 * <p><b>ES-kosullu:</b> {@code scorestv.elasticsearch.enabled=false} ise bu
 * bean (ve {@link NewsIndexer}) yuklenmez; {@link NewsService} buna opsiyonel
 * baglanir. Bir ES hatasi haber mutasyonunu ASLA dusurmez (NewsIndexer icinde
 * try/catch).
 */
@Component
@ConditionalOnProperty(prefix = "scorestv.elasticsearch", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class NewsSearchPublisher {

    private final NewsIndexer indexer;

    public NewsSearchPublisher(NewsIndexer indexer) {
        this.indexer = indexer;
    }

    /** Yayinlanan/guncellenen haberi commit sonrasi index'e yaz (veya cikar). */
    public void upsertAfterCommit(Long articleId) {
        if (articleId == null) return;
        // NewsIndexer.upsert @Async + @Transactional — proxy uzerinden cagrilir
        // (ayri thread + taze okuma). NewsPushPublisher ile ayni desen.
        afterCommit(() -> indexer.upsert(articleId));
    }

    /** Yayindan kaldirilan/arsivlenen/silinen haberi commit sonrasi index'ten cikar. */
    public void removeAfterCommit(Long articleId) {
        if (articleId == null) return;
        afterCommit(() -> indexer.remove(articleId));
    }

    private void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            r.run();
                        }
                    });
        } else {
            r.run();
        }
    }
}
