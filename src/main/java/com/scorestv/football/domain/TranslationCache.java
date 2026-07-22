package com.scorestv.football.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Otomatik makine-çevirisi (DeepL) sonuç cache'i (bkz. V89 migration).
 *
 * <p>API-Football'dan İngilizce gelen sabit metinlerin
 * ({@code FootballMessages} 3. katmanına düşen: sakatlık sebebi, puan durumu
 * açıklaması, istatistik adı, round, transfer türü...) TR karşılığı DeepL ile
 * bir kez çevrilip burada saklanır. Anahtar: (category, sourceText, targetLang).
 *
 * <p>Salt-veri; iş mantığı {@code AutoTranslateService}'te.
 */
@Entity
@Table(name = "translation_cache")
@Getter
@Setter
@NoArgsConstructor
public class TranslationCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Metin sınıfı: "injury_reason", "standing_desc", "statistic_type"... */
    @Column(nullable = false, length = 40)
    private String category;

    /** Ham İngilizce kaynak (trim'li). */
    @Column(name = "source_text", nullable = false, length = 500)
    private String sourceText;

    /** Hedef dil kodu — şimdilik yalnız "tr". */
    @Column(name = "target_lang", nullable = false, length = 5)
    private String targetLang;

    /** Çevrilmiş metin. */
    @Column(nullable = false, length = 500)
    private String translated;

    /** Çeviri sağlayıcısı — "deepl". */
    @Column(nullable = false, length = 20)
    private String provider = "deepl";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
