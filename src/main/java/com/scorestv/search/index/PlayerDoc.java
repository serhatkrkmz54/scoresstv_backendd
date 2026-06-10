package com.scorestv.search.index;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

/**
 * Oyuncu arama dokumani — index: <code>scorestv_players</code>.
 *
 * <p>name + lastName ayri alanlarda; query her ikisine de bakar.
 * teamId/teamName aktif kulup, position pozisyon filtresi icin.
 *
 * <p><b>Analyzer:</b> tum text alanlar autocomplete_index (edge_ngram 1-15) ile
 * indexlenir; query time autocomplete_search (standard + lowercase +
 * asciifolding) — Turkce karakter ('ü' → 'u') ve prefix arama destegi.
 */
@Document(indexName = "scorestv_players", createIndex = false)
@Setting(settingPath = "elasticsearch/settings.json")
public class PlayerDoc {

    @Id
    private Long id;

    @Field(type = FieldType.Text,
            analyzer = "autocomplete_index", searchAnalyzer = "autocomplete_search")
    private String name;       // tam isim

    @Field(type = FieldType.Text,
            analyzer = "autocomplete_index", searchAnalyzer = "autocomplete_search")
    private String firstName;

    @Field(type = FieldType.Text,
            analyzer = "autocomplete_index", searchAnalyzer = "autocomplete_search")
    private String lastName;

    @Field(type = FieldType.Keyword)
    private String slug;

    @Field(type = FieldType.Keyword)
    private String nationality;

    @Field(type = FieldType.Keyword)
    private String position;   // Goalkeeper / Defender / Midfielder / Attacker

    @Field(type = FieldType.Long)
    private Long teamId;

    @Field(type = FieldType.Keyword)
    private String teamName;

    @Field(type = FieldType.Keyword)
    private String photoUrl;

    @Field(type = FieldType.Integer)
    private Integer age;

    @Field(type = FieldType.Float)
    private float popularity = 1.0f;

    public PlayerDoc() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getNationality() { return nationality; }
    public void setNationality(String nationality) { this.nationality = nationality; }
    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }
    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public float getPopularity() { return popularity; }
    public void setPopularity(float popularity) { this.popularity = popularity; }
}
