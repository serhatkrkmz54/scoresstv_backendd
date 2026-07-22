package com.scorestv.volleyball;

import com.scorestv.football.FootballMessages;
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
    /**
     * Puan durumu açıklaması / grup adı / lig türü çevirileri futbolla ORTAKTIR
     * (spordan bağımsız). Aynı sözlük + parser + DeepL cache'i paylaşmak için
     * {@link FootballMessages}'a delege ederiz — tek kaynak, tam parite.
     */
    private final FootballMessages footballMessages;

    public VolleyballMessages(MessageSource messageSource, FootballMessages footballMessages) {
        this.messageSource = messageSource;
        this.footballMessages = footballMessages;
    }

    private static Locale locale(boolean turkish) {
        return turkish ? TURKISH : ENGLISH;
    }

    /** Puan durumu açıklaması — TR sözlük + DeepL (futbolla ortak). */
    public String standingDescription(String description, boolean turkish) {
        return footballMessages.standingDescription(description, turkish);
    }

    /** Grup adı ("Group A"→"Grup A", serbest adlar DeepL) — futbolla ortak. */
    public String standingGroupName(String groupName, boolean turkish) {
        return footballMessages.standingGroupName(groupName, turkish);
    }

    /** Lig türü ("League"→"Lig", "Cup"→"Kupa", diğerleri DeepL) — futbolla ortak. */
    public String leagueType(String type, boolean turkish) {
        return footballMessages.leagueType(type, turkish);
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
