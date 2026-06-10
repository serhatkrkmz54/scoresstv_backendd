package com.scorestv.football.translation.dto;

/**
 * Tek bir varlığın Türkçe adını güncelleme isteği.
 *
 * @param nameTr yeni Türkçe ad; boş/null verilirse mevcut çeviri temizlenir
 *               (name_tr = null). Azami uzunluk tipe göre servis katmanında
 *               doğrulanır.
 */
public record UpdateTranslationRequest(
        String nameTr
) {
}
