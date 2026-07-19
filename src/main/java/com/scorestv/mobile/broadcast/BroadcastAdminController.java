package com.scorestv.mobile.broadcast;

import com.scorestv.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Genel bildirim (broadcast) yonetim uclari. {@code /api/v1/admin/**} zaten
 * SecurityConfig'te authenticated; burada EDITOR/ADMIN ile gatelenir.
 *
 * <ul>
 *   <li>POST /broadcast — herkese/platforma/dile gore push gonder</li>
 *   <li>GET  /broadcast — gonderim gecmisi (en yeni ustte)</li>
 *   <li>POST /test — yalnizca bir e-postanin cihazlarina test push (senkron)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/admin/notifications")
public class BroadcastAdminController {

    private final BroadcastNotificationService service;

    public BroadcastAdminController(BroadcastNotificationService service) {
        this.service = service;
    }

    @PostMapping("/broadcast")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public BroadcastResult send(@Valid @RequestBody SendBroadcastRequest req,
                                @AuthenticationPrincipal CurrentUser currentUser) {
        // Kuyruga alir ve HEMEN doner; gonderim arka planda (garantili) yapilir.
        BroadcastNotification saved = service.enqueue(
                req.title().trim(),
                req.body().trim(),
                req.link(),
                req.platform(),
                req.lang(),
                currentUser != null ? currentUser.id() : null);
        return BroadcastResult.from(saved);
    }

    @GetMapping("/broadcast")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public List<BroadcastListItem> history(
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return service.history(limit).stream().map(BroadcastListItem::from).toList();
    }

    /**
     * TEST: yalnizca verilen e-postaya ait hesabin cihazlarina push gonderir
     * (senkron, herkese gitmez, gecmise yazilmaz). Push'un telefonda dogru
     * geldigini denemek icin.
     */
    @PostMapping("/test")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public TestNotificationResult sendTest(
            @Valid @RequestBody SendTestNotificationRequest req) {
        return service.sendTest(req.email().trim(), req.title().trim(),
                req.body().trim(), req.link());
    }
}
