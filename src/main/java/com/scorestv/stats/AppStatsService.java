package com.scorestv.stats;

import com.scorestv.game.GamePickRepository;
import com.scorestv.mobile.domain.MobileDeviceTokenRepository;
import com.scorestv.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel "Uygulama Istatistikleri" hesaplayici. Basit COUNT aggregate'leri —
 * hepsi indeksli/hafif; ekstra tablo yok. Salt-okunur tek transaction.
 */
@Service
public class AppStatsService {

    private static final int TOP_COUNTRIES = 10;

    private final UserRepository userRepo;
    private final MobileDeviceTokenRepository deviceRepo;
    private final GamePickRepository pickRepo;
    private final EntityManager em;

    public AppStatsService(UserRepository userRepo,
                           MobileDeviceTokenRepository deviceRepo,
                           GamePickRepository pickRepo,
                           EntityManager em) {
        this.userRepo = userRepo;
        this.deviceRepo = deviceRepo;
        this.pickRepo = pickRepo;
        this.em = em;
    }

    @Transactional(readOnly = true)
    public AppStats compute() {
        final Instant now = Instant.now();
        final Instant d1 = now.minus(Duration.ofDays(1));
        final Instant d7 = now.minus(Duration.ofDays(7));
        final Instant d30 = now.minus(Duration.ofDays(30));

        // ---- Uyeler ----
        long usersTotal = userRepo.count();
        long usersNew24h = countSince("User u", "u.createdAt", d1);
        long usersNew7d = countSince("User u", "u.createdAt", d7);
        long usersNew30d = countSince("User u", "u.createdAt", d30);
        long usersGoogle = count("SELECT COUNT(u) FROM User u WHERE u.googleId IS NOT NULL");
        long usersApple = count("SELECT COUNT(u) FROM User u WHERE u.appleId IS NOT NULL");
        long usersEmail = count(
                "SELECT COUNT(u) FROM User u WHERE u.googleId IS NULL AND u.appleId IS NULL");

        // ---- Cihazlar ----
        long devicesTotal = deviceRepo.count();
        long devicesAndroid = count(
                "SELECT COUNT(t) FROM MobileDeviceToken t WHERE LOWER(t.platform) = 'android'");
        long devicesIos = count(
                "SELECT COUNT(t) FROM MobileDeviceToken t WHERE LOWER(t.platform) = 'ios'");
        long devicesNotifOn = count(
                "SELECT COUNT(t) FROM MobileDeviceToken t WHERE t.notificationsEnabled = true");
        long devicesLinked = count(
                "SELECT COUNT(t) FROM MobileDeviceToken t WHERE t.appUserId IS NOT NULL");
        long devicesActive7d = countSince(
                "MobileDeviceToken t", "t.lastSeenAt", d7);
        long devicesActive30d = countSince(
                "MobileDeviceToken t", "t.lastSeenAt", d30);
        List<AppStats.CountryCount> topCountries = topCountries();

        // ---- Oyun ----
        long gamePicksTotal = pickRepo.count();
        long gamePlayers = count(
                "SELECT COUNT(DISTINCT p.userId) FROM GamePick p");

        return new AppStats(
                usersTotal, usersNew24h, usersNew7d, usersNew30d,
                usersGoogle, usersApple, usersEmail,
                devicesTotal, devicesAndroid, devicesIos, devicesNotifOn,
                devicesLinked, devicesActive7d, devicesActive30d, topCountries,
                gamePicksTotal, gamePlayers);
    }

    /** Parametresiz COUNT sorgusu. */
    private long count(String jpql) {
        Long n = em.createQuery(jpql, Long.class).getSingleResult();
        return n != null ? n : 0L;
    }

    /** "... WHERE {field} >= :since" seklinde tarih filtreli COUNT. */
    private long countSince(String fromEntity, String field, Instant since) {
        String alias = fromEntity.substring(fromEntity.indexOf(' ') + 1);
        String jpql = "SELECT COUNT(" + alias + ") FROM " + fromEntity
                + " WHERE " + field + " >= :since";
        Long n = em.createQuery(jpql, Long.class)
                .setParameter("since", since)
                .getSingleResult();
        return n != null ? n : 0L;
    }

    /** Ulke koduna gore en cok cihazi olan {@value #TOP_COUNTRIES} ulke. */
    private List<AppStats.CountryCount> topCountries() {
        List<Object[]> rows = em.createQuery(
                        "SELECT t.countryCode, COUNT(t) FROM MobileDeviceToken t "
                                + "WHERE t.countryCode IS NOT NULL "
                                + "GROUP BY t.countryCode ORDER BY COUNT(t) DESC",
                        Object[].class)
                .setMaxResults(TOP_COUNTRIES)
                .getResultList();
        List<AppStats.CountryCount> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            String cc = (String) r[0];
            long c = ((Number) r[1]).longValue();
            out.add(new AppStats.CountryCount(cc, c));
        }
        return out;
    }
}
