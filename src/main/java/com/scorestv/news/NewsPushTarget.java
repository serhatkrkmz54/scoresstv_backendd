package com.scorestv.news;

/**
 * Haber (news) push bildiriminin hedef kitlesi.
 *
 * <ul>
 *   <li>{@code ALL}       — haberin diliyle (lang) eslesen tum bildirimi acik
 *       cihazlar.</li>
 *   <li>{@code FAVORITES} — habere bagli takim/lig/oyuncu/ulke'yi takip eden
 *       cihazlar (varsayilan).</li>
 * </ul>
 */
public enum NewsPushTarget {
    ALL,
    FAVORITES
}
