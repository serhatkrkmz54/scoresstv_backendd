package com.scorestv.football.translation;

import java.util.List;

/**
 * Excel'e yazılacak çeviri tablosunun tipten bağımsız temsili.
 *
 * <p>{@link TranslationService} tipe özgü kolonları doldurup bunu üretir;
 * {@link TranslationExcelService} ise tipi hiç bilmeden {@code .xlsx}'e çevirir.
 *
 * <p>Kolon düzeni sabittir: 0 = id, 1 = İngilizce ad, 2 = Türkçe ad (düzenlenir),
 * 3+ = bağlam (yalnızca okuma — çevirmene yardımcı bilgi).
 *
 * @param sheetName            Excel sayfa adı
 * @param headers              başlık satırı hücreleri
 * @param rows                 veri satırları (her biri {@code headers} ile aynı uzunlukta)
 * @param editableColumnIndex  kullanıcının dolduracağı kolon (name_tr) indeksi
 */
public record ExportSheet(
        String sheetName,
        List<String> headers,
        List<List<String>> rows,
        int editableColumnIndex
) {
}
