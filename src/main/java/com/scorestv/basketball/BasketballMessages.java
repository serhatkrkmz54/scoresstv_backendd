package com.scorestv.basketball;

import com.scorestv.football.FootballMessages;
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
    /**
     * Puan durumu açıklaması / grup adı / lig türü çevirileri futbolla
     * ORTAKTIR (spordan bağımsız: "Playoffs"→"Play-off", "Group A"→"Grup A",
     * "Cup"→"Kupa"). Aynı sözlük + parser + DeepL cache'i paylaşmak için
     * {@link FootballMessages}'a delege ederiz — tek kaynak, tam parite.
     */
    private final FootballMessages footballMessages;

    public BasketballMessages(MessageSource messageSource, FootballMessages footballMessages) {
        this.messageSource = messageSource;
        this.footballMessages = footballMessages;
    }

    private static Locale locale(boolean turkish) {
        return turkish ? TURKISH : ENGLISH;
    }

    /** Puan durumu açıklaması (Playoffs / Promotion / Relegation ...) — TR + DeepL. */
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
