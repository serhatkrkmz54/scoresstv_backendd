package com.scorestv.football.web.dto;

import java.io.Serializable;

/**
 * Puan durumu tablosunda tek bir takım satırı.
 *
 * @param rank            Sıradaki yer (1 = lider)
 * @param description     API'nin ham etiketi (örn. "Promotion - Champions League (Group Stage)")
 * @param descriptionText Dile çevrilmiş etiket (örn. "Şampiyonlar Ligi (Grup)" / "Champions League (Group)")
 */
public record StandingRow(
        Integer rank,
        Long teamId,
        String teamName,
        String teamLogo,
        /** Takım detay slug'i ({@code besiktas-549}). Frontend /team/ veya /takim/ ekler. */
        String teamSlug,
        Integer points,
        Integer goalsDiff,
        String form,
        String description,
        String descriptionText,
        Integer played,
        Integer win,
        Integer draw,
        Integer lose,
        Integer goalsFor,
        Integer goalsAgainst
) implements Serializable {
}
