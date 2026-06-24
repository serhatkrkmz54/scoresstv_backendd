package com.scorestv.volleyball;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * API-Volleyball'dan gelen sabit sozcuk kumelerini (mac durumu vs.) hedef dile
 * cevirir. Basketbol {@code BasketballMessages}'in voleybol esi.
 *
 * <p>Anahtarlar {@code messages_tr.properties} / {@code messages_en.properties}
 * icinde {@code volleyball.status.X} bicimindedir. Eksik anahtarlar icin fallback
 * ham deger doner.
 */
@Component
public class VolleyballMessages {

    private static final Locale TURKISH = Locale.of("tr");
    private static final Locale ENGLISH = Locale.ENGLISH;

    private final MessageSource messageSource;

    public VolleyballMessages(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    private static Locale locale(boolean turkish) {
        return turkish ? TURKISH : ENGLISH;
    }

    /**
     * Mac durumunun dile cevrilmis kisa label'i.
     *
     * @param statusShort API durum kodu (NS, S1, FT, ...)
     * @param englishLong API'den gelen uzun ad — yedek deger
     * @param turkish     true ise TR, aksi halde EN
     */
    public String statusText(String statusShort, String englishLong, boolean turkish) {
        if (statusShort == null || statusShort.isBlank()) return englishLong;
        return messageSource.getMessage(
                "volleyball.status." + statusShort.trim(),
                null,
                englishLong != null ? englishLong : statusShort,
                locale(turkish));
    }
}
