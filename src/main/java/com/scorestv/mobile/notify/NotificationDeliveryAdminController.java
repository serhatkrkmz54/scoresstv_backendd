package com.scorestv.mobile.notify;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Admin "Bildirim Gönderimleri" takip uçları — maç-olay bildirimlerinin
 * (gol/kart/başladı/bitti/HT/2H/kadro) outbox durumunu ve teslim sayılarını
 * panelden görüntülemek için.
 *
 * <p>{@code /api/v1/admin/**} SecurityConfig'te authenticated; burada
 * EDITOR/ADMIN ile gatelenir. Salt-okunur.
 *
 * <ul>
 *   <li>GET /deliveries?status=&limit= — son gönderimler (en yeni üstte)</li>
 *   <li>GET /deliveries/summary — statü sayıları (rozetler)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/admin/notifications")
public class NotificationDeliveryAdminController {

    private static final int MAX_LIMIT = 200;

    private final NotificationOutboxRepository repository;

    public NotificationDeliveryAdminController(NotificationOutboxRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/deliveries")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    @Transactional(readOnly = true)
    public List<DeliveryItem> deliveries(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        final int capped = Math.max(1, Math.min(limit, MAX_LIMIT));
        final PageRequest page = PageRequest.of(0, capped);
        final List<NotificationOutbox> rows =
                (status != null && !status.isBlank())
                        ? repository.findByStatusOrderByCreatedAtDesc(
                                status.trim().toUpperCase(), page)
                        : repository.findAllByOrderByCreatedAtDesc(page);
        return rows.stream().map(DeliveryItem::from).toList();
    }

    @GetMapping("/deliveries/summary")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    @Transactional(readOnly = true)
    public Summary summary() {
        return new Summary(
                repository.countByStatus(NotificationOutbox.STATUS_PENDING),
                repository.countByStatus(NotificationOutbox.STATUS_SENT),
                repository.countByStatus(NotificationOutbox.STATUS_FAILED));
    }

    /** Statü rozet sayıları. */
    public record Summary(long pending, long sent, long failed) {}

    /** Tek gönderim satırı (admin listesi). */
    public record DeliveryItem(
            Long id,
            String kind,
            String notifType,
            Long fixtureId,
            Long teamId,
            String title,
            String body,
            String status,
            String sendMode,
            Integer recipients,
            Integer deliveredCount,
            int attempts,
            boolean silent,
            String lastError,
            Instant createdAt,
            Instant sentAt
    ) {
        static DeliveryItem from(NotificationOutbox o) {
            return new DeliveryItem(
                    o.getId(), o.getKind(), o.getNotifType(), o.getFixtureId(),
                    o.getTeamId(), o.getTitle(), o.getBody(), o.getStatus(),
                    o.getSendMode(), o.getRecipients(), o.getDeliveredCount(),
                    o.getAttempts(), o.isSilent(), o.getLastError(),
                    o.getCreatedAt(), o.getSentAt());
        }
    }
}
