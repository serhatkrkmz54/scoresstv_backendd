package com.scorestv.football.translation.dto;

import java.util.List;

/**
 * Excel çeviri içe aktarımının özeti.
 *
 * @param type      içe aktarılan tip ("countries", "leagues", ...)
 * @param totalRows dosyadaki veri satırı sayısı (başlık hariç)
 * @param updated   Türkçe adı güncellenen kayıt sayısı
 * @param skipped   atlanan satır sayısı (Türkçe hücre boş veya çok uzun)
 * @param notFound  id'si veritabanında bulunamayan satır sayısı
 * @param errors    kullanıcıya gösterilecek satır bazlı hata mesajları (kırpılmış)
 */
public record TranslationImportResult(
        String type,
        int totalRows,
        int updated,
        int skipped,
        int notFound,
        List<String> errors
) {
}
