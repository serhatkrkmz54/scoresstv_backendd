package com.scorestv.football.web.dto;

import java.io.Serializable;

/**
 * Maç detayı zaman çizelgesindeki tek bir olay — gol, kart, oyuncu değişikliği,
 * VAR.
 *
 * <p>Frontend takım adını {@code teamId}'den (maç detay yanıtındaki home/away
 * üzerinden) çözer. {@code type}/{@code detail} API'nin ham İngilizce değerleri;
 * {@code typeText}/{@code detailText} ise istek diline çevrilmiş hali —
 * frontend istediğini kullanabilir.
 *
 * @param elapsed    Maç dakikası
 * @param extra      Uzatma dakikası (90+3 → 3); yoksa null
 * @param teamId     Olayın takımı
 * @param type       Ham üst tip (Goal / Card / Subst / Var)
 * @param typeText   Dile çevrilmiş üst tip ("Gol" / "Goal", ...)
 * @param detail     Ham alt tip (Normal Goal, Yellow Card, ...)
 * @param detailText Dile çevrilmiş alt tip ("Gol" / "Sarı Kart", ...)
 * @param comments   Ek açıklama; yoksa null
 * @param playerId   İlgili oyuncu id'si
 * @param playerName İlgili oyuncu adı
 * @param assistId   Asist yapan / oyuna giren oyuncu id'si; yoksa null
 * @param assistName Asist / oyuna giren oyuncu adı; yoksa null
 */
public record EventSummary(
        Integer elapsed,
        Integer extra,
        Long teamId,
        String type,
        String typeText,
        String detail,
        String detailText,
        String comments,
        Long playerId,
        String playerName,
        Long assistId,
        String assistName
) implements Serializable {
}
