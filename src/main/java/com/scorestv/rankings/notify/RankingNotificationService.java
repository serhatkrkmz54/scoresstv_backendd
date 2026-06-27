package com.scorestv.rankings.notify;

import com.scorestv.football.domain.Team;
import com.scorestv.football.domain.TeamRepository;
import com.scorestv.mobile.domain.MobileDeviceToken;
import com.scorestv.mobile.domain.MobileDeviceTokenRepository;
import com.scorestv.mobile.domain.UserNotificationPref;
import com.scorestv.mobile.domain.UserNotificationPrefRepository;
import com.scorestv.mobile.fcm.FcmMessagingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Siralama (FIFA / UEFA Ulke / UEFA Kulup) sira degisimlerini FCM push olarak
 * gonderir.
 *
 * <p><b>Hedefleme:</b>
 * <ul>
 *   <li>FIFA + UEFA Ulke → degisen ulkenin koduyla eslesen, {@code
 *       notify_rankings_country} acik cihazlar (cihaz tabanli).</li>
 *   <li>UEFA Kulup → kulup app Team'e isim/kod ile eslenir; o takimi takip eden,
 *       {@code notify_rankings_club} acik cihazlar.</li>
 * </ul>
 *
 * <p><b>Async + commit-sonrasi:</b> Sync servisleri degisimleri tx icinde
 * toplar, {@code afterCommit} ile bu metodu cagirir. {@code @Async} sayesinde
 * FCM I/O sync transaction'ini bloke etmez; commit-sonrasi cagri sayesinde
 * rollback'te yanlis bildirim gitmez.
 *
 * <p><b>Lokalize:</b> Alicilar cihaz {@code locale}'ine (tr/en) gore gruplanir,
 * her gruba dilinde mesaj gonderilir.
 */
@Service
public class RankingNotificationService {

    private static final Logger log =
            LoggerFactory.getLogger(RankingNotificationService.class);

    private final MobileDeviceTokenRepository deviceRepository;
    private final UserNotificationPrefRepository prefRepository;
    private final TeamRepository teamRepository;
    private final FcmMessagingService fcm;

    public RankingNotificationService(MobileDeviceTokenRepository deviceRepository,
                                      UserNotificationPrefRepository prefRepository,
                                      TeamRepository teamRepository,
                                      FcmMessagingService fcm) {
        this.deviceRepository = deviceRepository;
        this.prefRepository = prefRepository;
        this.teamRepository = teamRepository;
        this.fcm = fcm;
    }

    /**
     * Degisim listesini bildirir. Sync servislerinden {@code afterCommit} ile
     * cagrilir; ayri thread + ayri (read-only) transaction'da calisir.
     */
    @Async
    @Transactional(readOnly = true)
    public void notifyChanges(List<RankingChange> changes) {
        if (!fcm.isEnabled() || changes == null || changes.isEmpty()) {
            return;
        }
        // Takim isim index'i sadece bir UEFA_CLUB degisimi varsa kurulur (maliyetli).
        Map<String, Team> teamIndex = null;
        int sentGroups = 0;
        for (RankingChange c : changes) {
            try {
                switch (c.scope()) {
                    case FIFA, UEFA_COUNTRY -> sentGroups += notifyCountry(c);
                    case UEFA_CLUB -> {
                        if (teamIndex == null) teamIndex = buildTeamIndex();
                        sentGroups += notifyClub(c, teamIndex);
                    }
                }
            } catch (Exception ex) {
                log.warn("Ranking bildirim hatasi ({} {}): {}",
                        c.scope(), c.displayName(), ex.getMessage());
            }
        }
        log.info("Ranking bildirim tamam: {} degisim, {} gonderim grubu",
                changes.size(), sentGroups);
    }

    // ============================================================
    // Ulke tabanli (FIFA + UEFA Ulke)
    // ============================================================

    private int notifyCountry(RankingChange c) {
        if (c.countryCode() == null || c.countryCode().isBlank()) return 0;
        List<MobileDeviceToken> recipients =
                deviceRepository.findRankingsCountryRecipients(c.countryCode());
        if (recipients.isEmpty()) return 0;

        Map<String, Set<String>> byLocale = new HashMap<>();
        for (MobileDeviceToken t : recipients) {
            if (t.getFcmToken() == null) continue;
            byLocale.computeIfAbsent(localeOf(t.getLocale()), k -> new LinkedHashSet<>())
                    .add(t.getFcmToken());
        }
        return sendPerLocale(byLocale, c, data(c, null));
    }

    // ============================================================
    // Kulup tabanli (UEFA Kulup) — isim/kod ile takim eslesmesi
    // ============================================================

