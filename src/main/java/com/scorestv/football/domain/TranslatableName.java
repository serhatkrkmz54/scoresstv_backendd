package com.scorestv.football.domain;

/**
 * İngilizce kaynak adının ({@code name}) yanında elle girilen Türkçe
 * karşılığını ({@code name_tr}) taşıyan referans varlıklar için ortak arayüz.
 *
 * <p>{@link Country}, {@link League}, {@link Team} ve {@link Venue} bunu uygular.
 * Çeviri export/import ve tekil düzenleme servisleri varlık tipine bağlı
 * kalmadan bu arayüz üzerinden çalışır; getter/setter'lar Lombok ile üretilir.
 */
public interface TranslatableName {

    /** Varlığın birincil anahtarı. */
    Long getId();

    /** API-Football'dan gelen İngilizce kaynak ad. */
    String getName();

    /** Elle girilen Türkçe ad; henüz çevrilmemişse {@code null}. */
    String getNameTr();

    /** Türkçe adı ayarlar — yalnızca çeviri akışı çağırır, senkron çağırmaz. */
    void setNameTr(String nameTr);
}
