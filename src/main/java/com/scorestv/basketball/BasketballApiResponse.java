package com.scorestv.basketball;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * API-Sports ortak yanıt zarfı: {@code { get, results, response: [...] }}.
 * {@code errors} ve diğer meta alanları yok sayılır.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BasketballApiResponse<T>(
        String get,
        Integer results,
        List<T> response
) {
    public List<T> responseOrEmpty() {
        return response == null ? List.of() : response;
    }
}
