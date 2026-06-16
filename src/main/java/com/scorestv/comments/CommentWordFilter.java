package com.scorestv.comments;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Yorum kelime filtresi (web + mobil — ikisi de bu servisten gecer).
 *
 * <p>Yasakli kelimeler {@code COMMENT_BANNED_WORDS} env'inden (virgulle ayrilmis)
 * okunur — KOD DEGISMEDEN .env'i guncelleyip backend'i restart edince yeni liste
 * gecerli olur.
 *
 * <p>Eslestirme normalize edilmis metin uzerinde substring kontroludur:
 * kucuk harf + Turkce karakter sadelestirme (ş→s, ı→i, ...) + aksan temizleme +
 * harf/rakam disi her seyi atma. Boylece "S.A.L.A.K", "Salak!", "ŞaLaK", "s a l a k"
 * gibi kacirma denemeleri de yakalanir. Yanlis pozitifi onlemek icin listeye
 * AYIRT EDICI (3+ harf) kelimeler gir.
 */
@Component
public class CommentWordFilter {

    private final Set<String> banned;

    public CommentWordFilter(
            @Value("${scorestv.comments.banned-words:${COMMENT_BANNED_WORDS:}}") String raw) {
        this.banned = (raw == null || raw.isBlank())
                ? Set.of()
                : Arrays.stream(raw.split(","))
                        .map(CommentWordFilter::normalize)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toUnmodifiableSet());
    }

    /** Metin yasakli kelime iceriyor mu? */
    public boolean containsBanned(String text) {
        if (text == null || banned.isEmpty()) return false;
        String n = normalize(text);
        if (n.isEmpty()) return false;
        for (String w : banned) {
            if (n.contains(w)) return true;
        }
        return false;
    }

    private static String normalize(String s) {
        String x = s.toLowerCase(Locale.forLanguageTag("tr"));
        x = x.replace('ı', 'i').replace('İ', 'i')
                .replace('ş', 's').replace('ğ', 'g')
                .replace('ü', 'u').replace('ö', 'o').replace('ç', 'c');
        x = Normalizer.normalize(x, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return x.replaceAll("[^a-z0-9]", "");
    }
}
