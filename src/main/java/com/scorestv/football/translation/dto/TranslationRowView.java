package com.scorestv.football.translation.dto;

/**
 * Tek bir varlığın çeviri durumu — tekil güncelleme yanıtı.
 *
 * @param type   tip yolu ("teams", ...)
 * @param id     varlık id'si
 * @param name   İngilizce kaynak ad
 * @param nameTr Türkçe ad; girilmemişse null
 */
public record TranslationRowView(
        String type,
        long id,
        String name,
        String nameTr
) {
}
