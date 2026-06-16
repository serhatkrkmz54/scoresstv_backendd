package com.scorestv.highlights.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Highlightly {@code /highlights} yanıtındaki tek highlight nesnesi
 * (yalnız kullandığımız alanlar; geri kalan — match, vb. — yok sayılır).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HighlightlyHighlightDto(
        Long id,
        String type,        // VERIFIED | UNVERIFIED
        String imgUrl,      // önizleme görseli
        String title,
        String description,
        String url,         // video sayfası
        String embedUrl,    // gömme (opsiyonel)
        String channel,
        String source       // youtube | twitter | ...
) {}
