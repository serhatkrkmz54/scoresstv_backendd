package com.scorestv.mobile.broadcast;

import java.io.Serializable;
import java.time.Instant;

/** Gonderim gecmisi ogesi (panel listesi). */
public record BroadcastListItem(
        Long id,
        String title,
        String body,
        String link,
        String platformTarget,
        String langTarget,
        String status,
        int recipientCount,
        int sentCount,
        Instant createdAt
) implements Serializable {

    public static BroadcastListItem from(BroadcastNotification n) {
        return new BroadcastListItem(
                n.getId(),
                n.getTitle(),
                n.getBody(),
                n.getLink(),
                n.getPlatformTarget().name(),
                n.getLangTarget().name(),
                n.getStatus(),
                n.getRecipientCount(),
                n.getSentCount(),
                n.getCreatedAt());
    }
}
