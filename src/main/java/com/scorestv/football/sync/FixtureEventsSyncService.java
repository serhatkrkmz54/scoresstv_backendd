package com.scorestv.football.sync;

import com.scorestv.football.ApiFootballClient;
import com.scorestv.football.ApiFootballResponse;
import com.scorestv.football.sync.dto.EventApiDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Bir maçın olaylarını API-Football'dan çekip DB'ye senkronlar.
 *
 * <p>HTTP çağrısı transaction dışında, DB yazımı {@link FixtureEventsUpserter}'ın
 * kendi transaction'ında (yavaş ağ DB'yi kilitlemez).
 *
 * <p>Çağrı: {@code GET /fixtures/events?fixture={id}}. Yanıt boş gelse bile
 * upserter "0 yazıldı, eskiler silindi" sonucunu döner — örn. yanlışlıkla
 * eklenmiş bir olayın geri çekildiği durumda DB temizlenmiş olur.
 */
@Service
public class FixtureEventsSyncService {

    private static final Logger log = LoggerFactory.getLogger(FixtureEventsSyncService.class);

    private static final ParameterizedTypeReference<ApiFootballResponse<List<EventApiDto>>>
            EVENTS_TYPE = new ParameterizedTypeReference<>() {
            };

    private final ApiFootballClient client;
    private final FixtureEventsUpserter upserter;

    public FixtureEventsSyncService(ApiFootballClient client,
                                    FixtureEventsUpserter upserter) {
        this.client = client;
        this.upserter = upserter;
    }

    /**
     * Verilen maçın olaylarını çekip DB'ye yazar.
     *
     * @return yazılan olay sayısı + maç id'si
     */
    public FixtureEventsSyncResult sync(Long fixtureId) {
        ApiFootballResponse<List<EventApiDto>> response = client.get(
                "/fixtures/events", Map.of("fixture", fixtureId), EVENTS_TYPE);
        return sync(fixtureId, response.response());
    }

    /**
     * Bundle'dan ({@code /fixtures?ids=}) gelen ÖNCEDEN ÇEKİLMİŞ olay listesiyle
     * upsert — API çağrısı YAPMAZ. Canlı detay batch'i kullanır.
     */
    public FixtureEventsSyncResult sync(Long fixtureId, List<EventApiDto> items) {
        int written = upserter.replace(fixtureId, items == null ? List.of() : items);
        log.info("Olay senkronu: fixtureId={} — {} olay yazıldı", fixtureId, written);
        return new FixtureEventsSyncResult(fixtureId, written);
    }
}
