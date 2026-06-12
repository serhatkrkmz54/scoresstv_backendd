package com.scorestv.football.team;

import com.scorestv.common.SlugUtil;
import com.scorestv.football.FootballProperties;
import com.scorestv.football.domain.Team;
import com.scorestv.football.domain.TeamRepository;
import com.scorestv.football.web.dto.PopularTeamView;
import com.scorestv.storage.MinioStorageService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Config'te elle belirlenen (popular-team-ids) takımları — genelde milli
 * takımlar — verilen sırayı koruyarak lokalize döner. Sol ray "Ülkeler" için.
 */
@Service
public class PopularTeamsService {

    private final TeamRepository teamRepository;
    private final FootballProperties properties;
    private final MinioStorageService storage;

    public PopularTeamsService(TeamRepository teamRepository,
                               FootballProperties properties,
                               MinioStorageService storage) {
        this.teamRepository = teamRepository;
        this.properties = properties;
        this.storage = storage;
    }

    public List<PopularTeamView> getPopular(boolean turkish) {
        List<Long> ids = properties.serving().popularTeamIds();
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        Map<Long, Team> byId = new HashMap<>();
        for (Team team : teamRepository.findAllById(ids)) {
            byId.put(team.getId(), team);
        }

        List<PopularTeamView> out = new ArrayList<>();
        for (Long id : ids) { // config sırasını koru
            Team team = byId.get(id);
            if (team == null) {
                continue;
            }
            String name = displayName(team, turkish);
            String logo = (team.getLogoKey() != null)
                    ? storage.publicUrl(team.getLogoKey())
                    : team.getLogoUrl();
            out.add(new PopularTeamView(
                    team.getId(),
                    name,
                    SlugUtil.teamSlug(name, team.getId()),
                    logo,
                    team.getCountry()));
        }
        return out;
    }

    private static String displayName(Team team, boolean turkish) {
        if (turkish) {
            String tr = team.getNameTr();
            if (tr != null && !tr.isBlank()) {
                return tr;
            }
        }
        return team.getName();
    }
}
