package com.scorestv.basketball.notify;

import com.scorestv.basketball.domain.BasketballGame;
import com.scorestv.basketball.domain.BasketballGameRepository;
import com.scorestv.basketball.domain.DeviceBasketballSubscription;
import com.scorestv.basketball.domain.DeviceBasketballSubscriptionRepository;
import com.scorestv.basketball.web.dto.SyncFavoriteBasketballRequest;
import com.scorestv.basketball.web.dto.SyncFavoriteBasketballResponse;
import com.scorestv.mobile.domain.MobileDeviceToken;
import com.scorestv.mobile.domain.MobileDeviceTokenRepository;
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
 * Mobile favori basketbol maçı listesini backend'e replace-sync eder
 * (football'daki {@code FavoriteMatchSubscriptionService}'in basketbol eşi).
 */
@Service
public class FavoriteBasketballSubscriptionService {

    private static final Logger log =
            LoggerFactory.getLogger(FavoriteBasketballSubscriptionService.class);

    private final DeviceBasketballSubscriptionRepository subscriptionRepository;
    private final MobileDeviceTokenRepository deviceTokenRepository;
    private final BasketballGameRepository gameRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public FavoriteBasketballSubscriptionService(
            DeviceBasketballSubscriptionRepository subscriptionRepository,
            MobileDeviceTokenRepository deviceTokenRepository,
            BasketballGameRepository gameRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.gameRepository = gameRepository;
    }

    @Transactional
    public SyncFavoriteBasketballResponse sync(SyncFavoriteBasketballRequest req) {
        final String fcmToken = req.fcmToken().trim();
        final List<Long> requestedIds = req.gameIds() == null ? List.of() : req.gameIds();

        MobileDeviceToken device = deviceTokenRepository
                .findByFcmToken(fcmToken)
                .orElseThrow(() -> new IllegalStateException(
                        "Device token bulunamadı — önce /api/v1/mobile/device-tokens "
                                + "POST ile kayıt yapılmalı."));

        // Replace pattern — eski abonelikleri sil.
        subscriptionRepository.deleteByDeviceTokenId(device.getId());
        entityManager.flush();
        entityManager.clear();
        device = deviceTokenRepository.getReferenceById(device.getId());

        if (requestedIds.isEmpty()) {
            log.debug("FavoriteBasketball sync: deviceId={} listesi temizlendi", device.getId());
            return new SyncFavoriteBasketballResponse(0, 0);
        }

        // FK guard — DB'de var olan maç id'leri.
        Set<Long> uniqueIds = new HashSet<>(requestedIds);
        List<BasketballGame> validGames = gameRepository.findAllById(uniqueIds);

        int written = 0;
        for (BasketballGame game : validGames) {
            DeviceBasketballSubscription sub = new DeviceBasketballSubscription();
            sub.setDeviceToken(device);
            sub.setGame(game);
            subscriptionRepository.save(sub);
            written++;
        }

        log.info("FavoriteBasketball sync: deviceId={} istek={} yazılan={}",
                device.getId(), requestedIds.size(), written);
        return new SyncFavoriteBasketballResponse(written, requestedIds.size());
    }
}
