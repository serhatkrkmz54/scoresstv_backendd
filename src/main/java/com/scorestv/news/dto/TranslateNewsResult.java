package com.scorestv.news.dto;

/**
 * Ceviri sonucu — cevrilmis baslik/ozet/govde. Bos alanlar aynen doner.
 * Editor bu degerlerle bagli (translationGroupId) yeni bir dil taslagi acar.
 */
public record TranslateNewsResult(
        String title,
        String summary,
        String body
) {
}
