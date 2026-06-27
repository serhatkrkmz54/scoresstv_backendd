package com.scorestv.rankings.notify;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * Siralama degisim bildirimlerini <b>commit sonrasi</b> tetikler.
 *
 * <p>Sync servisleri REPLACE transaction'i icinde degisimleri toplar ve bunu
 * cagirir. Aktif bir transaction varsa bildirim {@code afterCommit}'e ertelenir
 * — boylece sync rollback olursa yanlis bildirim gitmez. Cagri
 * {@link RankingNotificationService} <b>proxy'si</b> uzerinden yapildigi icin
 * {@code @Async} korunur (FCM I/O ayri thread'de calisir, self-invocation
 * tuzagina dusmez).
 */
@Component
public class RankingChangePublisher {

    private final RankingNotificationService notifier;

    public RankingChangePublisher(RankingNotificationService notifier) {
        this.notifier = notifier;
    }

    public void publishAfterCommit(List<RankingChange> changes) {
        if (changes == null || changes.isEmpty()) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            notifier.notifyChanges(changes);
                        }
                    });
        } else {
            // Aktif transaction yok — dogrudan (yine de @Async proxy uzerinden).
            notifier.notifyChanges(changes);
        }
    }
}
