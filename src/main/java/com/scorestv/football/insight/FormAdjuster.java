package com.scorestv.football.insight;

/**
 * v6 gol-Elo Poisson 1X2 olasılıklarına, takımların <b>güncel lig formuna</b>
 * göre küçük bir lojistik düzeltme uygular.
 *
 * <p>Özellikler (ev − deplasman farkı): son-10 puan/maç, son-10 averaj/maç,
 * son-5 puan ortalaması. Temel olasılık sabit ofset olarak alınır (motor-bağımsız),
 * yalnız form ağırlıkları uygulanır. Katsayılar ~433 bin maçlık walk-forward,
 * sızıntısız backtest'te fit edildi (holdout log-loss iyileşmesi doğrulandı).
 *
 * <p>Yalnızca 1X2 (ev/beraberlik/deplasman) olasılıklarını ayarlar; Alt/Üst 2.5,
 * KG ve gol beklentisi (λ) değişmez. İstatistiksel analiz — bahis tavsiyesi DEĞİL.
 */
public final class FormAdjuster {

    private FormAdjuster() {}

    // Özellik sırası: [dPPG10, dGD10, dFORM5]  (standardizasyon: (x-mean)/std)
    private static final double[] MEAN = {-0.01032, -0.01655, -0.02660};
    private static final double[] STD  = {0.78777, 1.35863, 0.95729};
    private static final double[] A    = {0.03152, -0.04574, 0.01422};   // intercept [H, X, A]
    // Form ağırlıkları — satır = sınıf [H, X, A], sütun = özellik [dPPG10, dGD10, dFORM5]
    private static final double[][] W = {
        {0.01490,  0.04308,  0.03450},   // H
        {-0.01791, 0.02408,  0.00387},   // X
        {0.00300, -0.06716, -0.03838},   // A
    };

    /**
     * @return {@code {pHome, pDraw, pAway}} form-düzeltmeli (toplamı 1). Form verisi
     *         yoksa girdi olasılıkları aynen döner.
     */
    public static double[] adjust(double pH, double pX, double pA,
                                  RatingEngine.TeamForm home, RatingEngine.TeamForm away) {
        if (home == null || away == null) {
            return new double[] {pH, pX, pA};
        }
        double eps = 1e-6;
        double l1 = Math.log(Math.max(eps, pH) / Math.max(eps, pX));
        double l2 = Math.log(Math.max(eps, pA) / Math.max(eps, pX));
        double f0 = ((home.ppg()   - away.ppg())   - MEAN[0]) / STD[0];
        double f1 = ((home.gdpg()  - away.gdpg())  - MEAN[1]) / STD[1];
        double f2 = ((home.form5() - away.form5()) - MEAN[2]) / STD[2];

        double zH = A[0] + l1 + W[0][0] * f0 + W[0][1] * f1 + W[0][2] * f2;
        double zX = A[1]      + W[1][0] * f0 + W[1][1] * f1 + W[1][2] * f2;
        double zA = A[2] + l2 + W[2][0] * f0 + W[2][1] * f1 + W[2][2] * f2;

        double m = Math.max(zH, Math.max(zX, zA));
        double eH = Math.exp(zH - m), eX = Math.exp(zX - m), eA = Math.exp(zA - m);
        double s = eH + eX + eA;
        return new double[] {eH / s, eX / s, eA / s};
    }
}
