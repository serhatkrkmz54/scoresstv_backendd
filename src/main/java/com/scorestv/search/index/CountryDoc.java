package com.scorestv.search.index;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

/**
 * Ulke arama dokumani — index: <code>scorestv_countries</code>.
 *
 * <p>Az sayida (~200) kayit oldugu icin tum alanlar text + keyword.
 *
 * <p><b>Analyzer:</b> name + nameTr edge_ngram + asciifolding — "Tur",
 * "Turkiy", "TÜRKİY" hepsi "Türkiye" ile eslesir.
 */
@Document(indexName = "scorestv_countries", createIndex = false)
@Setting(settingPath = "elasticsearch/settings.json")
public class CountryDoc {

    /** PK = Country entity id (BaseEntity). Code nullable, id stable. */
    @Id
    private Long id;

    @Field(type = FieldType.Text,
            analyzer = "autocomplete_index", searchAnalyzer = "autocomplete_search")
    private String name;

    @Field(type = FieldType.Text,
            analyzer = "autocomplete_index", searchAnalyzer = "autocomplete_search")
    private String nameTr;

    /** ISO code — nullable ("World" gibi sentetik kayitlar icin). */
    @Field(type = FieldType.Keyword)
    private String code;

    @Field(type = FieldType.Keyword)
    private String slug;

    @Field(type = FieldType.Keyword)
    private String flagUrl;

    public CountryDoc() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNameTr() { return nameTr; }
    public void setNameTr(String nameTr) { this.nameTr = nameTr; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getFlagUrl() { return flagUrl; }
    public void setFlagUrl(String flagUrl) { this.flagUrl = flagUrl; }
}
