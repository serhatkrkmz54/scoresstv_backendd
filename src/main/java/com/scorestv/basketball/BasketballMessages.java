package com.scorestv.basketball;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * API-Basketball'dan gelen sabit sozcuk kumelerini (mac durumu, ceyrek
 * etiketi vs.) hedef dile cevirir. Futbol {@code FootballMessages}'in
 * basketbol esi.
 *
 * <p>Anahtarlar {@code messages_tr.properties} / {@code messages_en.properties}
 * icinde {@code basketball.status.X} bicimindedir. Eksik anahtarlar icin
 * fallback ham deger doner — yeni API kodu eklendiginde site calismaya devam
 * eder.
 */
@Component
public class BasketballMessages {

    private static final Locale TURKISH = Locale.of("tr");
    private static final Locale ENGLISH = Locale.ENGLISH;

    private final MessageSource messageSource;

    public BasketballMessages(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    private static Locale locale(boolean turkish) {
        return turkish ? TURKISH : ENGLISH;
    }

    /**
     * Mac durumunun dile cevrilmis kisa label'i.
     *
     * @param statusShort API durum kodu (NS, Q1, FT, ...)
     * @param englishLong API'den gelen uzun ad — yedek deger
     * @param turkish     true ise TR, aksi halde EN
     */
    public String statusText(String statusShort, String englishLong, boolean turkish) {
        if (statusShort == null || statusShort.isBlank()) return englishLong;
        return messageSource.getMessage(
                "basketball.status." + statusShort.trim(),
                null,
                englishLong != null ? englishLong : statusShort,
                locale(turkish));
    }
}
