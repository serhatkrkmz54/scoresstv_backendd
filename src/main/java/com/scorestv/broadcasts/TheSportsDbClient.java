package com.scorestv.broadcasts;

import com.scorestv.broadcasts.dto.TsdbEventDto;
import com.scorestv.broadcasts.dto.TsdbEventsResponse;
import com.scorestv.broadcasts.dto.TsdbPlayerDto;
import com.scorestv.broadcasts.dto.TsdbPlayerLookupResponse;
import com.scorestv.broadcasts.dto.TsdbPlayerSearchResponse;
import com.scorestv.broadcasts.dto.TsdbTvDto;
import com.scorestv.broadcasts.dto.TsdbTvResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TheSportsDB v1 HTTP istemcisi. Anahtar URL'de gider ({@code /{key}/...}).
 * Hata/ağ sorununda boş liste döner — çağıran maç detayını yine gösterebilsin.
 */
@Component
public class TheSportsDbClient {

    private static final Logger log = LoggerFactory.getLogger(TheSportsDbClient.class);

    private final RestClient http;
    private final TheSportsDbProperties props;

    /** Gün bazında event cache (date → idAPIfootball → event). */
    private static final long DAY_TTL_MIN = 360;
    private final ConcurrentHashMap<String, DayEntry> dayCache = new ConcurrentHashMap<>();

    /** Oyuncu ayağı cache (API-Football oyuncu id → strSide). Ayak neredeyse hiç
     *  değişmez → uzun TTL. Bulunamayan (negatif) sonuç daha kısa TTL ile cache'lenir
     *  ki yeni eklenen oyuncular sonradan yakalanabilsin. */
    private static final long FOOT_TTL_MIN = 30L * 24 * 60;     // 30 gün
    private static final long FOOT_EMPTY_TTL_MIN = 24 * 60;     // 1 gün
    /** searchplayers'tan dönen aday sayısının üst sınırı (gereksiz lookupplayer
     *  çağrılarını engeller). Tam isim araması genelde 1-3 sonuç döner. */
    private static final int FOOT_MAX_CANDIDATES = 8;
    private final ConcurrentHashMap<Long, FootEntry> footCache = new ConcurrentHashMap<>();

