package com.scorestv.news;

/**
 * Haberin yayin durumu.
 * DRAFT     - taslak (public gormez)
 * SCHEDULED - ileri tarihli yayin (public gormez, published_at gelecekte)
 * PUBLISHED - yayinda (public okur)
 * ARCHIVED  - arsivlenmis (public gormez)
 */
public enum NewsStatus {
    DRAFT,
    SCHEDULED,
    PUBLISHED,
    ARCHIVED
}
