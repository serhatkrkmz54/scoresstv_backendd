package com.scorestv.football.translation;

import com.scorestv.common.ApiException;

/**
 * Türkçe adı (name_tr) elle çevrilebilen referans varlık tipleri.
 *
 * <p>Her tip bir URL segmenti ({@link #getPath()}), kullanıcıya gösterilen
 * etiketler ve {@code name_tr} kolonunun azami uzunluğunu taşır. Çeviri
 * export/import ve durum endpoint'leri bu enum üzerinden çalışır.
 */
public enum TranslationType {

    COUNTRIES("countries", "Ülkeler", "Ülke", 100),
    LEAGUES("leagues", "Ligler", "Lig", 150),
    TEAMS("teams", "Takımlar", "Takım", 150),
    VENUES("venues", "Stadyumlar", "Stadyum", 150);

    /** URL yol segmenti ve indirilen dosya adının parçası (örn. "teams"). */
    private final String path;
    /** Çoğul, kullanıcıya gösterilen etiket (durum yanıtı). */
    private final String label;
    /** Tekil etiket — Excel sayfa adı ve başlıkları için. */
    private final String singular;
    /** name_tr kolonunun DB'deki azami karakter uzunluğu. */
    private final int maxNameTrLength;

    TranslationType(String path, String label, String singular, int maxNameTrLength) {
        this.path = path;
        this.label = label;
        this.singular = singular;
        this.maxNameTrLength = maxNameTrLength;
    }

    public String getPath() {
        return path;
    }

    public String getLabel() {
        return label;
    }

    public String getSingular() {
        return singular;
    }

    public int getMaxNameTrLength() {
        return maxNameTrLength;
    }

    /**
     * URL segmentinden (örn. "teams") tipi çözer; büyük/küçük harf duyarsız.
     *
     * @throws ApiException 400 — segment hiçbir tipe karşılık gelmiyorsa
     */
    public static TranslationType fromPath(String value) {
        if (value != null) {
            for (TranslationType type : values()) {
                if (type.path.equalsIgnoreCase(value)) {
                    return type;
                }
            }
        }
        throw ApiException.badRequest(
                "Geçersiz çeviri tipi: '" + value + "'. Geçerli: countries, leagues, teams, venues.");
    }
}
