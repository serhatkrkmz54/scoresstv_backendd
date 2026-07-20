package com.scorestv.football.seo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scorestv.common.SlugUtil;
import com.scorestv.football.domain.Country;
import com.scorestv.football.domain.Team;
import com.scorestv.football.web.dto.TeamSeoResponse;
import com.scorestv.storage.MinioStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Takim detay sayfasi icin tam SEO paketini ({@link TeamSeoResponse}) uretir.
 *
 * <p>Hreflang davranisi {@link LeagueDetailSeoBuilder} ile ayni mantik:
 * <ul>
 *   <li>TR: {@code /takim/{slug}-{id}}</li>
 *   <li>EN: {@code /team/{slug}-{id}}</li>
 * </ul>
 * NOT: Slug uretiminde ID anchor zaten kullanildigi icin TR ve EN ayni slug
 * cikar (sluglar takim adina degil ID'ye bagli oldugundan); hreflang yine de
 * dilden bagimsiz URL prefix farki icin gereklidir.
 */
@Component
public class TeamDetailSeoBuilder {

    private static final Logger log = LoggerFactory.getLogger(TeamDetailSeoBuilder.class);

    private static final Locale TURKISH = Locale.of("tr");
    private static final Locale ENGLISH = Locale.ENGLISH;
    private static final String SITE_NAME = "Scores TV";

    private static final ObjectMapper JSON_LD_MAPPER = new ObjectMapper();

    private final MessageSource messageSource;
    private final SeoProperties seoProperties;
    private final MinioStorageService storage;

    public TeamDetailSeoBuilder(MessageSource messageSource,
                                SeoProperties seoProperties,
                                MinioStorageService storage) {
        this.messageSource = messageSource;
        this.seoProperties = seoProperties;
        this.storage = storage;
    }

    /**
     * Takim icin SEO paketi olusturur.
     *
     * @param team           takim entity
     * @param country        ulke entity (null olabilir)
     * @param selectedSeason kullanicinin baktigi sezon (URL veya default)
     * @param displayName    dile gore takim adi (TR ise name_tr, yoksa name)
     * @param lang           "tr" / "en"
     */
    public TeamSeoResponse build(Team team, Country country, Integer selectedSeason,
                                 String displayName, String lang) {
        boolean turkish = "tr".equalsIgnoreCase(lang);
        Locale locale = turkish ? TURKISH : ENGLISH;
        String localeCode = turkish ? "tr" : "en";

        String displayCountry = displayCountryName(country, team, turkish);
        String seasonLabel = formatSeason(selectedSeason);

        String slug = SlugUtil.teamSlug(displayName, team.getId());
        String baseUrl = trimTrailingSlash(seoProperties.siteUrl());
        String pathPrefix = turkish ? "/takim/" : "/team/";
        String canonicalUrl = baseUrl + pathPrefix + slug;

        Object[] args = {displayName, displayCountry, seasonLabel};
        String title = messageSource.getMessage("seo.team.title", args, locale);
        String description = messageSource.getMessage("seo.team.description", args, locale);
        String keywords = messageSource.getMessage("seo.team.keywords", args, locale);

        String image = team.getLogoKey() != null
                ? storage.publicUrl(team.getLogoKey())
                : null;

        TeamSeoResponse.OpenGraph og = new TeamSeoResponse.OpenGraph(
                title, description, "website", canonicalUrl, image,
                SITE_NAME, turkish ? "tr_TR" : "en_US");

        TeamSeoResponse.TwitterCard twitter = new TeamSeoResponse.TwitterCard(
                "summary_large_image", title, description, image);

        String jsonLd = buildJsonLd(team, displayName, displayCountry, canonicalUrl, image);

        List<TeamSeoResponse.Breadcrumb> breadcrumbs = buildBreadcrumbs(
                displayName, displayCountry, canonicalUrl, turkish, baseUrl);

        // Hreflang: TR ve EN ayni id'ye isaret eden iki url + x-default
        String slugTr = SlugUtil.teamSlug(turkish ? displayName
                : (team.getNameTr() != null && !team.getNameTr().isBlank()
                    ? team.getNameTr() : team.getName()),
                team.getId());
        String slugEn = SlugUtil.teamSlug(team.getName(), team.getId());
        List<TeamSeoResponse.Hreflang> hreflang = List.of(
                new TeamSeoResponse.Hreflang("tr", baseUrl + "/takim/" + slugTr),
                new TeamSeoResponse.Hreflang("en", baseUrl + "/team/" + slugEn),
                new TeamSeoResponse.Hreflang("x-default", baseUrl + "/team/" + slugEn));

        return new TeamSeoResponse(
                title, description, keywords, canonicalUrl, slug, localeCode,
                og, twitter, jsonLd, breadcrumbs, hreflang);
    }

    /** Schema.org SportsTeam JSON-LD. */
    private String buildJsonLd(Team team, String displayName, String displayCountry,
                                String canonicalUrl, String image) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("@context", "https://schema.org");
        root.put("@type", "SportsTeam");
        root.put("name", displayName);
        root.put("url", canonicalUrl);
        if (image != null) {
            root.put("logo", image);
        }
        if (team.getFounded() != null) {
            root.put("foundingDate", String.valueOf(team.getFounded()));
        }
        if (displayCountry != null && !displayCountry.isBlank()) {
            root.put("location", displayCountry);
        }
        root.put("sport", "Soccer");
        try {
            return JSON_LD_MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            log.warn("Takim JSON-LD serilesirilemedi: teamId={} — {}",
                    team.getId(), ex.getMessage());
            return "{}";
        }
    }

    /** Breadcrumb zinciri: Ana Sayfa › Ulke › Takim. */
    private List<TeamSeoResponse.Breadcrumb> buildBreadcrumbs(
            String teamName, String countryName,
            String canonicalUrl, boolean turkish, String baseUrl) {
        List<TeamSeoResponse.Breadcrumb> list = new ArrayList<>();
        list.add(new TeamSeoResponse.Breadcrumb(
                1, turkish ? "Ana Sayfa" : "Home", baseUrl + "/"));
        if (countryName != null && !countryName.isBlank()) {
            list.add(new TeamSeoResponse.Breadcrumb(2, countryName, baseUrl + "/"));
            list.add(new TeamSeoResponse.Breadcrumb(3, teamName, canonicalUrl));
        } else {
            list.add(new TeamSeoResponse.Breadcrumb(2, teamName, canonicalUrl));
        }
        return list;
    }

    private static String displayCountryName(Country country, Team team, boolean turkish) {
        if (turkish && country != null
                && country.getNameTr() != null && !country.getNameTr().isBlank()) {
            return country.getNameTr();
        }
        if (country != null && country.getName() != null) {
            return country.getName();
        }
        return team.getCountry();
    }

    /** "2025" → "2025-26" (Eylul-Mayis sezon konvansiyonu). */
    private static String formatSeason(Integer year) {
        if (year == null) return "";
        int next = (year + 1) % 100;
        return year + "-" + String.format(Locale.ROOT, "%02d", next);
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
