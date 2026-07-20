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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Oyuncu detay sayfasi icin tam SEO paketi ({@link PlayerSeoResponse}).
 * URL: TR {@code /oyuncu/{slug}}, EN {@code /player/{slug}}.
 */
@Component
public class PlayerDetailSeoBuilder {

    private static final Logger log = LoggerFactory.getLogger(PlayerDetailSeoBuilder.class);

    private static final Locale TURKISH = Locale.of("tr");
    private static final Locale ENGLISH = Locale.ENGLISH;
    private static final String SITE_NAME = "Scores TV";

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

        List<PlayerSeoResponse.Breadcrumb> breadcrumbs = buildBreadcrumbs(
                displayName, currentTeam, canonicalUrl, baseUrl, turkish);

        String fullName = fullName(player);
        String teamUrl = currentTeam != null
                ? baseUrl + (turkish ? "/takim/" : "/team/") + currentTeam.slug()
                : null;
        String jsonLd = buildJsonLd(player, displayName, fullName, currentTeam,
                teamUrl, canonicalUrl, image, breadcrumbs);

        List<PlayerSeoResponse.Hreflang> hreflang = List.of(
                new PlayerSeoResponse.Hreflang("tr", baseUrl + "/oyuncu/" + slug),
                new PlayerSeoResponse.Hreflang("en", baseUrl + "/player/" + slug),
                new PlayerSeoResponse.Hreflang("x-default", baseUrl + "/player/" + slug));

        return new PlayerSeoResponse(
                title, description, keywords, canonicalUrl, slug, localeCode,
                og, twitter, jsonLd, breadcrumbs, hreflang);
    }

    /**
     * Oyuncu detay sayfasi JSON-LD'si — {@code [Person, BreadcrumbList]} dizisi.
     *
     * <p>NOT: schema.org'da resmi bir "SportsPlayer" tipi YOKTUR; sporcular icin
     * gecerli ve Google'in tanidigi tip {@code Person}'dir. Bu yuzden zengin
     * alanlar (tam ad, kisa ad, dogum tarihi/yeri, milliyet, boy, kilo, mevcut
     * takim) gecerli Person semasi altinda verilir. BreadcrumbList ayni dizide
     * ikinci nesne olarak gomulur (tek {@code <script type=ld+json>}).
     */
    private String buildJsonLd(Player player, String displayName, String fullName,
                                PlayerDetailResponse.TeamRef currentTeam, String teamUrl,
                                String canonicalUrl, String image,
                                List<PlayerSeoResponse.Breadcrumb> crumbs) {
        Map<String, Object> person = new LinkedHashMap<>();
        person.put("@context", "https://schema.org");
        person.put("@type", "Person");
        // Ad: sayfadaki H1 ile ayni (kisa/yaygin ad). Tam ad varsa alternateName.
        person.put("name", displayName);
        if (fullName != null && !fullName.isBlank()
                && !fullName.equalsIgnoreCase(displayName)) {
            person.put("alternateName", fullName);
        }
        person.put("url", canonicalUrl);
        if (image != null) {
            person.put("image", image);
        }
        if (player.getBirthDate() != null) {
            person.put("birthDate", player.getBirthDate().toString());
        }
        // Dogum yeri — Place (ulke varsa PostalAddress ile).
        if (player.getBirthPlace() != null && !player.getBirthPlace().isBlank()) {
            Map<String, Object> place = new LinkedHashMap<>();
            place.put("@type", "Place");
            place.put("name", player.getBirthPlace());
            if (player.getBirthCountry() != null && !player.getBirthCountry().isBlank()) {
                Map<String, Object> addr = new LinkedHashMap<>();
                addr.put("@type", "PostalAddress");
                addr.put("addressCountry", player.getBirthCountry());
                place.put("address", addr);
            }
            person.put("birthPlace", place);
        }
        if (player.getNationality() != null && !player.getNationality().isBlank()) {
            Map<String, Object> nationality = new LinkedHashMap<>();
            nationality.put("@type", "Country");
            nationality.put("name", player.getNationality());
            person.put("nationality", nationality);
        }
        Map<String, Object> height = quantitative(player.getHeight());
        if (height != null) {
            person.put("height", height);
        }
        Map<String, Object> weight = quantitative(player.getWeight());
        if (weight != null) {
            person.put("weight", weight);
        }
        // Mevcut takim — SportsTeam (Person.memberOf).
        if (currentTeam != null && currentTeam.name() != null
                && !currentTeam.name().isBlank()) {
            Map<String, Object> team = new LinkedHashMap<>();
            team.put("@type", "SportsTeam");
            team.put("name", currentTeam.name());
            if (teamUrl != null) {
                team.put("url", teamUrl);
            }
            if (currentTeam.logo() != null && !currentTeam.logo().isBlank()) {
                team.put("logo", currentTeam.logo());
            }
            person.put("memberOf", team);
        }

        Map<String, Object> breadcrumbList = breadcrumbListMap(crumbs);

        try {
            return JSON_LD_MAPPER.writeValueAsString(List.of(person, breadcrumbList));
        } catch (JsonProcessingException ex) {
            log.warn("Player JSON-LD serilesirilemedi: playerId={} — {}",
                    player.getId(), ex.getMessage());
            return "{}";
        }
    }

    /** "Firstname Lastname" tam adi; ikisi de bossa null. */
    private static String fullName(Player player) {
        String f = player.getFirstname() == null ? "" : player.getFirstname();
        String l = player.getLastname() == null ? "" : player.getLastname();
        String full = (f + " " + l).trim();
        return full.isBlank() ? null : full;
    }

    /** Sayi + birim iceren string ("188 cm", "1,88 m", "75 kg") → QuantitativeValue. */
    private static final Pattern QUANTITY =
            Pattern.compile("([0-9]+(?:[.,][0-9]+)?)\\s*([a-zA-Z]+)?");

    private static Map<String, Object> quantitative(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher m = QUANTITY.matcher(raw.trim());
        if (!m.find()) {
            return null;
        }
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("@type", "QuantitativeValue");
        q.put("value", m.group(1).replace(',', '.'));
        String unit = m.group(2);
        if (unit != null && !unit.isBlank()) {
            q.put("unitText", unit);
        }
        return q;
    }

    /** Breadcrumb listesinden Schema.org BreadcrumbList map'i uretir. */
    private static Map<String, Object> breadcrumbListMap(
            List<PlayerSeoResponse.Breadcrumb> crumbs) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("@context", "https://schema.org");
        root.put("@type", "BreadcrumbList");
        List<Map<String, Object>> items = new ArrayList<>();
        for (PlayerSeoResponse.Breadcrumb b : crumbs) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("@type", "ListItem");
            item.put("position", b.position());
            item.put("name", b.name());
            item.put("item", b.url());
            items.add(item);
        }
        root.put("itemListElement", items);
        return root;
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
