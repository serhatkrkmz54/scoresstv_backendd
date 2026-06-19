package com.scorestv.broadcasts.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * TheSportsDB oyuncu objesi — hem {@code searchplayers.php} (lite: idPlayer +
 * isim + spor) hem {@code lookupplayer.php} (tam: + idAPIfootball + strSide)
 * yanıtları için ortak. Kullanmadığımız alanlar yok sayılır.
 *
 * <p>{@code idAPIfootball} API-Football oyuncu id'sidir — eşleştirmenin
 * anahtarı. {@code strSide} kullandığı ayaktır ("Right" / "Left" / "Both").
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TsdbPlayerDto(
        String idPlayer,
        String strPlayer,
        String strSport,
        String idAPIfootball,
        String strSide,
        String strPosition
) {}
