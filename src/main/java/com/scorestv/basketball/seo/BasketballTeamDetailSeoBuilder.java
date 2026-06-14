package com.scorestv.basketball.seo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scorestv.basketball.web.dto.BasketballTeamDetailResponse;
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
 * Basketbol takim detay sayfasi icin SEO paketi (OpenGraph + Twitter Card +
 * JSON-LD SportsTeam + Breadcrumb + hreflang).
 *
 * <p>URL pattern:
 * <ul>
 *   <li>TR: {@code /basketbol/takim/{slug}}
 *   <li>EN: {@code /basketball/team/{slug}}
 * </ul>
 *
 * <p>Yanit Map olarak doner — controller Map<String, Object> bekler. Frontend
 * sayfa head'ini bu yanitla doldurur.
 */
@Component
public class BasketballTeamDetailSeoBuilder {

    private static final Logger log =
            LoggerFactory.getLogger(BasketballTeamDetailSeoBuilder.class);

    private static final Locale TURKISH = Locale.of("tr");
    private static final Locale ENGLISH = Locale.ENGLISH;
    private static final String SITE_NAME = "ScoresTV";

    private static final ObjectMapper JSON_LD_MAPPER = new ObjectMapper();

    private final MessageSource messageSource;
    private final SeoProperties seoProperties;
    private final MinioStorageService storage;

    public BasketballTeamDetailSeoBuilder(MessageSource messageSource,
                                            SeoProperties seoProperties,
                                            MinioStorageService storage) {
        this.messageSource = messageSource;
        this.seoProperties = seoProperties;
        this.storage = storage;
    }

    public Map<String, Object> build(BasketballTeamDetailResponse detail,
                                       boolean turkish) {
        if (detail == null || detail.hero() == null) return Map.of();
        Locale locale = turkish ? TURKISH : ENGLISH;
        var hero = detail.hero();
        String displayName = hero.displayName() != null
                ? hero.displayName() : hero.name();
        String country = hero.countryName() != null ? hero.countryName() : "";

        String slug = hero.slug() != null ? hero.slug() : "";
        String baseUrl = trimTrailingSlash(seoProperties.siteUrl());
        String pathPrefix = turkish ? "/basketbol/takim/" : "/basketball/team/";
        String canonical = baseUrl + pathPrefix + slug;

        Object[] args = {displayName, country};
        String title = messageSource.getMessage(
                "seo.basketball.team.title", args, locale);
        String description = messageSource.getMessage(
                "seo.basketball.team.description", args, locale);

        String image = hero.logo();

        // JSON-LD SportsTeam
        Map<String, Object> jsonLd = new LinkedHashMap<>();
        jsonLd.put("@context", "https://schema.org");
        jsonLd.put("@type", "SportsTeam");
        jsonLd.put("name", displayName);
        jsonLd.put("alternateName", hero.name());
        jsonLd.put("sport", "Basketball");
        jsonLd.put("url", canonical);
        if (image != null) jsonLd.put("logo", image);
        if (hero.founded() != null) jsonLd.put("foundingDate", hero.founded().toString());
        if (country != null && !country.isBlank()) {
            Map<String, Object> nat = new LinkedHashMap<>();
            nat.put("@type", "Country");
            nat.put("name", country);
            jsonLd.put("nationality", nat);
        }
        if (hero.venueName() != null) {
            Map<String, Object> venue = new LinkedHashMap<>();
            venue.put("@type", "Place");
            venue.put("name", hero.venueName());
            if (hero.venueCity() != null) venue.put("address", hero.venueCity());
            jsonLd.put("location", venue);
        }

        // Breadcrumb
        Map<String, Object> breadcrumb = new LinkedHashMap<>();
        breadcrumb.put("@context", "https://schema.org");
        breadcrumb.put("@type", "BreadcrumbList");
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(breadcrumbItem(1, SITE_NAME, baseUrl));
        items.add(breadcrumbItem(2,
                turkish ? "Basketbol" : "Basketball",
                baseUrl + (turkish ? "/basketbol" : "/basketball")));
        items.add(breadcrumbItem(3, displayName, canonical));
        breadcrumb.put("itemListElement", items);

        // hreflang
        List<Map<String, String>> hreflang = List.of(
                Map.of("hreflang", "tr",
                        "href", baseUrl + "/basketbol/takim/" + slug),
                Map.of("hreflang", "en",
                        "href", baseUrl + "/basketball/team/" + slug),
                Map.of("hreflang", "x-default",
                        "href", baseUrl + "/basketball/team/" + slug)
        );

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("title", title);
        out.put("description", description);
        out.put("canonical", canonical);
        out.put("image", image);
        out.put("openGraph", buildOg(title, description, canonical, image, displayName));
        out.put("twitter", buildTwitter(title, description, image));
        out.put("jsonLd", serializeJson(jsonLd));
        out.put("breadcrumbJsonLd", serializeJson(breadcrumb));
        out.put("hreflang", hreflang);
        return out;
    }

    private Map<String, Object> buildOg(String title, String desc, String url,
                                          String image, String displayName) {
        Map<String, Object> og = new LinkedHashMap<>();
        og.put("og:title", title);
        og.put("og:description", desc);
        og.put("og:url", url);
        og.put("og:type", "website");
        og.put("og:site_name", SITE_NAME);
        if (image != null) og.put("og:image", image);
        og.put("og:locale", "tr_TR");
        return og;
    }

    private Map<String, Object> buildTwitter(String title, String desc, String image) {
        Map<String, Object> tw = new LinkedHashMap<>();
        tw.put("twitter:card", "summary_large_image");
        tw.put("twitter:title", title);
        tw.put("twitter:description", desc);
        if (image != null) tw.put("twitter:image", image);
        return tw;
    }

    private Map<String, Object> breadcrumbItem(int position, String name, String url) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("@type", "ListItem");
        item.put("position", position);
        item.put("name", name);
        item.put("item", url);
        return item;
    }

    private String serializeJson(Object o) {
        try {
            return JSON_LD_MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            log.debug("JSON-LD serialize hata: {}", e.toString());
            return "{}";
        }
    }

    private String trimTrailingSlash(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
