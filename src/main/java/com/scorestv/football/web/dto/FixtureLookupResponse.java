package com.scorestv.football.web.dto;

/**
 * Fikstür lookup yanıtı — bir takım aramasına göre, verilen referans tarihe
 * göre SIRADAKI ve ÖNCEKİ maç.
 *
 * <p><b>Kullanım:</b> Anasayfa (mobil/web) gün bazlı filtrede, aranan takımın
 * o gün maçı yoksa "sıradaki maç 5 Tem →" önerisi göstermek için. İstemci
 * {@code resolved} ile hangi takımın çözüldüğünü, {@code next}/{@code previous}
 * ile önerilecek maçları alır.
 *
 * <p><b>Boş durumlar:</b> Arama metni bir takıma çözülemezse {@code resolved}
 * {@code null} döner (HTTP 200, hata değil). Takımın referans andan sonra/önce
 * maçı yoksa ilgili {@code next}/{@code previous} alanı {@code null} olur.
 *
 * <p><b>Maç DTO'su:</b> {@code next}/{@code previous}, anasayfa listesinde
 * kullanılan KANONİK {@link FixtureSummary} tipidir — istemciler zaten bu tipi
 * parse eder, yeni bir maç DTO'su icat edilmez.
 *
 * @param resolved çözülen takım (yoksa null)
 * @param next     referans andan sonraki ilk maç (yoksa null)
 * @param previous referans andan önceki son maç (yoksa null)
 */
public record FixtureLookupResponse(
        Resolved resolved,
        FixtureSummary next,
        FixtureSummary previous
) {

    /**
     * Aramanın çözüldüğü varlık. Bu turda yalnız {@code type = "team"}
     * desteklenir (lig/ülke desteklenmez — bkz. controller Javadoc).
     *
     * @param type    varlık tipi ("team")
     * @param id      varlık id'si
     * @param name    görünen ad (dile göre TR/EN)
     * @param logoUrl logo/görsel URL'si (yoksa null)
     */
    public record Resolved(
            String type,
            Long id,
            String name,
            String logoUrl
    ) {
    }
}
