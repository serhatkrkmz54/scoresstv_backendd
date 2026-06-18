package com.scorestv.search.index;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

/**
 * Tekik direktor (coach) arama dokumani — index: <code>scorestv_coaches</code>.
 *
 * <p>Koç verisi nispeten az; arama sonucu kartı icin yeterli minimum set:
 * isim (tam + ad + soyad ayri alanlarda), uyruk, mevcut takim ve foto.
 * Detay sayfasi yok — sonuca tiklayinca hafif bilgi karti gosterilir, mevcut
 * takim biliniyorsa "Takima git" ile takim sayfasina yonlendirilir.
 *
 * <p><b>Analyzer:</b> name/firstName/lastName autocomplete_index (edge_ngram
 * 1-15) ile indexlenir; query time autocomplete_search (standard + lowercase +
 * asciifolding) — Turkce karakter ('ı'→'i', 'ş'→'s') ve prefix arama destegi.
 * Boylece "ismail", "kartal", "ismail kar" → "İsmail Kartal" yakalanir.
 */
@Document(indexName = "scorestv_coaches", createIndex = false)
@Setting(settingPath = "elasticsearch/settings.json")
public class CoachDoc {

    @Id
    private Long id;

    @Field(type = FieldType.Text,
            analyzer = "autocomplete_index", searchAnalyzer = "autocomplete_search")
    private String name;        // tam isim ("İsmail Kartal")

    @Field(type = FieldType.Text,
            analyzer = "autocomplete_index", searchAnalyzer = "autocomplete_search")
    private String firstName;

    @Field(type = FieldType.Text,
            analyzer = "autocomplete_index", searchAnalyzer = "autocomplete_search")
    private String lastName;

    @Field(type = FieldType.Keyword)
    private String nationality;

    /** Mevcut takim id'si (varsa) — "Takima git" yonlendirmesi icin. */
    @Field(type = FieldType.Long)
    private Long currentTeamId;

    /** Mevcut takim adi (varsa) — kartta gosterim icin. */
    @Field(type = FieldType.Keyword)
    private String currentTeamName;

    @Field(type = FieldType.Keyword)
    private String photoUrl;

    @Field(type = FieldType.Integer)
    private Integer age;

    /** Skor boost icin — koçlarda populer listesi yok, hepsi 1.0 (sorgu tutarliligi). */
    @Field(type = FieldType.Float)
    private float popularity = 1.0f;

    public CoachDoc() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getNationality() { return nationality; }
    public void setNationality(String nationality) { this.nationality = nationality; }
    public Long getCurrentTeamId() { return currentTeamId; }
    public void setCurrentTeamId(Long currentTeamId) { this.currentTeamId = currentTeamId; }
    public String getCurrentTeamName() { return currentTeamName; }
    public void setCurrentTeamName(String currentTeamName) { this.currentTeamName = currentTeamName; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public float getPopularity() { return popularity; }
    public void setPopularity(float popularity) { this.popularity = popularity; }
}
