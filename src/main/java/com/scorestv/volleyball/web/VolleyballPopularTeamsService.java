package com.scorestv.volleyball.web;

import com.scorestv.common.SlugUtil;
import com.scorestv.storage.MinioStorageService;
import com.scorestv.volleyball.VolleyballProperties;
import com.scorestv.volleyball.domain.VolleyballTeam;
import com.scorestv.volleyball.domain.VolleyballTeamRepository;
import com.scorestv.volleyball.web.dto.VolleyballPopularTeamView;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Config'te elle belirlenen (popular-team-ids) populer voleybol takimlarini,
 * verilen sirayi koruyarak lokalize doner. Web sol ray icin — basketbol
 * {@code BasketballPopularTeamsService} esi.
 *
 * <p>Liste bossa bos liste doner; TUM takimlara ASLA dusmez.
 */
@Service
public class VolleyballPopularTeamsService {

    private final VolleyballTeamRepository teamRepository;
    private final VolleyballProperties properties;
    private final MinioStorageService storage;

    public VolleyballPopularTeamsService(VolleyballTeamRepository teamRepository,
                                         VolleyballProperties properties,
                                         MinioStorageService storage) {
        this.teamRepository = teamRepository;
        this.properties = properties;
        this.storage = storage;
    }

    public List<VolleyballPopularTeamView> getPopular(boolean turkish) {
        List<Long> ids = properties.serving().popularTeamIds();
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        Map<Long, VolleyballTeam> byId = new HashMap<>();
        for (VolleyballTeam team : teamRepository.findAllById(ids)) {
            byId.put(team.getId(), team);
        }

        List<VolleyballPopularTeamView> out = new ArrayList<>();
        for (Long id : ids) { // config sirasini koru
            VolleyballTeam team = byId.get(id);
            if (team == null) {
                continue; // bilinmeyen/silinmis id'yi atla
            }
            String name = pick(team.getNameTr(), team.getName(), turkish);
            out.add(new VolleyballPopularTeamView(
                    team.getId(),
                    name,
                    SlugUtil.teamSlug(name, team.getId()),
                    logo(team.getLogoKey(), team.getLogo())));
        }
        return out;
    }

    /** key varsa CDN URL'i, yoksa API URL'i. */
    private String logo(String key, String apiUrl) {
        return key != null ? storage.publicUrl(key) : apiUrl;
    }

    /** TR isteniyorsa TR ad (varsa), yoksa Ingilizce ad. */
    private static String pick(String tr, String base, boolean turkish) {
        if (turkish && tr != null && !tr.isBlank()) {
            return tr;
        }
        return base;
    }
}