    private int notifyClub(RankingChange c, Map<String, Team> teamIndex) {
        Team team = matchTeam(c, teamIndex);
        if (team == null) {
            log.info("UEFA kulup app takimina eslesmedi, bildirim atlandi: {} ({})",
                    c.displayName(), c.teamCode());
            return 0;
        }
        List<UserNotificationPref> recipients =
                prefRepository.findRankingClubRecipients(team.getId());
        if (recipients.isEmpty()) return 0;

        Map<String, Set<String>> byLocale = new HashMap<>();
        for (UserNotificationPref p : recipients) {
            MobileDeviceToken t = p.getDeviceToken();
            if (t == null || t.getFcmToken() == null) continue;
            byLocale.computeIfAbsent(localeOf(t.getLocale()), k -> new LinkedHashSet<>())
                    .add(t.getFcmToken());
        }
        return sendPerLocale(byLocale, c, data(c, team.getId()));
    }

    /** Her locale grubuna dilinde mesaj gonderir; gonderilen grup sayisini doner. */
    private int sendPerLocale(Map<String, Set<String>> byLocale, RankingChange c,
                              Map<String, String> data) {
        int groups = 0;
        for (Map.Entry<String, Set<String>> e : byLocale.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            Msg msg = buildMessage(c, e.getKey());
            fcm.sendMulticast(new ArrayList<>(e.getValue()), msg.title(), msg.body(), data);
            groups++;
        }
        return groups;
    }

    // ============================================================
    // Mesaj uretimi (TR / EN)
    // ============================================================

    private record Msg(String title, String body) {}

    private Msg buildMessage(RankingChange c, String locale) {
        boolean up = c.isUp();
        boolean en = "en".equals(locale);
        String name = c.displayName() != null ? c.displayName() : "";
        String title = switch (c.scope()) {
            case FIFA -> en ? "📊 FIFA World Ranking" : "📊 FIFA Dünya Sıralaması";
            case UEFA_COUNTRY -> en ? "🏆 UEFA Country Ranking" : "🏆 UEFA Ülke Sıralaması";
            case UEFA_CLUB -> en ? "🏆 UEFA Club Ranking" : "🏆 UEFA Kulüp Sıralaması";
        };
        String body;
        if (en) {
            body = up
                    ? String.format("%s climbed to #%d (was #%d).", name, c.newRank(), c.oldRank())
                    : String.format("%s slipped to #%d (was #%d).", name, c.newRank(), c.oldRank());
        } else {
            body = up
                    ? String.format("%s %d. sıraya yükseldi (önceki %d.).", name, c.newRank(), c.oldRank())
                    : String.format("%s %d. sıraya geriledi (önceki %d.).", name, c.newRank(), c.oldRank());
        }
        return new Msg(title, body);
    }

    /** FCM data payload — mobile bildirime tiklayinca Siralamalar'a yonlendirir. */
    private Map<String, String> data(RankingChange c, Long teamId) {
        Map<String, String> d = new HashMap<>();
        d.put("type", "ranking");
        d.put("rankingType", switch (c.scope()) {
            case FIFA -> "fifa";
            case UEFA_COUNTRY -> "uefa_country";
            case UEFA_CLUB -> "uefa_club";
        });
        d.put("rank", String.valueOf(c.newRank()));
        if (teamId != null) d.put("teamId", String.valueOf(teamId));
        return d;
    }

    // ============================================================
    // Takim eslesmesi (isim/kod normalize)
    // ============================================================

    /** Kulup-olmayan (national=false) tum takimlardan normalize isim index'i. */
    private Map<String, Team> buildTeamIndex() {
        Map<String, Team> index = new HashMap<>();
        for (Team t : teamRepository.findAll()) {
            if (t.isNational()) continue; // milli takim, kulup degil
            putIfAbsent(index, norm(t.getName()), t);
            if (t.getNameTr() != null) putIfAbsent(index, norm(t.getNameTr()), t);
        }
        return index;
    }

    private Team matchTeam(RankingChange c, Map<String, Team> index) {
        for (String candidate : List.of(
                c.displayName() != null ? c.displayName() : "",
                c.clubShortName() != null ? c.clubShortName() : "")) {
            String key = norm(candidate);
            if (key.length() < 3) continue;
            Team t = index.get(key);
            if (t != null) return t;
        }
        return null;
    }

    private static void putIfAbsent(Map<String, Team> index, String key, Team t) {
        if (key != null && key.length() >= 3) {
            index.putIfAbsent(key, t);
        }
    }

    /**
     * Takim adi normalize: diakritik kaldir, kucuk harf, yaygin futbol
     * jeton/eklerini (fc, cf, sk, ...) ve alfanumerik-disi karakterleri temizle.
     */
    private static String norm(String raw) {
        if (raw == null) return "";
        String x = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        x = x.toLowerCase(Locale.ROOT);
        x = x.replaceAll(
                "\\b(fc|cf|sc|sk|ac|as|ssc|fk|bk|if|cd|ud|sv|afc|rc|sl|sad|"
                + "club|kulubu|kulubü|spor|kulup)\\b", " ");
        x = x.replaceAll("[^a-z0-9]", " ").replaceAll("\\s+", " ").trim();
        return x;
    }

    /** Cihaz locale'ini "tr" ya da "en"e indirger (default tr). */
    private static String localeOf(String raw) {
        if (raw == null || raw.isBlank()) return "tr";
        return raw.toLowerCase(Locale.ROOT).startsWith("en") ? "en" : "tr";
    }
}
