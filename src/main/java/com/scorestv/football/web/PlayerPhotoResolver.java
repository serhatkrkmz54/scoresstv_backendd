package com.scorestv.football.web;

import com.scorestv.football.domain.Player;
import com.scorestv.football.domain.PlayerRepository;
import com.scorestv.storage.MinioStorageService;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Oyuncu fotograflarini CDN-once mantigi ile cozer:
 * <ul>
 *   <li>Player master tablosunda {@code photo_key} doluysa → MinIO CDN URL</li>
 *   <li>Yoksa fallback olarak orijinal {@code photo_url} (yayinlandigi kaynak)</li>
 *   <li>O da yoksa null</li>
 * </ul>
 *
 * <p>Bir maç detayinda ortalama ~30 oyuncu (2 takim x 14 lineup); top scorers
 * sayfasinda 80 oyuncu (4 kategori x 20). Her birinin photo'sunu tek tek
 * sorgulamak N+1 olur — bu nedenle helper {@link #loadMap(Collection)} ile
 * tum player_id'leri tek sorguda cekip yerel map'te tutar.
 */
@Component
public class PlayerPhotoResolver {

    private final PlayerRepository playerRepository;
    private final MinioStorageService storage;

    public PlayerPhotoResolver(PlayerRepository playerRepository,
                               MinioStorageService storage) {
        this.playerRepository = playerRepository;
        this.storage = storage;
    }

    /**
     * Verilen player_id seti icin {@link Player} entity'lerini tek sorguda
     * cekip Map olarak doner. {@link #photoUrl(Player, String)} ile birlikte
     * kullanilir.
     */
    public Map<Long, Player> loadMap(Collection<Long> playerIds) {
        if (playerIds == null || playerIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Player> out = new HashMap<>();
        for (Player p : playerRepository.findAllById(playerIds)) {
            out.put(p.getId(), p);
        }
        return out;
    }

    /**
     * Tek oyuncu icin CDN URL'i cozer. Map'ten gelen entity null olabilir
     * (henuz master tabloda yok); o durumda {@code fallbackPhotoUrl} (genelde
     * orijinal API URL'i, snapshot olarak satirda saklanan) doner.
     */
    public String photoUrl(Player player, String fallbackPhotoUrl) {
        if (player != null && player.getPhotoKey() != null) {
            return storage.publicUrl(player.getPhotoKey());
        }
        if (player != null && player.getPhotoUrl() != null) {
            return player.getPhotoUrl();  // master entity var ama henuz mirrorlanmamis
        }
        return fallbackPhotoUrl;
    }

    /** Map + fallback'i tek cagrida birlestiren convenience. */
    public String photoUrl(Map<Long, Player> map, Long playerId, String fallbackPhotoUrl) {
        if (playerId == null) {
            return fallbackPhotoUrl;
        }
        return photoUrl(map.get(playerId), fallbackPhotoUrl);
    }
}
