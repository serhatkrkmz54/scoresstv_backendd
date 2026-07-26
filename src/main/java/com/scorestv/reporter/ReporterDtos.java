package com.scorestv.reporter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Saha muhabiri DTO'ları — tek dosyada (GameDtos deseni). */
public final class ReporterDtos {

    private ReporterDtos() {}

    // ================= Başvuru =================

    public record ApplyRequest(
            @NotBlank(message = "Lig adı zorunlu") @Size(max = 150) String leagueName,
            @Size(max = 150) String region,
            @NotBlank(message = "Açıklama zorunlu")
            @Size(min = 20, max = 1000, message = "Açıklama 20-1000 karakter olmalı")
            String message
    ) {}

    public record ApplicationView(
            Long id, String leagueName, String region, String message,
            String status, String reviewNote, Long leagueId, Instant createdAt) {

        public static ApplicationView from(ReporterApplication a) {
            return new ApplicationView(a.getId(), a.getLeagueName(), a.getRegion(),
                    a.getMessage(), a.getStatus(), a.getReviewNote(),
                    a.getLeagueId(), a.getCreatedAt());
        }
    }

    /** Panel başvuru satırı — kullanıcı bilgisiyle. */
    public record AdminApplicationView(
            Long id, String leagueName, String region, String message,
            String status, String reviewNote, Long leagueId,
            Instant createdAt, Instant reviewedAt,
            Long userId, String userEmail, String userDisplayName) {}

    public record ReviewRequest(@Size(max = 500) String note) {}

    // ================= Muhabir konsolu =================

    public record AssignedLeagueView(Long leagueId, String leagueName,
                                     int teamCount, int fixtureCount) {}

    /** Muhabirin genel görünümü: atamalar + başvurular + kazanılan puan. */
    public record MeResponse(
            List<AssignedLeagueView> leagues,
            List<ApplicationView> applications) {}

    public record CreateTeamRequest(
            @NotBlank(message = "Takım adı zorunlu") @Size(max = 150) String name
    ) {}

    public record TeamView(Long id, String name) {}

    public record CreateFixtureRequest(
            @NotNull(message = "Ev sahibi zorunlu") Long homeTeamId,
            @NotNull(message = "Deplasman zorunlu") Long awayTeamId,
            @NotNull(message = "Başlama zamanı zorunlu") Instant kickoffAt,
            @Size(max = 100) String round
    ) {}

    public record FixtureView(
            Long id, String slug, Instant kickoffAt, String statusShort,
            Integer elapsed, Integer homeGoals, Integer awayGoals,
            Long homeTeamId, String homeTeamName,
            Long awayTeamId, String awayTeamName, String round) {}

    /**
     * Canlı konsol aksiyonu.
     * action: START | GOAL_HOME | GOAL_AWAY | SET_SCORE | HT | SECOND_HALF |
     *         SET_ELAPSED | FINISH | POSTPONE | CANCEL
     */
    public record ActionRequest(
            @NotBlank(message = "Aksiyon zorunlu") String action,
            Integer minute,
            Integer homeGoals,
            Integer awayGoals
    ) {}
}
