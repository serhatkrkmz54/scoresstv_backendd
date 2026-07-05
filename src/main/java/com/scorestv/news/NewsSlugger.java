package com.scorestv.news;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Basliktan URL-guvenli slug uretir. Turkce karakterler (c, g, i, o, s, u)
 * ASCII karsiliklarina indirgenir; harf/rakam disi her sey tire olur.
 */
public final class NewsSlugger {

    private static final Pattern NON_LATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]+");
    private static final Pattern EDGE_DASHES = Pattern.compile("(^-+)|(-+$)");
    private static final Pattern MULTI_DASH = Pattern.compile("-{2,}");
    private static final int MAX_LEN = 200;

    private NewsSlugger() {
    }

    /** Verilen metinden temel slug uretir (collision suffix eklenmez). */
    public static String slugify(String input) {
        if (input == null || input.isBlank()) {
            return "haber";
        }
        String s = input.trim().toLowerCase(Locale.forLanguageTag("tr"));
        // Turkce ozel harfleri ASCII'ye cevir (Normalizer bunlari tam
        // cozemedigi icin acikca degistiriyoruz).
        s = s.replace("i", "i").replace("ı", "i")  // dotless i -> i
                .replace("ş", "s")  // s with cedilla
                .replace("ğ", "g")  // g with breve
                .replace("ç", "c")  // c with cedilla
                .replace("ö", "o")  // o with diaeresis
                .replace("ü", "u"); // u with diaeresis
        s = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        s = WHITESPACE.matcher(s).replaceAll("-");
        s = NON_LATIN.matcher(s).replaceAll("");
        s = MULTI_DASH.matcher(s).replaceAll("-");
        s = EDGE_DASHES.matcher(s).replaceAll("");
        if (s.length() > MAX_LEN) {
            s = s.substring(0, MAX_LEN);
            s = EDGE_DASHES.matcher(s).replaceAll("");
        }
        return s.isBlank() ? "haber" : s;
    }
}
