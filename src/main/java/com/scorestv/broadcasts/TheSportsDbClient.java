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
import java.util.List;

/**
 * TheSportsDB v1 HTTP istemcisi. Anahtar URL'de gider ({@code /{key}/...}).
 * Hata/ağ sorununda boş liste döner — çağıran maç detayını yine gösterebilsin.
 */
@Component
public class TheSportsDbClient {

    private static final Logger log = LoggerFactory.getLogger(TheSportsDbClient.class);

    private final RestClient http;
    private final TheSportsDbProperties props;

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
}
