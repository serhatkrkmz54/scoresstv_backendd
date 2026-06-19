package com.scorestv.football.live;

import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.mobile.notify.NotificationMessageBuilder;
import com.scorestv.mobile.notify.NotificationMessageBuilder.NotificationMessage;
import com.scorestv.mobile.notify.NotificationOutbox;
import com.scorestv.mobile.notify.NotificationOutboxEnqueuer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Maç "başladı/bitti" bildirimleri için TAM-BİR-KEZ claim + OUTBOX enqueue.
 *
 * <p>Tek transaction'da: atomik {@code UPDATE ... WHERE notif_*_at IS NULL} ile
 * claim alınır; KAZANILIRSA mesaj render edilip {@link NotificationOutbox}
 * PENDING satırı yazılır. İki kalkan: (1) {@code notif_*_at} kalıcı flag,
 * (2) outbox {@code dedup_key} UNIQUE. Gerçek FCM gönderimini {@link
 * com.scorestv.mobile.notify.NotificationOutboxWorker} backoff'lu retry ile yapar.
 */
@Service
public class FixtureNotifyGate {

    private final FixtureRepository fixtureRepository;
    private final NotificationMessageBuilder messageBuilder;
    private final NotificationOutboxEnqueuer enqueuer;

    public FixtureNotifyGate(FixtureRepository fixtureRepository,
                             NotificationMessageBuilder messageBuilder,
                             NotificationOutboxEnqueuer enqueuer) {
        this.fixtureRepository = fixtureRepository;
        this.messageBuilder = messageBuilder;
        this.enqueuer = enqueuer;
    }

    /** Kickoff'u atomik claim et; kazanırsa "başladı" mesajını render edip enqueue et (tek tx). */
    @Transactional
    public void enqueueKickoffIfClaimed(Long fixtureId) {
        if (fixtureRepository.claimKickoffNotification(fixtureId, Instant.now()) != 1) {
            return;
        }
        final Fixture fixture = fixtureRepository.findById(fixtureId).orElse(null);
        if (fixture == null) return;
        final NotificationMessage msg = messageBuilder.buildKickoffMessage(fixture);
        enqueuer.enqueue(NotificationOutbox.KIND_KICKOFF, "basladi", fixtureId, null,
                msg.title(), msg.body(), statusData("basladi", fixtureId),
                "KICKOFF:" + fixtureId);
    }

    /** Final'i atomik claim et; kazanırsa "bitti" mesajını render edip enqueue et (tek tx). */
    @Transactional
    public void enqueueFinalIfClaimed(Long fixtureId) {
        if (fixtureRepository.claimFinalNotification(fixtureId, Instant.now()) != 1) {
            return;
        }
        final Fixture fixture = fixtureRepository.findById(fixtureId).orElse(null);
        if (fixture == null) return;
        final NotificationMessage msg = messageBuilder.buildFinalMessage(fixture);
        enqueuer.enqueue(NotificationOutbox.KIND_FINAL, "bitti", fixtureId, null,
                msg.title(), msg.body(), statusData("bitti", fixtureId),
                "FINAL:" + fixtureId);
    }

    /**
     * "İlk yarı bitti" (HT) — claim flag YOK; outbox {@code dedup_key="HT:fixture"}
     * tek bildirim sağlar (outbox satırları silinmez → kalıcı exactly-once).
     */
    @Transactional
    public void enqueueHalftime(Long fixtureId) {
        final Fixture fixture = fixtureRepository.findById(fixtureId).orElse(null);
        if (fixture == null) return;
        final NotificationMessage msg = messageBuilder.buildHalftimeMessage(fixture);
        enqueuer.enqueue(NotificationOutbox.KIND_HALFTIME, "ht", fixtureId, null,
                msg.title(), msg.body(), statusData("ht", fixtureId),
                "HT:" + fixtureId);
    }

    /** "İkinci yarı başladı" (2H) — dedup_key="SECONDHALF:fixture" tek bildirim. */
    @Transactional
    public void enqueueSecondHalf(Long fixtureId) {
        final Fixture fixture = fixtureRepository.findById(fixtureId).orElse(null);
        if (fixture == null) return;
        final NotificationMessage msg = messageBuilder.buildSecondHalfMessage(fixture);
        enqueuer.enqueue(NotificationOutbox.KIND_SECONDHALF, "2yari", fixtureId, null,
                msg.title(), msg.body(), statusData("2yari", fixtureId),
                "SECONDHALF:" + fixtureId);
    }

    private static Map<String, String> statusData(String type, Long fixtureId) {
        Map<String, String> data = new HashMap<>();
        data.put("type", type);
        data.put("fixtureId", String.valueOf(fixtureId));
        return data;
    }
}
