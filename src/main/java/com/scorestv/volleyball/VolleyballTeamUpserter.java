package com.scorestv.volleyball;

import com.scorestv.common.SlugUtil;
import com.scorestv.volleyball.domain.VolleyballTeam;
import com.scorestv.volleyball.domain.VolleyballTeamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Voleybol takim entity'sinin {@link VbTeamDto} icinden upsert mantigini
 * ortakla.
 *
 * <p>Basketbol takiminden farkli olarak voleybolda code/founded/venue yok;
 * sadece country + national bayragi.
 */
@Component
public class VolleyballTeamUpserter {

    private static final Logger log = LoggerFactory.getLogger(VolleyballTeamUpserter.class);

    private final VolleyballTeamRepository teamRepo;

    public VolleyballTeamUpserter(VolleyballTeamRepository teamRepo) {
        this.teamRepo = teamRepo;
    }

    /** Hafif upsert — sadece id + name + logo. */
    public VolleyballTeam upsertMinimal(VbTeamDto dto) {
        if (dto == null || dto.id() == null || dto.name() == null) return null;
        VolleyballTeam team = teamRepo.findById(dto.id()).orElseGet(VolleyballTeam::new);
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
     * Tam profil upsert — country, national dahil. {@code nameTr} ASLA
     * dokunulmaz. {@code lastProfileSyncedAt} her cagrida guncellenir.
     */
    public VolleyballTeam upsertFromProfile(VbTeamDto dto) {
        if (dto == null || dto.id() == null || dto.name() == null) return null;

        VolleyballTeam team = teamRepo.findById(dto.id()).orElseGet(VolleyballTeam::new);
        team.setId(dto.id());
        team.setName(dto.name());

        if (dto.logo() != null) team.setLogo(dto.logo());
        if (dto.national() != null) team.setNational(dto.national());

        if (dto.country() != null) {
            var c = dto.country();
            if (c.name() != null) team.setCountryName(c.name());
            if (c.code() != null) team.setCountryCode(c.code());
            if (c.flag() != null) team.setCountryFlag(c.flag());
        }

        if (team.getSlug() == null || team.getSlug().isBlank()) {
            team.setSlug(SlugUtil.teamSlug(dto.name(), dto.id()));
        }

        team.setLastProfileSyncedAt(Instant.now());

        VolleyballTeam saved = teamRepo.save(team);
        log.debug("Voleybol takim profil upsert: id={} name={} country={}",
                saved.getId(), saved.getName(), saved.getCountryName());
        return saved;
    }
}
