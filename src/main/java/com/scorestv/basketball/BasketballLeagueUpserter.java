package com.scorestv.basketball;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.scorestv.basketball.domain.BasketballLeague;
import com.scorestv.basketball.domain.BasketballLeagueRepository;
import com.scorestv.common.SlugUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * API-Basketball {@code /leagues?id=X} cevabini DB'ye yazan full info
 * upserter. Mevcut {@code BasketballSyncService#upsertLeague} mac
 * senkronundan minimal upsert yapar; bu sinif tek lig icin TUM alanlari
 * (sezonlar JSONB, slug, ulke kodu) doldurur.
 *
 * <p>Sadece API'den gelen NON-NULL alanlari yazar; mevcut name_tr,
 * country_name_tr, covered bayragi, logoKey gibi manuel/admin alanlari
 * korunur. nameTr "siliniyor" gibi durumlarin onune gecer.
 *
 * <p>{@code lastInfoSyncedAt} update edilir — lazy refresh freshness'i
 * bu alani kontrol eder.
 */
@Component
public class BasketballLeagueUpserter {

    private static final Logger log = LoggerFactory.getLogger(BasketballLeagueUpserter.class);

    /**
     * Lokal ObjectMapper — Spring DI'da custom {@code RedisConfig} mapper'i
     * primary olmadigindan global ObjectMapper bean'ina dayanmiyoruz. Bu mapper
     * sadece seasons listesi -> JSON string donusumunde kullaniliyor;
     * hicbir custom config gerekmez.
     */
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final BasketballLeagueRepository leagueRepo;

    public BasketballLeagueUpserter(BasketballLeagueRepository leagueRepo) {
        this.leagueRepo = leagueRepo;
    }

    /**
     * Tek lig DTO'sundan full upsert.
     *
     * <p>Davranis:
     * <ul>
     *   <li>Mevcut entity bulunursa update; yoksa yeni olusturulur.
     *   <li>name/type/logo/country alanlari sadece API'de NON-NULL ise overwrite.
     *   <li>Slug yoksa veya isim degistiyse {@link SlugUtil#leagueSlug} ile uretilir.
     *   <li>Sezonlar listesi JSONB olarak serialize edilip seasonsJson'a yazilir.
     *   <li>{@code currentSeason} -- seasons[].current=true olan ilk eleman.
     *   <li>{@code lastInfoSyncedAt = now}.
     * </ul>
     *
     * @return upsert edilmis entity
     */
    @Transactional
    public BasketballLeague upsertFromApi(BkLeagueDto dto) {
        if (dto == null || dto.id() == null) {
            log.warn("BasketballLeagueUpserter: null DTO veya id");
            return null;
        }

        BasketballLeague e = leagueRepo.findById(dto.id()).orElseGet(BasketballLeague::new);
        boolean isNew = e.getId() == null;
        e.setId(dto.id());

        if (dto.name() != null) e.setName(dto.name());
        if (dto.type() != null) e.setType(dto.type());
        if (dto.logo() != null) e.setLogo(dto.logo());

        // Ulke alanlari — sadece API'de varsa overwrite (countryNameTr korunur)
        BkLeagueDto.Country c = dto.country();
        if (c != null) {
            if (c.name() != null) e.setCountryName(c.name());
            if (c.code() != null) e.setCountryCode(c.code());
            if (c.flag() != null) e.setCountryFlag(c.flag());
        }

        // Slug — yoksa veya isim degistiyse uret
        if (e.getSlug() == null || e.getSlug().isBlank()) {
            e.setSlug(SlugUtil.leagueSlug(e.getName(), e.getId()));
        }

        // Sezonlar
        if (dto.seasons() != null && !dto.seasons().isEmpty()) {
            try {
                e.setSeasonsJson(JSON_MAPPER.writeValueAsString(dto.seasons()));
            } catch (JsonProcessingException ex) {
                log.warn("BasketballLeague seasons JSON serialize hatasi league={}: {}",
                        e.getId(), ex.getMessage());
            }
            // Current sezon — bayrakli olani al, yoksa son listedeki
            String currentSeason = pickCurrentSeason(dto.seasons());
            if (currentSeason != null) e.setCurrentSeason(currentSeason);
        }

        e.setLastInfoSyncedAt(Instant.now());

        BasketballLeague saved = leagueRepo.save(e);
        if (isNew) {
            log.info("BasketballLeague YENI eklendi id={} name={} slug={}",
                    saved.getId(), saved.getName(), saved.getSlug());
        } else {
            log.debug("BasketballLeague tazelendi id={} seasons={}",
                    saved.getId(),
                    dto.seasons() != null ? dto.seasons().size() : 0);
        }
        return saved;
    }

    /**
     * Sezonlar listesinde {@code current=true} olani bul. Birden fazla varsa
     * en son geleni alir (API'nin sirasi tipik olarak eskiden yeniye).
     */
    private static String pickCurrentSeason(List<BkLeagueDto.Season> seasons) {
        String best = null;
        for (var s : seasons) {
            if (s != null && Boolean.TRUE.equals(s.current()) && s.season() != null) {
                best = s.season();
            }
        }
        // current=true yoksa son sezonu fallback al
        if (best == null && !seasons.isEmpty()) {
            var last = seasons.get(seasons.size() - 1);
            if (last != null) best = last.season();
        }
        return best;
    }
}
