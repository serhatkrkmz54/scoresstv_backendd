package com.scorestv.football.web.dto;

import java.io.Serializable;

/**
 * Maç detayında tek bir istatistik satırının karşılaştırmalı görünümü
 * (ev sahibi vs deplasman).
 *
 * <p>Değerler String — API Integer/String/null karışık döner ("3", "32%",
 * null). Frontend ihtiyaca göre parse eder: "32%" → progress bar, "3" → sayı.
 *
 * @param type     Ham API stat etiketi: "Shots on Goal", "Ball Possession", ...
 *                 Stable key — frontend grup/sıralama mantığı bunu kullanır.
 * @param typeText Dile göre çevrilmiş etiket: "İsabetli Şut" / "Shots on Goal".
 *                 Frontend doğrudan UI'da bunu gösterir.
 * @param home     Ev sahibinin değeri; bilinmiyorsa null
 * @param away     Deplasmanın değeri; bilinmiyorsa null
 */
public record StatisticView(
        String type,
        String typeText,
        String home,
        String away
) implements Serializable {
}
