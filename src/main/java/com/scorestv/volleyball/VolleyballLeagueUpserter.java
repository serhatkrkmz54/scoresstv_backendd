package com.scorestv.volleyball;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.scorestv.common.SlugUtil;
import com.scorestv.volleyball.domain.VolleyballLeague;
import com.scorestv.volleyball.domain.VolleyballLeagueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * API-Volleyball {@code /leagues?id=X} cevabini DB'ye yazan full info upserter.
 *
 * <p>Sadece API'den gelen NON-NULL alanlari yazar; mevcut name_tr,
 * country_name_tr, covered bayragi, logoKey gibi manuel/admin alanlari
 * korunur. {@code lastInfoSyncedAt} update edilir.
 */
@Component
public class VolleyballLeagueUpserter {

    private static final Logger log = LoggerFactory.getLogger(VolleyballLeagueUpserter.class);

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final VolleyballLeagueRepository leagueRepo;

    public VolleyballLeagueUpserter(VolleyballLeagueRepository leagueRepo) {
        this.leagueRepo = leagueRepo;
    }

    @Transactional
    public VolleyballLeague upsertFromApi(VbLeagueDto dto) {
        if (dto == null || dto.id() == null) {
            log.warn("VolleyballLeagueUpserter: null DTO veya id");
            return null;
        }

        VolleyballLeague e = leagueRepo.findById(dto.id()).orElseGet(VolleyballLeague::new);
        boolean isNew = e.getId() == null;
        e.setId(dto.id());

        if (dto.name() != null) e.setName(dto.name());
        if (dto.type() != null) e.setType(dto.type());
        if (dto.logo() != null) e.setLogo(dto.logo());

        VbLeagueDto.Country c = dto.country();
        if (c != null) {
            if (c.name() != null) e.setCountryName(c.name());
            if (c.code() != null) e.setCountryCode(c.code());
            if (c.flag() != null) e.setCountryFlag(c.flag());
        }

        if (e.getSlug() == null || e.getSlug().isBlank()) {
            e.setSlug(SlugUtil.leagueSlug(e.getName(), e.getId()));
        }

        if (dto.seasons() != null && !dto.seasons().isEmpty()) {
            try {
                e.setSeasonsJson(JSON_MAPPER.writeValueAsString(dto.seasons()));
            } catch (JsonProcessingException ex) {
                log.warn("VolleyballLeague seasons JSON serialize hatasi league={}: {}",
                        e.getId(), ex.getMessage());
            }
            String currentSeason = pickCurrentSeason(dto.seasons());
            if (currentSeason != null) e.setCurrentSeason(currentSeason);
        }

        e.setLastInfoSyncedAt(Instant.now());

        VolleyballLeague saved = leagueRepo.save(e);
        if (isNew) {
            log.info("VolleyballLeague YENI eklendi id={} name={} slug={}",
                    saved.getId(), saved.getName(), saved.getSlug());
        } else {
            log.debug("VolleyballLeague tazelendi id={} seasons={}",
                    saved.getId(), dto.seasons() != null ? dto.seasons().size() : 0);
        }
        return saved;
    }

    /** {@code current=true} olan sezon; yoksa listedeki son (en yeni) sezon. */
    private static String pickCurrentSeason(List<VbLeagueDto.Season> seasons) {
        String best = null;
        for (var s : seasons) {
            if (s != null && Boolean.TRUE.equals(s.current()) && s.seasonAsString() != null) {
                best = s.seasonAsString();
            }
        }
        if (best == null && !seasons.isEmpty()) {
            var last = seasons.get(seasons.size() - 1);
            if (last != null) best = last.seasonAsString();
        }
        return best;
    }
}
