package com.scorestv.mobile.web.dto;

/**
 * Bir voleybol takiminin bildirim tercihleri — mobile <-> backend dto.
 *
 * <p>Basketbol {@link BasketballNotificationPrefsDto} ile ayni pattern, 3 olay:
 * <ul>
 *   <li>basladi — mac basladi (S1)</li>
 *   <li>set     — set bitti</li>
 *   <li>bitti   — FT / AW</li>
 * </ul>
 */
public record VolleyballNotificationPrefsDto(
        boolean basladi,
        boolean set,
        boolean bitti
) {}
