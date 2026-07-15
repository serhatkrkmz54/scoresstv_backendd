package com.scorestv.game;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Oyun (Scores Coin) API DTO'ları — istek + yanıt kayıtları. */
public final class GameDtos {

    private GameDtos() {}

    // ---- Yanıt ----

    public record PlayerView(
            Long id, String name, String photo, String team, String teamLogo) {}

    public record DuelView(
            Long id,
            DuelPosition position,
            DuelMetric metric,
            DuelDirection direction,
            DuelStatus status,
            String winner,          // A | B | DRAW | VOID | null
            BigDecimal valueA,
            BigDecimal valueB,
            PlayerView playerA,
            PlayerView playerB,
            String myPick,          // giriş yapmış kullanıcının tahmini (A/B) veya null
            long pickCountA,
            long pickCountB) {}

    public record CompetitionView(
            Long id,
            GameScope scope,
            String title,
            GameStatus status,
            Instant startAt,
            Instant endAt,
            Instant lockAt,
            boolean locked,         // artık tahmin verilemez mi
            List<DuelView> duels) {}

    public record LeaderboardEntry(
            int rank, Long userId, String displayName,
            long coins, long correct, long total) {}

    public record LedgerEntry(
            int delta, long balanceAfter, String reason,
            String refType, Long refId, Instant createdAt) {}

    public record WalletView(
            long balance, long lifetimeCoins,
            int totalPicks, int correctPicks,
            int currentStreak, int bestStreak,
            List<LedgerEntry> history) {}

    // ---- İstek ----

    public record PickRequest(@NotNull String pick) {} // "A" | "B"

    public record CreateCompetitionRequest(
            @NotNull GameScope scope,
            @NotNull String title,
            Integer season,
            Long leagueId,
            @NotNull Instant startAt,
            @NotNull Instant endAt,
            @NotNull Instant lockAt) {}

    public record PlayerRef(
            @NotNull Long id, String name, String photo, String team, String teamLogo) {}

    public record CreateDuelRequest(
            @NotNull DuelPosition position,
            @NotNull DuelMetric metric,
            DuelDirection direction,
            Integer sortOrder,
            @NotNull PlayerRef playerA,
            @NotNull PlayerRef playerB) {}

    public record UpdateStatusRequest(@NotNull GameStatus status) {}
}