    public TheSportsDbClient(TheSportsDbProperties props) {
        this.props = props;
        var rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(8));
        rf.setReadTimeout(Duration.ofSeconds(12));
        this.http = RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(rf)
                .build();
    }

    /**
     * {@code searchevents.php?e={Home}_vs_{Away}&d={date}} — isim + tarihle
     * event arar. Sonuçlar {@code idAPIfootball} ile doğrulanır (çağıran).
     */
    public List<TsdbEventDto> searchEvents(String home, String away, String date) {
        if (!props.enabled()) return List.of();
        String e = (home + "_vs_" + away).replace(' ', '_');
        try {
            TsdbEventsResponse resp = http.get()
                    .uri(u -> u.path("/{key}/searchevents.php")
                            .queryParam("e", e)
                            .queryParam("d", date)
                            .build(props.apiKey()))
                    .retrieve()
                    .body(TsdbEventsResponse.class);
            if (resp == null || resp.event() == null) return List.of();
            return resp.event();
        } catch (Exception ex) {
            log.debug("TheSportsDB searchevents hata e={} d={}: {}", e, date, ex.toString());
            return List.of();
        }
    }

    /**
     * Maçı TheSportsDB event'iyle eşleştir: önce isim+tarih (searchevents),
     * {@code idAPIfootball == fixtureId} doğrula; tek sonuç varsa kabul et;
     * bulamazsa isimden bağımsız gün listesi (eventsday) yedeği. TV ve highlight
     * (strVideo) için ortak giriş noktası.
     */
    public TsdbEventDto matchEvent(String home, String away, String date, long fixtureId) {
        String fid = String.valueOf(fixtureId);
        List<TsdbEventDto> events = searchEvents(home, away, date);
        for (TsdbEventDto ev : events) {
            if (fid.equals(ev.idAPIfootball())) return ev;
        }
        if (events.size() == 1) return events.get(0);
        return findByApiFootballId(date, fixtureId);
    }

    /**
     * İsimden BAĞIMSIZ eşleştirme — o günün tüm futbol event'lerini çekip
     * {@code idAPIfootball == fixtureId} olanı döner. searchevents takım adı
     * farkına takıldığında (ör. "Czechia" vs "Czech Republic") yedek olarak
     * kullanılır. Gün bazında cache'lenir (aynı günün maçları tek çağrıyı paylaşır).
     */
    public TsdbEventDto findByApiFootballId(String date, long fixtureId) {
        if (!props.enabled()) return null;
        DayEntry de = dayCache.get(date);
        if (de == null || de.expiresAt().isBefore(Instant.now())) {
            Map<String, TsdbEventDto> map = new HashMap<>();
            for (TsdbEventDto ev : eventsDay(date)) {
                String fid = ev.idAPIfootball();
                if (fid != null && !fid.isBlank()) map.putIfAbsent(fid, ev);
            }
            de = new DayEntry(map, Instant.now().plus(Duration.ofMinutes(DAY_TTL_MIN)));
            dayCache.put(date, de);
        }
        return de.byFixture().get(String.valueOf(fixtureId));
    }

    /** {@code eventsday.php?d={date}&s=Soccer} — o günün futbol event'leri. */
    private List<TsdbEventDto> eventsDay(String date) {
        try {
            TsdbEventsResponse resp = http.get()
                    .uri(u -> u.path("/{key}/eventsday.php")
                            .queryParam("d", date)
                            .queryParam("s", "Soccer")
                            .build(props.apiKey()))
                    .retrieve()
                    .body(TsdbEventsResponse.class);
            if (resp == null || resp.event() == null) return List.of();
            return resp.event();
        } catch (Exception ex) {
            log.debug("TheSportsDB eventsday hata d={}: {}", date, ex.toString());
            return List.of();
        }
    }

    /** {@code lookuptv.php?id={idEvent}} — event'i yayınlayan TV kanalları. */
    public List<TsdbTvDto> lookupTv(String idEvent) {
        if (!props.enabled() || idEvent == null || idEvent.isBlank()) return List.of();
        try {
            TsdbTvResponse resp = http.get()
                    .uri(u -> u.path("/{key}/lookuptv.php")
                            .queryParam("id", idEvent)
                            .build(props.apiKey()))
                    .retrieve()
                    .body(TsdbTvResponse.class);
            if (resp == null || resp.tvevent() == null) return List.of();
            return resp.tvevent();
        } catch (Exception ex) {
            log.debug("TheSportsDB lookuptv hata id={}: {}", idEvent, ex.toString());
            return List.of();
        }
    }

    private record DayEntry(Map<String, TsdbEventDto> byFixture, Instant expiresAt) {}

    // ---------------------------------------------------------------------
    // Oyuncu — kullandığı ayak (strSide). API-Football oyuncu pozisyonu verir
    // ama ayağı vermez; TheSportsDB verir. İsimle searchplayers → her aday için
    // lookupplayer → idAPIfootball eşleşmesi → strSide. idAPIfootball tam sayı
    // eşleşmesi olduğu için yanlış oyuncu gelme riski yok.
    // ---------------------------------------------------------------------

    /**
     * Oyuncunun kullandığı ayağı ("Right" / "Left" / "Both") döner; bulunamazsa
     * {@code null}. {@code apiFootballPlayerId} ile eşleşme doğrulanır. Sonuç
     * uzun süre cache'lenir (ayak değişmez). Hata/ağ sorununda null döner —
     * çağıran oyuncu sayfasını yine gösterebilsin.
     */
    public String lookupFoot(String playerName, long apiFootballPlayerId) {
        if (!props.enabled() || playerName == null || playerName.isBlank()) return null;
        FootEntry fe = footCache.get(apiFootballPlayerId);
        if (fe != null && fe.expiresAt().isAfter(Instant.now())) {
            return fe.foot();  // pozitif VE negatif (null) sonuç cache'lenir
        }
        String foot = resolveFoot(playerName, String.valueOf(apiFootballPlayerId));
        long ttl = (foot != null) ? FOOT_TTL_MIN : FOOT_EMPTY_TTL_MIN;
        footCache.put(apiFootballPlayerId,
                new FootEntry(foot, Instant.now().plus(Duration.ofMinutes(ttl))));
        return foot;
    }

    private String resolveFoot(String name, String apiFootballId) {
        List<TsdbPlayerDto> candidates = searchPlayers(name);
        int checked = 0;
        for (TsdbPlayerDto c : candidates) {
            if (c.idPlayer() == null || c.idPlayer().isBlank()) continue;
            if (c.strSport() != null && !"Soccer".equalsIgnoreCase(c.strSport())) continue;
            if (checked++ >= FOOT_MAX_CANDIDATES) break;
            TsdbPlayerDto full = lookupPlayer(c.idPlayer());
            if (full == null) continue;
            if (apiFootballId.equals(full.idAPIfootball())) {
                String side = full.strSide();
                return (side != null && !side.isBlank()) ? side.trim() : null;
            }
        }
        return null;
    }

    /** {@code searchplayers.php?p={name}} — isimle oyuncu arar (lite kayıtlar). */
    private List<TsdbPlayerDto> searchPlayers(String name) {
        try {
            TsdbPlayerSearchResponse resp = http.get()
                    .uri(u -> u.path("/{key}/searchplayers.php")
                            .queryParam("p", name)
                            .build(props.apiKey()))
                    .retrieve()
                    .body(TsdbPlayerSearchResponse.class);
            if (resp == null || resp.player() == null) return List.of();
            return resp.player();
        } catch (Exception ex) {
            log.debug("TheSportsDB searchplayers hata p={}: {}", name, ex.toString());
            return List.of();
        }
    }

    /** {@code lookupplayer.php?id={idPlayer}} — tam oyuncu kaydı (idAPIfootball + strSide). */
    private TsdbPlayerDto lookupPlayer(String idPlayer) {
        try {
            TsdbPlayerLookupResponse resp = http.get()
                    .uri(u -> u.path("/{key}/lookupplayer.php")
                            .queryParam("id", idPlayer)
                            .build(props.apiKey()))
                    .retrieve()
                    .body(TsdbPlayerLookupResponse.class);
            if (resp == null || resp.players() == null || resp.players().isEmpty()) return null;
            return resp.players().get(0);
        } catch (Exception ex) {
            log.debug("TheSportsDB lookupplayer hata id={}: {}", idPlayer, ex.toString());
            return null;
        }
    }

    /** Ayak cache girişi — null foot da geçerli (negatif sonuç). */
    private record FootEntry(String foot, Instant expiresAt) {}
}
