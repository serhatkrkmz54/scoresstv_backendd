package com.scorestv.mobile.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Mobile favori mac listesini backend'e batch sync etme istegi.
 *
 * <p>Cihaz lokal favori listesini her degistirdiginde tum guncel listeyi
 * gonderir. Backend bu cihaz icin eski tum abonelikleri silip yenilerini
 * yazar — basit, idempotent, race-free.
 *
 * <p><b>Maks 200 favori</b> — kullanici cumlesini sinirsiz buyutmesin diye.
 * 200'den fazla istek gelirse ilk 200 alinir.
 */
public record SyncFavoriteMatchesRequest(
        @NotBlank
        String fcmToken,
        /**
         * Favori fixture id listesi. Bos olabilir (kullanici tumunu sildi).
         * Backend bu durumda cihaz icin tum subscription'lari siler.
         */
        @NotNull
        @Size(max = 200, message = "En fazla 200 favori mac kaydedilebilir")
        List<Long> fixtureIds
) {}
