package com.scorestv.news.dto;

/**
 * Haber cevirisi istegi (EDITOR/ADMIN). Kaynak dilden hedef dile baslik/ozet/
 * govde cevrilir. sourceLang null ise otomatik dil algilama kullanilir.
 * body HTML'dir; sunucu tag-korumali ceviri yapar.
 */
public record TranslateNewsRequest(
        String title,
        String summary,
        String body,
        String sourceLang,
        String targetLang
) {
}
