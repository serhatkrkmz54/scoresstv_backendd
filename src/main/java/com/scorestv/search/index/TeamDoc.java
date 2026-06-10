package com.scorestv.search.index;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

/**
 * Takim arama dokumani — index: <code>scorestv_teams</code>.
 *
 * <p><b>Tasarim:</b> hem orijinal isim hem Turkce yerel isim ayri alanlarda
 * indexlenir. Search query her ikisine birden bakar (multi-match). Slug ve
 * country keyword olarak tutulur (filtreleme + facet).
 *
 * <p><b>Analyzer:</b> name + nameTr edge_ngram + asciifolding ile — "Galat",
 * "galat", "GALATAS" hepsi "Galatasaray" ile eslesir.
 */
// createIndex = false: startup'ta ES'e baglanmayi denemez (uygulama ES kapaliyken
// de ayaga kalksin). Ilk reindex/save cagrisinda Spring Data ES otomatik yaratir.
@Document(indexName = "scorestv_teams", createIndex = false)
@Setting(settingPath = "elasticsearch/settings.json")
public class TeamDoc {

    @Id
    private Long id;

    @Field(type = FieldType.Text,
            analyzer = "autocomplete_index", searchAnalyzer = "autocomplete_search")
    private String name;

    /** Turkce yerel ad — varsa indexlenir. */
    @Field(type = FieldType.Text,
            analyzer = "autocomplete_index", searchAnalyzer = "autocomplete_search")
    private String nameTr;

    @Field(type = FieldType.Keyword)
    private String slug;

    @Field(type = FieldType.Keyword)
    private String code;     // kisa kod (ornek "GAL", "FB")

    @Field(type = FieldType.Keyword)
    private String country;  // ulke ismi (Ingilizce — filtreleme icin)

    @Field(type = FieldType.Keyword)
    private String countryTr; // ulke ismi (Turkce — gosterim icin)

    @Field(type = FieldType.Keyword)
    private String logoUrl;

    @Field(type = FieldType.Boolean)
    private boolean national;

    /** Skor boost icin (populer takimlar uste cikar). Default 1.0. */
    @Field(type = FieldType.Float)
    private float popularity = 1.0f;

    public TeamDoc() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNameTr() { return nameTr; }
    public void setNameTr(String nameTr) { this.nameTr = nameTr; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getCountryTr() { return countryTr; }
    public void setCountryTr(String countryTr) { this.countryTr = countryTr; }
    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
    public boolean isNational() { return national; }
    public void setNational(boolean national) { this.national = national; }
    public float getPopularity() { return popularity; }
    public void setPopularity(float popularity) { this.popularity = popularity; }
}
