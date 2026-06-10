package com.scorestv.football.league;

import java.util.Locale;

/**
 * API-Football round adlarini knockout (eleme) / group (grup) olarak siniflar.
 * BracketBuilder bunu kullanir.
 *
 * <p>Knockout pattern'leri:
 * <ul>
 *   <li>"Final", "Semi-finals" / "Semifinals"</li>
 *   <li>"Quarter-finals" / "Quarterfinals"</li>
 *   <li>"Round of 16", "Round of 32", "Round of 64", "Round of 128"</li>
 *   <li>"Eighth Final", "1/8 Finals", "1/4 Finals", "1/2 Finals"</li>
 *   <li>"Knockout Round", "Knockout Play-offs"</li>
 *   <li>"Play-off", "Play-offs" (kupa kontekstinde knockout)</li>
 *   <li>"3rd Place Final" (uclu maci da knockout sayariz, en alt order)</li>
 * </ul>
 *
 * <p>Group / regular pattern'leri (SKIP):
 * <ul>
 *   <li>"Group Stage", "Group A", "Group B", ...</li>
 *   <li>"Regular Season", "Regular Season - X"</li>
 *   <li>"League Stage" (Champions League yeni format), "League Phase"</li>
 * </ul>
 */
final class KnockoutClassifier {

    private KnockoutClassifier() {}

    /** Round adi knockout asamasi mi? */
    static boolean isKnockout(String roundName) {
        if (roundName == null) return false;
        String r = roundName.toLowerCase(Locale.ROOT);

        // Once skip pattern'leri
        if (r.contains("group") || r.contains("regular season")
                || r.contains("league stage") || r.contains("league phase")
                || r.startsWith("matchday") || r.contains("matchweek")) {
            return false;
        }
        // Knockout pattern'leri
        return r.contains("final") || r.contains("semi") || r.contains("quarter")
                || r.contains("round of") || r.contains("eighth")
                || r.contains("knockout") || r.contains("play-off")
                || r.contains("play off") || r.contains("playoff")
                || r.contains("1/8") || r.contains("1/4") || r.contains("1/2")
                || r.contains("3rd place") || r.contains("third place");
    }

    /**
     * "Round of 16 - 1st Leg" gibi son ek'i sok: aslen sinif normalize edilmis
     * tur adi. 2 leg'in ayni tura toplanmasi icin.
     *
     * <p>ONEMLI: SADECE "leg" suffix'i strip ediliyor. Onceden " - DIGIT"
     * de stripleniyordu ama TR Kupasi'nda API "Final - 1", "Final - 2" gibi
     * adlar kullanip 1. eleme turunu kastediyor. Onlari Final'e cevirip
     * tek bir tura toplamamak icin sayisal suffix'i KORUYORUZ.
     */
    static String normalize(String roundName) {
        if (roundName == null) return null;
        String s = roundName;
        // "X - 1st Leg" / "X - 2nd Leg" gibi son ek'i sok — sadece leg pattern
        int dash = s.lastIndexOf(" - ");
        if (dash > 0) {
            String tail = s.substring(dash + 3).toLowerCase(Locale.ROOT);
            if (tail.contains("leg")) {
                s = s.substring(0, dash);
            }
        }
        return s.trim();
    }

    /**
     * Bracket sirasinda kullanilan order — buyuk = Final'e yakin.
     * Final = 100, Semi = 90, Quarter = 80, R16 = 70, R32 = 60, R64 = 50,
     * Play-off / Qualifying = 20, 3rd Place = 95.
     */
    static int orderOf(String roundName) {
        if (roundName == null) return 0;
        String r = roundName.toLowerCase(Locale.ROOT);
        if (r.contains("3rd place") || r.contains("third place")) return 95;
        if (r.contains("final") && !r.contains("semi") && !r.contains("quarter")
                && !r.contains("1/")) return 100;
        if (r.contains("semi") || r.contains("1/2")) return 90;
        if (r.contains("quarter") || r.contains("1/4")) return 80;
        if (r.contains("round of 16") || r.contains("eighth") || r.contains("1/8")) return 70;
        if (r.contains("round of 32") || r.contains("1/16")) return 60;
        if (r.contains("round of 64") || r.contains("1/32")) return 50;
        if (r.contains("round of 128")) return 40;
        if (r.contains("knockout play")) return 30;
        if (r.contains("play-off") || r.contains("play off") || r.contains("playoff")) return 20;
        if (r.contains("qualifying") || r.contains("preliminary")) return 10;
        return 0;
    }

    /**
     * UCL / UEL / UECL'nin yeni format <b>pre-knockout play-off</b> turu mu?
     *
     * <p>Champions League yeni formatinda lig asamasi sonrasi 9-24. siralar
     * arasi takimlar "Knockout Round Play-offs" turunda eslesir, galipler
     * Round of 16'ya gider. Bu tur klasik 2:1 bracket disiplinine uymaz
     * (8 play-off tie → 8 R16 tie, 1:1) ve gercek eslesme bilgisi flat
     * fixture listesinden cikarilamaz. SofaScore vs benzerleri de bunu
     * bracket disinda tutar.
     *
     * <p>Pattern: "knockout" + "play-off"/"play off"/"playoff" birlikte gecer.
     *
     * <p><b>Etkilemez:</b> "Play-off Final" (TFF 1. Lig), "Promotion Play-off"
     * (alt lig) gibi standalone play-off turlari — sadece "knockout" kelimesi
     * ile birlikte gectiginde filter calisir.
     */
    static boolean isPreKnockoutPlayoff(String roundName) {
        if (roundName == null) return false;
        String r = roundName.toLowerCase(Locale.ROOT);
        if (!r.contains("knockout")) return false;
        return r.contains("play-off") || r.contains("play off") || r.contains("playoff");
    }

    /** Bu round Final'in kendisi mi? (3. lik macini Final saymayiz.) */
    static boolean isFinalRound(String roundName) {
        if (roundName == null) return false;
        String r = roundName.toLowerCase(Locale.ROOT);
        if (r.contains("3rd place") || r.contains("third place")) return false;
        return r.contains("final") && !r.contains("semi")
                && !r.contains("quarter") && !r.contains("1/");
    }
}
