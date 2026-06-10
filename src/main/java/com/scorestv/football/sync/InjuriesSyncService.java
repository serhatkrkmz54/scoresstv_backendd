package com.scorestv.football.sync;

import com.scorestv.football.ApiFootballClient;
import com.scorestv.football.ApiFootballResponse;
import com.scorestv.football.sync.dto.InjuryApiDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Bir maçın sakatlık listesini API-Football'dan çekip DB'ye senkronlar.
 * Çağrı: {@code GET /injuries?fixture={id}}.
 *
 * <p>API güncelleme cadence'ı 4 saat; günlük {@link DailyInjuriesJob} bu
 * frekansı yeterince kapsar (yarınki kapsamlı maçlar için).
 */
@Service
public class InjuriesSyncService {

    private static final Logger log = LoggerFactory.getLogger(InjuriesSyncService.class);

    private static final ParameterizedTypeReference<ApiFootballResponse<List<InjuryApiDto>>>
            INJURIES_TYPE = new ParameterizedTypeReference<>() {
            };

    private final ApiFootballClient client;
    private final InjuriesUpserter upserter;

    public InjuriesSyncService(ApiFootballClient client, InjuriesUpserter upserter) {
        this.client = client;
        this.upserter = upserter;
    }

    public InjuriesSyncResult sync(Long fixtureId) {
        ApiFootballResponse<List<InjuryApiDto>> response = client.get(
                "/injuries", Map.of("fixture", fixtureId), INJURIES_TYPE);
        List<InjuryApiDto> items = response.response();
        int written = upserter.replace(fixtureId, items == null ? List.of() : items);
        if (written > 0) {
            log.info("Sakatlık senkronu: fixtureId={} — {} kayıt yazıldı",
                    fixtureId, written);
        }
        return new InjuriesSyncResult(fixtureId, written);
    }
}
