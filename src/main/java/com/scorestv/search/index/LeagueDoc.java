package com.scorestv.search.index;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

/**
 * Lig arama dokumani — index: <code>scorestv_leagues</code>.
 *
 * <p>Tip (League/Cup) filtreleme icin keyword olarak tutulur.
 *
 * <p><b>Analyzer:</b> name + nameTr edge_ngram + asciifolding — "Sup" yazinca
 * "Süper Lig" gelir.
 */
@Document(indexName = "scorestv_leagues", createIndex = false)
@Setting(settingPath = "elasticsearch/settings.json")
public class LeagueDoc {

    @Id
    private Long id;

    @Field(type = FieldType.Text,
            analyzer = "autocomplete_index", searchAnalyzer = "autocomplete_search")
    private String name;

    @Field(type = FieldType.Text,
            analyzer = "autocomplete_index", searchAnalyzer = "autocomplete_search")
    private String nameTr;

    @Field(type = FieldType.Keyword)
    private String slug;

    @Field(type = FieldType.Keyword)
    private String country;

    @Field(type = FieldType.Keyword)
    private String countryTr; // ulke ismi (Turkce — gosterim icin)

    /** "League" / "Cup" — filtreleme icin. */
    @Field(type = FieldType.Keyword)
    private String type;

    @Field(type = FieldType.Keyword)
    private String logoUrl;

    @Field(type = FieldType.Keyword)
    private String flagUrl;

    @Field(type = FieldType.Boolean)
    private boolean covered;

    @Field(type = FieldType.Float)
    private float popularity = 1.0f;

    public LeagueDoc() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNameTr() { return nameTr; }
    public void setNameTr(String nameTr) { this.nameTr = nameTr; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getCountryTr() { return countryTr; }
    public void setCountryTr(String countryTr) { this.countryTr = countryTr; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
    public String getFlagUrl() { return flagUrl; }
    public void setFlagUrl(String flagUrl) { this.flagUrl = flagUrl; }
    public boolean isCovered() { return covered; }
    public void setCovered(boolean covered) { this.covered = covered; }
    public float getPopularity() { return popularity; }
    public void setPopularity(float popularity) { this.popularity = popularity; }
}
