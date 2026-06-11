package com.scorestv.bilyoner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Bilyoner gamelist API'sinden ham oranları çeker ve parse eder.
 *
 * <p>Yanıt {@code {"events": { "<id>": {...} }}} yapısında; her event'ten
 * takım adları ({@code htn}/{@code atn}), kickoff ({@code esdl} epoch ms) ve
 * tüm market oranları ({@code marketGroups[].odds[]} → {@code n}:{@code val})
 * toplanır. Hata/timeout durumunda boş liste döner (akış asla patlamaz).
 */
@Component
public class BilyonerOddsClient {

    private static final Logger log = LoggerFactory.getLogger(BilyonerOddsClient.class);

    private final RestClient http;
    private final ObjectMapper mapper = new ObjectMapper();
    private final BilyonerProperties props;

    public BilyonerOddsClient(BilyonerProperties props) {
        this.props = props;
        var rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(8));
        rf.setReadTimeout(Duration.ofSeconds(12));
        this.http = RestClient.builder()
                .requestFactory(rf)
                // Bilyoner bot korumasına takılmamak için tarayıcı benzeri header.
                .defaultHeader("User-Agent",
                        "Mozilla/5.0 (compatible; ScoresTV/1.0; +https://scorestv.com)")
                .defaultHeader("Accept", "application/json")
                .build();
    }

    /** Parse edilmiş event listesi. Hata olursa boş liste. */
    public List<RawEvent> fetchEvents() {
        final String body;
        try {
            body = http.get().uri(props.apiUrl()).retrieve().body(String.class);
        } catch (Exception e) {
            log.warn("Bilyoner fetch başarısız: {}", e.toString());
            return List.of();
        }
        if (body == null || body.isBlank()) return List.of();
        try {
            JsonNode root = mapper.readTree(body);
            List<RawEvent> out = new ArrayList<>();
            // Bilyoner bültene göre maçları "events" VEYA "onComingEvents"
            // altında döndürüyor — ikisini de tara.
            for (String key : new String[]{"events", "onComingEvents"}) {
                JsonNode node = root.path(key);
                for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
                    RawEvent ev = parse(it.next().getValue());
                    if (ev != null) out.add(ev);
                }
            }
            return out;
        } catch (Exception e) {
            log.warn("Bilyoner parse başarısız: {}", e.toString());
            return List.of();
        }
    }

    private RawEvent parse(JsonNode e) {
        String htn = text(e, "htn");
        String atn = text(e, "atn");
        if (htn == null || atn == null) return null;

        Instant kickoff = null;
        JsonNode esdl = e.get("esdl");
        if (esdl != null && esdl.isNumber()) {
            kickoff = Instant.ofEpochMilli(esdl.asLong());
        } else if (e.hasNonNull("esd")) {
            try {
                kickoff = LocalDateTime.parse(e.get("esd").asText())
                        .atZone(ZoneId.of("Europe/Istanbul")).toInstant();
            } catch (Exception ignore) {
                // tarih parse edilemezse kickoff null; isim eşleşmesi yeterli.
            }
        }

        Map<String, String> odds = new HashMap<>();
        for (JsonNode group : e.path("marketGroups")) {
            for (JsonNode od : group.path("odds")) {
                String n = text(od, "n");
                String val = text(od, "val");
                if (n != null && val != null) odds.putIfAbsent(n, val);
            }
        }
        return new RawEvent(e.path("id").asLong(0), htn, atn, kickoff, odds);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }

    /** Tek Bilyoner maçı: takım adları, kickoff ve oran adı→değer haritası. */
    public record RawEvent(long eventId, String home, String away, Instant kickoff,
                           Map<String, String> odds) {}
}
