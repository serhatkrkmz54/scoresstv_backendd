package com.scorestv.common;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * URL-dostu "slug" üretimi.
 *
 * <p>Türkçe ve diğer dillerin diakritiklerini ASCII'ye indirger, küçük harfe
 * çevirir, harf/rakam dışındaki her şeyi tek tireye dönüştürür. SEO-uyumlu,
 * okunabilir çıktılar verir. Örnekler:
 * <pre>
 *   "Beşiktaş JK"          -> "besiktas-jk"
 *   "Bayern München"       -> "bayern-munchen"
 *   "Paris Saint-Germain"  -> "paris-saint-germain"
 * </pre>
 */
public final class SlugUtil {

    private SlugUtil() {
    }

    /**
     * Unicode NFD ayrıştırmasıyla doğru çözülmeyen / hiç çözülmeyen karakterler
     * için elle eşleme. (Aksanlı harflerin çoğu NFD + işaret-silme ile zaten
     * ASCII'ye iner; buradakiler istisnalardır.)
     */
    private static final Map<Character, String> SPECIAL = Map.ofEntries(
            Map.entry('ı', "i"),                      // Türkçe noktasız i
            Map.entry('İ', "i"),                      // Türkçe noktalı büyük I
            Map.entry('ş', "s"), Map.entry('Ş', "s"),
            Map.entry('ğ', "g"), Map.entry('Ğ', "g"),
            Map.entry('ç', "c"), Map.entry('Ç', "c"),
            Map.entry('ö', "o"), Map.entry('Ö', "o"),
            Map.entry('ü', "u"), Map.entry('Ü', "u"),
            Map.entry('ß', "ss"),
            Map.entry('æ', "ae"), Map.entry('Æ', "ae"),
            Map.entry('œ', "oe"), Map.entry('Œ', "oe"),
            Map.entry('ø', "o"), Map.entry('Ø', "o"),
            Map.entry('ł', "l"), Map.entry('Ł', "l"),
            Map.entry('đ', "d"), Map.entry('Đ', "d"),
            Map.entry('ð', "d"), Map.entry('Ð', "d"),
            Map.entry('þ', "th"), Map.entry('Þ', "th"),
            Map.entry('ñ', "n"), Map.entry('Ñ', "n")
    );

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_SLUG_CHARS = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_DASHES = Pattern.compile("(^-+)|(-+$)");
    private static final Pattern TRAILING_ID = Pattern.compile("(\\d+)\\s*$");

