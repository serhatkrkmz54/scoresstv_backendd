package com.scorestv.football.status;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * API-Football {@code /status} endpoint'inin {@code response} govdesi:
 * hesap, abonelik ve gunluk istek kotasi bilgisi.
 *
 * <p>{@code Serializable} olmasinin nedeni Redis cache'inin (JDK serializer)
 * bu tipi gerektiginde saklayabilmesidir.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiFootballStatus(
        Account account,
        Subscription subscription,
        Requests requests
) implements Serializable {

    /** Hesap sahibi bilgileri. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Account(
            String firstname,
            String lastname,
            String email
    ) implements Serializable {
    }

    /** Abonelik plani ve durumu. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Subscription(
            String plan,
            String end,
            boolean active
    ) implements Serializable {
    }

    /** Gunluk istek kullanimi: {@code current} = bugun yapilan, {@code limitDay} = gunluk limit. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Requests(
            int current,
            @JsonProperty("limit_day") int limitDay
    ) implements Serializable {
    }
}
