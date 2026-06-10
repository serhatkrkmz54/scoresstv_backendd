package com.scorestv.football.seo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scorestv.common.SlugUtil;
import com.scorestv.football.domain.Player;
import com.scorestv.football.web.dto.PlayerDetailResponse;
import com.scorestv.football.web.dto.PlayerSeoResponse;
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
 * Oyuncu detay sayfasi icin tam SEO paketi ({@link PlayerSeoResponse}).
 * URL: TR {@code /oyuncu/{slug}}, EN {@code /player/{slug}}.
 */
@Component
public class PlayerDetailSeoBuilder {

    private static final Logger log = LoggerFactory.getLogger(PlayerDetailSeoBuilder.class);

    private static final Locale TURKISH = Locale.of("tr");
    private static final Locale ENGLISH = Locale.ENGLISH;
    private static final String SITE_NAME = "ScoresTV";

    private static final ObjectMapper JSON_LD_MAPPER = new ObjectMapper();

    private final MessageSource messageSource;
    private final SeoProperties seoProperties;
    private final MinioStorageService storage;

    public PlayerDetailSeoBuilder(MessageSource messageSource,
                                  SeoProperties seoProperties,
                                  MinioStorageService storage) {
        this.messageSource = messageSource;
        this.seoProperties = seoProperties;
        this.storage = storage;
    }

    public PlayerSeoResponse build(Player player, String displayName,
                                   PlayerDetailResponse.TeamRef currentTeam,
                                   Integer selectedSeason, String lang) {
        boolean turkish = "tr".equalsIgnoreCase(lang);
        Locale locale = turkish ? TURKISH : ENGLISH;
        String localeCode = turkish ? "tr" : "en";

        String slug = SlugUtil.playerSlug(
                player.getFirstname(), player.getLastname(), displayName, player.getId());
        String baseUrl = trimTrailingSlash(seoProperties.siteUrl());
        String pathPrefix = turkish ? "/oyuncu/" : "/player/";
        String canonicalUrl = baseUrl + pathPrefix + slug;

        String teamName = currentTeam != null ? currentTeam.name() : "";
        String seasonLabel = formatSeason(selectedSeason);
        Object[] args = {displayName, teamName, seasonLabel};

        String title = messageSource.getMessage("seo.player.title", args, locale);
        String description = messageSource.getMessage("seo.player.description", args, locale);
        String keywords = messageSource.getMessage("seo.player.keywords", args, locale);

        String image = player.getPhotoKey() != null
                ? storage.publicUrl(player.getPhotoKey())
                : player.getPhotoUrl();

        PlayerSeoResponse.OpenGraph og = new PlayerSeoResponse.OpenGraph(
                title, description, "profile", canonicalUrl, image,
                SITE_NAME, turkish ? "tr_TR" : "en_US");

        PlayerSeoResponse.TwitterCard twitter = new PlayerSeoResponse.TwitterCard(
                "summary_large_image", title, description, image);

        String jsonLd = buildJsonLd(player, displayName, canonicalUrl, image);

        List<PlayerSeoResponse.Breadcrumb> breadcrumbs = buildBreadcrumbs(
                displayName, currentTeam, canonicalUrl, baseUrl, turkish);

        List<PlayerSeoResponse.Hreflang> hreflang = List.of(
                new PlayerSeoResponse.Hreflang("tr", baseUrl + "/oyuncu/" + slug),
                new PlayerSeoResponse.Hreflang("en", baseUrl + "/player/" + slug),
                new PlayerSeoResponse.Hreflang("x-default", baseUrl + "/player/" + slug));

        return new PlayerSeoResponse(
                title, description, keywords, canonicalUrl, slug, localeCode,
                og, twitter, jsonLd, breadcrumbs, hreflang);
    }

    /** Schema.org Person JSON-LD. */
    private String buildJsonLd(Player player, String displayName,
                                String canonicalUrl, String image) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("@context", "https://schema.org");
        root.put("@type", "Person");
        root.put("name", displayName);
        root.put("url", canonicalUrl);
        if (image != null) {
            root.put("image", image);
        }
        if (player.getBirthDate() != null) {
            root.put("birthDate", player.getBirthDate().toString());
        }
        if (player.getNationality() != null && !player.getNationality().isBlank()) {
            root.put("nationality", player.getNationality());
        }
        if (player.getHeight() != null && !player.getHeight().isBlank()) {
            root.put("height", player.getHeight());
        }
        try {
            return JSON_LD_MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            log.warn("Player JSON-LD serilesirilemedi: playerId={} — {}",
                    player.getId(), ex.getMessage());
            return "{}";
        }
    }

    /** Breadcrumb zinciri: Ana Sayfa › (Mevcut Takim) › Oyuncu. */
    private List<PlayerSeoResponse.Breadcrumb> buildBreadcrumbs(
            String playerName, PlayerDetailResponse.TeamRef currentTeam,
            String canonicalUrl, String baseUrl, boolean turkish) {
        List<PlayerSeoResponse.Breadcrumb> list = new ArrayList<>();
        list.add(new PlayerSeoResponse.Breadcrumb(
                1, turkish ? "Ana Sayfa" : "Home", baseUrl + "/"));
        if (currentTeam != null) {
            String teamPath = (turkish ? "/takim/" : "/team/") + currentTeam.slug();
            list.add(new PlayerSeoResponse.Breadcrumb(2, currentTeam.name(), baseUrl + teamPath));
            list.add(new PlayerSeoResponse.Breadcrumb(3, playerName, canonicalUrl));
        } else {
            list.add(new PlayerSeoResponse.Breadcrumb(2, playerName, canonicalUrl));
        }
        return list;
    }

    private static String formatSeason(Integer year) {
        if (year == null) return "";
        int next = (year + 1) % 100;
        return year + "-" + String.format(Locale.ROOT, "%02d", next);
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
