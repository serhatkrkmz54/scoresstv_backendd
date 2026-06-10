package com.scorestv.mobile.web;

import com.scorestv.mobile.service.FavoriteMatchSubscriptionService;
import com.scorestv.mobile.web.dto.SyncFavoriteMatchesRequest;
import com.scorestv.mobile.web.dto.SyncFavoriteMatchesResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mobile favori mac bildirim abonelikleri endpoint'i.
 *
 * <p>Public — anonim cihaz tabanli (fcmToken ile identity). Cihaz favori
 * listesini her degistirdiginde mobile bu endpoint'i fire-and-forget cagirir;
 * backend cihaz icin eski abonelikleri silip yenilerini yazar (replace).
 *
 * <p>Subscribe edilen maclar icin gol/kart/MB/MS/penalti olaylarinda FCM
 * push gonderilir (NotificationDispatcher).
 */
@RestController
@RequestMapping("/api/v1/mobile/favorite-matches")
public class MobileFavoriteMatchController {

    private final FavoriteMatchSubscriptionService service;

    public MobileFavoriteMatchController(
            FavoriteMatchSubscriptionService service) {
        this.service = service;
    }

    /**
     * POST /api/v1/mobile/favorite-matches/sync
     * Body: { fcmToken, fixtureIds: [1, 2, 3] }
     */
    @PostMapping("/sync")
    public SyncFavoriteMatchesResponse sync(
            @Valid @RequestBody SyncFavoriteMatchesRequest req) {
        return service.sync(req);
    }
}
