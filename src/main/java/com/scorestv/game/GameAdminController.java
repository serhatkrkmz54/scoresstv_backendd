package com.scorestv.game;

import com.scorestv.game.GameDtos.CompetitionView;
import com.scorestv.game.GameDtos.CreateCompetitionRequest;
import com.scorestv.game.GameDtos.CreateDuelRequest;
import com.scorestv.game.GameDtos.AdminGrantResult;
import com.scorestv.game.GameDtos.AdminUserCoinView;
import com.scorestv.game.GameDtos.GrantCoinsRequest;
import com.scorestv.game.GameDtos.UpdateStatusRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Oyun — ADMIN: yarışma + düello oluşturma/yönetme. Yalnız ADMIN. */
@RestController
@RequestMapping("/api/v1/admin/game")
@PreAuthorize("hasRole('ADMIN')")
public class GameAdminController {

    private final GameAdminService adminService;
    private final GameService gameService;

    public GameAdminController(GameAdminService adminService, GameService gameService) {
        this.adminService = adminService;
        this.gameService = gameService;
    }

    @GetMapping("/competitions")
    public List<GameCompetition> list() {
        return adminService.listCompetitions();
    }

    @PostMapping("/competitions")
    @ResponseStatus(HttpStatus.CREATED)
    public GameCompetition create(@Valid @RequestBody CreateCompetitionRequest req) {
        return adminService.createCompetition(req);
    }

    @GetMapping("/competitions/{id}")
    public CompetitionView detail(@PathVariable Long id) {
        return gameService.getCompetition(id, null);
    }

    @PostMapping("/competitions/{id}/duels")
    @ResponseStatus(HttpStatus.CREATED)
    public GameDuel addDuel(@PathVariable Long id, @Valid @RequestBody CreateDuelRequest req) {
        return adminService.addDuel(id, req);
    }

    @PatchMapping("/competitions/{id}/status")
    public void updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest req) {
        adminService.updateStatus(id, req.status());
    }

    @DeleteMapping("/duels/{duelId}")
    public void deleteDuel(@PathVariable Long duelId) {
        adminService.deleteDuel(duelId);
    }

    @DeleteMapping("/competitions/{id}")
    public void deleteCompetition(@PathVariable Long id) {
        adminService.deleteCompetition(id);
    }

    // ---- Scores Coin yönetimi ----

    @GetMapping("/users")
    public List<AdminUserCoinView> searchUsers(@RequestParam("q") String q) {
        return adminService.searchUsers(q);
    }

    @PostMapping("/users/{userId}/coins")
    public AdminGrantResult grantCoins(@PathVariable Long userId,
                                       @Valid @RequestBody GrantCoinsRequest req) {
        return adminService.grantCoins(userId, req.delta(), req.reason());
    }
}
