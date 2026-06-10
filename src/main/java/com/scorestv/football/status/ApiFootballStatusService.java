package com.scorestv.football.status;

import com.scorestv.football.ApiFootballClient;
import com.scorestv.football.ApiFootballException;
import com.scorestv.football.ApiFootballResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * API-Football hesap/abonelik/kota durumunu getiren servis.
 *
 * <p>{@code /status} cagrisi <b>gunluk kotadan dusmez</b>; bu yuzden hem entegrasyon
 * iskeletini dogrulamak hem de kalan kotayi izlemek icin guvenle kullanilabilir.
 * Bu nedenle yanit ayrica cache'lenmez - her zaman guncel bilgi doner.
 */
@Service
public class ApiFootballStatusService {

    private final ApiFootballClient client;

    public ApiFootballStatusService(ApiFootballClient client) {
        this.client = client;
    }

    /** Hesap, abonelik ve gunluk kota bilgisini doner. */
    public ApiFootballStatus getStatus() {
        ApiFootballResponse<ApiFootballStatus> response = client.get(
                "/status",
                Map.of(),
                new ParameterizedTypeReference<ApiFootballResponse<ApiFootballStatus>>() {
                });

        ApiFootballStatus status = response.response();
        if (status == null) {
            throw ApiFootballException.upstream(
                    "Futbol veri sağlayıcısı durum bilgisi döndürmedi.");
        }
        return status;
    }
}
