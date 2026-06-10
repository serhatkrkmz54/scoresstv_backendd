package com.scorestv.search.index;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.time.Instant;

/**
 * Fikstur arama dokumani — index: <code>scorestv_fixtures</code>.
 *
 * <p>Mac arama acisindan en zor index: kullanici genelde "ev takimi - dep takimi"
 * yazar. Bu yuzden iki takim adi tek bir <code>matchup</code> alaninda da
 * birlestirilip indexlenir (multi-match: name + matchup).
 *
 * <p>kickoff (date) range query'leri icin tutulur.
 *
 * <p><b>Analyzer:</b> takim adi + matchup edge_ngram + asciifolding — "galat
 * fener" yazinca "Galatasaray - Fenerbahce" gelir.
 */
@Document(indexName = "scorestv_fixtures", createIndex = false)
@Setting(settingPath = "elasticsearch/settings.json")
public class FixtureDoc {

    @Id
    private Long id;

    @Field(type = FieldType.Long)
    private Long homeTeamId;

    @Field(type = FieldType.Text,
            analyzer = "autocomplete_index", searchAnalyzer = "autocomplete_search")
    private String homeTeamName;

    /** TR adi — Team.nameTr varsa doldurulur, arama da bu alana bakar. */
    @Field(type = FieldType.Text,
            analyzer = "autocomplete_index", searchAnalyzer = "autocomplete_search")
    private String homeTeamNameTr;

    @Field(type = FieldType.Long)
    private Long awayTeamId;

    @Field(type = FieldType.Text,
            analyzer = "autocomplete_index", searchAnalyzer = "autocomplete_search")
    private String awayTeamName;

    @Field(type = FieldType.Text,
            analyzer = "autocomplete_index", searchAnalyzer = "autocomplete_search")
    private String awayTeamNameTr;

    /** "Galatasaray - Fenerbahce" formatinda birlesik alan (EN/native). */
    @Field(type = FieldType.Text,
            analyzer = "autocomplete_index", searchAnalyzer = "autocomplete_search")
    private String matchup;

    /** "Galatasaray - Fenerbahce" formatinda TR alan. */
    @Field(type = FieldType.Text,
            analyzer = "autocomplete_index", searchAnalyzer = "autocomplete_search")
    private String matchupTr;

    @Field(type = FieldType.Keyword)
    private String slug;

    @Field(type = FieldType.Long)
    private Long leagueId;

    @Field(type = FieldType.Keyword)
    private String leagueName;

    @Field(type = FieldType.Keyword)
    private String leagueNameTr;

    @Field(type = FieldType.Integer)
    private Integer season;

    @Field(type = FieldType.Date)
    private Instant kickoff;

    /** "Match Finished" / "First Half" / "Not Started" — filtreleme icin. */
    @Field(type = FieldType.Keyword)
    private String statusLong;

    /** "FT" / "1H" / "NS" — kisa kod. */
    @Field(type = FieldType.Keyword)
    private String statusShort;

    public FixtureDoc() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getHomeTeamId() { return homeTeamId; }
    public void setHomeTeamId(Long homeTeamId) { this.homeTeamId = homeTeamId; }
    public String getHomeTeamName() { return homeTeamName; }
    public void setHomeTeamName(String homeTeamName) { this.homeTeamName = homeTeamName; }
    public String getHomeTeamNameTr() { return homeTeamNameTr; }
    public void setHomeTeamNameTr(String homeTeamNameTr) { this.homeTeamNameTr = homeTeamNameTr; }
    public Long getAwayTeamId() { return awayTeamId; }
    public void setAwayTeamId(Long awayTeamId) { this.awayTeamId = awayTeamId; }
    public String getAwayTeamName() { return awayTeamName; }
    public void setAwayTeamName(String awayTeamName) { this.awayTeamName = awayTeamName; }
    public String getAwayTeamNameTr() { return awayTeamNameTr; }
    public void setAwayTeamNameTr(String awayTeamNameTr) { this.awayTeamNameTr = awayTeamNameTr; }
    public String getMatchup() { return matchup; }
    public void setMatchup(String matchup) { this.matchup = matchup; }
    public String getMatchupTr() { return matchupTr; }
    public void setMatchupTr(String matchupTr) { this.matchupTr = matchupTr; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public Long getLeagueId() { return leagueId; }
    public void setLeagueId(Long leagueId) { this.leagueId = leagueId; }
    public String getLeagueName() { return leagueName; }
    public void setLeagueName(String leagueName) { this.leagueName = leagueName; }
    public String getLeagueNameTr() { return leagueNameTr; }
    public void setLeagueNameTr(String leagueNameTr) { this.leagueNameTr = leagueNameTr; }
    public Integer getSeason() { return season; }
    public void setSeason(Integer season) { this.season = season; }
    public Instant getKickoff() { return kickoff; }
    public void setKickoff(Instant kickoff) { this.kickoff = kickoff; }
    public String getStatusLong() { return statusLong; }
    public void setStatusLong(String statusLong) { this.statusLong = statusLong; }
    public String getStatusShort() { return statusShort; }
    public void setStatusShort(String statusShort) { this.statusShort = statusShort; }
}
