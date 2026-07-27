package com.scorestv.news;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Haber (news) push'unu transactional OUTBOX'a yazar ve commit sonrasi
 * dusuk-gecikme gonderimini tetikler.
 *
 * <p>Akis: {@link NewsService} publish/update transaction'i ICINDE
 * {@link #publishAfterCommit} cagirir → outbox satiri ({@code news_push_log},
 * status=PENDING) AYNI transaction'da atomik eklenir (ON CONFLICT DO NOTHING —
 * cift tetik sessizce yutulur, rollback'te satir da gider). Commit olunca
 * {@code afterCommit} ile {@link NewsNotificationService#dispatchOneAsync}
 * hemen gonderir; sunucu o anda cokse bile satir DB'de kaldigi icin
 * {@link NewsPushDispatchJob} emniyet agi olarak devralir. Atomik claim
 * sayesinde iki yol ayni haberi ASLA iki kez gondermez.
 */
@Component
public class NewsPushPublisher {

    private final NewsNotificationService notifier;
    private final NewsPushLogRepository pushLogRepository;

    public NewsPushPublisher(NewsNotificationService notifier,
                             NewsPushLogRepository pushLogRepository) {
        this.notifier = notifier;
        this.pushLogRepository = pushLogRepository;
    }

    /** Outbox'a yaz (mevcut transaction icinde) + commit sonrasi gonderimi tetikle. */
    public void publishAfterCommit(Long articleId, NewsPushTarget target) {
        if (articleId == null) return;
        final NewsPushTarget effective =
                target != null ? target : NewsPushTarget.FAVORITES;
        // Yayinlama transaction'inin PARCASI: yayin commit olursa niyet de olur,
        // rollback olursa ikisi de gider. Zaten kayitliysa (onceki push) no-op.
        pushLogRepository.enqueue(articleId, effective.name());

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            notifier.dispatchOneAsync(articleId);
                        }
                    });
        } else {
            // Aktif transaction yok — dogrudan (yine de @Async proxy uzerinden).
            notifier.dispatchOneAsync(articleId);
        }
    }
}
