package com.scorestv.broadcasts.admin;

import com.scorestv.broadcasts.domain.LeagueBroadcaster;
import com.scorestv.broadcasts.domain.LeagueBroadcasterRepository;
import com.scorestv.broadcasts.domain.MatchBroadcast;
import com.scorestv.broadcasts.domain.MatchBroadcastRepository;
import com.scorestv.broadcasts.domain.TvChannel;
import com.scorestv.broadcasts.domain.TvChannelRepository;
import com.scorestv.common.ApiException;
import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.football.domain.League;
import com.scorestv.football.domain.LeagueRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin TV yayin yonetimi.
 *
 * <p>3 ayri kategori:
 * <ul>
 *   <li><b>Channels</b>: TV kanal master CRUD</li>
 *   <li><b>League defaults</b>: Lig+sezon+ulke icin varsayilan kanal atamasi</li>
 *   <li><b>Match overrides</b>: Tek bir mac icin ozel kanal atamasi</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/admin/broadcasts")
@PreAuthorize("hasRole('ADMIN')")
public class BroadcastsAdminController {

    private final TvChannelRepository channelRepository;
    private final LeagueBroadcasterRepository leagueRepository;
    private final MatchBroadcastRepository matchRepository;
    private final LeagueRepository leagueMasterRepository;
    private final FixtureRepository fixtureRepository;

    public BroadcastsAdminController(TvChannelRepository channelRepository,
                                     LeagueBroadcasterRepository leagueRepository,
                                     MatchBroadcastRepository matchRepository,
                                     LeagueRepository leagueMasterRepository,
                                     FixtureRepository fixtureRepository) {
        this.channelRepository = channelRepository;
        this.leagueRepository = leagueRepository;
        this.matchRepository = matchRepository;
        this.leagueMasterRepository = leagueMasterRepository;
        this.fixtureRepository = fixtureRepository;
    }

    // ============================================================
    // Channels CRUD
    // ============================================================

    @GetMapping("/channels")
    public List<TvChannel> listChannels(
            @RequestParam(required = false) String country) {
        if (country != null && !country.isBlank()) {
            return channelRepository.findByCountryCodeAndActiveTrueOrderBySortOrderAsc(
                    country.trim().toUpperCase());
        }
        return channelRepository.findByActiveTrueOrderByCountryCodeAscSortOrderAsc();
    }

    @PostMapping("/channels")
    @Transactional
    public TvChannel createChannel(@Valid @RequestBody ChannelInput input) {
        TvChannel ch = new TvChannel();
        applyChannel(ch, input);
        return channelRepository.save(ch);
    }

    @PutMapping("/channels/{id}")
    @Transactional
    public TvChannel updateChannel(@PathVariable Long id,
                                    @Valid @RequestBody ChannelInput input) {
        TvChannel ch = channelRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Kanal bulunamadi: " + id));
        applyChannel(ch, input);
        return channelRepository.save(ch);
    }

    @DeleteMapping("/channels/{id}")
    @Transactional
    public void deleteChannel(@PathVariable Long id) {
        if (!channelRepository.existsById(id)) {
            throw ApiException.notFound("Kanal bulunamadi: " + id);
        }
        channelRepository.deleteById(id);
    }

    private static void applyChannel(TvChannel ch, ChannelInput input) {
        ch.setName(input.name);
        ch.setNameTr(input.nameTr);
        ch.setShortName(input.shortName);
        ch.setLogoUrl(input.logoUrl);
        ch.setCountryCode(input.countryCode.toUpperCase());
        ch.setStreamingUrl(input.streamingUrl);
        ch.setStreamingOnly(input.streamingOnly != null && input.streamingOnly);
        ch.setSortOrder(input.sortOrder != null ? input.sortOrder : 100);
        ch.setActive(input.active == null || input.active);
    }

    // ============================================================
    // League broadcaster (default) CRUD
    // ============================================================

