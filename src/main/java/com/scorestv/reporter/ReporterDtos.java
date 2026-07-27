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

    public record AssignedLeagueView(Long leagueId, String leagueName, String logo,
                                     int teamCount, int fixtureCount) {}

    /** Muhabirin genel görünümü: atamalar + başvurular + kazanılan puan. */
    public record MeResponse(
            List<AssignedLeagueView> leagues,
            List<ApplicationView> applications) {}

    public record CreateTeamRequest(
            @NotBlank(message = "Takım adı zorunlu") @Size(max = 150) String name
    ) {}

    public record TeamView(Long id, String name, String logo) {}

    public record CreateFixtureRequest(
            @NotNull(message = "Ev sahibi zorunlu") Long homeTeamId,
            @NotNull(message = "Deplasman zorunlu") Long awayTeamId,
            @NotNull(message = "Başlama zamanı zorunlu") Instant kickoffAt,
            @Size(max = 100) String round
    ) {}

    public record FixtureView(
            Long id, String slug, Instant kickoffAt, String statusShort,
            Integer elapsed, Integer statusExtra,
            Integer homeGoals, Integer awayGoals,
            Integer penHome, Integer penAway,
            Long homeTeamId, String homeTeamName,
            Long awayTeamId, String awayTeamName, String round) {}

    /**
     * Canlı konsol aksiyonu — faz makinesi:
     * NS/PST → START → 1H → HT → SECOND_HALF → 2H → FINISH(FT)
     * Uzatma: 2H → BREAK(BT) → EXTRA_TIME(ET, 91') → BREAK(BT, 105')
     *         → EXTRA_TIME(ET, 106') → FINISH(AET)
     * Penaltılar: 2H/BT/ET → PENALTIES(P) → SET_PEN_SCORE → FINISH(PEN)
     * Duraklatma: 1H/2H/ET → PAUSE(INT) → RESUME (kaldığı dakikadan)
     * Diğer: SET_SCORE, SET_ELAPSED(minute), SET_STOPPAGE(minute = hakem ilanı),
     *        ABANDON(ABD), POSTPONE, CANCEL.
     * SET_PEN_SCORE homeGoals/awayGoals alanlarını penaltı skoru olarak kullanır.
     */
    public record ActionRequest(
            @NotBlank(message = "Aksiyon zorunlu") String action,
            Integer minute,
            Integer homeGoals,
            Integer awayGoals
    ) {}

    // ================= Maç olayları =================

    /**
     * Olay girişi. type: GOAL | PEN_GOAL | OWN_GOAL | PEN_MISS | YELLOW |
     * RED | SUB | VAR_GOAL_CANCELLED | VAR_PEN_CONFIRMED.
     * team: HOME | AWAY (olayı yapan oyuncunun takımı).
     * SUB'da playerName = çıkan, assistName = giren.
     */
    public record EventRequest(
            @NotBlank(message = "Olay türü zorunlu") String type,
            @NotBlank(message = "Takım zorunlu") String team,
            Integer minute,
            Integer extra,
            @Size(max = 120) String playerName,
            @Size(max = 120) String assistName
    ) {}

    public record EventView(
            Long id, Integer minute, Integer extra, String type, String detail,
            Long teamId, String teamName, String playerName, String assistName) {}

    /** Olay sonrası güncel durum — skor da değişmiş olabilir. */
    public record EventResult(EventView event, FixtureView fixture) {}

    // ================= Kadro =================

    public record LineupPlayerInput(
            @NotBlank(message = "Oyuncu adı zorunlu") @Size(max = 120) String name,
            Integer number,
            @Size(max = 10) String position,
            boolean substitute
    ) {}

    public record LineupRequest(
            @Size(max = 20) String formation,
            @Size(max = 120) String coachName,
            @NotNull(message = "Oyuncu listesi zorunlu")
            @Size(min = 1, max = 30, message = "1-30 oyuncu girilebilir")
            List<LineupPlayerInput> players
    ) {}

    public record LineupView(
            String formation, String coachName, List<LineupPlayerInput> players) {}

    // ================= Yayın =================

    /** Maçın yayınlandığı kanal adları (tam liste — mevcutların yerine geçer). */
    public record BroadcastsRequest(
            @NotNull @Size(max = 5, message = "En fazla 5 kanal")
            List<@Size(max = 100) String> channels
    ) {}

    public record BroadcastsView(List<String> channels) {}
}
