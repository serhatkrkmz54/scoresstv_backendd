package com.scorestv.basketball.seo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scorestv.basketball.domain.BasketballLeague;
import com.scorestv.basketball.web.dto.BasketballLeagueSeoResponse;
import com.scorestv.common.SlugUtil;
import com.scorestv.football.seo.SeoProperties;
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
 * Basketbol lig detay sayfasi icin tam SEO paketini ({@link
 * BasketballLeagueSeoResponse}) uretir.
 *
 * <p>Futboldaki {@code LeagueDetailSeoBuilder} patternine birebir paralel.
 * Farklar:
 * <ul>
 *   <li>URL prefix: TR {@code /basketbol/lig/} · EN {@code /basketball/league/}
 *   <li>JSON-LD sport alani: {@code "Basketball"}
 *   <li>Sezon: API'den olduugu gibi string ("2024-2025") — formatlanmaz
 *   <li>Message key prefix'i: {@code seo.basketball.league.*}
 * </ul>
 */
@Component
public class BasketballLeagueDetailSeoBuilder {

    private static final Logger log = LoggerFactory.getLogger(BasketballLeagueDetailSeoBuilder.class);

    private static final Locale TURKISH = Locale.of("tr");
    private static final Locale ENGLISH = Locale.ENGLISH;
    private static final String SITE_NAME = "Scores TV";

    private static final ObjectMapper JSON_LD_MAPPER = new ObjectMapper();

    private final MessageSource messageSource;
    private final SeoProperties seoProperties;
    private final MinioStorageService storage;

    public BasketballLeagueDetailSeoBuilder(MessageSource messageSource,
                                            SeoProperties seoProperties,
                                            MinioStorageService storage) {
        this.messageSource = messageSource;
        this.seoProperties = seoProperties;
        this.storage = storage;
    }

    /**
     * Lig icin SEO paketi olusturur.
     *
     * @param league         lig entity
     * @param selectedSeason secili sezon ("2024-2025" formatinda string)
     * @param lang           "tr" / "en"
     */
    public BasketballLeagueSeoResponse build(BasketballLeague league,
                                              String selectedSeason,
                                              String lang) {
        boolean turkish = "tr".equalsIgnoreCase(lang);
        Locale locale = turkish ? TURKISH : ENGLISH;

        String displayLeague = displayLeagueName(league, turkish);
        String displayCountry = displayCountryName(league, turkish);
        String seasonLabel = selectedSeason != null ? selectedSeason : "";

        String slug = league.getSlug() != null && !league.getSlug().isBlank()
                ? league.getSlug()
                : SlugUtil.leagueSlug(displayLeague, league.getId());
        String baseUrl = trimTrailingSlash(seoProperties.siteUrl());
        String pathPrefix = turkish ? "/basketbol/lig/" : "/basketball/league/";
        String canonicalUrl = baseUrl + pathPrefix + slug;

        Object[] args = {displayLeague, displayCountry, seasonLabel};
        String title = messageSource.getMessage(
                "seo.basketball.league.title", args, locale);
        String description = messageSource.getMessage(
                "seo.basketball.league.description", args, locale);

        String image = league.getLogoKey() != null
                ? storage.publicUrl(league.getLogoKey())
                : league.getLogo();

        String jsonLd = buildJsonLd(league, displayLeague, displayCountry,
                canonicalUrl, image);
        String breadcrumbsJsonLd = buildBreadcrumbsJsonLd(
                displayLeague, displayCountry, canonicalUrl, turkish, baseUrl);

        // Hreflang: ayni lig icin iki url (TR + EN) + x-default
        List<BasketballLeagueSeoResponse.HreflangAlt> hreflang = List.of(
                new BasketballLeagueSeoResponse.HreflangAlt(
                        "tr", baseUrl + "/basketbol/lig/" + slug),
                new BasketballLeagueSeoResponse.HreflangAlt(
                        "en", baseUrl + "/basketball/league/" + slug),
                new BasketballLeagueSeoResponse.HreflangAlt(
                        "x-default", baseUrl + "/basketball/league/" + slug));

        return new BasketballLeagueSeoResponse(
                title,
                description,
                canonicalUrl,
                title,           // ogTitle
                description,     // ogDescription
                image,           // ogImage
                jsonLd,
                breadcrumbsJsonLd,
                hreflang);
    }

    /** Schema.org SportsOrganization JSON-LD. */
    private String buildJsonLd(BasketballLeague league, String displayLeague,
                                String displayCountry, String canonicalUrl,
                                String image) {
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
        root.put("sport", "Basketball");

        try {
            return JSON_LD_MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            log.warn("Basketbol lig JSON-LD serilestirilemedi: leagueId={} — {}",
                    league.getId(), ex.getMessage());
            return "{}";
        }
    }

    /** Breadcrumb JSON-LD: Ana Sayfa › Ulke › Lig. */
    private String buildBreadcrumbsJsonLd(String leagueName, String countryName,
                                           String canonicalUrl, boolean turkish,
                                           String baseUrl) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("@context", "https://schema.org");
        root.put("@type", "BreadcrumbList");

        List<Map<String, Object>> items = new ArrayList<>();
        items.add(breadcrumbItem(1, turkish ? "Ana Sayfa" : "Home", baseUrl + "/"));
        if (countryName != null && !countryName.isBlank()) {
            items.add(breadcrumbItem(2, countryName, baseUrl + "/"));
            items.add(breadcrumbItem(3, leagueName, canonicalUrl));
        } else {
            items.add(breadcrumbItem(2, leagueName, canonicalUrl));
        }
        root.put("itemListElement", items);

        try {
            return JSON_LD_MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            log.warn("Basketbol lig breadcrumb JSON-LD serilestirilemedi: {}",
                    ex.getMessage());
            return "{}";
        }
    }

    private static Map<String, Object> breadcrumbItem(int pos, String name, String url) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("@type", "ListItem");
        m.put("position", pos);
        m.put("name", name);
        m.put("item", url);
        return m;
    }

    private static String displayLeagueName(BasketballLeague league, boolean turkish) {
        if (turkish && league.getNameTr() != null && !league.getNameTr().isBlank()) {
            return league.getNameTr();
        }
        return league.getName();
    }

    private static String displayCountryName(BasketballLeague league, boolean turkish) {
        if (turkish && league.getCountryNameTr() != null
                && !league.getCountryNameTr().isBlank()) {
            return league.getCountryNameTr();
        }
        return league.getCountryName();
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
