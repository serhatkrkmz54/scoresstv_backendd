package com.scorestv.mobile.broadcast;

/**
 * Genel bildirim gonderiminde dil hedefi.
 * ALL = dil ayrimi yok (herkes), TR = Turkce cihazlar, EN = Ingilizce cihazlar.
 * (Cihaz kaydindaki {@code locale} on eki ile eslenir: "tr%", "en%".)
 */
public enum BroadcastLang {
    ALL,
    TR,
    EN
}
