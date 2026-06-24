package com.scorestv.volleyball.notify;

import com.scorestv.mobile.domain.MobileDeviceToken;
import com.scorestv.mobile.domain.MobileDeviceTokenRepository;
import com.scorestv.volleyball.domain.DeviceVolleyballSubscription;
import com.scorestv.volleyball.domain.DeviceVolleyballSubscriptionRepository;
import com.scorestv.volleyball.domain.VolleyballGame;
import com.scorestv.volleyball.domain.VolleyballGameRepository;
import com.scorestv.volleyball.web.dto.SyncFavoriteVolleyballRequest;
import com.scorestv.volleyball.web.dto.SyncFavoriteVolleyballResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mobile favori voleybol maci listesini backend'e replace-sync eder
 * (basketbol {@code FavoriteBasketballSubscriptionService}'in voleybol esi).
 */
@Service
public class FavoriteVolleyballSubscriptionService {

    private static final Logger log =
            LoggerFactory.getLogger(FavoriteVolleyballSubscriptionService.class);

    private final DeviceVolleyballSubscriptionRepository subscriptionRepository;
    private final MobileDeviceTokenRepository deviceTokenRepository;
    private final VolleyballGameRepository gameRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public FavoriteVolleyballSubscriptionService(
            DeviceVolleyballSubscriptionRepository subscriptionRepository,
            MobileDeviceTokenRepository deviceTokenRepository,
            VolleyballGameRepository gameRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.gameRepository = gameRepository;
    }

    @Transactional
    public SyncFavoriteVolleyballResponse sync(SyncFavoriteVolleyballRequest req) {
        final String fcmToken = req.fcmToken().trim();
        final List<Long> requestedIds = req.gameIds() == null ? List.of() : req.gameIds();

        MobileDeviceToken device = deviceTokenRepository
                .findByFcmToken(fcmToken)
                .orElseThrow(() -> new IllegalStateException(
                        "Device token bulunamadi — once /api/v1/mobile/device-tokens "
                                + "POST ile kayit yapilmali."));

        subscriptionRepository.deleteByDeviceTokenId(device.getId());
        entityManager.flush();
        entityManager.clear();
        device = deviceTokenRepository.getReferenceById(device.getId());

        if (requestedIds.isEmpty()) {
            log.debug("FavoriteVolleyball sync: deviceId={} listesi temizlendi", device.getId());
            return new SyncFavoriteVolleyballResponse(0, 0);
        }

        Set<Long> uniqueIds = new HashSet<>(requestedIds);
        List<VolleyballGame> validGames = gameRepository.findAllById(uniqueIds);

        int written = 0;
        for (VolleyballGame game : validGames) {
            DeviceVolleyballSubscription sub = new DeviceVolleyballSubscription();
            sub.setDeviceToken(device);
            sub.setGame(game);
            subscriptionRepository.save(sub);
            written++;
        }

        log.info("FavoriteVolleyball sync: deviceId={} istek={} yazilan={}",
                device.getId(), requestedIds.size(), written);
        return new SyncFavoriteVolleyballResponse(written, requestedIds.size());
    }
}
