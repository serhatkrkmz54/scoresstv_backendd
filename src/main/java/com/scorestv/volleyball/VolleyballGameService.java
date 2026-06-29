package com.scorestv.volleyball;

import com.scorestv.storage.MinioStorageService;
import com.scorestv.volleyball.domain.VolleyballGameRepository;
import com.scorestv.volleyball.web.VolleyballGameView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Voleybol maclarini mobile'a sunan okuma servisi (DB'den). */
@Service
public class VolleyballGameService {

    /** Canli (in-play) voleybol durum kodlari. */
    private static final Set<String> LIVE_STATUSES =
            Set.of("S1", "S2", "S3", "S4", "S5");

    private final VolleyballGameRepository gameRepo;
    private final VolleyballProperties props;
    private final MinioStorageService storage;

    public VolleyballGameService(VolleyballGameRepository gameRepo,
                                 VolleyballProperties props,
                                 MinioStorageService storage) {
        this.gameRepo = gameRepo;
        this.props = props;
        this.storage = storage;
    }

    /** Logo/bayrak cozucu: key varsa CDN URL, yoksa API URL. */
    private VolleyballGameView.LogoResolver logoResolver() {
        return (key, apiUrl) -> key != null ? storage.publicUrl(key) : apiUrl;
    }

    /** Bir takvim gununun (yapilandirilan timezone'da) maclari. */
    @Transactional(readOnly = true)
    public List<VolleyballGameView> byDate(LocalDate date, boolean turkish) {
        ZoneId zone = ZoneId.of(props.timezone());
        Instant start = date.atStartOfDay(zone).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(zone).toInstant();
        var logo = logoResolver();
        return gameRepo.findDayWithDetails(start, end).stream()
                .map(g -> VolleyballGameView.from(g, turkish, logo))
                .toList();
    }

    /** Su an canli maclar. */
    @Transactional(readOnly = true)
    public List<VolleyballGameView> live(boolean turkish) {
        var logo = logoResolver();
        return gameRepo.findByStatusWithDetails(LIVE_STATUSES).stream()
                .map(g -> VolleyballGameView.from(g, turkish, logo))
                .toList();
    }

    /** Tek mac. */
    @Transactional(readOnly = true)
    public Optional<VolleyballGameView> byId(Long id, boolean turkish) {
        var logo = logoResolver();
        return gameRepo.findOneWithDetails(id)
                .map(g -> VolleyballGameView.from(g, turkish, logo));
    }

    /** Id listesindeki maclar (favoriler tabi). Tarihe gore sirali. */
    @Transactional(readOnly = true)
    public List<VolleyballGameView> byIds(Collection<Long> ids, boolean turkish) {
        if (ids == null || ids.isEmpty()) return List.of();
        var logo = logoResolver();
        return gameRepo.findByIdsWithDetails(ids).stream()
                .map(g -> VolleyballGameView.from(g, turkish, logo))
                .toList();
    }

    /**
     * Bugunun POPULER lig maclari — ana ekran widget'i icin. Canli → yaklasan →
     * biten sirali, {@code limit} ile sinirli. Populer lig listesi BOSSA bugunun
     * TUM voleybolu kullanilir (voleybol az oldugu icin guvenli fallback).
     */
    @Transactional(readOnly = true)
    public List<VolleyballGameView> popularToday(boolean turkish, int limit) {
        LocalDate today = LocalDate.now(ZoneId.of(props.timezone()));
        List<VolleyballGameView> dayGames = byDate(today, turkish);
        List<Long> ids = props.serving().popularLeagueIds();
        List<VolleyballGameView> base = dayGames;
        if (ids != null && !ids.isEmpty()) {
            Set<Long> popular = new HashSet<>(ids);
            List<VolleyballGameView> filtered = dayGames.stream()
                    .filter(g -> g.league() != null && popular.contains(g.league().id()))
                    .toList();
            // Populer ligde bugun mac yoksa voleybol bos kalmasin diye tumune dus.
            if (!filtered.isEmpty()) base = filtered;
        }
        return base.stream()
                .sorted(Comparator
                        .comparingInt((VolleyballGameView g) -> statusRank(g.status().shortCode()))
                        .thenComparing(VolleyballGameView::startAt,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(limit > 0 ? limit : Long.MAX_VALUE)
                .toList();
    }

    /** Sira anahtari: canli=0, baslamamis=1, biten/diger=2. */
    private static int statusRank(String shortCode) {
        if (LIVE_STATUSES.contains(shortCode)) return 0;
        if ("NS".equals(shortCode)) return 1;
        return 2;
    }
}
