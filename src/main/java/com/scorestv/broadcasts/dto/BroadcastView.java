package com.scorestv.broadcasts.dto;

/**
 * İstemcilere dönen sade TV yayını modeli — bir kanalın adı, ülkesi ve logosu.
 */
public record BroadcastView(
        String channel,
        String country,
        String logoUrl
) {}
