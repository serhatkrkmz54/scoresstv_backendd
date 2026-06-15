package com.scorestv.contact;

/**
 * İletişim mesajının durumu (admin iş akışı).
 */
public enum ContactStatus {
    /** Yeni gelen, henüz okunmamış. */
    NEW,
    /** Admin tarafından okundu. */
    READ,
    /** Arşivlendi / kapatıldı. */
    ARCHIVED
}
