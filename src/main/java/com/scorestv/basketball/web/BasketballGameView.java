package com.scorestv.basketball.web;

import com.scorestv.basketball.domain.BasketballGame;

import java.io.Serializable;
import java.time.Instant;

/** Mobile'a dönen basketbol maç özeti (fikstür + canlı skor). */
public record BasketballGameView(
        Long id,
        Instant startAt,
        String season,
        /** Anasayfa lig basligi alt satiri: "Hafta 3" / "Week 3" ya da asama. */
        String round,
        Status status,
        League league,
        Team home,
        Team away,
        Score score
) implements Serializable {

    public record Status(String shortCode, String longName, String timer) implements Serializable {}

    public record League(Long id, String name, String logo,
                         String country, String countryFlag) implements Serializable {}

    public record Team(Long id, String name, String logo) implements Serializable {}

    public record Score(Integer homeTotal, Integer awayTotal,
                        Quarters home, Quarters away) implements Serializable {}

    public record Quarters(Integer q1, Integer q2, Integer q3, Integer q4, Integer ot)
            implements Serializable {}

    /** key → CDN URL çözer; key yoksa API URL'ine düşer. */
    @FunctionalInterface
    public interface LogoResolver {
        String url(String key, String apiUrl);
    }

    /**
     * {@code turkish=true} ise TR ad (nameTr/countryNameTr) varsa onu, yoksa
     * İngilizce adı kullanır. Logo/bayrak [logo] resolver ile CDN'e çözülür.
     */
    public static BasketballGameView from(BasketballGame g, boolean turkish, LogoResolver logo) {
        var l = g.getLeague();
        var h = g.getHomeTeam();
        var a = g.getAwayTeam();
        return new BasketballGameView(
                g.getId(),
                g.getStartAt(),
                g.getSeason(),
                round(g.getWeek(), g.getStage(), turkish),
                new Status(g.getStatusShort(), g.getStatusLong(), g.getTimer()),
                new League(l.getId(),
                        pick(l.getNameTr(), l.getName(), turkish),
                        logo.url(l.getLogoKey(), l.getLogo()),
                        pick(l.getCountryNameTr(), l.getCountryName(), turkish),
                        logo.url(l.getCountryFlagKey(), l.getCountryFlag())),
                new Team(h.getId(), pick(h.getNameTr(), h.getName(), turkish),
                        logo.url(h.getLogoKey(), h.getLogo())),
                new Team(a.getId(), pick(a.getNameTr(), a.getName(), turkish),
                        logo.url(a.getLogoKey(), a.getLogo())),
                new Score(
                        g.getHomeTotal(), g.getAwayTotal(),
                        new Quarters(g.getHomeQ1(), g.getHomeQ2(), g.getHomeQ3(),
                                g.getHomeQ4(), g.getHomeOt()),
                        new Quarters(g.getAwayQ1(), g.getAwayQ2(), g.getAwayQ3(),
                                g.getAwayQ4(), g.getAwayOt())));
    }

    /** TR isteniyorsa TR ad (varsa), yoksa İngilizce ad. */
    private static String pick(String tr, String base, boolean turkish) {
        if (turkish && tr != null && !tr.isBlank()) return tr;
        return base;
    }

    /**
     * Anasayfa lig başlığı alt satırındaki "round" etiketi (futbol paritesi).
     * week SADECE sayıysa "Hafta N"/"Week N"; değilse week metni olduğu gibi;
     * week yoksa stage; hiçbiri yoksa null (başlıkta alt satır gizlenir).
     */
    private static String round(String week, String stage, boolean turkish) {
        if (week != null && !week.isBlank()) {
            String w = week.trim();
            if (w.matches("\\d+")) return (turkish ? "Hafta " : "Week ") + w;
            return w;
        }
        if (stage != null && !stage.isBlank()) return stage.trim();
        return null;
    }
}
