package com.scorestv.football.sync;

import com.scorestv.football.domain.Transfer;
import com.scorestv.football.domain.TransferRepository;
import com.scorestv.football.sync.dto.TransferApiDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Transfer kayitlarini DB'ye yazar. Idempotent — UNIQUE(player, date,
 * in_team, out_team) duplicate'ler atilir. Player master upsert edilir.
 */
@Service
public class TransfersUpserter {

    private static final Logger log = LoggerFactory.getLogger(TransfersUpserter.class);

    private final TransferRepository repository;
    private final PlayerUpserter playerUpserter;

    public TransfersUpserter(TransferRepository repository,
                             PlayerUpserter playerUpserter) {
        this.repository = repository;
        this.playerUpserter = playerUpserter;
    }

    /**
     * Transferleri yazar. Pre-check ile dup atlanir — kesinlikle exception
     * firlamasi engellenir, boylelikle tx poisoning olmaz ve tum kalan
     * satirlar yazilir.
     *
     * <p><b>Onceki bug:</b> {@code save()} cagrisinda
     * {@code DataIntegrityViolationException} catch ediliyordu ama Hibernate
     * tx'i rollbackOnly olarak isaretler — sonraki tum save'ler commit anida
     * rollback'e takilirdi. ~800 transfer'in sadece ilk 13'u DB'ye yazilabiliyordu.
     */
    @Transactional
    public int upsert(List<TransferApiDto> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        int written = 0;
        int skipped = 0;
        for (TransferApiDto item : items) {
            if (item == null || item.player() == null || item.player().id() == null) {
                continue;
            }
            Long playerId = item.player().id();
            String playerName = item.player().name();
            // Player master upsert (foto bilinmiyor — null gecer)
            playerUpserter.upsert(playerId, playerName, null);

            if (item.transfers() == null) {
                continue;
            }
            for (TransferApiDto.TransferEntry entry : item.transfers()) {
                if (entry == null) {
                    continue;
                }
                Transfer t = toEntity(playerId, playerName, entry);
                if (t == null) {
                    continue;
                }
                // PRE-CHECK: dup'i DB sorgusuyla tespit et, save() fail
                // ettirme — UNIQUE exception tx'i kirletir, sonraki tum save'ler
                // commit anida rollback'e takilir.
                if (repository.existsUnique(t.getPlayerId(), t.getTransferDate(),
                        t.getInTeamId(), t.getOutTeamId())) {
                    skipped++;
                    continue;
                }
                repository.save(t);
                written++;
            }
        }
        if (skipped > 0) {
            log.debug("Transfers upsert: {} yeni, {} mevcut (atlandi)", written, skipped);
        }
        return written;
    }

    private Transfer toEntity(Long playerId, String playerName,
                              TransferApiDto.TransferEntry entry) {
        Transfer t = new Transfer();
        t.setPlayerId(playerId);
        t.setPlayerName(playerName);
        t.setTransferDate(parseDate(entry.date()));
        t.setTransferType(entry.type());
        TransferApiDto.TeamsPair teams = entry.teams();
        if (teams != null) {
            if (teams.in() != null) {
                t.setInTeamId(teams.in().id());
                t.setInTeamName(teams.in().name());
                t.setInTeamLogo(teams.in().logo());
            }
            if (teams.out() != null) {
                t.setOutTeamId(teams.out().id());
                t.setOutTeamName(teams.out().name());
                t.setOutTeamLogo(teams.out().logo());
            }
        }
        return t;
    }

    private LocalDate parseDate(String value) {
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
