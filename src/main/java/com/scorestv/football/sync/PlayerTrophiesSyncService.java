package com.scorestv.football.sync;

import com.scorestv.football.ApiFootballClient;
import com.scorestv.football.ApiFootballResponse;
import com.scorestv.football.domain.PlayerTrophy;
import com.scorestv.football.domain.PlayerTrophyRepository;
import com.scorestv.football.sync.dto.TrophyApiDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bir oyuncunun kupalarini API'den ceker + REPLACE pattern ile DB'ye yazar.
 *   {@code GET /trophies?player=X}
 *
 * <p>Not: API'nin coach/player trophy endpoint'leri ayni person id'de AYNI
 * sonucu doner (player+coach kariyeri birlestirilmis). Biz player_trophies
 * + coach_trophies'i ayri tablolarda tutuyoruz.
 */
@Service
public class PlayerTrophiesSyncService {

    private static final Logger log = LoggerFactory.getLogger(PlayerTrophiesSyncService.class);

    private static final ParameterizedTypeReference<ApiFootballResponse<List<TrophyApiDto>>>
            TROPHIES_TYPE = new ParameterizedTypeReference<>() {
            };

    private final ApiFootballClient client;
    private final PlayerTrophyRepository trophyRepository;

    public PlayerTrophiesSyncService(ApiFootballClient client,
                                     PlayerTrophyRepository trophyRepository) {
        this.client = client;
        this.trophyRepository = trophyRepository;
    }

    @Transactional
    public int sync(Long playerId) {
        if (playerId == null) return 0;
        ApiFootballResponse<List<TrophyApiDto>> response = client.get(
                "/trophies", Map.of("player", playerId), TROPHIES_TYPE);
        List<TrophyApiDto> items = response.response();
        trophyRepository.deleteByPlayerId(playerId);
        if (items == null || items.isEmpty()) {
            return 0;
        }
        int written = 0;
        Set<String> seen = new HashSet<>();
        for (TrophyApiDto item : items) {
            if (item == null) continue;
            String league = item.league() != null ? item.league() : "Unknown";
            // uq_player_trophies (player_id, league, season, place): API ayni
            // kupayi (player+coach kariyeri birlesik) tekrar dondurebiliyor.
            // Dup'i parti icinde ele — yoksa ikinci insert 23505 verip tx'i
            // kirletir, kalan tum satirlar 25P02 ile duser.
            if (!seen.add(league + "|" + item.season() + "|" + item.place())) {
                continue;
            }
            PlayerTrophy t = new PlayerTrophy();
            t.setPlayerId(playerId);
            t.setLeague(league);
            t.setCountry(item.country());
            t.setSeason(item.season());
            t.setPlace(item.place());
            trophyRepository.save(t);
            written++;
        }
        log.info("Player trophies sync: playerId={} — {} kupa yazildi", playerId, written);
        return written;
    }
}
