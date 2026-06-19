package com.scorestv.broadcasts;

import com.scorestv.broadcasts.dto.BroadcastView;
import com.scorestv.broadcasts.dto.TsdbEventDto;
import com.scorestv.broadcasts.dto.TsdbTvDto;
import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maç TV yayını servisi. Fixture'ın takım adları + tarihiyle TheSportsDB
 * event'ini bulur ({@code idAPIfootball == fixtureId} ile doğrular), o event'i
 * yayınlayan TV kanallarını döner. Sonuçlar fixture bazında TTL cache'lenir
 * (ücretsiz kota 30 istek/dk).
 */
@Service
public class BroadcastService {

    /** Kullanıcının ülkesini öne almak için ISO-2 → TheSportsDB ülke adı. */
    private static final Map<String, String> CC_TO_NAME = Map.ofEntries(
            Map.entry("TR", "Turkey"), Map.entry("GB", "United Kingdom"),
            Map.entry("US", "United States"), Map.entry("DE", "Germany"),
            Map.entry("ES", "Spain"), Map.entry("FR", "France"),
            Map.entry("IT", "Italy"), Map.entry("NL", "Netherlands"),
            Map.entry("PT", "Portugal"), Map.entry("BE", "Belgium"),
            Map.entry("BR", "Brazil"), Map.entry("AR", "Argentina"),
            Map.entry("CA", "Canada"), Map.entry("AU", "Australia"),
            Map.entry("IE", "Ireland"),
            Map.entry("GR", "Greece"), Map.entry("RU", "Russia"),
            Map.entry("SA", "Saudi Arabia"), Map.entry("AE", "United Arab Emirates"));

    private final FixtureRepository fixtureRepository;
    private final TheSportsDbClient client;
    private final TheSportsDbProperties props;

    private final ConcurrentHashMap<Long, CacheEntry> cache = new ConcurrentHashMap<>();

    public BroadcastService(FixtureRepository fixtureRepository,
                            TheSportsDbClient client,
                            TheSportsDbProperties props) {
        this.fixtureRepository = fixtureRepository;
        this.client = client;
        this.props = props;
    }

    /**
     * Bir maçın TV kanalları. {@code country} (ISO-2) verilirse o ülkenin
     * kanalları listenin başına alınır. Eşleşme yoksa boş liste.
     */
    @Transactional(readOnly = true)
    public List<BroadcastView> forFixture(Long fixtureId, String country) {
        List<BroadcastView> all = baseList(fixtureId);
        if (all.isEmpty()) return all;

        String name = country == null ? null : CC_TO_NAME.get(country.trim().toUpperCase());
        if (name == null) return all;

        List<BroadcastView> mine = new ArrayList<>();
        List<BroadcastView> rest = new ArrayList<>();
        for (BroadcastView b : all) {
            if (name.equalsIgnoreCase(b.country())) {
                mine.add(b);
            } else {
                rest.add(b);
            }
        }
        mine.addAll(rest);
        return mine;
    }

    /**
     * Ülkeden bağımsız ham kanal listesi (cache'li). Event eşleştirme + TV
     * çağrıları yalnız cache dolarken yapılır.
     */
    private List<BroadcastView> baseList(Long fixtureId) {
        if (!props.enabled()) return List.of();

        CacheEntry cached = cache.get(fixtureId);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.data();
        }

        Fixture f = fixtureRepository.findById(fixtureId).orElse(null);
        if (f == null) return putAndReturn(fixtureId, List.of());

        String home = f.getHomeTeam() != null ? f.getHomeTeam().getName() : null;
        String away = f.getAwayTeam() != null ? f.getAwayTeam().getName() : null;
        if (home == null || away == null || f.getKickoffAt() == null) {
            return putAndReturn(fixtureId, List.of());
        }

        String date = LocalDate
                .ofInstant(f.getKickoffAt(), ZoneId.of(props.timezone()))
                .toString(); // YYYY-MM-DD

        LinkedHashMap<String, BroadcastView> dedup = new LinkedHashMap<>();

        // 1) TheSportsDB event'ini eşleştir (isim+idAPIfootball; isim farkında
        //    "Czechia" vs "Czech Republic" gün listesi yedeği devreye girer).
        TsdbEventDto matched = client.matchEvent(home, away, date, fixtureId);
        String idEvent = matched != null ? matched.idEvent() : null;

        // 2) Event bulunduysa o event'in TV kanallarını ekle (kanal+ülke tekille).
        if (idEvent != null && !idEvent.isBlank()) {
            for (TsdbTvDto t : client.lookupTv(idEvent)) {
                if (t.strChannel() == null || t.strChannel().isBlank()) continue;
                String ch = t.strChannel().trim();
                String cn = (t.strCountry() == null || t.strCountry().isBlank())
                        ? null : t.strCountry().trim();
                dedup.putIfAbsent(keyOf(cn, ch),
                        new BroadcastView(ch, cn, emptyToNull(t.strLogo())));
            }
        }

        List<BroadcastView> out = new ArrayList<>(dedup.values());
        out.sort(Comparator.comparing(
                b -> b.country() == null ? "" : b.country(),
                String.CASE_INSENSITIVE_ORDER));
        return putAndReturn(fixtureId, out);
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /** Kanal+ülke tekilleştirme anahtarı (büyük/küçük harf duyarsız). */
    private static String keyOf(String country, String channel) {
        return (country == null ? "" : country.toLowerCase()) + "|" + channel.toLowerCase();
    }

    private List<BroadcastView> putAndReturn(Long fixtureId, List<BroadcastView> items) {
        long ttlMin = items.isEmpty()
                ? props.emptyCacheTtlMinutes()
                : props.cacheTtlMinutes();
        cache.put(fixtureId, new CacheEntry(
                items, Instant.now().plus(Duration.ofMinutes(ttlMin))));
        return items;
    }

    private record CacheEntry(List<BroadcastView> data, Instant expiresAt) {}
}