    /**
     * Bir metni slug'a çevirir. {@code null}/boş girişte boş string döner.
     *
     * @param text kaynak metin (takım/lig adı vb.)
     * @return küçük harf, ASCII, tireli slug
     */
    public static String slugify(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        // 1) Özel karakterleri elle çevir (ı, ş, ß, ø ...).
        StringBuilder pre = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String mapped = SPECIAL.get(c);
            pre.append(mapped != null ? mapped : c);
        }
        // 2) NFD ile ayrıştır, birleşen işaretleri (aksanları) sil: é -> e
        String normalized = Normalizer.normalize(pre, Normalizer.Form.NFD);
        normalized = COMBINING_MARKS.matcher(normalized).replaceAll("");
        // 3) Küçük harf (Locale.ROOT — Türkçe I sorununu önler).
        String lower = normalized.toLowerCase(Locale.ROOT);
        // 4) Harf/rakam dışını tireye, kenar tirelerini kırp.
        String dashed = NON_SLUG_CHARS.matcher(lower).replaceAll("-");
        return EDGE_DASHES.matcher(dashed).replaceAll("");
    }

    /**
     * Maç detayı slug'ı üretir: {@code {ev}-vs-{deplasman}-{fixtureId}}.
     *
     * <p>Sondaki id sayesinde slug benzersizdir ve takım adı değişse bile eski
     * URL'ler çözülebilir (bkz. {@link #extractFixtureId(String)}).
     *
     * <pre>fixtureSlug("Bayern Munich", "Paris Saint-Germain", 12345)
     *   -> "bayern-munich-vs-paris-saint-germain-12345"</pre>
     */
    public static String fixtureSlug(String homeTeam, String awayTeam, long fixtureId) {
        String home = slugify(homeTeam);
        String away = slugify(awayTeam);
        if (home.isEmpty()) {
            home = "home";
        }
        if (away.isEmpty()) {
            away = "away";
        }
        return home + "-vs-" + away + "-" + fixtureId;
    }

    /**
     * Slug'ın sonundaki maç (fixture) id'sini çıkarır.
     *
     * <p>URL'deki slug metni yalnızca SEO/insan içindir; gerçek anahtar sondaki
     * sayıdır. Böylece "/eski-takim-adi-...-12345" gibi bayatlamış URL'ler de
     * doğru maça çözülür.
     *
     * @return id; slug {@code null} ise veya sonunda sayı yoksa {@code null}
     */
    public static Long extractFixtureId(String slug) {
        return extractTrailingId(slug);
    }

    /**
     * Lig detay slug'i: {@code {lig-adi}-{ligId}}. TR'de ad name_tr'den
     * gelmeli ("super-lig-203"), EN'de ham name'den ("super-league-203").
     */
    public static String leagueSlug(String leagueName, long leagueId) {
        String name = slugify(leagueName);
        if (name.isEmpty()) {
            name = "league";
        }
        return name + "-" + leagueId;
    }

    /**
     * Takım detay slug'i: {@code {takim-adi}-{teamId}}. TR'de name_tr'den
     * ("besiktas-jk-549"), EN'de ham name'den ("besiktas-549"). Frontend
     * URL prefix'i diline göre ekler: {@code /team/} (EN) veya {@code /takim/} (TR).
     */
    public static String teamSlug(String teamName, long teamId) {
        String name = slugify(teamName);
        if (name.isEmpty()) {
            name = "team";
        }
        return name + "-" + teamId;
    }

    /**
     * Lig slug'ının sonundaki id'yi çıkarır. {@link #extractFixtureId} ile
     * aynı mantık — slug ne içerirse içersin, sondaki sayıyı yakalar.
     *
     * @return id; slug null/boş ya da sayı yoksa null
     */
    public static Long extractLeagueId(String slug) {
        return extractTrailingId(slug);
    }

    /**
     * Takım slug'ının sonundaki team id'sini çıkarır.
     *
     * @return id; slug null/boş ya da sayı yoksa null
     */
    public static Long extractTeamId(String slug) {
        return extractTrailingId(slug);
    }

    /**
     * Oyuncu detay slug'i: {@code {ad-soyad}-{playerId}}. firstname + lastname
     * yoksa name fallback. Frontend URL prefix dile gore: TR {@code /oyuncu/},
     * EN {@code /player/}.
     */
    public static String playerSlug(String firstName, String lastName,
                                    String fallbackName, long playerId) {
        String combined;
        if (firstName != null && !firstName.isBlank()
                && lastName != null && !lastName.isBlank()) {
            combined = firstName + " " + lastName;
        } else if (fallbackName != null && !fallbackName.isBlank()) {
            combined = fallbackName;
        } else {
            combined = "player";
        }
        String slug = slugify(combined);
        if (slug.isEmpty()) {
            slug = "player";
        }
        return slug + "-" + playerId;
    }

    /** Player slug'inin sonundaki player id'sini cikarir. */
    public static Long extractPlayerId(String slug) {
        return extractTrailingId(slug);
    }

    /** Slug'ın sonundaki sayıyı Long olarak çıkarır; bulamazsa null. */
    private static Long extractTrailingId(String slug) {
        if (slug == null || slug.isBlank()) {
            return null;
        }
        Matcher matcher = TRAILING_ID.matcher(slug.trim());
        if (!matcher.find()) {
            return null;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
