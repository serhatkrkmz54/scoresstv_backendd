package com.scorestv.football.sync;

import com.scorestv.football.domain.Player;
import com.scorestv.football.domain.PlayerRepository;
import com.scorestv.football.queue.SyncJobType;
import com.scorestv.football.queue.SyncQueueService;
import com.scorestv.search.events.EntityIndexedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Oyuncu master tablosuna upsert eden bilesen. Diger upserter'lar (injury,
 * top_players, lineup_players, player_stats, events) bir oyuncu satirini
 * isledikleri her seferinde bunu cagirir; sayede her oyuncunun foto URL'si
 * master tabloya da yazilir ve {@link
 * com.scorestv.football.image.ImageMirrorService} arka planda MinIO'ya
 * aynalar.
 *
 * <p>Foto URL degisirse ({@code photoUrl} farkli geldiyse) {@code photo_key}
 * sifirlanir — mirror servisi yeni URL'yi yeniden indirir.
 *
 * <p><b>Otomatik isim hidratasyonu:</b> API-Football lineup/squad/playerStat
 * endpoint'leri sadece kisa form ("A. Guler") doner; tam isim
 * ({@code firstname/lastname}) sadece {@code /players/profiles} ile gelir.
 * Bu upserter yeni bir Player olusturdugunda VEYA mevcut Player'in firstname
 * field'i hala bos ise anlik olarak PLAYER_PROFILE_SYNC queue'sune ekler —
 * worker birkac saniye/dakika icinde isler ve "Arda Guler" formatina ulasilir.
 * Idempotent ({@code enqueueIfAbsent}) ile ayni player icin dup PENDING
 * eklenmez. Saatlik {@code AutoEnqueueScheduler.hourlyHydrateMissingPlayerNames}
 * cron'u bu trigger'i kacirilan playerlar icin safety net olarak calisir.
 */
@Service
public class PlayerUpserter {

    private final PlayerRepository playerRepository;
    private final ApplicationEventPublisher events;
    private final SyncQueueService queueService;

    public PlayerUpserter(PlayerRepository playerRepository,
                          ApplicationEventPublisher events,
                          @Lazy SyncQueueService queueService) {
        this.playerRepository = playerRepository;
        this.events = events;
        this.queueService = queueService;
    }

    /**
     * Bir oyuncuyu upsert eder. Sessizce hata yutmaz — caller transaction'ina
     * dahil olur. ID null veya isim null ise atlanir.
     */
    @Transactional
    public void upsert(Long playerId, String playerName, String photoUrl) {
        if (playerId == null || playerName == null || playerName.isBlank()) {
            return;
        }
        Player player = playerRepository.findById(playerId).orElseGet(Player::new);
        boolean isNew = player.getId() == null;
        player.setId(playerId);
        player.setName(playerName);
        // Foto URL degisirse mirror key'i sifirla → ImageMirrorService bir
        // sonraki tickte yeniden indirsin.
        if (photoUrl != null && !photoUrl.equals(player.getPhotoUrl())) {
            player.setPhotoUrl(photoUrl);
            player.setPhotoKey(null);
        } else if (isNew && photoUrl != null) {
            player.setPhotoUrl(photoUrl);
        }
        playerRepository.save(player);

        // Anlik isim hidratasyon trigger'i — yeni player veya firstname hala
        // bos ise PLAYER_PROFILE_SYNC queue'sune ekle. Idempotent: ayni
        // payload PENDING'deyse atlanir. Worker birkac dakika icinde isler.
        boolean nameMissing = player.getFirstname() == null
                || player.getFirstname().isBlank();
        if (nameMissing) {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("playerId", playerId);
                payload.put("season", LocalDate.now().getYear());
                queueService.enqueueIfAbsent(SyncJobType.PLAYER_PROFILE_SYNC,
                        payload, SyncQueueService.PRIORITY_BULK);
            } catch (RuntimeException ignored) {
                // Queue write hatasi upsert'i bloklamasin — saatlik cron
                // safety net yakalar.
            }
        }

        // ES indeksini guncelle — AFTER_COMMIT + @Async ile yazilir.
        events.publishEvent(new EntityIndexedEvent.PlayerIndexed(player));
    }
}
