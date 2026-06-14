package com.scorestv.basketball;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code /players?team=X&season=Y} yanit ogesi: bir takimin sezon kadrosundaki
 * oyuncu.
 *
 * <p>API ornek:
 * <pre>{
 *   "id": 3540,
 *   "name": "Baldwin Wade",
 *   "number": "5",
 *   "country": "USA",
 *   "position": "Guard",
 *   "age": 27
 * }</pre>
 *
 * <p>{@code /players?id=X} ({@link BkPlayerDto}) cevabindan tamamen farkli bir
 * yapi — bu yanitta {@code name} tek alanda, {@code number} string, ek field
 * yok. Tasarim: bu DTO yalniz kadro listeleme amacli; tam profil icin yine
 * {@link BkPlayerDto} kullanilir.
 *
 * @param id        API player id
 * @param name      "Soyad Ad" formatinda (orn. "Baldwin Wade", "Birsen James")
 * @param number    forma numarasi (String — bazen null)
 * @param country   uyruk (orn. "USA", "Turkey") — null olabilir
 * @param position  oyuncu pozisyonu ("Guard", "Forward", "Center")
 * @param age       yas (int) — null olabilir
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BkRosterPlayerDto(
        Long id,
        String name,
        String number,
        String country,
        String position,
        Integer age
) {}
