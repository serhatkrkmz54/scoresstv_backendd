package com.scorestv.news;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Haber (news) push bildirimini <b>commit sonrasi</b> tetikler.
 *
 * <p>{@link NewsService} publish/create transaction'i icinde bunu cagirir.
 * Aktif bir transaction varsa push {@code afterCommit}'e ertelenir — boylece
 * yayinlama rollback olursa yanlis bildirim gitmez ve push, article + linkler
 * DB'ye yazildiktan SONRA (ayri thread'de tazece okuyarak) calisir. Cagri
 * {@link NewsNotificationService} <b>proxy'si</b> uzerinden yapildigi icin
 * {@code @Async} korunur (FCM I/O ayri thread'de calisir, self-invocation
 * tuzagina dusmez).
 */
@Component
public class NewsPushPublisher {

    private final NewsNotificationService notifier;

    public NewsPushPublisher(NewsNotificationService notifier) {
        this.notifier = notifier;
    }

    /** Yayinlanan haber icin (commit sonrasi) push tetikle. */
    public void publishAfterCommit(Long articleId, NewsPushTarget target) {
        if (articleId == null) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            notifier.sendForArticle(articleId, target);
                        }
                    });
        } else {
            // Aktif transaction yok — dogrudan (yine de @Async proxy uzerinden).
            notifier.sendForArticle(articleId, target);
        }
    }
}
