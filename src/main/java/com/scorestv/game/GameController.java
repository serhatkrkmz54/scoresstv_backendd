package com.scorestv.game;

import com.scorestv.common.ApiException;
import com.scorestv.game.GameDtos.CompetitionView;
import com.scorestv.game.GameDtos.LeaderboardEntry;
import com.scorestv.game.GameDtos.PickRequest;
import com.scorestv.game.GameDtos.WalletView;
import com.scorestv.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Oyun (Scores Coin) — kullanıcı uçları. GET'ler herkese açık (misafir düelloları
 * görür, myPick null); tahmin + cüzdan giriş ister.
 */
@RestController
@RequestMapping("/api/v1/game")
public class GameController {

    private final GameService service;

    public GameController(GameService service) {
        this.service = service;
    }

    /** Aktif (OPEN) yarışma + düellolar. Yoksa 204. */
    @GetMapping("/active")
    public ResponseEntity<CompetitionView> active(
            @RequestParam(defaultValue = "WEEKLY") GameScope scope,
            @AuthenticationPrincipal CurrentUser currentUser) {
        final Long uid = currentUser != null ? currentUser.id() : null;
        final CompetitionView v = service.getActive(scope, uid);
        return v == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(v);
    }

    @GetMapping("/competitions/{id}")
    public CompetitionView competition(@PathVariable Long id,
                                       @AuthenticationPrincipal CurrentUser currentUser) {
        return service.getCompetition(id, currentUser != null ? currentUser.id() : null);
    }

    @GetMapping("/competitions/{id}/leaderboard")
    public List<LeaderboardEntry> competitionLeaderboard(
            @PathVariable Long id, @RequestParam(defaultValue = "50") int limit) {
        return service.competitionLeaderboard(id, limit);
    }

    @GetMapping("/leaderboard")
    public List<LeaderboardEntry> leaderboard(@RequestParam(defaultValue = "50") int limit) {
        return service.globalLeaderboard(limit);
    }

    /** Tahmin gönder/güncelle (giriş zorunlu). */
    @PostMapping("/duels/{duelId}/pick")
    public void pick(@PathVariable Long duelId, @Valid @RequestBody PickRequest req,
                     @AuthenticationPrincipal CurrentUser currentUser) {
        if (currentUser == null) throw ApiException.unauthorized("Tahmin için giriş gerekli.");
        service.submitPick(currentUser.id(), duelId, req.pick());
    }

    @GetMapping("/wallet")
    public WalletView wallet(@AuthenticationPrincipal CurrentUser currentUser) {
        if (currentUser == null) throw ApiException.unauthorized("Cüzdan için giriş gerekli.");
        return service.wallet(currentUser.id());
    }
}
