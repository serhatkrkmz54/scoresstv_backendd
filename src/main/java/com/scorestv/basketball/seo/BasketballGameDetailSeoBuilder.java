package com.scorestv.basketball.seo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scorestv.basketball.domain.BasketballGame;
import com.scorestv.basketball.domain.BasketballLeague;
import com.scorestv.basketball.domain.BasketballTeam;
import com.scorestv.basketball.web.dto.BasketballGameDetailResponse.HreflangAlt;
import com.scorestv.basketball.web.dto.BasketballGameDetailResponse.SeoBundle;
import com.scorestv.common.SlugUtil;
import com.scorestv.football.seo.SeoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Basketbol mac detay sayfasi icin SEO paketi uretir — futbol
 * {@code MatchDetailSeoBuilder}'in basketbol esi.
 *
 * <p>URET&Ccedil;TIKLERI: title/description/canonical + Open Graph + Twitter
 * Card + JSON-LD Schema.org SportsEvent + breadcrumbs + hreflang.
 *
 * <p>FT/AOT maclar icin sonuc-odakli baslik/aciklama; canli/NS maclar icin
 * "canli skor" sablonu kullanir. Mesajlar
 * {@code messages_tr.properties}/{@code messages_en.properties} icinde
 * {@code seo.basketball.*} on ekiyle saklidir.
 */
@Component
public class BasketballGameDetailSeoBuilder {

    private static final Logger log = LoggerFactory.getLogger(BasketballGameDetailSeoBuilder.class);

    private static final Locale TURKISH = Locale.of("tr");
    private static final Locale ENGLISH = Locale.ENGLISH;
    private static final String SITE_NAME = "Scores TV";
    private static final Set<String> FINISHED_STATUSES = Set.of("FT", "AOT");

    private static final ObjectMapper JSON_LD_MAPPER = new ObjectMapper();

    private final MessageSource messageSource;
    private final SeoProperties seoProperties;

    public BasketballGameDetailSeoBuilder(MessageSource messageSource,
                                          SeoProperties seoProperties) {
        this.messageSource = messageSource;
        this.seoProperties = seoProperties;
    }

    /**
     * Verilen mac ve dil icin tam SEO paketini uretir.
     *
     * @param game basketbol mac entity'si (league + team JOIN FETCH zaten yapilmis)
     * @param lang "tr" -> Turkce; aksi halde EN
     */
    public SeoBundle build(BasketballGame game, String lang) {
        boolean turkish = "tr".equalsIgnoreCase(lang);
        Locale locale = turkish ? TURKISH : ENGLISH;
        String localeCode = turkish ? "tr" : "en";

        BasketballTeam home = game.getHomeTeam();
        BasketballTeam away = game.getAwayTeam();
        BasketballLeague league = game.getLeague();

        String homeName = displayName(home, turkish);
        String awayName = displayName(away, turkish);
        String leagueName = displayName(league, turkish);
        String date = formatDate(game, locale);

        // Slug DAIMA Ingilizce addan — URL'ler dilden bagimsiz sabit kalir.
        String slug = SlugUtil.gameSlug(
                home != null ? home.getName() : "home",
                away != null ? away.getName() : "away",
                game.getId());
        String baseUrl = trimTrailingSlash(seoProperties.siteUrl());
        // Web URL paterni: TR /basketbol/mac/{slug}, EN /basketball/match/{slug}.
        // Canonical o dilin KENDI URL'ine isaret eder (self-referencing).
        String urlTr = baseUrl + "/basketbol/mac/" + slug;
        String urlEn = baseUrl + "/basketball/match/" + slug;
        String canonicalUrl = turkish ? urlTr : urlEn;

        // Title + description
        String title;
        String description;
        Integer hScore = game.getHomeTotal();
        Integer aScore = game.getAwayTotal();
        String status = game.getStatusShort();
        boolean finished = status != null && FINISHED_STATUSES.contains(status);

        Object[] args = {
                homeName, awayName, leagueName, date,
                hScore != null ? hScore : 0,
                aScore != null ? aScore : 0,
                resolveWinnerName(homeName, awayName, hScore, aScore)
        };
        if (finished && hScore != null && aScore != null) {
            String titleKey = "AOT".equals(status)
                    ? "seo.basketball.title.aot" : "seo.basketball.title.ft";
            String descKey = "AOT".equals(status)
                    ? "seo.basketball.description.aot"
                    : "seo.basketball.description.ft.win";
            title = messageSource.getMessage(titleKey, args, fallbackTitle(homeName, awayName), locale);
            description = messageSource.getMessage(descKey, args, fallbackDesc(homeName, awayName), locale);
        } else {
            title = messageSource.getMessage("seo.basketball.title", args,
                    fallbackTitle(homeName, awayName), locale);
            description = messageSource.getMessage("seo.basketball.description", args,
                    fallbackDesc(homeName, awayName), locale);
        }

        // OG image — lig logosu varsa onu kullan, yoksa null (frontend default
        // OG'yi enjekte eder).
        String ogImage = league != null ? league.getLogo() : null;

        // JSON-LD SportsEvent
        String jsonLd = buildSportsEventJsonLd(
                game, home, away, league, homeName, awayName, leagueName, canonicalUrl);

        // Breadcrumbs JSON-LD: Anasayfa → Basketbol → Lig → Mac
        String breadcrumbsJsonLd = buildBreadcrumbsJsonLd(
                baseUrl, leagueName, league, homeName, awayName, slug, turkish);

        // Hreflang alternatif URL'leri
        List<HreflangAlt> hreflang = List.of(
                new HreflangAlt("tr", urlTr),
                new HreflangAlt("en", urlEn),
                new HreflangAlt("x-default", urlEn)
        );

        return new SeoBundle(
                title,
                description,
                canonicalUrl,
                title,           // OG title = title (Pixiu basketboldakine duzleme)
                description,     // OG description = description
                ogImage,
                jsonLd,
                breadcrumbsJsonLd,
                hreflang
        );
    }

