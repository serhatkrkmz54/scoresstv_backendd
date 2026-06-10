package com.scorestv.football.sync;

import com.scorestv.football.domain.Coach;
import com.scorestv.football.domain.CoachCareer;
import com.scorestv.football.domain.CoachCareerRepository;
import com.scorestv.football.domain.CoachRepository;
import com.scorestv.football.sync.dto.CoachApiDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Coach master tablo + career history upsert/replace.
 *
 * <p>Coach UPSERT: var olan id'yi guncelle, yoksa insert. Foto degistiyse
 * photo_key sifirla (re-mirror tetikleyici, players altyapisi gibi).
 *
 * <p>Career REPLACE: bir cagrida tam set gelir (API'nin doc'una gore),
 * tum eski satirlari sil + yenileri yaz.
 */
@Service
public class CoachUpserter {

    private final CoachRepository coachRepository;
    private final CoachCareerRepository careerRepository;

    public CoachUpserter(CoachRepository coachRepository,
                         CoachCareerRepository careerRepository) {
        this.coachRepository = coachRepository;
        this.careerRepository = careerRepository;
    }

    @Transactional
    public void upsert(CoachApiDto dto) {
        if (dto == null || dto.id() == null) {
            return;
        }
        Coach coach = coachRepository.findById(dto.id()).orElseGet(Coach::new);
        boolean isNew = coach.getId() == null;
        coach.setId(dto.id());
        coach.setName(dto.name());
        coach.setFirstname(dto.firstname());
        coach.setLastname(dto.lastname());
        coach.setAge(dto.age());
        coach.setNationality(dto.nationality());
        coach.setHeight(dto.height());
        coach.setWeight(dto.weight());
        if (dto.birth() != null) {
            coach.setBirthDate(parseDate(dto.birth().date()));
            coach.setBirthPlace(dto.birth().place());
            coach.setBirthCountry(dto.birth().country());
        }
        // Foto URL degistiyse photo_key sifirla → ImageMirrorService yeniden indirir
        if (dto.photo() != null && !dto.photo().equals(coach.getPhotoUrl())) {
            coach.setPhotoUrl(dto.photo());
            coach.setPhotoKey(null);
        } else if (isNew && dto.photo() != null) {
            coach.setPhotoUrl(dto.photo());
        }
        // NOT: currentTeamId BURADA set EDILMEZ. /coachs?team=X yaniti ayni
        // takima bagli birden cok coach doner (bas antrenor + altyapi +
        // asistan + gecmis interim'ler) — hepsinin dto.team().id() ayni geliyor.
        // Bu yuzden currentTeamId atamasi CoachesSyncService.syncByTeam icinde
        // "en yeni end=null kariyer entry'sine sahip coach" kuralina gore yapilir.
        coachRepository.save(coach);

        // Career REPLACE
        careerRepository.deleteByCoachId(coach.getId());
        if (dto.career() != null) {
            for (CoachApiDto.CareerEntry entry : dto.career()) {
                if (entry == null) continue;
                CoachCareer c = new CoachCareer();
                c.setCoach(coach);
                if (entry.team() != null) {
                    c.setTeamId(entry.team().id());
                    c.setTeamName(entry.team().name());
                    c.setTeamLogo(entry.team().logo());
                }
                c.setStartDate(parseDate(entry.start()));
                c.setEndDate(parseDate(entry.end()));
                careerRepository.save(c);
            }
        }
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
