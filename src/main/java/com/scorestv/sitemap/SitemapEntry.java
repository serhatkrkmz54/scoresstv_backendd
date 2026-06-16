package com.scorestv.sitemap;

import java.time.Instant;

/**
 * Tek bir sitemap girdisi: site-koku'ne gore relatif path + son guncelleme.
 * Frontend (Next) bunlari SITE_URL ile birlestirip XML uretir.
 */
public record SitemapEntry(String path, Instant lastmod) {}
