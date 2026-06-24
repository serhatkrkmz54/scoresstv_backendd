package com.scorestv.volleyball.web.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Voleybol lig detay sayfasi SEO paketi — OG/Twitter/JSON-LD/hreflang.
 *
 * <p>{@code VolleyballLeagueDetailSeoBuilder} tarafindan doldurulur. Frontend
 * head section'a yerlestirir (mobile webview, web SSR). Basketboldaki
 * {@code BasketballLeagueSeoResponse}'in voleybol esi.
 */
public record VolleyballLeagueSeoResponse(
        String title,
        String description,
        String canonical,
        String ogTitle,
        String ogDescription,
        String ogImage,
        /** SportsOrganization JSON-LD payload. */
        String jsonLd,
        /** BreadcrumbList JSON-LD payload. */
        String breadcrumbsJsonLd,
        List<HreflangAlt> hreflang
) implements Serializable {

    public record HreflangAlt(String lang, String url) implements Serializable {}
}
