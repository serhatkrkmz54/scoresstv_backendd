package com.scorestv.basketball.web;

import com.scorestv.basketball.notify.FavoriteBasketballSubscriptionService;
import com.scorestv.basketball.web.dto.SyncFavoriteBasketballRequest;
import com.scorestv.basketball.web.dto.SyncFavoriteBasketballResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mobile favori basketbol maçı bildirim abonelikleri endpoint'i (public,
 * anonim cihaz tabanlı — fcmToken identity). Cihaz favori listesini her
 * değiştirdiğinde replace-sync çağırır.
 *
 * <p>Abone olunan maçlar için: maç başladı, çeyrek bitti (skorlu), maç bitti
 * FCM push'ları gönderilir.
 */
@RestController
@RequestMapping("/api/v1/mobile/favorite-basketball")
public class BasketballFavoriteController {

    private final FavoriteBasketballSubscriptionService service;

    public BasketballFavoriteController(FavoriteBasketballSubscriptionService service) {
        this.service = service;
    }

    /** POST /api/v1/mobile/favorite-basketball/sync — Body: { fcmToken, gameIds:[...] } */
    @PostMapping("/sync")
    public SyncFavoriteBasketballResponse sync(
            @Valid @RequestBody SyncFavoriteBasketballRequest req) {
        return service.sync(req);
    }
}