    // ---- yardimcilar ----

    private static String displayName(BasketballTeam t, boolean turkish) {
        if (t == null) return "?";
        if (turkish && t.getNameTr() != null && !t.getNameTr().isBlank()) return t.getNameTr();
        return t.getName();
    }

    private static String displayName(BasketballLeague l, boolean turkish) {
        if (l == null) return "?";
        if (turkish && l.getNameTr() != null && !l.getNameTr().isBlank()) return l.getNameTr();
        return l.getName();
    }

    private static String formatDate(BasketballGame g, Locale locale) {
        if (g.getStartAt() == null) return "";
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
                .withLocale(locale)
                .withZone(ZoneId.of("Europe/Istanbul"))
                .format(g.getStartAt());
    }

    private static String resolveWinnerName(String home, String away,
                                             Integer hScore, Integer aScore) {
        if (hScore == null || aScore == null) return "";
        if (hScore > aScore) return home;
        if (aScore > hScore) return away;
        return "";
    }

    private static String fallbackTitle(String home, String away) {
        return home + " vs " + away + " | Scores TV";
    }

    private static String fallbackDesc(String home, String away) {
        return home + " vs " + away + " basketball game on Scores TV.";
    }

    private static String trimTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    /** Schema.org SportsEvent — basketbol icin. */
    private String buildSportsEventJsonLd(BasketballGame game,
                                           BasketballTeam home,
                                           BasketballTeam away,
                                           BasketballLeague league,
                                           String homeName, String awayName,
                                           String leagueName, String canonicalUrl) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("@context", "https://schema.org");
        root.put("@type", "SportsEvent");
        root.put("name", homeName + " vs " + awayName);
        root.put("sport", "Basketball");
        root.put("url", canonicalUrl);
        if (game.getStartAt() != null) {
            root.put("startDate", game.getStartAt().toString());
        }
        if (leagueName != null) {
            Map<String, Object> superEvent = new LinkedHashMap<>();
            superEvent.put("@type", "SportsEvent");
            superEvent.put("name", leagueName);
            if (league != null && league.getLogo() != null) {
                superEvent.put("image", league.getLogo());
            }
            root.put("superEvent", superEvent);
        }

        List<Map<String, Object>> competitors = new ArrayList<>();
        if (home != null) competitors.add(teamJsonLd(home, homeName));
        if (away != null) competitors.add(teamJsonLd(away, awayName));
        if (!competitors.isEmpty()) root.put("competitor", competitors);

        // Skorlar (FT/AOT)
        if (game.getHomeTotal() != null && game.getAwayTotal() != null) {
            root.put("eventStatus", "https://schema.org/EventCompleted");
            root.put("homeScore", game.getHomeTotal());
            root.put("awayScore", game.getAwayTotal());
        }
        return toJson(root);
    }

    private Map<String, Object> teamJsonLd(BasketballTeam team, String displayName) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("@type", "SportsTeam");
        m.put("name", displayName);
        if (team.getLogo() != null) m.put("logo", team.getLogo());
        return m;
    }

    private String buildBreadcrumbsJsonLd(String baseUrl, String leagueName,
                                           BasketballLeague league,
                                           String homeName, String awayName,
                                           String matchSlug, boolean turkish) {
        String basketballRoot = turkish ? baseUrl + "/basketbol" : baseUrl + "/en/basketball";
        String leagueLabel = turkish ? "Basketbol" : "Basketball";
        String matchUrl = basketballRoot + "/mac/" + matchSlug;
        if (!turkish) matchUrl = basketballRoot + "/game/" + matchSlug;

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("@context", "https://schema.org");
        root.put("@type", "BreadcrumbList");

        List<Map<String, Object>> items = new ArrayList<>();
        items.add(breadcrumbItem(1, turkish ? "Anasayfa" : "Home", baseUrl));
        items.add(breadcrumbItem(2, leagueLabel, basketballRoot));
        if (league != null) {
            String leagueSlug = SlugUtil.leagueSlug(leagueName, league.getId());
            String leagueUrl = basketballRoot + "/lig/" + leagueSlug;
            if (!turkish) leagueUrl = basketballRoot + "/league/" + leagueSlug;
            items.add(breadcrumbItem(3, leagueName, leagueUrl));
            items.add(breadcrumbItem(4, homeName + " vs " + awayName, matchUrl));
        } else {
            items.add(breadcrumbItem(3, homeName + " vs " + awayName, matchUrl));
        }
        root.put("itemListElement", items);
        return toJson(root);
    }

    private Map<String, Object> breadcrumbItem(int position, String name, String url) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("@type", "ListItem");
        m.put("position", position);
        m.put("name", name);
        m.put("item", url);
        return m;
    }

    private String toJson(Object o) {
        try {
            return JSON_LD_MAPPER.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            log.warn("JSON-LD serialize hatasi: {}", e.toString());
            return "{}";
        }
    }
}
