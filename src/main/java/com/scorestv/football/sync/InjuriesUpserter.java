package com.scorestv.football.sync;

import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.football.domain.Injury;
import com.scorestv.football.domain.InjuryRepository;
import com.scorestv.football.domain.Team;
import com.scorestv.football.domain.TeamRepository;
import com.scorestv.football.sync.dto.InjuryApiDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Sakatlık listesini DB'ye <b>replace</b> stratejisiyle yazar:
 * o maçın TÜM injury satırları silinir, gelenler yazılır.
 *
 * <p>API quirk: {@link InjuryApiDto.Player#type()} ve {@code reason} player
 * objesi içinde gelir; upserter bunu Injury entity'sine top-level olarak
 * yazıştırır.
 */
@Service
public class InjuriesUpserter {

    private final InjuryRepository injuryRepository;
    private final FixtureRepository fixtureRepository;
    private final TeamRepository teamRepository;
    private final PlayerUpserter playerUpserter;

    public InjuriesUpserter(InjuryRepository injuryRepository,
                            FixtureRepository fixtureRepository,
                            TeamRepository teamRepository,
                            PlayerUpserter playerUpserter) {
        this.injuryRepository = injuryRepository;
        this.fixtureRepository = fixtureRepository;
        this.teamRepository = teamRepository;
        this.playerUpserter = playerUpserter;
    }

    @Transactional
    public int replace(Long fixtureId, List<InjuryApiDto> items) {
        Fixture fixtureRef = fixtureRepository.getReferenceById(fixtureId);
        injuryRepository.deleteByFixtureId(fixtureId);

        if (items == null || items.isEmpty()) {
            return 0;
        }
        int written = 0;
        for (InjuryApiDto item : items) {
            if (item == null || item.team() == null || item.team().id() == null) {
                continue;
            }
            Team teamRef = teamRepository.getReferenceById(item.team().id());
            Injury entity = new Injury();
            entity.setFixture(fixtureRef);
            entity.setTeam(teamRef);
            if (item.player() != null) {
                entity.setPlayerId(item.player().id());
                entity.setPlayerName(item.player().name());
                entity.setPlayerPhoto(item.player().photo());
                // QUIRK: type ve reason API'da player içinde gelir.
                entity.setType(item.player().type());
                entity.setReason(item.player().reason());
                // Player master tablosuna upsert — ImageMirrorService MinIO'ya aynalar
                playerUpserter.upsert(
                        item.player().id(), item.player().name(), item.player().photo());
            }
            injuryRepository.save(entity);
            written++;
        }
        return written;
    }
}
