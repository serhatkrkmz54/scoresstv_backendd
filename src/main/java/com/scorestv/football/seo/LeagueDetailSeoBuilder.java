package com.scorestv.football.seo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scorestv.common.SlugUtil;
import com.scorestv.football.domain.Country;
import com.scorestv.football.domain.League;
import com.scorestv.football.web.dto.LeagueSeoResponse;
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
 * Lig detay sayfasi icin tam SEO paketini ({@link LeagueSeoResponse}) uretir.
 *
 * <p>Hreflang ozellikle dikkatli: frontend URL'leri dile gore farkli:
 * <ul>
 *   <li>TR: {@code /lig/{tr-slug}-{id}}</li>
 *   <li>EN: {@code /league/{en-slug}-{id}}</li>
 * </ul>
 * Hreflang bunlari birbirine isaret eder; arama motoru hangi dilde gosterecegini
 * dogru secsin.
 */
@Component
public class LeagueDetailSeoBuilder {

    private static final Logger log = LoggerFactory.getLogger(LeagueDetailSeoBuilder.class);

    private static final Locale TURKISH = Locale.of("tr");
    private static final Locale ENGLISH = Locale.ENGLISH;
    private static final String SITE_NAME = "Scores TV";

    private static final ObjectMapper JSON_LD_MAPPER = new ObjectMapper();

    private final MessageSource messageSource;
    private final SeoProperties seoProperties;
    private final MinioStorageService storage;

    public LeagueDetailSeoBuilder(MessageSource messageSource,
                                  SeoProperties seoProperties,
                                  MinioStorageService storage) {
        this.messageSource = messageSource;
        this.seoProperties = seoProperties;
        this.storage = storage;
    }

    /**
     * Lig icin SEO paketi olusturur.
     *
     * @param league   lig entity
     * @param country  ulke entity (null olabilir — name_tr/flag_key icin)
     * @param selectedSeason kullanicinin baktigi sezon (URL veya default)
     * @param lang     "tr" / "en"
     */
    public LeagueSeoResponse build(League league, Country country,
                                   Integer selectedSeason, String lang) {
        boolean turkish = "tr".equalsIgnoreCase(lang);
        Locale locale = turkish ? TURKISH : ENGLISH;
        String localeCode = turkish ? "tr" : "en";

        String displayLeague = displayLeagueName(league, turkish);
        String displayCountry = displayCountryName(country, league, turkish);
        String seasonLabel = formatSeason(selectedSeason);

        // Slug: TR ise name_tr'den; yoksa kaynak isimden.
        String slug = SlugUtil.leagueSlug(displayLeague, league.getId());
        String baseUrl = trimTrailingSlash(seoProperties.siteUrl());
        // URL prefix: TR → /lig, EN → /league
        String pathPrefix = turkish ? "/lig/" : "/league/";
        String canonicalUrl = baseUrl + pathPrefix + slug;

        Object[] args = {displayLeague, displayCountry, seasonLabel};
        String title = messageSource.getMessage("seo.league.title", args, locale);
        String description = messageSource.getMessage("seo.league.description", args, locale);
        String keywords = messageSource.getMessage("seo.league.keywords", args, locale);

        String image = league.getLogoKey() != null
                ? storage.publicUrl(league.getLogoKey())
                : null;

        LeagueSeoResponse.OpenGraph og = new LeagueSeoResponse.OpenGraph(
                title, description, "website", canonicalUrl, image,
                SITE_NAME, turkish ? "tr_TR" : "en_US");

        LeagueSeoResponse.TwitterCard twitter = new LeagueSeoResponse.TwitterCard(
                "summary_large_image", title, description, image);

        String jsonLd = buildJsonLd(league, displayLeague, displayCountry, canonicalUrl, image);

        List<LeagueSeoResponse.Breadcrumb> breadcrumbs = buildBreadcrumbs(
                displayLeague, displayCountry, canonicalUrl, turkish, baseUrl);

        // Hreflang: ayni lig icin iki url (TR + EN) + x-default
        String slugTr = SlugUtil.leagueSlug(displayLeagueName(league, true), league.getId());
        String slugEn = SlugUtil.leagueSlug(displayLeagueName(league, false), league.getId());
        List<LeagueSeoResponse.Hreflang> hreflang = List.of(
                new LeagueSeoResponse.Hreflang("tr", baseUrl + "/lig/" + slugTr),
                new LeagueSeoResponse.Hreflang("en", baseUrl + "/league/" + slugEn),
                new LeagueSeoResponse.Hreflang("x-default", baseUrl + "/league/" + slugEn));

        return new LeagueSeoResponse(
                title, description, keywords, canonicalUrl, slug, localeCode,
                og, twitter, jsonLd, breadcrumbs, hreflang);
    }

    /** Schema.org SportsLeague (varsa Organization) JSON-LD'sini uretir. */
    private String buildJsonLd(League league, String displayLeague,
                                String displayCountry, String canonicalUrl, String image) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("@context", "https://schema.org");
        root.put("@type", "SportsOrganization");
        root.put("name", displayLeague);
        root.put("url", canonicalUrl);
        if (image != null) {
            root.put("logo", image);
        }
        if (displayCountry != null && !displayCountry.isBlank()) {
            root.put("location", displayCountry);
        }
        root.put("sport", "Soccer");

        try {
            return JSON_LD_MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            log.warn("Lig JSON-LD serilesirilemedi: leagueId={} — {}",
                    league.getId(), ex.getMessage());
            return "{}";
        }
    }

    /** Breadcrumb zinciri: Ana Sayfa › Ulke › Lig. */
    private List<LeagueSeoResponse.Breadcrumb> buildBreadcrumbs(
            String leagueName, String countryName,
            String canonicalUrl, boolean turkish, String baseUrl) {
        List<LeagueSeoResponse.Breadcrumb> list = new ArrayList<>();
        list.add(new LeagueSeoResponse.Breadcrumb(
                1, turkish ? "Ana Sayfa" : "Home", baseUrl + "/"));
        if (countryName != null && !countryName.isBlank()) {
            // Ulke detay URL'si v2 — simdilik yer tutucu olarak ana sayfa.
            list.add(new LeagueSeoResponse.Breadcrumb(2, countryName, baseUrl + "/"));
            list.add(new LeagueSeoResponse.Breadcrumb(3, leagueName, canonicalUrl));
        } else {
            list.add(new LeagueSeoResponse.Breadcrumb(2, leagueName, canonicalUrl));
        }
        return list;
    }

    private static String displayLeagueName(League league, boolean turkish) {
        if (turkish && league.getNameTr() != null && !league.getNameTr().isBlank()) {
            return league.getNameTr();
        }
        return league.getName();
    }

    private static String displayCountryName(Country country, League league, boolean turkish) {
        if (turkish && country != null
                && country.getNameTr() != null && !country.getNameTr().isBlank()) {
            return country.getNameTr();
        }
        if (country != null && country.getName() != null) {
            return country.getName();
        }
        return league.getCountryName();
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
