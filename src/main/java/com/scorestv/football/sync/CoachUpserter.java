package com.scorestv.football.sync;

import com.scorestv.football.domain.Coach;
import com.scorestv.football.domain.CoachCareer;
import com.scorestv.football.domain.CoachCareerRepository;
import com.scorestv.football.domain.CoachRepository;
import com.scorestv.football.sync.dto.CoachApiDto;
import com.scorestv.search.events.EntityIndexedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Set;

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
    private final ApplicationEventPublisher events;

    public CoachUpserter(CoachRepository coachRepository,
                         CoachCareerRepository careerRepository,
                         ApplicationEventPublisher events) {
        this.coachRepository = coachRepository;
        this.careerRepository = careerRepository;
        this.events = events;
    }

    // REQUIRES_NEW: her coach kendi tx'inde upsert edilir. Boylece bir coach'un
    // hatasi (dup career, master race) cagiran CoachesSyncService.syncByTeam
    // tx'ini KIRLETMEZ; aksi halde ilk hatadan sonra kalan tum coach'larin
    // sorgulari 25P02 (current transaction is aborted) verirdi.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
            // uq_coach_career_unique (coach_id, team_id, start_date): API ayni
            // anahtari kariyer listesinde tekrar dondurebiliyor. Dup'i parti
            // icinde ele — yoksa ikinci insert 23505 verip tx'i kirletirdi.
            Set<String> seen = new HashSet<>();
            for (CoachApiDto.CareerEntry entry : dto.career()) {
                if (entry == null) continue;
                Long teamId = entry.team() != null ? entry.team().id() : null;
                LocalDate start = parseDate(entry.start());
                if (teamId != null && start != null
                        && !seen.add(teamId + "|" + start)) {
                    continue;
                }
                CoachCareer c = new CoachCareer();
                c.setCoach(coach);
                if (entry.team() != null) {
                    c.setTeamId(entry.team().id());
                    c.setTeamName(entry.team().name());
                    c.setTeamLogo(entry.team().logo());
                }
                c.setStartDate(start);
                c.setEndDate(parseDate(entry.end()));
                careerRepository.save(c);
            }
        }

        // ES koç arama indeksi — tx commit sonrasi @Async indexlenir
        // (SearchIndexEventListener.onCoachIndexed). ES kapaliysa NOOP.
        // NOT: currentTeamId burada henuz set degildir (CoachesSyncService
        // sonra atar + tekrar event yayinlar); deep-search ile gelen koçta
        // ise zaten null kalir.
        events.publishEvent(new EntityIndexedEvent.CoachIndexed(coach));
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
