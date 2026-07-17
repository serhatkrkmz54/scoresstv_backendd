package com.scorestv.volleyball.web;

import com.scorestv.volleyball.domain.VolleyballGame;

import java.io.Serializable;
import java.time.Instant;

/**
 * Mobile'a donen voleybol mac ozeti (fikstur + canli skor).
 *
 * <p><b>Skor:</b> {@code score.homeTotal/awayTotal} = kazanilan set sayisi.
 * {@code score.home/away} (Sets) = her setteki sayi (set1..set5).
 */
public record VolleyballGameView(
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

    public record Status(String shortCode, String longName) implements Serializable {}

    public record League(Long id, String name, String logo,
                         String country, String countryFlag) implements Serializable {}

    public record Team(Long id, String name, String logo) implements Serializable {}

    /** Skor: kazanilan setler (total) + set bazli sayilar (home/away). */
    public record Score(Integer homeTotal, Integer awayTotal,
                        Sets home, Sets away) implements Serializable {}

    /** Bir takimin set bazli sayilari (her set icin bir sayi). */
    public record Sets(Integer set1, Integer set2, Integer set3, Integer set4, Integer set5)
            implements Serializable {}

    /** key → CDN URL cozer; key yoksa API URL'ine duser. */
    @FunctionalInterface
    public interface LogoResolver {
        String url(String key, String apiUrl);
    }

    public static VolleyballGameView from(VolleyballGame g, boolean turkish, LogoResolver logo) {
        var l = g.getLeague();
        var h = g.getHomeTeam();
        var a = g.getAwayTeam();
        return new VolleyballGameView(
                g.getId(),
                g.getStartAt(),
                g.getSeason(),
                round(g.getWeek(), g.getStage(), turkish),
                new Status(g.getStatusShort(), g.getStatusLong()),
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
                        new Sets(g.getHomeSet1(), g.getHomeSet2(), g.getHomeSet3(),
                                g.getHomeSet4(), g.getHomeSet5()),
                        new Sets(g.getAwaySet1(), g.getAwaySet2(), g.getAwaySet3(),
                                g.getAwaySet4(), g.getAwaySet5())));
    }

    private static String pick(String tr, String base, boolean turkish) {
        if (turkish && tr != null && !tr.isBlank()) return tr;
        return base;
    }

    /**
     * Anasayfa lig başlığı alt satırındaki "round" etiketi (futbol paritesi).
     * week SADECE sayıysa "Hafta N"/"Week N"; değilse week metni olduğu gibi;
     * week yoksa stage; hiçbiri yoksa null.
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
