package com.scorestv.football.insight;

import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.Team;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Bir maç için "AI Analiz" olasılıklarını üretir: lig+sezon reytinglerini
 * {@link RatingService}'ten alır, iki takımın reytinginden Poisson olasılıkları
 * ({@link RatingEngine}) hesaplar. Yeterli veri yoksa {@code available=false}.
 *
 * <p>Çıktı yalnız istatistiksel analizdir; bahis tavsiyesi/tüyo DEĞİLDİR.
 */
@Service
public class MatchInsightService {

    /** Bir takımın motora girmesi için gereken min. geçmiş maç. */
    private static final int MIN_APPEARANCES = 4;
    /** Lig ortalamasının anlamlı olması için gereken min. maç. */
    private static final int MIN_LEAGUE_MATCHES = 20;

    private final RatingService ratingService;

    public MatchInsightService(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    public MatchInsightResponse forFixture(Fixture fixture, boolean turkish) {
        String note = turkish
                ? "İstatistiksel analiz — bahis tavsiyesi değildir."
                : "Statistical analysis — not betting advice.";

        Long leagueId = fixture.getLeague() != null ? fixture.getLeague().getId() : null;
        Integer season = fixture.getSeason();
        Long homeId = fixture.getHomeTeam() != null ? fixture.getHomeTeam().getId() : null;
        Long awayId = fixture.getAwayTeam() != null ? fixture.getAwayTeam().getId() : null;
        if (leagueId == null || season == null || homeId == null || awayId == null) {
            return MatchInsightResponse.unavailable(note);
        }

        RatingEngine.Ratings ratings = ratingService.ratingsFor(leagueId, season);
        if (ratings.matches() < MIN_LEAGUE_MATCHES) {
            return MatchInsightResponse.unavailable(note);
        }

        RatingEngine.Rating home = ratings.map().get(homeId);
        RatingEngine.Rating away = ratings.map().get(awayId);
        int homeSeen = ratings.appearances().getOrDefault(homeId, 0);
        int awaySeen = ratings.appearances().getOrDefault(awayId, 0);
        if (home == null || away == null
                || homeSeen < MIN_APPEARANCES || awaySeen < MIN_APPEARANCES) {
            return MatchInsightResponse.unavailable(note);
        }

        RatingEngine.Probabilities p = RatingEngine.predict(
                home, away, ratings.leagueHomeAvg(), ratings.leagueAwayAvg());

        double max = Math.max(p.homeWin(), Math.max(p.draw(), p.awayWin()));
        // Net favori yalnız en yüksek olasılık ikinciyi belirgin geçiyorsa;
        // aksi halde null (başa baş) — "hep ev favori" yanılgısını önler.
        String favorite = favorite(p.homeWin(), p.draw(), p.awayWin());

        // Yüzdeler tam 100'e toplansın: 1X2 için en-büyük-kalan (Hamilton)
        // yöntemi; Alt/Üst ve KG için ikinci değer 100'den çıkarılır.
        int[] wdl = round100(p.homeWin(), p.draw(), p.awayWin());
        int over = (int) Math.round(p.over25() * 100);
        int bttsYes = (int) Math.round(p.bttsYes() * 100);

        // En olası ilk 3 kesin skor (tek skor yanıltıcı olduğu için).
        List<MatchInsightResponse.ScoreLine> topScores = new ArrayList<>();
        for (RatingEngine.Score s : p.topScores()) {
            topScores.add(new MatchInsightResponse.ScoreLine(
                    s.home() + "-" + s.away(), (int) Math.round(s.prob() * 100)));
        }

        String homeName = displayName(fixture.getHomeTeam(), turkish);
        String awayName = displayName(fixture.getAwayTeam(), turkish);
        String summary = summary(p, homeName, awayName,
                wdl[0], wdl[2], over, bttsYes, favorite, turkish);

        return new MatchInsightResponse(
                true,
                wdl[0], wdl[1], wdl[2],
                over, 100 - over,
                bttsYes, 100 - bttsYes,
                round1(p.lambdaHome()), round1(p.lambdaAway()),
                topScores, favorite, confidence(max, turkish),
                summary, note);
    }

    private static String displayName(Team t, boolean tr) {
        if (t == null) {
            return "";
        }
        if (tr && t.getNameTr() != null && !t.getNameTr().isBlank()) {
            return t.getNameTr();
        }
        return t.getName() != null ? t.getName() : "";
    }

    /**
     * Sayıları kelimeye döken kısa analiz okuması — <b>betimleyici, tüyo DEĞİL</b>.
     * Favori tarafı, gol (Alt/Üst) eğilimini ve belirgin ise KG eğilimini
     * anlatır; dengeli/başa baş durumları dürüstçe "net üstünlük yok" der.
     * Emir kipi ("oyna", "yatır") KULLANILMAZ.
     */
    private static String summary(RatingEngine.Probabilities p,
                                  String homeName, String awayName,
                                  int homePct, int awayPct, int overPct, int bttsYesPct,
                                  String favorite, boolean tr) {
        StringBuilder sb = new StringBuilder();

        // 1) Sonuç eğilimi
        if (favorite == null || "DRAW".equals(favorite)) {
            sb.append(tr
                    ? "Model bu maçı başa baş görüyor; net bir favori yok."
                    : "The model sees this as a toss-up; no clear favorite.");
        } else {
            boolean home = "HOME".equals(favorite);
            String favName = home ? homeName : awayName;
            int favPct = home ? homePct : awayPct;
            if (favPct < 45) {
                sb.append(tr
                        ? favName + " hafif favori (%" + favPct + "), ama net bir üstünlük yok."
                        : favName + " is a slight favorite (" + favPct + "%), but no clear edge.");
            } else {
                sb.append(tr
                        ? favName + " istatistiksel favori (%" + favPct + ")."
                        : favName + " is the statistical favorite (" + favPct + "%).");
            }
        }

        // 2) Gol (Alt/Üst 2.5) eğilimi
        String tot = String.format(java.util.Locale.US, "%.1f",
                p.lambdaHome() + p.lambdaAway());
        if (overPct >= 55) {
            sb.append(tr
                    ? " Gol beklentisi ~" + tot + "; 2.5 ÜST tarafı öne çıkıyor (%" + overPct + ")."
                    : " Expected goals ~" + tot + "; over 2.5 stands out (" + overPct + "%).");
        } else if (overPct <= 45) {
            sb.append(tr
                    ? " Gol beklentisi ~" + tot + "; 2.5 ALT tarafı öne çıkıyor (%" + (100 - overPct) + ")."
                    : " Expected goals ~" + tot + "; under 2.5 stands out (" + (100 - overPct) + "%).");
        } else {
            sb.append(tr
                    ? " Gol beklentisi ~" + tot + "; 2.5 Alt/Üst başa baş."
                    : " Expected goals ~" + tot + "; over/under 2.5 is a coin-flip.");
        }

        // 3) KG — yalnız belirgin eğilimde
        if (bttsYesPct >= 58) {
            sb.append(tr
                    ? " Karşılıklı gol eğilimi yüksek (%" + bttsYesPct + ")."
                    : " Both teams scoring is likely (" + bttsYesPct + "%).");
        } else if (bttsYesPct <= 42) {
            sb.append(tr
                    ? " Bir tarafın gol yememe eğilimi var (%" + (100 - bttsYesPct) + ")."
                    : " A clean sheet somewhere is likely (" + (100 - bttsYesPct) + "%).");
        }

        return sb.toString();
    }

    /**
     * Üç olasılığı (0..1, toplam ~1) tam 100'e toplanan tamsayı yüzdelere çevirir
     * — en-büyük-kalan (Hamilton) yöntemi. Bağımsız yuvarlamanın 99/101 artığını
     * önler: taban değerler alınır, eksik kalan puanlar en büyük ondalıklı
     * paylara dağıtılır.
     */
    private static int[] round100(double a, double b, double c) {
        double[] s = {a * 100, b * 100, c * 100};
        int[] f = new int[3];
        double[] rem = new double[3];
        int sum = 0;
        for (int i = 0; i < 3; i++) {
            f[i] = (int) Math.floor(s[i]);
            rem[i] = s[i] - f[i];
            sum += f[i];
        }
        int left = 100 - sum;
        for (int k = 0; k < left && k < 3; k++) {
            int best = 0;
            for (int i = 1; i < 3; i++) {
                if (rem[i] > rem[best]) {
                    best = i;
                }
            }
            f[best] += 1;
            rem[best] = -1;
        }
        return f;
    }

    /**
     * Net favori — en yüksek 1X2 olasılığı ikinciyi belirgin (≥6 puan) geçiyorsa
     * "HOME"/"AWAY"/"DRAW"; aksi halde {@code null} (başa baş). Bu, iki taraf
     * birbirine yakınken mekanik olarak birini "favori" gösterme yanılgısını önler.
     */
    private static String favorite(double h, double d, double a) {
        double top = Math.max(h, Math.max(d, a));
        double min = Math.min(h, Math.min(d, a));
        double second = h + d + a - top - min;
        if (top - second < 0.06) {
            return null;
        }
        if (top == h) {
            return "HOME";
        }
        if (top == a) {
            return "AWAY";
        }
        return "DRAW";
    }

    private static double round1(double x) {
        return Math.round(x * 10) / 10.0;
    }

    private static String confidence(double max, boolean tr) {
        if (max >= 0.55) {
            return tr ? "Yüksek" : "High";
        }
        if (max >= 0.42) {
            return tr ? "Orta" : "Moderate";
        }
        return tr ? "Düşük" : "Low";
    }
}
