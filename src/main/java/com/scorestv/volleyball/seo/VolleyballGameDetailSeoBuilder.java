package com.scorestv.volleyball.seo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scorestv.common.SlugUtil;
import com.scorestv.football.seo.SeoProperties;
import com.scorestv.volleyball.domain.VolleyballGame;
import com.scorestv.volleyball.domain.VolleyballLeague;
import com.scorestv.volleyball.domain.VolleyballTeam;
import com.scorestv.volleyball.web.dto.VolleyballGameDetailResponse.HreflangAlt;
import com.scorestv.volleyball.web.dto.VolleyballGameDetailResponse.SeoBundle;
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
 * Voleybol mac detay sayfasi icin SEO paketi uretir — basketbol
 * {@code BasketballGameDetailSeoBuilder}'in voleybol esi.
 *
 * <p>URETTIKLERI: title/description/canonical + Open Graph + Twitter
 * Card + JSON-LD Schema.org SportsEvent + breadcrumbs + hreflang.
 *
 * <p>FT/AW maclar icin sonuc-odakli baslik/aciklama (set sonuclari);
 * canli/NS maclar icin "canli skor" sablonu kullanir. Mesajlar
 * {@code messages_tr.properties}/{@code messages_en.properties} icinde
 * {@code seo.volleyball.*} on ekiyle saklidir.
 */
@Component
public class VolleyballGameDetailSeoBuilder {

    private static final Logger log = LoggerFactory.getLogger(VolleyballGameDetailSeoBuilder.class);

    private static final Locale TURKISH = Locale.of("tr");
    private static final Locale ENGLISH = Locale.ENGLISH;
    private static final String SITE_NAME = "ScoresTV";
    private static final Set<String> FINISHED_STATUSES = Set.of("FT", "AW");

    private static final ObjectMapper JSON_LD_MAPPER = new ObjectMapper();

    private final MessageSource messageSource;
    private final SeoProperties seoProperties;

    public VolleyballGameDetailSeoBuilder(MessageSource messageSource,
                                          SeoProperties seoProperties) {
        this.messageSource = messageSource;
        this.seoProperties = seoProperties;
    }

    /**
     * Verilen mac ve dil icin tam SEO paketini uretir.
     *
     * @param game voleybol mac entity'si (league + team JOIN FETCH zaten yapilmis)
     * @param lang "tr" -> Turkce; aksi halde EN
     */
    public SeoBundle build(VolleyballGame game, String lang) {
        boolean turkish = "tr".equalsIgnoreCase(lang);
        Locale locale = turkish ? TURKISH : ENGLISH;

        VolleyballTeam home = game.getHomeTeam();
        VolleyballTeam away = game.getAwayTeam();
        VolleyballLeague league = game.getLeague();

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
        // Web URL paterni: /voleybol/mac/{slug}
        String canonicalUrl = baseUrl + "/voleybol/mac/" + slug;

        // Title + description
        String title;
        String description;
        Integer hSets = game.getHomeTotal();
        Integer aSets = game.getAwayTotal();
        String status = game.getStatusShort();
        boolean finished = status != null && FINISHED_STATUSES.contains(status);

        Object[] args = {
                homeName, awayName, leagueName, date,
                hSets != null ? hSets : 0,
                aSets != null ? aSets : 0,
                resolveWinnerName(homeName, awayName, hSets, aSets)
        };
        if (finished && hSets != null && aSets != null) {
            title = messageSource.getMessage("seo.volleyball.title.ft", args,
                    fallbackTitle(homeName, awayName), locale);
            description = messageSource.getMessage("seo.volleyball.description.ft.win", args,
                    fallbackDesc(homeName, awayName), locale);
        } else {
            title = messageSource.getMessage("seo.volleyball.title", args,
                    fallbackTitle(homeName, awayName), locale);
            description = messageSource.getMessage("seo.volleyball.description", args,
                    fallbackDesc(homeName, awayName), locale);
        }

        // OG image — lig logosu varsa onu kullan, yoksa null (frontend default
        // OG'yi enjekte eder).
        String ogImage = league != null ? league.getLogo() : null;

        // JSON-LD SportsEvent
        String jsonLd = buildSportsEventJsonLd(
                game, home, away, league, homeName, awayName, leagueName, canonicalUrl);

        // Breadcrumbs JSON-LD: Anasayfa → Voleybol → Lig → Mac
        String breadcrumbsJsonLd = buildBreadcrumbsJsonLd(
                baseUrl, leagueName, league, homeName, awayName, slug, turkish);

        // Hreflang alternatif URL'leri
        List<HreflangAlt> hreflang = List.of(
                new HreflangAlt("tr",
                        baseUrl + "/voleybol/mac/" + slug),
                new HreflangAlt("en",
                        baseUrl + "/volleyball/match/" + slug),
                new HreflangAlt("x-default",
                        baseUrl + "/voleybol/mac/" + slug)
        );

        return new SeoBundle(
                title,
                description,
                canonicalUrl,
                title,           // OG title = title
                description,     // OG description = description
                ogImage,
                jsonLd,
                breadcrumbsJsonLd,
                hreflang
        );
    }

