package com.scorestv.football.sync;

import com.scorestv.football.domain.PlayerCareerTeam;
import com.scorestv.football.domain.PlayerCareerTeamRepository;
import com.scorestv.football.domain.Team;
import com.scorestv.football.domain.TeamRepository;
import com.scorestv.football.sync.dto.PlayerCareerTeamApiDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Oyuncu kariyer takimlari REPLACE pattern ile yazilir. Bir oyuncunun tum
 * eski kayitlari silinir, gelen tam set yazilir.
 */
@Service
public class PlayerCareerTeamsUpserter {

    private static final Logger log = LoggerFactory.getLogger(PlayerCareerTeamsUpserter.class);

    private final PlayerCareerTeamRepository repository;
    private final TeamRepository teamRepository;

    public PlayerCareerTeamsUpserter(PlayerCareerTeamRepository repository,
                                     TeamRepository teamRepository) {
        this.repository = repository;
        this.teamRepository = teamRepository;
    }

    @Transactional
    public int upsert(Long playerId, List<PlayerCareerTeamApiDto> items) {
        if (playerId == null) return 0;
        // Veri-kaybi korumasi: API bos/hatali dondurduyse MEVCUT veriyi SILME
        // (transient bos cogu zaman gecici API hatasidir). Once bos-guard, SONRA
        // replace — boylece kariyer takimlari yanlislikla wipe edilmez.
        if (items == null || items.isEmpty()) return 0;
        repository.deleteByPlayerId(playerId);
        int written = 0;
        for (PlayerCareerTeamApiDto dto : items) {
            if (dto == null || dto.team() == null || dto.team().id() == null) continue;
            // Master'da yoksa minimal "stub" takim olustur — boylece kapsanmayan
            // liglere (Suudi, MLS, vb.) giden oyuncularin guncel takimi da yazilir.
            // Detay (lig, slug, milli/kulup, logo aynalama) takim sayfasi ziyaret
            // edilince lazy-sync ile tamamlanir.
            Team team = teamRepository.findById(dto.team().id())
                    .orElseGet(() -> createStubTeam(dto.team()));
            PlayerCareerTeam ct = new PlayerCareerTeam();
            ct.setPlayerId(playerId);
            ct.setTeam(team);
            ct.setSeasons(dto.seasons() != null ? dto.seasons() : List.of());
            repository.save(ct);
            written++;
        }
        return written;
    }

    /**
     * Master'da olmayan takim icin minimal kayit. Kulup varsayilir (national=false;
     * milli takimlar zaten master'da yuklu). Native sorgu/FK'dan once DB'de olmasi
     * icin {@code saveAndFlush}.
     */
    private Team createStubTeam(PlayerCareerTeamApiDto.Team dto) {
        Team team = new Team();
        team.setId(dto.id());
        team.setName(dto.name() != null && !dto.name().isBlank()
                ? dto.name() : ("Team#" + dto.id()));
        team.setLogoUrl(dto.logo());
        team.setNational(false);
        team.setCovered(false);
        Team saved = teamRepository.saveAndFlush(team);
        log.info("Career team stub olusturuldu (master'da yoktu): teamId={} '{}'",
                dto.id(), team.getName());
        return saved;
    }
}
