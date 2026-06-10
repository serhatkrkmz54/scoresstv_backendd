package com.scorestv.football;

import com.scorestv.common.ApiException;
import org.springframework.http.HttpStatus;

/**
 * API-Football entegrasyonuna ozgu hata. {@link ApiException}'dan turedigi icin
 * {@code GlobalExceptionHandler} tarafindan otomatik olarak standart
 * {@code ErrorResponse}'a cevrilir; ek bir handler gerekmez.
 *
 * <p>Istemciye gosterilen mesaj sade tutulur; ham saglayici hatasi yalnizca
 * sunucu loglarina yazilir.
 */
public class ApiFootballException extends ApiException {

    public ApiFootballException(HttpStatus status, String message) {
        super(status, message);
    }

    /** API-Football yapilandirilmamis (anahtar yok vb.). */
    public static ApiFootballException notConfigured(String message) {
        return new ApiFootballException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }

    /** Saglayiciya ulasilamadi ya da beklenmeyen bir yanit alindi. */
    public static ApiFootballException upstream(String message) {
        return new ApiFootballException(HttpStatus.BAD_GATEWAY, message);
    }

    /** Saglayicinin istek kotasi (gunluk/dakikalik) doldu. */
    public static ApiFootballException quotaExceeded(String message) {
        return new ApiFootballException(HttpStatus.TOO_MANY_REQUESTS, message);
    }
}
