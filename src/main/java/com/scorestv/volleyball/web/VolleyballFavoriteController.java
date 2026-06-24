package com.scorestv.volleyball.web;

import com.scorestv.volleyball.notify.FavoriteVolleyballSubscriptionService;
import com.scorestv.volleyball.web.dto.SyncFavoriteVolleyballRequest;
import com.scorestv.volleyball.web.dto.SyncFavoriteVolleyballResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mobile favori voleybol maci bildirim abonelikleri endpoint'i (public, anonim
 * cihaz tabanli — fcmToken identity). Cihaz favori listesini her degistirdiginde
 * replace-sync cagirir.
 */
@RestController
@RequestMapping("/api/v1/mobile/favorite-volleyball")
public class VolleyballFavoriteController {

    private final FavoriteVolleyballSubscriptionService service;

    public VolleyballFavoriteController(FavoriteVolleyballSubscriptionService service) {
        this.service = service;
    }

    /** POST /api/v1/mobile/favorite-volleyball/sync — Body: { fcmToken, gameIds:[...] } */
    @PostMapping("/sync")
    public SyncFavoriteVolleyballResponse sync(
            @Valid @RequestBody SyncFavoriteVolleyballRequest req) {
        return service.sync(req);
    }
}
