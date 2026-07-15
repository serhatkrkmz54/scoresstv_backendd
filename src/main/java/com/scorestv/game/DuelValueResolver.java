package com.scorestv.game;

import com.scorestv.football.domain.FixturePlayerStat;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Bir düellonun metriğini, oyuncunun dönemdeki OYNANMIŞ maç istatistiklerinden
 * hesaplar. Oyuncu hiç oynamadıysa null döner → düello VOID olur (adalet).
 */
@Component
public class DuelValueResolver {

    /** Oyuncu değeri; oynamadıysa (qualify etmezse) null. */
    public BigDecimal value(DuelMetric metric, List<FixturePlayerStat> stats) {
        final List<FixturePlayerStat> played = stats.stream()
                .filter(s -> s.getMinutes() != null && s.getMinutes() > 0)
                .toList();
        if (played.isEmpty()) return null;

        return switch (metric) {
            case RATING -> avgRating(played);
            case GOALS -> sum(played, FixturePlayerStat::getGoalsTotal);
            case ASSISTS -> sum(played, FixturePlayerStat::getGoalsAssists);
            case KEY_PASSES -> sum(played, FixturePlayerStat::getPassesKey);
            case ASSISTS_KEYPASS -> sum(played,
                    s -> n(s.getGoalsAssists()) + n(s.getPassesKey()));
            case SHOTS_ON -> sum(played, FixturePlayerStat::getShotsOn);
            case SAVES -> sum(played, FixturePlayerStat::getGoalsSaves);
            case CLEAN_SHEET -> BigDecimal.valueOf(
                    played.stream().filter(s -> n(s.getGoalsConceded()) == 0).count());
            case DUELS_WON -> sum(played, FixturePlayerStat::getDuelsWon);
            case TACKLES_INT -> sum(played,
                    s -> n(s.getTacklesTotal()) + n(s.getTacklesInterceptions()));
            case DRIBBLES -> sum(played, FixturePlayerStat::getDribblesSuccess);
            case CARDS -> sum(played,
                    s -> n(s.getCardsYellow()) + n(s.getCardsRed()));
            case FOULS -> sum(played, FixturePlayerStat::getFoulsCommitted);
        };
    }

    private interface IntGetter {
        Integer get(FixturePlayerStat s);
    }

    private static BigDecimal sum(List<FixturePlayerStat> list, IntGetter g) {
        int total = 0;
        for (FixturePlayerStat s : list) total += n(g.get(s));
        return BigDecimal.valueOf(total);
    }

    /** Oynanmış maçlarda ortalama rating (rating string; parse edilebilenler). */
    private static BigDecimal avgRating(List<FixturePlayerStat> played) {
        double total = 0;
        int cnt = 0;
        for (FixturePlayerStat s : played) {
            final String r = s.getRating();
            if (r == null || r.isBlank()) continue;
            try {
                total += Double.parseDouble(r.trim());
                cnt++;
            } catch (NumberFormatException ignored) {
                // rating "-" vb. → atla
            }
        }
        if (cnt == 0) return null;
        return BigDecimal.valueOf(total / cnt).setScale(2, RoundingMode.HALF_UP);
    }

    private static int n(Integer v) {
        return v == null ? 0 : v;
    }
}
