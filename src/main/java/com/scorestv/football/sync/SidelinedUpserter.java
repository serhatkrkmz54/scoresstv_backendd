package com.scorestv.football.sync;

import com.scorestv.football.domain.PlayerSidelined;
import com.scorestv.football.domain.PlayerSidelinedRepository;
import com.scorestv.football.sync.dto.SidelinedApiDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        if (items == null || items.isEmpty()) {
            return 0;
        }
        repository.deleteByPlayerId(playerId);

        // API AYNI cevapta tekrar eden (type, start_date) satirlar dondurebiliyor.
        // Boyle bir satiri ikinci kez INSERT edince UNIQUE (player_id, type,
        // start_date) ihlal edilir ve Postgres tum transaction'i abort eder
        // (25P02) — Java tarafi catch etse bile is islemez. Bu yuzden yazmadan
        // ONCE (type|start) anahtarina gore tekillestiriyoruz; boylece ihlal
        // HIC olusmaz. start_date null olanlar UNIQUE'te cakismaz (Postgres
        // null'lari ayri sayar) → her birine benzersiz anahtar verilir.
        final Map<String, PlayerSidelined> byKey = new LinkedHashMap<>();
        for (SidelinedApiDto dto : items) {
            if (dto == null || dto.type() == null) continue;
            final LocalDate start = parseDate(dto.start());
            final String key = (start == null)
                    ? "null#" + byKey.size()
                    : dto.type() + '|' + start;
            final PlayerSidelined entity = new PlayerSidelined();
            entity.setPlayerId(playerId);
            entity.setType(dto.type());
            entity.setStartDate(start);
            entity.setEndDate(parseDate(dto.end()));
            // Ayni anahtar tekrar gelirse SONUNCU kazanir (daha guncel/tam veri).
            byKey.put(key, entity);
        }
        repository.saveAll(byKey.values());
        return byKey.size();
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
