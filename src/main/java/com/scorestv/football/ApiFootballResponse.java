package com.scorestv.football;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Collection;
import java.util.Map;

/**
 * API-Football v3'un tum endpoint'lerinde kullandigi ortak yanit zarfi.
 *
 * <pre>
 * {
 *   "get": "...",
 *   "parameters": [] veya {...},
 *   "errors": []      veya {...},
 *   "results": 0,
 *   "paging": { "current": 1, "total": 1 },
 *   "response": [...] veya {...}
 * }
 * </pre>
 *
 * <p>{@code parameters} ve {@code errors} alanlari API tarafindan kimi zaman bos
 * dizi ([]), kimi zaman nesne ({...}) olarak donuyor. Bu nedenle tipleri
 * {@code Object} olarak birakildi; {@link #hasErrors()} her iki durumu da ele alir.
 *
 * @param <T> {@code response} alaninin tipi. Liste donen endpoint'ler icin
 *            {@code List<...>}, tekil nesne donen endpoint'ler (orn. /status)
 *            icin ilgili DTO tipi verilir.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiFootballResponse<T>(
        String get,
        Object parameters,
        Object errors,
        int results,
        Paging paging,
        T response
) {

    /** Sayfalama bilgisi. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Paging(int current, int total) {}

    /**
     * API mantiksal bir hata dondurdu mu? API-Football hatalarda da cogu zaman
     * HTTP 200 dondugu icin yalnizca status koduna bakmak yetmez.
     */
    public boolean hasErrors() {
        return switch (errors) {
            case null -> false;
            case Collection<?> c -> !c.isEmpty();
            case Map<?, ?> m -> !m.isEmpty();
            default -> true;
        };
    }

    /** Hata icerigini loglama/mesaj icin tek satirlik metne cevirir. */
    public String errorText() {
        return hasErrors() ? String.valueOf(errors) : "";
    }
}
