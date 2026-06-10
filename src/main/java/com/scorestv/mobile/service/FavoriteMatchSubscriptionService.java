package com.scorestv.mobile.service;

import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.mobile.domain.DeviceMatchSubscription;
import com.scorestv.mobile.domain.DeviceMatchSubscriptionRepository;
import com.scorestv.mobile.domain.MobileDeviceToken;
import com.scorestv.mobile.domain.MobileDeviceTokenRepository;
import com.scorestv.mobile.web.dto.SyncFavoriteMatchesRequest;
import com.scorestv.mobile.web.dto.SyncFavoriteMatchesResponse;
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
 * Mobile favori mac listesini backend'e replace pattern ile sync eder.
 *
 * <p><b>Akis:</b>
 * <ol>
 *   <li>Mobile tum favori fixture id listesini POST eder</li>
 *   <li>Token DB'de aranir (yoksa "device token bulunamadi" hata)</li>
 *   <li>Bu cihaz icin eski tum {@code device_match_subscriptions} kayitlari
 *       silinir (cascade ile)</li>
 *   <li>Yeni id'lerin her biri icin fixture DB'de var mi kontrol edilir
 *       (FK guard); olmayanlar atlanir</li>
 *   <li>Tum gecerli id'ler tek seferde INSERT edilir</li>
 * </ol>
 *
 * <p><b>Idempotent:</b> ayni listeyi 100 kez sync etse bile sonuc ayni —
 * delete + insert tek transaction icinde.
 */
@Service
public class FavoriteMatchSubscriptionService {

    private static final Logger log =
            LoggerFactory.getLogger(FavoriteMatchSubscriptionService.class);

    private final DeviceMatchSubscriptionRepository subscriptionRepository;
    private final MobileDeviceTokenRepository deviceTokenRepository;
    private final FixtureRepository fixtureRepository;

    /**
     * Delete + insert ayni TX icinde — Hibernate insert'i once flush etmesin
     * diye manuel flush + clear.
     */
    @PersistenceContext
    private EntityManager entityManager;

    public FavoriteMatchSubscriptionService(
            DeviceMatchSubscriptionRepository subscriptionRepository,
            MobileDeviceTokenRepository deviceTokenRepository,
            FixtureRepository fixtureRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.fixtureRepository = fixtureRepository;
    }

    @Transactional
    public SyncFavoriteMatchesResponse sync(SyncFavoriteMatchesRequest req) {
        final String fcmToken = req.fcmToken().trim();
        final List<Long> requestedIds = req.fixtureIds() == null
                ? List.of() : req.fixtureIds();

        // 1) Cihaz token DB'de var mi?
        MobileDeviceToken device = deviceTokenRepository
                .findByFcmToken(fcmToken)
                .orElseThrow(() -> new IllegalStateException(
                        "Device token bulunamadi — once /api/v1/mobile/device-tokens "
                                + "POST ile kayit yapilmali."));

        // 2) Eski abonelikleri sil (replace pattern)
        subscriptionRepository.deleteByDeviceTokenId(device.getId());
        entityManager.flush();
        entityManager.clear();

        // 3) Token yine ataniyor — clear sonrasi proxy yenile
        device = deviceTokenRepository.getReferenceById(device.getId());

        if (requestedIds.isEmpty()) {
            log.debug("FavoriteMatch sync: deviceId={} (token={}) listesi temizlendi",
                    device.getId(), maskToken(fcmToken));
            return new SyncFavoriteMatchesResponse(0, 0);
        }

        // 4) FK guard — DB'de var olan fixture id'leri filtre.
        // Set ile dedup; istekte tekrar gelen id'leri tek say.
        Set<Long> uniqueIds = new HashSet<>(requestedIds);
        List<Fixture> validFixtures = fixtureRepository.findAllById(uniqueIds);
        if (validFixtures.size() < uniqueIds.size()) {
            log.debug("FavoriteMatch sync: {} id istendi, {} fixture DB'de var",
                    uniqueIds.size(), validFixtures.size());
        }

        // 5) Yeni abonelikleri yaz
        int written = 0;
        for (Fixture fixture : validFixtures) {
            DeviceMatchSubscription sub = new DeviceMatchSubscription();
            sub.setDeviceToken(device);
            sub.setFixture(fixture);
            subscriptionRepository.save(sub);
            written++;
        }

        log.info("FavoriteMatch sync: deviceId={} istek={} yazilan={} (token={})",
                device.getId(), requestedIds.size(), written, maskToken(fcmToken));
        return new SyncFavoriteMatchesResponse(written, requestedIds.size());
    }

    /** Log'da token'in tamamini gostermemek icin maske. */
    private static String maskToken(String token) {
        if (token == null || token.length() < 12) return "***";
        return token.substring(0, 6) + "..." + token.substring(token.length() - 4);
    }
}
