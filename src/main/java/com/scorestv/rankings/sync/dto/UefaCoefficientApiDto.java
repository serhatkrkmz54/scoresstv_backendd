package com.scorestv.rankings.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * UEFA katsayi API yaniti — hem kulup hem milli takim icin ayni yapi.
 *
 * <p>Endpoint:
 * <ul>
 *   <li>Kulup: {@code coefficientType=MEN_CLUB}</li>
 *   <li>Milli takim: {@code coefficientType=MEN_ASSOCIATION}</li>
 * </ul>
 *
 * <p>Paginated — {@code page=1&pagesize=50}. {@code meta.collection.totalElements}
 * toplam kayit sayisi. Kulup ~415, milli takim ~55.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UefaCoefficientApiDto(Data data, Meta meta) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(String lastUpdateDate, List<Member> members) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(Collection collection) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Collection(String totalElements) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Member(
            MemberInfo member,
            OverallRanking overallRanking,
            List<Map<String, Object>> seasonRankings
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MemberInfo(
            String id,
            String displayName,
            String displayNameShort,
            String displayOfficialName,
            String displayTeamCode,
            String teamCode,
            String internationalName,
            String countryCode,
            String countryName,
            String logoUrl,
            String bigLogoUrl,
            String mediumLogoUrl,
            String associationLogoUrl,
            Long associationId,
            Boolean typeIsNational,
            String typeTeam
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OverallRanking(
            Integer baseSeasonYear,
            Integer targetSeasonYear,
            Integer position,
            BigDecimal totalPoints,
            BigDecimal totalValue,
            Integer numberOfMatches,
            Integer numberOfTeams,
            Integer numberOfTeamsStillInPlaying,
            BigDecimal nationalAssociationPoints,
            BigDecimal factorBonusForTitleHeld,
            String trend
    ) {}
}
