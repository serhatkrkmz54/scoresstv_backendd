package com.scorestv.basketball;

import com.scorestv.basketball.domain.BasketballTeam;
import com.scorestv.basketball.domain.BasketballTeamRepository;
import com.scorestv.common.SlugUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Basketbol takim entity'sinin {@link BkTeamDto} (gerek {@code /teams?league=X}
 * gerek {@code /teams?id=X} yanitlari) icinden upsert mantigini ortakla.
 *
 * <p>{@link BasketballTeamSyncService} hafif upsert (id+name+logo) icin
 * inline kod kullaniyor; bu sinif daha kapsamli — country/founded/venue
 * alanlarini da doldurur ve slug uretir.
 *
 * <p>Cagri uclari:
 * <ul>
 *   <li>{@link BasketballTeamProfileSyncService} — {@code /teams?id=X} ile cagrir.
 *   <li>{@link BasketballTeamStatisticsSyncService} — DTO icindeki team blogundan
 *       fallback olarak (bos takim icin profil verisi gelmis ise).
 * </ul>
 */
@Component
public class BasketballTeamUpserter {

    private static final Logger log = LoggerFactory.getLogger(BasketballTeamUpserter.class);

    private final BasketballTeamRepository teamRepo;

    public BasketballTeamUpserter(BasketballTeamRepository teamRepo) {
        this.teamRepo = teamRepo;
    }

    /**
     * Hafif upsert — sadece id + name + logo (mevcut sync inline kodu icin).
     * Eski alanlar (country/founded/venue) korunur — yalniz NULL ise dolar.
     */
    public BasketballTeam upsertMinimal(BkTeamDto dto) {
        if (dto == null || dto.id() == null || dto.name() == null) return null;
        BasketballTeam team = teamRepo.findById(dto.id()).orElseGet(BasketballTeam::new);
        team.setId(dto.id());
        team.setName(dto.name());
        if (dto.logo() != null) {
            team.setLogo(dto.logo());
        }
        if (team.getSlug() == null || team.getSlug().isBlank()) {
            team.setSlug(SlugUtil.teamSlug(dto.name(), dto.id()));
        }
        return teamRepo.save(team);
    }

    /**
     * Tam profil upsert — country, founded, code, national, venue dahil.
     * {@code /teams?id=X} cevabindaki tum alanlari yazar.
     *
     * <p>{@code nameTr} ASLA dokunulmaz (manuel ceviri). {@code logoKey}
     * (MinIO mirror) korunur — logo URL'i degisirse mirror job tekrar isler.
     *
     * <p>{@code lastProfileSyncedAt} her cagrida guncellenir — freshness gate.
     */
    public BasketballTeam upsertFromProfile(BkTeamDto dto) {
        if (dto == null || dto.id() == null || dto.name() == null) return null;

        BasketballTeam team = teamRepo.findById(dto.id()).orElseGet(BasketballTeam::new);
        team.setId(dto.id());
        team.setName(dto.name());

        if (dto.logo() != null) team.setLogo(dto.logo());
        if (dto.code() != null) team.setCode(dto.code());
        if (dto.founded() != null) team.setFounded(dto.founded());
        if (dto.national() != null) team.setNational(dto.national());

        if (dto.country() != null) {
            var c = dto.country();
            if (c.name() != null) team.setCountryName(c.name());
            if (c.code() != null) team.setCountryCode(c.code());
            if (c.flag() != null) team.setCountryFlag(c.flag());
        }

        if (dto.venue() != null) {
            var v = dto.venue();
            if (v.name() != null) team.setVenueName(v.name());
            if (v.city() != null) team.setVenueCity(v.city());
            if (v.capacity() != null) team.setVenueCapacity(v.capacity());
        }

        if (team.getSlug() == null || team.getSlug().isBlank()) {
            team.setSlug(SlugUtil.teamSlug(dto.name(), dto.id()));
        }

        team.setLastProfileSyncedAt(Instant.now());

        BasketballTeam saved = teamRepo.save(team);
        log.debug("Basketbol takim profil upsert: id={} name={} country={}",
                saved.getId(), saved.getName(), saved.getCountryName());
        return saved;
    }
}
