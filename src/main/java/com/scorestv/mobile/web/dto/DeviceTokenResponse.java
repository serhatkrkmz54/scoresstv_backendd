package com.scorestv.mobile.web.dto;

/** Device token register endpoint yaniti. */
public record DeviceTokenResponse(
        Long deviceTokenId,
        boolean newlyRegistered
) {}
