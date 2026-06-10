package com.scorestv.football.web.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Lig detay sayfasi icin tam SEO paketi. {@link MatchSeoResponse} ile ayni
 * yapida — ortak base record yerine paralel record (frontend tarafinda ayri
 * model olarak da basit kullanim).
 *
 * <p>Hreflang ozellikle onemli: TR url /lig/{slug}, EN url /league/{slug}
 * farkli oldugu icin hreflang ile birbirine isaret edilir.
 */
public record LeagueSeoResponse(
        String title,
        String description,
        String keywords,
        String canonicalUrl,
        String slug,
        /** Aktif dil ("tr" veya "en"). */
        String locale,
        OpenGraph openGraph,
        TwitterCard twitter,
        /** {@code application/ld+json} olarak embed edilecek SportsLeague JSON metni. */
        String jsonLd,
        List<Breadcrumb> breadcrumbs,
        List<Hreflang> hreflang
) implements Serializable {

    public record OpenGraph(
            String title,
            String description,
            String type,
            String url,
            String image,
            String siteName,
            String locale
    ) implements Serializable {}

    public record TwitterCard(
            String card,
            String title,
            String description,
            String image
    ) implements Serializable {}

    public record Breadcrumb(
            int position,
            String name,
            String url
    ) implements Serializable {}

    public record Hreflang(
            String lang,
            String href
    ) implements Serializable {}
}
