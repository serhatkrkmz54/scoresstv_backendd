package com.scorestv.sitemap;

import java.time.Instant;

/**
 * Tek bir varlik icin iki dildeki sitemap path'i (site-koku'ne gore relatif) +
 * son guncelleme. Frontend bunlardan EN ve TR url'lerini + hreflang alternatif
 * baglantilarini uretir.
 *
 * <p>Ornek (takim): enPath="/team/argentina-26", trPath="/takim/arjantin-26".
 */
public record SitemapEntry(String enPath, String trPath, Instant lastmod) {}
