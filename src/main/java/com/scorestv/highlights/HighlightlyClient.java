package com.scorestv.highlights;

import com.scorestv.highlights.dto.HighlightlyGeoRestrictionDto;
import com.scorestv.highlights.dto.HighlightlyHighlightDto;
import com.scorestv.highlights.dto.HighlightlyResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Highlightly (soccer.highlightly.net) HTTP istemcisi. {@code x-rapidapi-key}
 * header'ı ile kimlik doğrular. Hata/ağ sorununda boş liste döner — çağıran
 * (servis) maç detayını yine de gösterebilsin.
 */
@Component
public class HighlightlyClient {

    private static final Logger log = LoggerFactory.getLogger(HighlightlyClient.class);
    private static final String KEY_HEADER = "x-rapidapi-key";

    private final RestClient http;
    private final boolean keyConfigured;
    private final int limit;
    private final String timezone;

    /** 429 (özellikle GÜNLÜK limit aşımı) sonrası çağrıları durduracak an (epoch
     * ms). Aşımda ağa hiç dokunmadan hızlı boş dön → hammer'lama + log spam biter,
     * boşuna kota (rejected istek) yakılmaz. Günlük aşımda UTC gün sonuna kadar. */
    private volatile long breachedUntilMs = 0L;

    public HighlightlyClient(HighlightlyProperties props) {
        this.keyConfigured = props.apiKey() != null && !props.apiKey().isBlank();
        this.limit = Math.max(1, Math.min(props.limit(), 40));
        this.timezone = props.timezone();

        var rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(8));
        rf.setReadTimeout(Duration.ofSeconds(12));

        var builder = RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(rf);
        if (keyConfigured) {
            builder.defaultHeader(KEY_HEADER, props.apiKey());
        } else {
            log.warn("Highlightly apiKey tanımlı değil — highlights çağrıları boş döner.");
        }
        this.http = builder.build();
    }

    /**
     * {@code GET /highlights?date&homeTeamName&awayTeamName&timezone&limit}.
     * Highlightly maç id'leri bizimkinden farklı olduğu için takım adları +
     * tarih ile sorgularız.
     */
    public List<HighlightlyHighlightDto> fetchHighlights(
            String date, String homeTeamName, String awayTeamName) {
        if (!keyConfigured || inBreachCooldown()) return List.of();
        try {
            HighlightlyResponse resp = http.get()
                    .uri(u -> u.path("/highlights")
                            .queryParam("date", date)
                            .queryParam("homeTeamName", homeTeamName)
                            .queryParam("awayTeamName", awayTeamName)
                            .queryParam("timezone", timezone)
                            .queryParam("limit", limit)
                            .build())
                    .retrieve()
                    .body(HighlightlyResponse.class);
            if (resp == null || resp.data() == null) return List.of();
            return resp.data();
        } catch (Exception e) {
            handleRateLimit(e);
            log.warn("Highlightly fetch hata date={} {} vs {}: {}",
                    date, homeTeamName, awayTeamName, e.toString());
            return List.of();
        }
    }

    /**
     * {@code GET /highlights/geo-restrictions/{id}} (ücretli plan). Highlight'ın
     * gömülebilirliğini ve engelli ülkelerini döner. Çağrı yapılamazsa (free
     * plan / 404 / ağ) {@code null} döner — çağıran iyimser davranabilir.
     */
    public HighlightlyGeoRestrictionDto fetchGeoRestriction(long highlightId) {
        if (!keyConfigured || inBreachCooldown()) return null;
        try {
            return http.get()
                    .uri(u -> u.path("/highlights/geo-restrictions/{id}")
                            .build(highlightId))
                    .retrieve()
                    .body(HighlightlyGeoRestrictionDto.class);
        } catch (Exception e) {
            handleRateLimit(e);
            log.debug("Highlightly geo-restriction hata id={}: {}",
                    highlightId, e.toString());
            return null;
        }
    }

    /** 429/limit aşımı cooldown'u aktif mi? */
    private boolean inBreachCooldown() {
        return System.currentTimeMillis() < breachedUntilMs;
    }

    /**
     * 429 yakalarsa çağrıları geçici durdurur. GÜNLÜK limit aşımı ("daily"/
     * "breached") → kota UTC gece yarısı sıfırlanana kadar; geçici 429 → 60 sn.
     * Böylece aşım sonrası bir daha hammer'lanmaz (log spam + boşa kota biter).
     */
    private void handleRateLimit(Exception e) {
        if (!(e instanceof HttpClientErrorException hce)
                || hce.getStatusCode().value() != 429) {
            return;
        }
        final String body = hce.getResponseBodyAsString().toLowerCase();
        if (body.contains("daily") || body.contains("breached")) {
            breachedUntilMs = LocalDate.now(ZoneOffset.UTC).plusDays(1)
                    .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            log.warn("Highlightly GÜNLÜK limit aşıldı — UTC gün sonuna kadar çağrılar durduruldu.");
        } else {
            breachedUntilMs = System.currentTimeMillis() + 60_000L;
            log.warn("Highlightly 429 (geçici) — 60 sn çağrı durduruldu.");
        }
    }
}
