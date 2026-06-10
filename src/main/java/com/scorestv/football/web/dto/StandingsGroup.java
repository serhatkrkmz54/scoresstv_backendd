package com.scorestv.football.web.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Puan durumu grup wrapper'ı — gruplu turnuvalarda (Şampiyonlar Ligi, Copa,
 * EURO vs.) takımları gruplara böler. Tek-gruplu liglerde tek eleman gelir.
 *
 * <p>API-Football {@code league.standings} alanı zaten group-bazlı dizi
 * döner; biz bu yapıyı koruyup frontend'e direkt iletiyoruz.
 *
 * @param groupName     Ham grup adı (örn. "Group A")
 * @param groupNameText Dile çevrilmiş ad (örn. "Grup A" / "Group A")
 * @param rows          Grup içindeki takım satırları, sıraya göre (1 → N)
 */
public record StandingsGroup(
        String groupName,
        String groupNameText,
        List<StandingRow> rows
) implements Serializable {
}
