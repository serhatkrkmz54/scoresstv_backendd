package com.scorestv.news;

/**
 * Panel toplu (bulk) haber islemleri.
 * PUBLISH/UNPUBLISH  - yayinla / yayindan geri cek
 * ARCHIVE            - arsive tasi (status=ARCHIVED, public gormez)
 * SET_CATEGORY       - kategori degistir (category alani zorunlu)
 * SET_SPORT          - spor kolu degistir (sport alani zorunlu)
 * DELETE             - soft-delete (yalniz ADMIN)
 */
public enum BulkAction {
    PUBLISH,
    UNPUBLISH,
    ARCHIVE,
    SET_CATEGORY,
    SET_SPORT,
    DELETE
}