    // ---- yardimcilar ----

    private static String displayName(VolleyballTeam t, boolean turkish) {
        if (t == null) return "?";
        if (turkish && t.getNameTr() != null && !t.getNameTr().isBlank()) return t.getNameTr();
        return t.getName();
    }

    private static String displayName(VolleyballLeague l, boolean turkish) {
        if (l == null) return "?";
        if (turkish && l.getNameTr() != null && !l.getNameTr().isBlank()) return l.getNameTr();
        return l.getName();
    }

    private static String formatDate(VolleyballGame g, Locale locale) {
        if (g.getStartAt() == null) return "";
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
                .withLocale(locale)
                .withZone(ZoneId.of("Europe/Istanbul"))
                .format(g.getStartAt());
    }

    private static String resolveWinnerName(String home, String away,
                                             Integer hSets, Integer aSets) {
        if (hSets == null || aSets == null) return "";
        if (hSets > aSets) return home;
        if (aSets > hSets) return away;
        return "";
    }

    private static String fallbackTitle(String home, String away) {
        return home + " vs " + away + " | ScoresTV";
    }

    private static String fallbackDesc(String home, String away) {
        return home + " vs " + away + " volleyball match on ScoresTV.";
    }

    private static String trimTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    /** Schema.org SportsEvent — voleybol icin. */
    private String buildSportsEventJsonLd(VolleyballGame game,
                                           VolleyballTeam home,
                                           VolleyballTeam away,
                                           VolleyballLeague league,
                                           String homeName, String awayName,
                                           String leagueName, String canonicalUrl) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("@context", "https://schema.org");
        root.put("@type", "SportsEvent");
        root.put("name", homeName + " vs " + awayName);
        root.put("sport", "Volleyball");
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

        // Skorlar (kazanilan set sayisi)
        if (game.getHomeTotal() != null && game.getAwayTotal() != null) {
            root.put("eventStatus", "https://schema.org/EventCompleted");
            root.put("homeScore", game.getHomeTotal());
            root.put("awayScore", game.getAwayTotal());
        }
        return toJson(root);
    }

    private Map<String, Object> teamJsonLd(VolleyballTeam team, String displayName) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("@type", "SportsTeam");
        m.put("name", displayName);
        if (team.getLogo() != null) m.put("logo", team.getLogo());
        return m;
    }

    private String buildBreadcrumbsJsonLd(String baseUrl, String leagueName,
                                           VolleyballLeague league,
                                           String homeName, String awayName,
                                           String matchSlug, boolean turkish) {
        String volleyballRoot = turkish ? baseUrl + "/voleybol" : baseUrl + "/en/volleyball";
        String leagueLabel = turkish ? "Voleybol" : "Volleyball";
        String matchUrl = volleyballRoot + "/mac/" + matchSlug;
        if (!turkish) matchUrl = volleyballRoot + "/match/" + matchSlug;

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("@context", "https://schema.org");
        root.put("@type", "BreadcrumbList");

        List<Map<String, Object>> items = new ArrayList<>();
        items.add(breadcrumbItem(1, turkish ? "Anasayfa" : "Home", baseUrl));
        items.add(breadcrumbItem(2, leagueLabel, volleyballRoot));
        if (league != null) {
            String leagueSlug = SlugUtil.leagueSlug(leagueName, league.getId());
            String leagueUrl = volleyballRoot + "/lig/" + leagueSlug;
            if (!turkish) leagueUrl = volleyballRoot + "/league/" + leagueSlug;
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
