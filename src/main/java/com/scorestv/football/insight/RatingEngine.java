package com.scorestv.football.insight;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maç olasılık motoru (v5) — opponent-adjusted "gol-Elo" reytingi + Poisson.
 *
 * <p>Her takıma online hücum/savunma reytingi tutulur; bir maçtan sonra reyting,
 * <b>rakibin gücüne göre</b> güncellenir. Öğrenme sinyali olarak xG varsa xG
 * (daha az gürültülü), yoksa gerçek gol kullanılır. Reytinglerden beklenen gol
 * (λ) → Poisson dağılımı → 1X2 / Alt-Üst 2.5 / KG olasılıkları (Dixon-Coles
 * düşük-skor düzeltmesiyle).
 *
 * <p>Bu sınıf tamamen saf/duraksız (Spring'siz) — backtest'te doğrulanan Python
 * modelinin birebir portu. Parametreler çok-lig backtest'iyle sabitlendi.
 *
 * <p><b>Not:</b> Bu bir istatistik analizdir, bahis tavsiyesi değildir.
 */
public final class RatingEngine {

    private RatingEngine() {}

    // Backtest'te sabitlenen parametreler (çok-lig doğrulaması: lr 0.04–0.08).
    private static final double LR = 0.06;     // öğrenme hızı
    private static final double REG = 0.05;    // reyting L2 çekme (kararlılık)
    private static final double RHO = -0.08;   // Dixon-Coles düşük-skor düzeltmesi
    private static final double CLAMP = 1.6;   // reyting farkı sınırı (λ patlamasın)
    private static final int MAXG = 9;         // skor matrisi 0..8 gol
    private static final double DEF_HOME_AVG = 1.45;
    private static final double DEF_AWAY_AVG = 1.15;

    /** Bir takımın hücum/savunma reytingi (log-ölçek, 0 = lig ortalaması). */
    public record Rating(double attack, double defense) {}

    /** Reyting hesaplaması için tek maç (kronolojik). xG null olabilir → gole düşer. */
    public record MatchRow(long homeId, long awayId, int homeGoals, int awayGoals,
                           Double homeXg, Double awayXg) {}

    /** Hesaplanan reytingler + görülme sayıları + lig gol ortalamaları. */
    public record Ratings(Map<Long, Rating> map, Map<Long, Integer> appearances,
                          double leagueHomeAvg, double leagueAwayAvg, int matches) {}

    /** Bir maç için olasılık çıktısı (yüzde 0..1). */
    public record Probabilities(double homeWin, double draw, double awayWin,
                                double over25, double under25,
                                double bttsYes, double bttsNo,
                                double lambdaHome, double lambdaAway) {}

    private static double clamp(double x) {
        return x < -CLAMP ? -CLAMP : (x > CLAMP ? CLAMP : x);
    }

    private static double pois(int k, double lam) {
        double f = 1;
        for (int i = 2; i <= k; i++) f *= i;
        return Math.exp(-lam) * Math.pow(lam, k) / f;
    }

    /**
     * Kronolojik maç dizisinden takım reytinglerini hesaplar (walk-forward,
     * leak yok — her maç sadece kendinden öncekileri günceller).
     */
    public static Ratings compute(List<MatchRow> rows) {
        double sumH = 0, sumA = 0;
        for (MatchRow r : rows) { sumH += r.homeGoals(); sumA += r.awayGoals(); }
        int n = rows.size();
        double lhAvg = n > 0 ? sumH / n : DEF_HOME_AVG;
        double laAvg = n > 0 ? sumA / n : DEF_AWAY_AVG;
        double muH = Math.log(Math.max(0.2, lhAvg));
        double muA = Math.log(Math.max(0.2, laAvg));

        Map<Long, double[]> R = new HashMap<>();       // teamId -> {attack, defense}
        Map<Long, Integer> seen = new HashMap<>();
        for (MatchRow r : rows) {
            double[] rh = R.computeIfAbsent(r.homeId(), k -> new double[2]);
            double[] ra = R.computeIfAbsent(r.awayId(), k -> new double[2]);
            double lh = Math.exp(muH + clamp(rh[0] - ra[1]));
            double la = Math.exp(muA + clamp(ra[0] - rh[1]));
            // Öğrenme sinyali: xG (ikisi de varsa) yoksa gerçek gol.
            double sh = r.homeGoals(), sa = r.awayGoals();
            if (r.homeXg() != null && r.awayXg() != null) {
                sh = r.homeXg(); sa = r.awayXg();
            }
            double eh = sh - lh, ea = sa - la;
            rh[0] += LR * eh - LR * REG * rh[0];
            ra[1] += -LR * eh - LR * REG * ra[1];
            ra[0] += LR * ea - LR * REG * ra[0];
            rh[1] += -LR * ea - LR * REG * rh[1];
            seen.merge(r.homeId(), 1, Integer::sum);
            seen.merge(r.awayId(), 1, Integer::sum);
        }

        Map<Long, Rating> map = new HashMap<>();
        for (Map.Entry<Long, double[]> e : R.entrySet()) {
            map.put(e.getKey(), new Rating(e.getValue()[0], e.getValue()[1]));
        }
        return new Ratings(map, seen, lhAvg, laAvg, n);
    }

    /** İki takımın reytinginden maç olasılıklarını üretir. */
    public static Probabilities predict(Rating home, Rating away,
                                        double leagueHomeAvg, double leagueAwayAvg) {
        double muH = Math.log(Math.max(0.2, leagueHomeAvg));
        double muA = Math.log(Math.max(0.2, leagueAwayAvg));
        double lh = Math.exp(muH + clamp(home.attack() - away.defense()));
        double la = Math.exp(muA + clamp(away.attack() - home.defense()));

        double[][] m = new double[MAXG][MAXG];
        double tot = 0;
        for (int i = 0; i < MAXG; i++) {
            for (int j = 0; j < MAXG; j++) {
                m[i][j] = pois(i, lh) * pois(j, la) * tau(i, j, lh, la);
                tot += m[i][j];
            }
        }
        double ph = 0, px = 0, pa = 0, over = 0, btts = 0;
        for (int i = 0; i < MAXG; i++) {
            for (int j = 0; j < MAXG; j++) {
                double p = m[i][j] / tot;
                if (i > j) ph += p; else if (i == j) px += p; else pa += p;
                if (i + j > 2) over += p;
                if (i >= 1 && j >= 1) btts += p;
            }
        }
        return new Probabilities(ph, px, pa, over, 1 - over, btts, 1 - btts, lh, la);
    }

    private static double tau(int x, int y, double lh, double la) {
        if (x == 0 && y == 0) return 1 - lh * la * RHO;
        if (x == 0 && y == 1) return 1 + lh * RHO;
        if (x == 1 && y == 0) return 1 + la * RHO;
        if (x == 1 && y == 1) return 1 - RHO;
        return 1.0;
    }
}
