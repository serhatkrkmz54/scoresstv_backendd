package com.scorestv.football.sync;

import com.scorestv.common.ApiException;
import com.scorestv.football.domain.LeagueTopPlayer.Category;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * ADMIN'in bir lig+sezon icin top scorers / assists / yellow cards / red
 * cards listelerini manuel senkronlamasi icin endpoint.
 *
 * <pre>
 *   POST /admin/api-football/leagues/{leagueId}/top-players/{category}/sync?season=2025
 *   category: scorers | assists | yellowcards | redcards
 *   (Eski: "cards" path artik desteklenmiyor — yellow ve red ayri.)
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/admin/api-football/leagues")
@PreAuthorize("hasRole('ADMIN')")
public class TopPlayersSyncController {

    private final TopPlayersSyncService syncService;

    public TopPlayersSyncController(TopPlayersSyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/{leagueId}/top-players/{category}/sync")
    public TopPlayersSyncResult sync(@PathVariable Long leagueId,
                                     @PathVariable String category,
                                     @RequestParam Integer season) {
        Category cat = parseCategory(category);
        return syncService.sync(leagueId, season, cat);
    }

    /**
     * Path parametresini Category enum'a cevirir. URL'de tire/case ozgurlugu
     * tanir: "yellow-cards", "yellowcards", "YELLOW_CARDS" hepsi olur.
     */
    private static Category parseCategory(String raw) {
        if (raw == null) {
            throw ApiException.badRequest("Kategori bos.");
        }
        String normalized = raw.toUpperCase(Locale.ROOT).replace("-", "_");
        return switch (normalized) {
            case "SCORERS" -> Category.SCORERS;
            case "ASSISTS" -> Category.ASSISTS;
            case "YELLOWCARDS", "YELLOW_CARDS" -> Category.YELLOW_CARDS;
            case "REDCARDS", "RED_CARDS" -> Category.RED_CARDS;
            default -> throw ApiException.badRequest(
                    "Gecersiz kategori: " + raw + " (scorers|assists|yellowcards|redcards)");
        };
    }
}
