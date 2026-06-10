package com.scorestv.football.player;

import com.scorestv.common.ApiException;
import com.scorestv.common.SlugUtil;
import com.scorestv.football.web.dto.PlayerDetailResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Oyuncu detay sayfasi public endpoint'i.
 *
 * <p>URL semasi: {@code /api/v1/players/{slug}} — slug formati
 * {@code {ad-soyad}-{playerId}}. Frontend rotalari:
 * <ul>
 *   <li>TR: {@code /oyuncu/{slug}}</li>
 *   <li>EN: {@code /player/{slug}}</li>
 * </ul>
 * Iki yol da bu tek backend endpoint'ini cagirir; {@code ?lang=} dile karar verir.
 *
 * <p>{@code ?season=} param secili sezonu belirler — verilmezse oyuncunun
 * DB'deki en son sezonu (en yeni stat satiri).
 */
@RestController
@RequestMapping("/api/v1/players")
public class PublicPlayerController {

    private final PlayerDetailService service;

    public PublicPlayerController(PlayerDetailService service) {
        this.service = service;
    }

    @GetMapping("/{slug:[a-z0-9-]+}")
    public PlayerDetailResponse bySlug(
            @PathVariable String slug,
            @RequestParam(required = false) Integer season,
            @RequestParam(required = false, defaultValue = "en") String lang) {
        Long id = SlugUtil.extractPlayerId(slug);
        if (id == null) {
            throw ApiException.notFound("Oyuncu bulunamadi: gecersiz slug.");
        }
        return service.getById(id, season, "tr".equalsIgnoreCase(lang));
    }
}
