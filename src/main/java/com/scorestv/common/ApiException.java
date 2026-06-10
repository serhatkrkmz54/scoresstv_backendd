package com.scorestv.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Uygulama genelinde kullanilan, HTTP durum kodu tasiyan istisna.
 * Statik fabrika metotlari ile kullanilir: ApiException.notFound(...) vb.
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

    public static ApiException tooManyRequests(String message) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, message);
    }

    /** Ust akis (3rd party API — FIFA/UEFA gibi) hatasi — 502 Bad Gateway. */
    public static ApiException upstream(String message) {
        return new ApiException(HttpStatus.BAD_GATEWAY, message);
    }
}
