package com.scorestv.mobile.broadcast;

/**
 * Genel bildirim gonderiminde platform hedefi.
 * ALL = hepsi, IOS = yalniz iOS, ANDROID = yalniz Android.
 * (Cihaz kaydindaki {@code platform} degeri "ios"/"android" ile eslenir.)
 */
public enum BroadcastPlatform {
    ALL,
    IOS,
    ANDROID
}
