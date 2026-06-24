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
}
