package com.scorestv.search.index;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.time.Instant;
import java.util.List;

/**
 * Haber (news) arama dokumani — index: <code>scorestv_news</code>.
 *
 * <p>Yalniz PUBLISHED + silinmemis + publishedAt &lt;= now haberler indexlenir
 * (NewsIndexer bunu garanti eder; yayindan kaldirilan/silinen haber index'ten
 * cikarilir). Baslik takim/lig dokumanlariyla ayni autocomplete analyzer'i
 * kullanir (edge_ngram + asciifolding) — "galat" → "Galatasaray transferi".
 *
 * <p>Bagli varlik id'leri (teams/leagues/countries/players/fixtures) keyword
 * dizileridir; "ilgili haberler" (related) terms query'si bunlar uzerinden
 * calisir. {@code popularity} viewCount'tan turetilir (function_score boost).
 */
@Document(indexName = "scorestv_news", createIndex = false)
@Setting(settingPath = "elasticsearch/settings.json")
public class ArticleDoc {

    @Id
    private Long id;

    @Field(type = FieldType.Keyword)
    private String slug;

    /** Dil kodu: "tr" / "en" — arama dil filtresi. */
    @Field(type = FieldType.Keyword)
    private String lang;

    @Field(type = FieldType.Text,
            analyzer = "autocomplete_index", searchAnalyzer = "autocomplete_search")
    private String title;

    @Field(type = FieldType.Text,
            analyzer = "autocomplete_index", searchAnalyzer = "autocomplete_search")
    private String summary;

    /** Govdeden strip edilmis duz metin — more-like-this / tam metin arama. */
    @Field(type = FieldType.Text)
    private String bodyText;

    @Field(type = FieldType.Keyword)
    private List<Long> teams;

    @Field(type = FieldType.Keyword)
    private List<Long> leagues;

    @Field(type = FieldType.Keyword)
    private List<Long> countries;

    @Field(type = FieldType.Keyword)
    private List<Long> players;

    @Field(type = FieldType.Keyword)
    private List<Long> fixtures;

    @Field(type = FieldType.Keyword)
    private String sport;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(type = FieldType.Boolean)
    private boolean isBreaking;

    @Field(type = FieldType.Boolean)
    private boolean isFeatured;

    @Field(type = FieldType.Date)
    private Instant publishedAt;

    /** viewCount'tan turetilen populerlik (function_score boost). */
    @Field(type = FieldType.Double)
    private double popularity = 0.0;

    /** Kapak gorseli URL'si — aranmaz, yalniz sonuc gosteriminde tasinir. */
    @Field(type = FieldType.Keyword, index = false)
    private String coverImageUrl;

    public ArticleDoc() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getLang() { return lang; }
    public void setLang(String lang) { this.lang = lang; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getBodyText() { return bodyText; }
    public void setBodyText(String bodyText) { this.bodyText = bodyText; }
    public List<Long> getTeams() { return teams; }
    public void setTeams(List<Long> teams) { this.teams = teams; }
    public List<Long> getLeagues() { return leagues; }
    public void setLeagues(List<Long> leagues) { this.leagues = leagues; }
    public List<Long> getCountries() { return countries; }
    public void setCountries(List<Long> countries) { this.countries = countries; }
    public List<Long> getPlayers() { return players; }
    public void setPlayers(List<Long> players) { this.players = players; }
    public List<Long> getFixtures() { return fixtures; }
    public void setFixtures(List<Long> fixtures) { this.fixtures = fixtures; }
    public String getSport() { return sport; }
    public void setSport(String sport) { this.sport = sport; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public boolean isBreaking() { return isBreaking; }
    public void setBreaking(boolean breaking) { isBreaking = breaking; }
    public boolean isFeatured() { return isFeatured; }
    public void setFeatured(boolean featured) { isFeatured = featured; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public double getPopularity() { return popularity; }
    public void setPopularity(double popularity) { this.popularity = popularity; }
    public String getCoverImageUrl() { return coverImageUrl; }
    public void setCoverImageUrl(String coverImageUrl) { this.coverImageUrl = coverImageUrl; }
}
