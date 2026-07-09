package com.scorestv.news.dto;

/**
 * Haber bayraklarini HIZLI degistir (kurato­rluk icin). Null olan alan
 * DEGISMEZ; yalniz verilen bayraklar guncellenir. Tam duzenleme gerektirmez.
 */
public record UpdateFlagsRequest(
        Boolean isFeatured,
        Boolean isBreaking,
        Boolean inSlider
) {
}
