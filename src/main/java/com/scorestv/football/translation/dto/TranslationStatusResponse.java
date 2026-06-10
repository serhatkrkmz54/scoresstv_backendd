package com.scorestv.football.translation.dto;

import java.util.List;

/**
 * Her varlık tipi için Türkçe çeviri ilerlemesi.
 */
public record TranslationStatusResponse(
        List<TypeStatus> types
) {

    /**
     * Tek bir tipin çeviri durumu.
     *
     * @param type         tip yolu ("countries", ...)
     * @param label        kullanıcıya gösterilen etiket ("Ülkeler", ...)
     * @param total        toplam kayıt sayısı
     * @param translated   Türkçe adı girilmiş kayıt sayısı
     * @param untranslated henüz çevrilmemiş kayıt sayısı
     */
    public record TypeStatus(
            String type,
            String label,
            long total,
            long translated,
            long untranslated
    ) {
    }
}
