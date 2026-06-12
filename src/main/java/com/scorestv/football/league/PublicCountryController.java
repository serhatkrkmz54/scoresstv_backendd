package com.scorestv.football.league;

import com.scorestv.football.web.dto.PopularCountryView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public ülke endpoint'leri (sol ray vb.). */
@RestController
@RequestMapping("/api/v1/countries")
public class PublicCountryController {

    private final PopularCountriesService popularCountriesService;

    public PublicCountryController(PopularCountriesService popularCountriesService) {
        this.popularCountriesService = popularCountriesService;
    }

    /**
     * Sol ray "Ülkeler" listesi (config'ten, elle seçilmiş).
     *
     * @param lang "tr" → Türkçe ad/slug; aksi halde "en"
     */
    @GetMapping("/popular")
    public List<PopularCountryView> popular(
            @RequestParam(required = false, defaultValue = "en") String lang) {
        return popularCountriesService.getPopular("tr".equalsIgnoreCase(lang));
    }
}
