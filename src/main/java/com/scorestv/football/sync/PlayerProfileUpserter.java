package com.scorestv.football.sync;

import com.scorestv.football.domain.Player;
import com.scorestv.football.domain.PlayerRepository;
import com.scorestv.football.sync.dto.PlayerSeasonApiDto;
import com.scorestv.search.events.EntityIndexedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Player master tablosuna TAM profil yazar (firstname/lastname/age/birth/
 * height/weight/nationality/injured). {@link PlayerUpserter}'in genis versiyonu —
 * o yalniz id+name+photo upsert eder; bu /players?id=X yanitindan tam profili
 * alir.
 *
 * <p>Player detay sayfasinda kullanilir. Squad/lineup/event/stats gibi
 * yerlerden de hala PlayerUpserter cagrilir (minimal alanlarla); detay
 * sayfasi acildiginda PlayerProfileUpserter ile tam doldurulur.
 */
@Service
public class PlayerProfileUpserter {

    private final PlayerRepository playerRepository;
    private final ApplicationEventPublisher events;

    public PlayerProfileUpserter(PlayerRepository playerRepository,
                                 ApplicationEventPublisher events) {
        this.playerRepository = playerRepository;
        this.events = events;
    }

    @Transactional
    public void upsert(PlayerSeasonApiDto.Player dto) {
        if (dto == null || dto.id() == null) return;
        Player player = playerRepository.findById(dto.id()).orElseGet(Player::new);
        boolean isNew = player.getId() == null;
        player.setId(dto.id());
        player.setName(dto.name());
        player.setFirstname(dto.firstname());
        player.setLastname(dto.lastname());
        player.setAge(dto.age());
        player.setNationality(dto.nationality());
        player.setHeight(dto.height());
        player.setWeight(dto.weight());
        player.setInjured(dto.injured());
        if (dto.birth() != null) {
            player.setBirthDate(parseDate(dto.birth().date()));
            player.setBirthPlace(dto.birth().place());
            player.setBirthCountry(dto.birth().country());
        }
        // Foto URL degisirse mirror key sifirla → ImageMirrorService yeniden indirir
        if (dto.photo() != null && !dto.photo().equals(player.getPhotoUrl())) {
            player.setPhotoUrl(dto.photo());
            player.setPhotoKey(null);
        } else if (isNew && dto.photo() != null) {
            player.setPhotoUrl(dto.photo());
        }
        playerRepository.save(player);
        // ES indeksini guncelle.
        events.publishEvent(new EntityIndexedEvent.PlayerIndexed(player));
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
