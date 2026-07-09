package com.scorestv.news.dto;

import java.util.List;

/**
 * Ana sayfa slider'ini kaydet — {@code lang} dilindeki slider uyeligini ve
 * sirasini {@code ids} ile TAM olarak degistir: listedeki haberler verilen
 * sirada inSlider=true olur, o dildeki digerleri slider'dan cikarilir.
 */
public record SaveSliderRequest(
        String lang,
        List<Long> ids
) {
}
