package com.scorestv.football.translation;

/**
 * Yüklenen Excel dosyasından okunan tek bir düzenleme satırı.
 *
 * @param id        varlık id'si (0. kolon)
 * @param nameTr    girilen Türkçe ad (2. kolon); boş olabilir
 * @param excelRow  Excel'deki 1 tabanlı satır numarası — hata mesajları içindir
 */
public record RowEdit(long id, String nameTr, int excelRow) {
}
