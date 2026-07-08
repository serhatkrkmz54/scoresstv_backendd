package com.scorestv.mobile.broadcast;

/**
 * Enqueue yaniti — kuyruk durumu ({@code status}: QUEUED), hedeflenen cihaz
 * sayisi ({@code recipientCount}) ve (varsa) iletilen sayi ({@code sentCount}).
 * Gonderim arka planda oldugu icin enqueue aninda status=QUEUED, sentCount=0.
 */
public record BroadcastResult(Long id, String status, int recipientCount, int sentCount) {

    public static BroadcastResult from(BroadcastNotification n) {
        return new BroadcastResult(
                n.getId(), n.getStatus(), n.getRecipientCount(), n.getSentCount());
    }
}