    @GetMapping("/league-broadcasters")
    public List<LeagueBroadcaster> listLeagueBroadcasters(
            @RequestParam Long leagueId) {
        return leagueRepository.findByLeagueIdOrderBySeasonDescSortOrderAsc(leagueId);
    }

    @PostMapping("/league-broadcasters")
    @Transactional
    public LeagueBroadcaster createLeagueBroadcaster(
            @Valid @RequestBody LeagueBroadcasterInput input) {
        League league = leagueMasterRepository.findById(input.leagueId)
                .orElseThrow(() -> ApiException.notFound("Lig bulunamadi: " + input.leagueId));
        TvChannel channel = channelRepository.findById(input.channelId)
                .orElseThrow(() -> ApiException.notFound("Kanal bulunamadi: " + input.channelId));

        LeagueBroadcaster entity = new LeagueBroadcaster();
        entity.setLeague(league);
        entity.setSeason(input.season);
        entity.setCountryCode(input.countryCode.toUpperCase());
        entity.setChannel(channel);
        entity.setSortOrder(input.sortOrder != null ? input.sortOrder : 100);
        entity.setNotes(input.notes);
        return leagueRepository.save(entity);
    }

    @DeleteMapping("/league-broadcasters/{id}")
    @Transactional
    public void deleteLeagueBroadcaster(@PathVariable Long id) {
        if (!leagueRepository.existsById(id)) {
            throw ApiException.notFound("Lig yayinci bulunamadi: " + id);
        }
        leagueRepository.deleteById(id);
    }

    // ============================================================
    // Match broadcast (override) CRUD
    // ============================================================

    @GetMapping("/match-broadcasts")
    public List<MatchBroadcast> listMatchBroadcasts(
            @RequestParam Long fixtureId) {
        return matchRepository.findByFixtureIdOrderBySortOrderAsc(fixtureId);
    }

    @PostMapping("/match-broadcasts")
    @Transactional
    public MatchBroadcast createMatchBroadcast(
            @Valid @RequestBody MatchBroadcastInput input) {
        Fixture fixture = fixtureRepository.findById(input.fixtureId)
                .orElseThrow(() -> ApiException.notFound(
                        "Fixture bulunamadi: " + input.fixtureId));
        TvChannel channel = channelRepository.findById(input.channelId)
                .orElseThrow(() -> ApiException.notFound(
                        "Kanal bulunamadi: " + input.channelId));

        MatchBroadcast entity = new MatchBroadcast();
        entity.setFixture(fixture);
        entity.setChannel(channel);
        entity.setCountryCode(input.countryCode.toUpperCase());
        entity.setSortOrder(input.sortOrder != null ? input.sortOrder : 100);
        entity.setNotes(input.notes);
        entity.setSource(input.source != null
                ? MatchBroadcast.BroadcastSource.valueOf(input.source)
                : MatchBroadcast.BroadcastSource.MANUAL);
        return matchRepository.save(entity);
    }

    @DeleteMapping("/match-broadcasts/{id}")
    @Transactional
    public void deleteMatchBroadcast(@PathVariable Long id) {
        if (!matchRepository.existsById(id)) {
            throw ApiException.notFound("Mac yayini bulunamadi: " + id);
        }
        matchRepository.deleteById(id);
    }

    // ============================================================
    // Input records
    // ============================================================

    public record ChannelInput(
            @NotBlank String name,
            String nameTr,
            String shortName,
            String logoUrl,
            @NotBlank String countryCode,
            String streamingUrl,
            Boolean streamingOnly,
            Integer sortOrder,
            Boolean active) {}

    public record LeagueBroadcasterInput(
            @NotNull Long leagueId,
            @NotNull Integer season,
            @NotBlank String countryCode,
            @NotNull Long channelId,
            Integer sortOrder,
            String notes) {}

    public record MatchBroadcastInput(
            @NotNull Long fixtureId,
            @NotNull Long channelId,
            @NotBlank String countryCode,
            Integer sortOrder,
            String notes,
            /** "MANUAL" / "LIVESOCCERTV" / "IMPORT". */
            String source) {}
}
