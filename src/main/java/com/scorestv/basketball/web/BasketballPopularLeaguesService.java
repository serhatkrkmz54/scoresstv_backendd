package com.scorestv.basketball.web;

import com.scorestv.basketball.BasketballProperties;
import com.scorestv.basketball.domain.BasketballLeague;
import com.scorestv.basketball.domain.BasketballLeagueRepository;
import com.scorestv.basketball.web.dto.BasketballPopularLeagueView;
import com.scorestv.common.SlugUtil;
import com.scorestv.storage.MinioStorageService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Config'te elle belirlenen (popular-league-ids) popüler basketbol liglerini,
 * verilen sırayı koruyarak lokalize döner. Sol ray için — futbol
 * {@code PopularLeaguesService} esi.
 *
 * <p>Liste boşsa boş liste döner; TÜM liglere ASLA düşmez (elle seçim şarttır).
 */
@Service
public class BasketballPopularLeaguesService {

    private final BasketballLeagueRepository leagueRepository;
    private final BasketballProperties properties;
    private final MinioStorageService storage;

    public BasketballPopularLeaguesService(BasketballLeagueRepository leagueRepository,
                                           BasketballProperties properties,
                                           MinioStorageService storage) {
        this.leagueRepository = leagueRepository;
        this.properties = properties;
        this.storage = storage;
    }

    public List<BasketballPopularLeagueView> getPopular(boolean turkish) {
        List<Long> ids = properties.serving().popularLeagueIds();
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        Map<Long, BasketballLeague> byId = new HashMap<>();
        for (BasketballLeague league : leagueRepository.findAllById(ids)) {
            byId.put(league.getId(), league);
        }

        List<BasketballPopularLeagueView> out = new ArrayList<>();
        for (Long id : ids) { // config sırasını koru
            BasketballLeague league = byId.get(id);
            if (league == null) {
                continue; // bilinmeyen/silinmiş id'yi atla
            }
            String name = pick(league.getNameTr(), league.getName(), turkish);
            out.add(new BasketballPopularLeagueView(
                    league.getId(),
                    name,
                    SlugUtil.leagueSlug(name, league.getId()),
                    logo(league.getLogoKey(), league.getLogo())));
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
