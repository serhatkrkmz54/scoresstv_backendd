package com.scorestv.news.dto;

import java.io.Serializable;

/**
 * Bir medya (gorsel) anahtarinin bir haberde nasil kullanildigi — medya
 * kutuphanesinde silme oncesi "hangi habere bagli" gostermek icin.
 *
 * @param cover  bu gorsel haberin KAPAGI mi
 * @param inBody bu gorsel haberin GOVDESINDE (metin ici) mi geciyor
 */
public record MediaUsage(
        Long articleId,
        String title,
        String slug,
        String lang,
        String status,
        boolean cover,
        boolean inBody
) implements Serializable {
}
