package com.scorestv.football.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * API-Football {@code GET /fixtures?ids=A-B-C} (ya da tek {@code ?id=}) yanıtındaki
 * <b>zengin</b> maç öğesi. Tekil/çoklu id sorgusunda API, her maç için
 * {@code events}, {@code lineups}, {@code statistics} ve {@code players}'ı
 * <b>gömülü</b> döndürür — böylece 20 maçın tüm canlı detayı <b>tek istekte</b>
 * gelir (ayrı {@code /fixtures/events|statistics|players} çağrılarına gerek yok).
 *
 * <p>Bu DTO yalnızca canlı detay batch'inin ({@code LiveDetailBatchService})
 * kullandığı alanları modeller: fixture (id/durum) + events + statistics +
 * players. {@code lineups} bilerek atlanır — kadro (ve "kadro açıklandı"
 * bildirimi) maç ÖNCESİ {@code ImminentLineupsJob} ile yönetilir; canlı
 * batch'te tekrar upsert etmek re-notify riski taşır. Bilinmeyen alanlar
 * {@code @JsonIgnoreProperties} ile yok sayılır.
 *
 * <p><b>Coverage notu:</b> düşük-coverage ligler için {@code statistics} ve
 * {@code players} boş dizi gelir (ayrı uçlar da aynı boşu döndürürdü). Batch
 * boş dizide upsert'i atlar → mevcut veri KORUNUR (silinmez).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FixtureBundleApiDto(
        FixtureApiDto.Fixture fixture,
        List<EventApiDto> events,
        List<StatisticApiDto> statistics,
        List<PlayerStatApiDto> players
) {}
