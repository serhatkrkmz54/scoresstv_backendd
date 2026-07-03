package com.scorestv.broadcasts;

import com.scorestv.broadcasts.domain.LeagueBroadcaster;
import com.scorestv.broadcasts.domain.LeagueBroadcasterRepository;
import com.scorestv.broadcasts.domain.MatchBroadcast;
import com.scorestv.broadcasts.domain.MatchBroadcastRepository;
import com.scorestv.broadcasts.domain.TvChannel;
import com.scorestv.football.domain.Fixture;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Yayin bilgisi cozumleyici.
 *
 * <p>Oncelik:
 * <ol>
 *   <li><b>Match override</b> — mac icin spesifik atama varsa (derbi vs.)</li>
 *   <li><b>League default</b> — yoksa ligin sezonluk varsayilan kanallari</li>
 *   <li><b>Bos</b> — hicbiri yoksa "yayin bilgisi yok"</li>
 * </ol>
 *
 * <p>{@code country_code} kullanici ulkesi — frontend gonderir.
 * Eksikse "TR" varsayilir (Turkce site).
 */
@Service
public class BroadcastsService {

    private static final String DEFAULT_COUNTRY = "TR";

    private final MatchBroadcastRepository matchRepository;
    private final LeagueBroadcasterRepository leagueRepository;

    public BroadcastsService(MatchBroadcastRepository matchRepository,
                              LeagueBroadcasterRepository leagueRepository) {
        this.matchRepository = matchRepository;
        this.leagueRepository = leagueRepository;
    }

    /**
     * Verilen mac icin yayinlayan kanal listesi. Once mac override, sonra
     * lig default.
     *
     * @param fixture lig + sezon iceren mac
     * @param country kullanici ulke kodu (null → "TR")
     * @param turkish lokalize ad gerek mi (true → kanal.name_tr varsa kullan)
     */
    @Transactional(readOnly = true)
    public List<BroadcastView> resolveForFixture(Fixture fixture, String country,
                                                  boolean turkish) {
        if (fixture == null) return List.of();
        String countryCode = (country != null && !country.isBlank())
                ? country.trim().toUpperCase(Locale.ROOT)
                : DEFAULT_COUNTRY;

        List<BroadcastView> result = new ArrayList<>();
        Set<Long> seenChannelIds = new LinkedHashSet<>();

        // 1) Mac override
        List<MatchBroadcast> overrides = matchRepository
                .findByFixtureIdAndCountryCodeOrderBySortOrderAsc(
                        fixture.getId(), countryCode);
        for (MatchBroadcast m : overrides) {
            if (m.getChannel() == null) continue;
            if (!seenChannelIds.add(m.getChannel().getId())) continue;
            result.add(toView(m.getChannel(), m.getNotes(),
                    m.getSource().name(), turkish));
        }

        // 2) Lig default (eger override yetersizse veya hicbiri yoksa)
        if (fixture.getLeague() != null && fixture.getSeason() != null) {
            List<LeagueBroadcaster> defaults = leagueRepository
                    .findByLeagueIdAndSeasonAndCountryCodeOrderBySortOrderAsc(
                            fixture.getLeague().getId(), fixture.getSeason(),
                            countryCode);
            for (LeagueBroadcaster lb : defaults) {
                if (lb.getChannel() == null) continue;
                if (!seenChannelIds.add(lb.getChannel().getId())) continue;
                result.add(toView(lb.getChannel(), lb.getNotes(),
                        "LEAGUE_DEFAULT", turkish));
            }
        }

        return result;
    }

    /**
     * Bir LİG için (mac override YOK) yalnizca sezonluk varsayilan kanallarin
     * GÖRÜNEN adlarini doner — TV programi hub'i (canli-mac-programi) icin.
     *
     * <p>{@link #resolveForFixture}'daki lig-default dalinin adi-yalnizca
     * karsiligidir: {@code findByLeagueIdAndSeasonAndCountryCodeOrderBySortOrderAsc}
     * ile kanallar sirali gelir, her kanalin {@code turkish && name_tr} varsa
     * name_tr'si, yoksa {@code name}'i alinir. Tekrarlar elenir, ilk 3 kanal
     * dondurulur. Season/leagueId eksikse bos liste.
     *
     * @param leagueId lig id (null → bos)
     * @param season   sezon (null → bos)
     * @param country  kullanici ulke kodu (null → "TR")
     * @param turkish  true ise kanal.name_tr varsa kullan
     */
    @Transactional(readOnly = true)
    public List<String> leagueDefaultChannels(Long leagueId, Integer season,
                                              String country, boolean turkish) {
        if (leagueId == null || season == null) {
            return List.of();
        }
        String countryCode = (country != null && !country.isBlank())
                ? country.trim().toUpperCase(Locale.ROOT)
                : DEFAULT_COUNTRY;

        List<LeagueBroadcaster> defaults = leagueRepository
                .findByLeagueIdAndSeasonAndCountryCodeOrderBySortOrderAsc(
                        leagueId, season, countryCode);

        List<String> names = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (LeagueBroadcaster lb : defaults) {
            TvChannel ch = lb.getChannel();
            if (ch == null) {
                continue;
            }
            String displayName = ch.getName();
            if (turkish && ch.getNameTr() != null && !ch.getNameTr().isBlank()) {
                displayName = ch.getNameTr();
            }
            if (displayName == null || displayName.isBlank()) {
                continue;
            }
            if (!seen.add(displayName)) {
                continue;
            }
            names.add(displayName);
            if (names.size() >= 3) {
                break;
            }
        }
        return names;
    }

    private static BroadcastView toView(TvChannel ch, String notes, String source,
                                         boolean turkish) {
        String displayName = ch.getName();
        if (turkish && ch.getNameTr() != null && !ch.getNameTr().isBlank()) {
            displayName = ch.getNameTr();
        }
        return new BroadcastView(
                ch.getId(),
                displayName,
                ch.getShortName(),
                ch.getLogoUrl(),
                ch.getCountryCode(),
                ch.getStreamingUrl(),
                ch.isStreamingOnly(),
                notes,
                source);
    }

    /**
     * Frontend'e dondurulen tek bir yayin satiri. UI'da kart olarak gosterilir.
     *
     * @param source "MANUAL", "LIVESOCCERTV", "IMPORT", "LEAGUE_DEFAULT"
     *               UI badge'i icin
     */
    public record BroadcastView(
            Long channelId,
            String channelName,
            String shortName,
            String logoUrl,
            String countryCode,
            String streamingUrl,
            boolean streamingOnly,
            String notes,
            String source
    ) implements Serializable {}
}
