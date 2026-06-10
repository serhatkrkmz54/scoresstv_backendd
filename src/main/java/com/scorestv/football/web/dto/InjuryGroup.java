package com.scorestv.football.web.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Bir takımın o maça katılamayacak / şüpheli oyuncuları.
 * Frontend iki kolon (home / away) olarak render eder.
 *
 * @param teamId  Hangi takımın listesi
 * @param players Oyuncular — boş olabilir (sakatlık yok)
 */
public record InjuryGroup(
        Long teamId,
        List<InjuryView> players
) implements Serializable {

    /**
     * @param playerId   API-Football player id
     * @param playerName Oyuncu adı
     * @param photo      Oyuncu fotosu URL'si
     * @param type       Ham tip: "Missing Fixture" / "Questionable"
     * @param typeText   Dile çevrilmiş tip ("Maça Çıkamayacak" / "Şüpheli")
     * @param reason     Ham sebep: "Thigh Injury", "Red Card", "Coach Decision" ...
     * @param reasonText Dile çevrilmiş sebep ("Adale Sakatlığı", "Kırmızı Kart" ...)
     */
    public record InjuryView(
            Long playerId,
            String playerName,
            String photo,
            String type,
            String typeText,
            String reason,
            String reasonText
    ) implements Serializable {
    }
}
