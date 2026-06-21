package com.scorestv.football.sync;

import com.scorestv.football.domain.PlayerSidelined;
import com.scorestv.football.domain.PlayerSidelinedRepository;
import com.scorestv.football.sync.dto.SidelinedApiDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Tek bir oyuncunun sakatlik/cezalik gecmisini REPLACE pattern ile yazar.
 * UNIQUE (player_id, type, start_date) kisitlamasi var.
 */
@Service
public class SidelinedUpserter {

    private static final Logger log = LoggerFactory.getLogger(SidelinedUpserter.class);

    private final PlayerSidelinedRepository repository;

    public SidelinedUpserter(PlayerSidelinedRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public int upsert(Long playerId, List<SidelinedApiDto> items) {
        if (playerId == null) return 0;
        // Veri-kaybi korumasi: API bos dondurduyse mevcut kayitlari SILME.
        // Once bos-guard, sonra replace.
        if (items == null || items.isEmpty()) {
            return 0;
        }
        repository.deleteByPlayerId(playerId);
        int written = 0;
        for (SidelinedApiDto dto : items) {
            if (dto == null || dto.type() == null) continue;
            PlayerSidelined entity = new PlayerSidelined();
            entity.setPlayerId(playerId);
            entity.setType(dto.type());
            entity.setStartDate(parseDate(dto.start()));
            entity.setEndDate(parseDate(dto.end()));
            try {
                repository.save(entity);
                written++;
            } catch (DataIntegrityViolationException dup) {
                log.debug("Sidelined duplicate (UNIQUE): playerId={} type={} start={}",
                        playerId, dto.type(), dto.start());
            }
        }
        return written;
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException ex) {
            log.warn("Sidelined tarih parse hatasi: {}", raw);
            return null;
        }
    }
}
