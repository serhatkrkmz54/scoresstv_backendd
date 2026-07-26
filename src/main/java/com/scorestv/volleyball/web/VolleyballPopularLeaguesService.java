package com.scorestv.volleyball.web;

import com.scorestv.common.SlugUtil;
import com.scorestv.storage.MinioStorageService;
import com.scorestv.volleyball.VolleyballProperties;
import com.scorestv.volleyball.domain.VolleyballLeague;
import com.scorestv.volleyball.domain.VolleyballLeagueRepository;
import com.scorestv.volleyball.web.dto.VolleyballPopularLeagueView;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Config'te elle belirlenen (popular-league-ids) populer voleybol liglerini,
 * verilen sirayi koruyarak lokalize doner. Web sol ray icin — basketbol
 * {@code BasketballPopularLeaguesService} esi.
 *
 * <p>Liste bossa bos liste doner; TUM liglere ASLA dusmez (elle secim sarttir).
 */
@Service
public class VolleyballPopularLeaguesService {

    private final VolleyballLeagueRepository leagueRepository;
    private final VolleyballProperties properties;
    private final MinioStorageService storage;

    public VolleyballPopularLeaguesService(VolleyballLeagueRepository leagueRepository,
                                           VolleyballProperties properties,
                                           MinioStorageService storage) {
        this.leagueRepository = leagueRepository;
        this.properties = properties;
        this.storage = storage;
    }

    public List<VolleyballPopularLeagueView> getPopular(boolean turkish) {
        List<Long> ids = properties.serving().popularLeagueIds();
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        Map<Long, VolleyballLeague> byId = new HashMap<>();
        for (VolleyballLeague league : leagueRepository.findAllById(ids)) {
            byId.put(league.getId(), league);
        }

        List<VolleyballPopularLeagueView> out = new ArrayList<>();
        for (Long id : ids) { // config sirasini koru
            VolleyballLeague league = byId.get(id);
            if (league == null) {
                continue; // bilinmeyen/silinmis id'yi atla
            }
            String name = pick(league.getNameTr(), league.getName(), turkish);
            out.add(new VolleyballPopularLeagueView(
                    league.getId(),
                    name,
                    SlugUtil.leagueSlug(name, league.getId()),
                    logo(league.getLogoKey(), league.getLogo())));
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
