package com.scorestv.football.web.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Takim detay sayfasi icin tam SEO paketi. {@link LeagueSeoResponse} ile ayni
 * yapida — frontend SSR'da head meta'larini bu yanitan uretir.
 *
 * <p>Hreflang ozellikle onemli: TR url /takim/{slug}, EN url /team/{slug}
 * farkli oldugu icin hreflang ile birbirine isaret edilir.
 */
public record TeamSeoResponse(
        String title,
        String description,
        String keywords,
        String canonicalUrl,
        String slug,
        /** Aktif dil ("tr" veya "en"). */
        String locale,
        OpenGraph openGraph,
        TwitterCard twitter,
        /** {@code application/ld+json} olarak embed edilecek SportsTeam JSON metni. */
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
