package com.scorestv.football.web.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * Kupa eleme bracket gosterimi — sadece "knockout" turlarini icerir
 * (Final, Yari Final, Ceyrek Final, 16'lar Turu vb.). Grup asamasi varsa
 * o {@code standings} ile gosterilir, bracket'ta yer almaz.
 *
 * <p>Bir "tie" iki takim arasindaki escapesin tamamidir; tek mac (single leg)
 * veya iki mac (two leg) olabilir. Aggregate skor, kazanan ve penalti
 * bilgisi tie seviyesinde toplanir.
 *
 * <p>Round sirasi: 0 = en erken knockout, artarak Final'e. UI sol→sag
 * boyle render eder.
 */
public record BracketView(
        /** Knockout turlari — sirali (Round of 16 → ... → Final). */
        List<KnockoutRound> rounds,
        /** Final kazanani (varsa). Henuz oynanmadiysa null. */
        Champion champion
) implements Serializable {

    public record KnockoutRound(
            /** Ham tur adi — orn. "Round of 16", "Final", "Semi-finals". */
            String name,
            /** Dile cevrilmis — orn. "16'lar Turu", "Yari Final", "Final". */
            String nameText,
            /** Sira — 0=ilk knockout, artarak Final'e. UI ordering. */
            int order,
            /** Bu turdaki bagimsiz ties sayisi (2-leg ise tek tie sayilir). */
            int tiesCount,
            /** Bu turdaki escapesler. */
            List<KnockoutTie> ties
    ) implements Serializable {}

    public record KnockoutTie(
            /** Sentetik tie id — UI key olarak kullanilabilir (homeId-awayId). */
            String tieId,
            /** Tek mac mi (1) yoksa iki mac mi (2). */
            int legsCount,
            /** "Home" takim — bracket gorsel siralama icin (genelde ilk leg ev sahibi). */
            BracketTeam home,
            BracketTeam away,
            /** Ic-ice maclar — kickoff sirali. */
            List<TieLeg> legs,
            /** Aggregate (2-leg toplami; tek mac icin tek skoru). */
            Integer aggregateHome,
            Integer aggregateAway,
            /** Penalti sonucu (yarisma 2-leg sonunda berabere kalip pen'e gittiyse). */
            Integer penaltyHome,
            Integer penaltyAway,
            /**
             * Kazanan takim id'si. Henuz oynanmadi/berabere ise null.
             * Two-leg: aggregate'a + penalty'ye + away-goals rule'a gore karar
             * verilir. Buradaki implementasyon: aggregate, sonra penalty.
             * (Away-goals UEFA'da 2021'den sonra kalkti.)
             */
            Long winnerTeamId,
            /** Tie hala oynaniyor mu (en az 1 leg LIVE/NS). */
            boolean inProgress
    ) implements Serializable {}

    public record TieLeg(
            Long fixtureId,
            /** SEO slug — frontend /mac/ veya /match/ ile birlestirir. */
            String fixtureSlug,
            Instant kickoff,
            /** "NS", "FT", "1H", "AET", "PEN" vb. — UI ozet. */
            String status,
            String statusText,
            Integer homeScore,
            Integer awayScore,
            /** Bu leg'in ev sahibi takim id'si (1. leg ile 2. leg ters). */
            Long homeTeamId,
            Long awayTeamId,
            /** "1st Leg" / "2nd Leg" gibi — kickoff sirasina gore atanir. */
            String legLabel
    ) implements Serializable {}

    public record BracketTeam(
            Long id,
            String name,
            String logo,
            /** /takim/{slug} icin. */
            String slug
    ) implements Serializable {}

    public record Champion(
            Long teamId,
            String teamName,
            String teamLogo,
            String teamSlug
    ) implements Serializable {}
}
