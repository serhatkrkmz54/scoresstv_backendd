package com.scorestv.mobile.web.dto;

/**
 * Bir basketbol takiminin bildirim tercihleri — mobile <-> backend dto.
 *
 * <p>Futbol {@link NotificationPrefsDto} ile aynı pattern, 3 olay:
 * <ul>
 *   <li>basladi — tip-off / Q1 başladı</li>
 *   <li>ceyrek  — Q1/Q2/Q3 sonu + HT</li>
 *   <li>bitti   — FT / AOT</li>
 * </ul>
 */
public record BasketballNotificationPrefsDto(
        boolean basladi,
        boolean ceyrek,
        boolean bitti
) {}
