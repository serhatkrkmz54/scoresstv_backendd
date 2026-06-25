package com.scorestv.basketball.web;

import com.scorestv.basketball.BasketballProperties;
import com.scorestv.basketball.domain.BasketballTeam;
import com.scorestv.basketball.domain.BasketballTeamRepository;
import com.scorestv.basketball.web.dto.BasketballPopularTeamView;
import com.scorestv.common.SlugUtil;
import com.scorestv.storage.MinioStorageService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Config'te elle belirlenen (popular-team-ids) popüler basketbol takımlarını,
 * verilen sırayı koruyarak lokalize döner. Sol ray için — futbol
 * {@code PopularTeamsService} esi.
 *
 * <p>Liste boşsa boş liste döner; TÜM takımlara ASLA düşmez.
 */
@Service
public class BasketballPopularTeamsService {

    private final BasketballTeamRepository teamRepository;
    private final BasketballProperties properties;
    private final MinioStorageService storage;

    public BasketballPopularTeamsService(BasketballTeamRepository teamRepository,
                                         BasketballProperties properties,
                                         MinioStorageService storage) {
        this.teamRepository = teamRepository;
        this.properties = properties;
        this.storage = storage;
    }

    public List<BasketballPopularTeamView> getPopular(boolean turkish) {
        List<Long> ids = properties.serving().popularTeamIds();
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        Map<Long, BasketballTeam> byId = new HashMap<>();
        for (BasketballTeam team : teamRepository.findAllById(ids)) {
            byId.put(team.getId(), team);
        }

        List<BasketballPopularTeamView> out = new ArrayList<>();
        for (Long id : ids) { // config sırasını koru
            BasketballTeam team = byId.get(id);
            if (team == null) {
                continue; // bilinmeyen/silinmiş id'yi atla
            }
            String name = pick(team.getNameTr(), team.getName(), turkish);
            out.add(new BasketballPopularTeamView(
                    team.getId(),
                    name,
                    SlugUtil.teamSlug(name, team.getId()),
                    logo(team.getLogoKey(), team.getLogo())));
        }
        return out;
    }

    /** key varsa CDN URL'i, yoksa API URL'i (BasketballGameView.LogoResolver eşi). */
    private String logo(String key, String apiUrl) {
        return key != null ? storage.publicUrl(key) : apiUrl;
    }

    /** TR isteniyorsa TR ad (varsa), yoksa İngilizce ad. */
    private static String pick(String tr, String base, boolean turkish) {
        if (turkish && tr != null && !tr.isBlank()) {
            return tr;
        }
        return base;
    }
}
