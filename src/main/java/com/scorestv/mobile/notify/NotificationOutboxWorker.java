package com.scorestv.mobile.notify;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scorestv.mobile.fcm.FcmMessagingService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bildirim OUTBOX worker'ı — GARANTİLİ teslim.
 *
 * <p>Periyodik olarak gönderilmeyi bekleyen (PENDING) ve zamanı gelmiş satırları
 * alır, FCM'e gönderir:
 * <ul>
 *   <li>Başarı → {@code SENT}</li>
 *   <li>Sert FCM hatası → {@code attempts++} + üstel backoff ile tekrar dene
 *       ({@value #MAX_ATTEMPTS} denemeden sonra {@code FAILED})</li>
 *   <li>Çok eski (stale) satır → {@code FAILED} (geç/alakasız "başladı/bitti"
 *       göndermemek için)</li>
 * </ul>
 *
 * <p>Canlı tick'ten TAMAMEN ayrı çalışır; bildirim teslimi skor akışını
 * etkilemez. @Scheduled fixedDelay olduğundan kendisiyle çakışmaz (önceki tur
 * bitmeden yenisi başlamaz).
 */
@Component
public class NotificationOutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxWorker.class);

    private static final int BATCH = 100;
    private static final int MAX_ATTEMPTS = 6;
    private static final int MAX_ERR_LEN = 500;
    /** Gönderim eşzamanlılığı (FCM + alıcı sorgusu I/O-bound). */
    private static final int SEND_CONCURRENCY = 8;
    /** Bu kadar eski PENDING satır artık gönderilmez (geç bildirim olmasın). */
    private static final Duration EXPIRE = Duration.ofMinutes(20);

    /** Basit JSON→Map için yeterli; Spring bean'ine bağımlılık yok. */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final AtomicInteger SEND_N = new AtomicInteger();

    private final NotificationOutboxRepository repository;
    private final NotificationDispatcherService dispatcher;
    private final FcmMessagingService fcm;

    /**
     * Gönderim havuzu — bir tur içindeki satırları PARALEL işler. Önceden seri
     * for-loop vardı; yoğun anda (çok maçta gol/kart) 50+ satırın her biri
     * alıcı sorgusu + FCM (ağ) beklediği için tur uzuyor, satırlar birikip
     * 20dk EXPIRE ile SESSİZCE düşüyordu. Sınırlı eşzamanlılık ile drenaj hızı
     * ~{@value #SEND_CONCURRENCY}× artar. Daemon thread'ler.
     */
    private final ExecutorService sendPool =
            Executors.newFixedThreadPool(SEND_CONCURRENCY, r -> {
                Thread t = new Thread(r, "stv-notif-send-" + SEND_N.incrementAndGet());
                t.setDaemon(true);
                return t;
            });

    public NotificationOutboxWorker(NotificationOutboxRepository repository,
                                    NotificationDispatcherService dispatcher,
                                    FcmMessagingService fcm) {
        this.repository = repository;
        this.dispatcher = dispatcher;
        this.fcm = fcm;
    }

    @PreDestroy
    void shutdownSendPool() {
        sendPool.shutdown();
    }

    @Scheduled(fixedDelayString = "${scorestv.notify.outbox-interval-ms:5000}")
    @SchedulerLock(name = "notificationOutboxWorker", lockAtMostFor = "PT2M")
    public void process() {
        if (!fcm.isEnabled()) {
            return; // FCM kapali — satirlar PENDING bekler (ya da EXPIRE olur).
        }
        List<NotificationOutbox> due =
                repository.findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                        NotificationOutbox.STATUS_PENDING, Instant.now(),
                        PageRequest.of(0, BATCH));
        if (due.isEmpty()) return;
        // Satırları PARALEL işle (I/O-bound); turun sonunda hepsini bekle —
        // böylece ShedLock kilidi işlem boyunca tutulur ve fixedDelay aralığı
        // önceki tur bitmeden yenisini başlatmaz.
        final List<CompletableFuture<Void>> tasks = new ArrayList<>(due.size());
        for (NotificationOutbox row : due) {
            tasks.add(CompletableFuture.runAsync(() -> processOne(row), sendPool));
        }
        try {
            CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
        } catch (Exception ex) {
            // processOne kendi hatalarını yutar; buraya normalde gelinmez.
            log.warn("Outbox paralel tur hatası: {}", ex.getMessage());
        }
    }

    /** Tek satırı işler — her durumda repo'ya kendi kaydını yazar (idempotent). */
    private void processOne(NotificationOutbox row) {
        // Stale → gönderme, FAILED işaretle. Bu bir SESSİZ KAYIP (bildirim hiç
        // gitmedi); WARN'la görünür yap ki alarm/log takibinde fark edilsin.
        // Ayrıca admin "Bildirim Takip" panelinde FAILED olarak listelenir.
        if (row.getCreatedAt() != null
                && Duration.between(row.getCreatedAt(), Instant.now()).compareTo(EXPIRE) > 0) {
            row.setStatus(NotificationOutbox.STATUS_FAILED);
            row.setLastError("expired (>" + EXPIRE.toMinutes() + "dk bekledi)");
            repository.save(row);
            log.warn("Outbox EXPIRE — bildirim gonderilmeden dustu (kayip) id={} kind={} "
                    + "fixtureId={} yas={}dk attempts={}", row.getId(), row.getKind(),
                    row.getFixtureId(),
                    Duration.between(row.getCreatedAt(), Instant.now()).toMinutes(),
                    row.getAttempts());
            return;
        }
        try {
            NotificationDispatcherService.SendResult result = dispatcher.sendOutboxRow(
                    row.getFixtureId(), row.getTeamId(), row.getNotifType(),
                    row.getTitle(), row.getBody(), row.getTitleEn(), row.getBodyEn(),
                    parseData(row.getDataJson()), row.getCollapseKey(), row.isSilent());
            row.setStatus(NotificationOutbox.STATUS_SENT);
            row.setSendMode(result.mode());
            row.setRecipients(result.recipients());
            row.setDeliveredCount(result.delivered());
            row.setSentAt(Instant.now());
            repository.save(row);
        } catch (Exception ex) {
            row.setAttempts(row.getAttempts() + 1);
            row.setLastError(clip(ex.getMessage()));
            if (row.getAttempts() >= MAX_ATTEMPTS) {
                row.setStatus(NotificationOutbox.STATUS_FAILED);
            } else {
                // Üstel backoff: 4, 8, 16, 32, 64 sn ...
                long backoffSec = (long) Math.pow(2, row.getAttempts()) * 2L;
                row.setNextAttemptAt(Instant.now().plusSeconds(backoffSec));
            }
            repository.save(row);
            log.warn("Outbox gönderim hata id={} kind={} fixtureId={} attempt={}/{}: {}",
                    row.getId(), row.getKind(), row.getFixtureId(),
                    row.getAttempts(), MAX_ATTEMPTS, ex.getMessage());
        }
    }

    private Map<String, String> parseData(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.warn("Outbox data_json parse hatası: {}", e.getMessage());
            return Map.of();
        }
    }

    private static String clip(String s) {
        if (s == null) return null;
        return s.length() > MAX_ERR_LEN ? s.substring(0, MAX_ERR_LEN) : s;
    }
}
