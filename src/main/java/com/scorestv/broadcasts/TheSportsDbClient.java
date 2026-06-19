package com.scorestv.broadcasts;

import com.scorestv.broadcasts.dto.TsdbEventDto;
import com.scorestv.broadcasts.dto.TsdbEventsResponse;
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
}
