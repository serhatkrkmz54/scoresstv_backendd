package com.scorestv.basketball;

import com.scorestv.basketball.domain.BasketballGameRepository;
import com.scorestv.basketball.web.BasketballGameView;
import com.scorestv.storage.MinioStorageService;
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

/** Basketbol maçlarını mobile'a sunan okuma servisi (DB'den). */
@Service
public class BasketballGameService {

    /** Canlı (in-play) basketbol durum kodları. */
    private static final Set<String> LIVE_STATUSES =
            Set.of("Q1", "Q2", "Q3", "Q4", "OT", "BT", "HT");

    private final BasketballGameRepository gameRepo;
    private final BasketballProperties props;
    private final MinioStorageService storage;

    public BasketballGameService(BasketballGameRepository gameRepo,
                                 BasketballProperties props,
                                 MinioStorageService storage) {
        this.gameRepo = gameRepo;
        this.props = props;
        this.storage = storage;
    }

    /** Logo/bayrak çözücü: key varsa CDN URL, yoksa API URL. */
    private BasketballGameView.LogoResolver logoResolver() {
        return (key, apiUrl) -> key != null ? storage.publicUrl(key) : apiUrl;
    }

    /** Bir takvim gününün (yapılandırılan timezone'da) maçları. */
    @Transactional(readOnly = true)
    public List<BasketballGameView> byDate(LocalDate date, boolean turkish) {
        ZoneId zone = ZoneId.of(props.timezone());
        Instant start = date.atStartOfDay(zone).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(zone).toInstant();
        var logo = logoResolver();
        return gameRepo.findDayWithDetails(start, end).stream()
                .map(g -> BasketballGameView.from(g, turkish, logo))
                .toList();
    }

    /** Şu an canlı maçlar. */
    @Transactional(readOnly = true)
    public List<BasketballGameView> live(boolean turkish) {
        var logo = logoResolver();
        return gameRepo.findByStatusWithDetails(LIVE_STATUSES).stream()
                .map(g -> BasketballGameView.from(g, turkish, logo))
                .toList();
    }

    /** Tek maç. */
    @Transactional(readOnly = true)
    public Optional<BasketballGameView> byId(Long id, boolean turkish) {
        var logo = logoResolver();
        return gameRepo.findOneWithDetails(id)
                .map(g -> BasketballGameView.from(g, turkish, logo));
    }

    /** Id listesindeki maçlar (favoriler tabı). Tarihe göre sıralı. */
    @Transactional(readOnly = true)
    public List<BasketballGameView> byIds(Collection<Long> ids, boolean turkish) {
        if (ids == null || ids.isEmpty()) return List.of();
        var logo = logoResolver();
        return gameRepo.findByIdsWithDetails(ids).stream()
                .map(g -> BasketballGameView.from(g, turkish, logo))
                .toList();
    }

    /**
     * Bugünün POPÜLER lig maçları — ana ekran widget'ı için. Canlı → yaklaşan →
     * biten sıralı, {@code limit} ile sınırlı. Popüler lig listesi boşsa boş döner.
     */
    @Transactional(readOnly = true)
    public List<BasketballGameView> popularToday(boolean turkish, int limit) {
        List<Long> ids = props.serving().popularLeagueIds();
        if (ids == null || ids.isEmpty()) return List.of();
        Set<Long> popular = new HashSet<>(ids);
        LocalDate today = LocalDate.now(ZoneId.of(props.timezone()));
        return byDate(today, turkish).stream()
                .filter(g -> g.league() != null && popular.contains(g.league().id()))
                .sorted(Comparator
                        .comparingInt((BasketballGameView g) -> statusRank(g.status().shortCode()))
                        .thenComparing(BasketballGameView::startAt,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(limit > 0 ? limit : Long.MAX_VALUE)
                .toList();
    }

    /** Sıra anahtarı: canlı=0, başlamamış=1, biten/diğer=2. */
    private static int statusRank(String shortCode) {
        if (LIVE_STATUSES.contains(shortCode)) return 0;
        if ("NS".equals(shortCode)) return 1;
        return 2;
    }
}
