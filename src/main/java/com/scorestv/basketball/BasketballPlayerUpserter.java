package com.scorestv.basketball;

import com.scorestv.basketball.domain.BasketballPlayer;
import com.scorestv.basketball.domain.BasketballPlayerRepository;
import com.scorestv.basketball.domain.BasketballTeam;
import org.springframework.stereotype.Component;

/**
 * Oyuncu master tablo (basketball_players) upsert helper'i.
 *
 * <p>Game player stats sync sirasinda her oyuncu FK gerektirir; bu helper
 * idempotent sekilde oyuncuyu garantiler. {@code team} guncel iletilir
 * (mac sirasindaki kadrosu) — eski takim referansi overwrite olur, bu
 * basketbolda kabul edilebilir (oyuncular sezonlar arasi takim degistirir).
 */
@Component
public class BasketballPlayerUpserter {

    private final BasketballPlayerRepository playerRepo;

    public BasketballPlayerUpserter(BasketballPlayerRepository playerRepo) {
        this.playerRepo = playerRepo;
    }

    /**
     * Oyuncuyu ensure et — yoksa olustur, varsa ad/takim guncelle.
     * {@code name} bos ise iskelet oyuncu yazilir ("Player #id").
     */
    public BasketballPlayer ensure(Long id, String name, BasketballTeam team) {
        if (id == null) return null;
        BasketballPlayer p = playerRepo.findById(id).orElseGet(BasketballPlayer::new);
        p.setId(id);
        if (name != null && !name.isBlank()) {
            p.setName(name);
        } else if (p.getName() == null || p.getName().isBlank()) {
            p.setName("Player #" + id);
        }
        if (team != null) {
            p.setTeam(team);
        }
        return playerRepo.save(p);
    }
}
