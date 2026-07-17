package com.scorestv.mobile.notify;

import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureEvent;
import com.scorestv.football.domain.Team;
import org.springframework.stereotype.Component;

/**
 * Mobile push notification icin TR + EN mesaj uretici.
 *
 * <p>Her mesaj {@link Localized} olarak hem Turkce hem Ingilizce uretilir;
 * gonderim aninda cihaz locale'ine gore ({@code MobileDeviceToken.locale})
 * dogru dil secilir. Takim adi TR'de {@code nameTr}, EN'de {@code name}
 * tercih edilir.
 */
@Component
public class NotificationMessageBuilder {

    /** Iki dilli bildirim metni. */
    public record Localized(String titleTr, String bodyTr, String titleEn, String bodyEn) {}

    /** Geriye donuk basit tek-dil kayit (kullanan kalmadi ama API korunur). */
    public record NotificationMessage(String title, String body) {}

    public Localized event(Fixture fixture, FixtureEvent event, String mobileType) {
        return switch (mobileType) {
            case "kirmizi" -> _redCard(fixture, event);
            case "penalti" -> _penaltyOrVar(fixture, event);
            default -> new Localized("ScoresTV", "Yeni olay", "ScoresTV", "New event");
        };
    }

    public Localized kickoff(Fixture f) {
        final String h = _tr(f.getHomeTeam()), a = _tr(f.getAwayTeam());
        final String he = _en(f.getHomeTeam()), ae = _en(f.getAwayTeam());
        return new Localized(
                "⏱️ Maç başladı!",
                "%s - %s maçında ilk düdük çaldı".formatted(h, a),
                "⏱️ Kick-off!",
                "%s vs %s has kicked off".formatted(he, ae));
    }

    public Localized halftime(Fixture f) {
        final String score = _score(f);
        return new Localized(
                "⏱️ İlk yarı bitti",
                "%s %s %s".formatted(_tr(f.getHomeTeam()), score, _tr(f.getAwayTeam())).trim(),
                "⏱️ Half-time",
                "%s %s %s".formatted(_en(f.getHomeTeam()), score, _en(f.getAwayTeam())).trim());
    }

    public Localized secondHalf(Fixture f) {
        return new Localized(
                "▶ İkinci yarı başladı",
                "%s - %s maçında ikinci yarı başladı".formatted(
                        _tr(f.getHomeTeam()), _tr(f.getAwayTeam())),
                "▶ Second half",
                "%s vs %s — second half underway".formatted(
                        _en(f.getHomeTeam()), _en(f.getAwayTeam())));
    }

    public Localized lineup(Fixture f) {
        return new Localized(
                "📋 İlk 11 açıklandı",
                "%s - %s maçının ilk 11'i belli oldu".formatted(
                        _tr(f.getHomeTeam()), _tr(f.getAwayTeam())),
                "📋 Line-ups are out",
                "%s vs %s starting XI confirmed".formatted(
                        _en(f.getHomeTeam()), _en(f.getAwayTeam())));
    }

    public Localized finalScore(Fixture f) {
        final String score = _score(f);
        return new Localized(
                "🏁 Maç bitti",
                "%s %s %s".formatted(_tr(f.getHomeTeam()), score, _tr(f.getAwayTeam())).trim(),
                "🏁 Full-time",
                "%s %s %s".formatted(_en(f.getHomeTeam()), score, _en(f.getAwayTeam())).trim());
    }

    /// Skor degisiminden gol bildirimi. Golcu biliniyorsa eklenir; degilse
    /// dakika (varsa) gosterilir. Golcu+dakika govdesi dil-notr (orn. "Icardi 78'").
    public Localized scoreGoal(Fixture f, String scorerName, Integer minute) {
        final String hTr = _tr(f.getHomeTeam()), aTr = _tr(f.getAwayTeam());
        final String hEn = _en(f.getHomeTeam()), aEn = _en(f.getAwayTeam());
        final Integer h = f.getHomeGoals(), a = f.getAwayGoals();
        final String titleTr = (h != null && a != null)
                ? "⚽ GOL! %s %d-%d %s".formatted(hTr, h, a, aTr)
                : "⚽ GOL! %s - %s".formatted(hTr, aTr);
        final String titleEn = (h != null && a != null)
                ? "⚽ GOAL! %s %d-%d %s".formatted(hEn, h, a, aEn)
                : "⚽ GOAL! %s - %s".formatted(hEn, aEn);
        final String bodyTr, bodyEn;
        if (scorerName != null && !scorerName.isBlank()) {
            final String line = minute != null
                    ? "%s %d'".formatted(scorerName, minute) : scorerName;
            bodyTr = line;
            bodyEn = line; // golcu+dakika dil-notr
        } else if (minute != null) {
            bodyTr = "%d'  •  %s - %s".formatted(minute, hTr, aTr);
            bodyEn = "%d'  •  %s - %s".formatted(minute, hEn, aEn);
        } else {
            bodyTr = "%s - %s".formatted(hTr, aTr);
            bodyEn = "%s - %s".formatted(hEn, aEn);
        }
        return new Localized(titleTr, bodyTr, titleEn, bodyEn);
    }

    /**
     * VAR ile İPTAL edilen gol bildirimi. Skor düşüşü ({@link
     * com.scorestv.football.live.LiveTickerService}) tetikler; aynı gol collapse
     * slotu kullanıldığından cihazdaki "GOL!" kartı bununla değişir.
     *
     * @param homeTeam iptal edilen golün ev sahibine mi ait olduğu
     */
    public Localized goalCancelled(Fixture f, boolean homeTeam) {
        final String teamTr = homeTeam ? _tr(f.getHomeTeam()) : _tr(f.getAwayTeam());
        final String teamEn = homeTeam ? _en(f.getHomeTeam()) : _en(f.getAwayTeam());
        final String hTr = _tr(f.getHomeTeam()), aTr = _tr(f.getAwayTeam());
        final String hEn = _en(f.getHomeTeam()), aEn = _en(f.getAwayTeam());
        final Integer h = f.getHomeGoals(), a = f.getAwayGoals();
        final String scoreTr = (h != null && a != null)
                ? "%s %d-%d %s".formatted(hTr, h, a, aTr) : "%s - %s".formatted(hTr, aTr);
        final String scoreEn = (h != null && a != null)
                ? "%s %d-%d %s".formatted(hEn, h, a, aEn) : "%s - %s".formatted(hEn, aEn);
        return new Localized(
                "🚫 Gol iptal! %s".formatted(teamTr),
                "VAR sonrası iptal edildi  •  %s".formatted(scoreTr),
                "🚫 Goal disallowed! %s".formatted(teamEn),
                "Cancelled after VAR  •  %s".formatted(scoreEn));
    }

    private Localized _redCard(Fixture f, FixtureEvent e) {
        return new Localized(
                "🟥 Kırmızı kart! %s".formatted(_tr(e.getTeam())),
                _playerLineTr(e),
                "🟥 Red card! %s".formatted(_en(e.getTeam())),
                _playerLineEn(e));
    }

    private Localized _penaltyOrVar(Fixture f, FixtureEvent e) {
        final String type = e.getType() == null ? "" : e.getType().toLowerCase();
        return "var".equals(type) ? _var(f, e) : _penalty(f, e);
    }

    private Localized _penalty(Fixture f, FixtureEvent e) {
        final String d = e.getDetail() == null ? "" : e.getDetail().toLowerCase();
        final String c = e.getComments() == null ? "" : e.getComments().toLowerCase();
        final String teamTr = _tr(e.getTeam()), teamEn = _en(e.getTeam());

        // PENALTI ATIŞLARI (seri) — event.comments "Penalty Shootout". Maç-içi
        // penaltıdan KESİN ayrı: skor, maç skoru (goals=0-1) DEĞİL, atış tallysi
        // (score.penalty) gösterilir; atılan/kaçan ayrılır.
        if (c.contains("shootout")) {
            final boolean missed = d.contains("missed");
            final String hTr = _tr(f.getHomeTeam()), aTr = _tr(f.getAwayTeam());
            final String hEn = _en(f.getHomeTeam()), aEn = _en(f.getAwayTeam());
            final Integer ph = f.getScorePenHome(), pa = f.getScorePenAway();
            final String scoreTr = (ph != null && pa != null)
                    ? "%s %d-%d %s".formatted(hTr, ph, pa, aTr)
                    : "%s - %s".formatted(hTr, aTr);
            final String scoreEn = (ph != null && pa != null)
                    ? "%s %d-%d %s".formatted(hEn, ph, pa, aEn)
                    : "%s - %s".formatted(hEn, aEn);
            final String player = e.getPlayerName() == null ? "" : e.getPlayerName();
            final String tTr = missed
                    ? "🥅 Penaltı Atışları — Kaçırdı!" : "🥅 Penaltı Atışları — Gol!";
            final String tEn = missed
                    ? "🥅 Penalty Shootout — Missed!" : "🥅 Penalty Shootout — Scored!";
            final String bTr = player.isBlank() ? scoreTr : "%s  •  %s".formatted(player, scoreTr);
            final String bEn = player.isBlank() ? scoreEn : "%s  •  %s".formatted(player, scoreEn);
            return new Localized(tTr, bTr, tEn, bEn);
        }

        final String titleTr, titleEn;
        if (d.contains("missed")) {
            titleTr = "🚫 Penaltı kaçtı! %s".formatted(teamTr);
            titleEn = "🚫 Penalty missed! %s".formatted(teamEn);
        } else if (d.contains("cancel")) {
            titleTr = "⚡ Penaltı iptal edildi — %s".formatted(teamTr);
            titleEn = "⚡ Penalty cancelled — %s".formatted(teamEn);
        } else {
            titleTr = "⚡ Penaltı! %s".formatted(teamTr);
            titleEn = "⚡ Penalty! %s".formatted(teamEn);
        }
        return new Localized(titleTr, _playerLineTr(e), titleEn, _playerLineEn(e));
    }

    private Localized _var(Fixture f, FixtureEvent e) {
        final String d = e.getDetail() == null ? "" : e.getDetail().toLowerCase();
        final String titleTr, titleEn;
        if (d.contains("penalty")) {
            titleTr = d.contains("cancel") ? "📺 VAR: Penaltı iptal edildi" : "📺 VAR: Penaltı verildi";
            titleEn = d.contains("cancel") ? "📺 VAR: Penalty cancelled" : "📺 VAR: Penalty awarded";
        } else if (d.contains("goal")) {
            final boolean off = d.contains("cancel") || d.contains("disallow");
            titleTr = off ? "📺 VAR: Gol iptal edildi" : "📺 VAR: Gol onaylandı";
            titleEn = off ? "📺 VAR: Goal disallowed" : "📺 VAR: Goal confirmed";
        } else {
            titleTr = "📺 VAR kararı";
            titleEn = "📺 VAR decision";
        }
        return new Localized(titleTr, _varBodyTr(f, e), titleEn, _varBodyEn(f, e));
    }

    private String _varBodyTr(Fixture f, FixtureEvent e) {
        final String min = _minuteStr(e);
        final String who = (e.getPlayerName() != null && !e.getPlayerName().isBlank())
                ? e.getPlayerName()
                : (e.getTeam() != null ? _tr(e.getTeam())
                        : "%s - %s".formatted(_tr(f.getHomeTeam()), _tr(f.getAwayTeam())));
        return min.isEmpty() ? who : "%s  •  %s".formatted(who, min);
    }

    private String _varBodyEn(Fixture f, FixtureEvent e) {
        final String min = _minuteStr(e);
        final String who = (e.getPlayerName() != null && !e.getPlayerName().isBlank())
                ? e.getPlayerName()
                : (e.getTeam() != null ? _en(e.getTeam())
                        : "%s - %s".formatted(_en(f.getHomeTeam()), _en(f.getAwayTeam())));
        return min.isEmpty() ? who : "%s  •  %s".formatted(who, min);
    }

    private String _playerLineTr(FixtureEvent e) {
        final String player = e.getPlayerName();
        final String min = _minuteStr(e);
        if (player != null && !player.isBlank()) {
            return min.isEmpty() ? player : "%s %s".formatted(player, min);
        }
        return min.isEmpty() ? "Maçtan yeni gelişme" : min;
    }

    private String _playerLineEn(FixtureEvent e) {
        final String player = e.getPlayerName();
        final String min = _minuteStr(e);
        if (player != null && !player.isBlank()) {
            return min.isEmpty() ? player : "%s %s".formatted(player, min);
        }
        return min.isEmpty() ? "New update" : min;
    }

    /** Dakika metni: "78'" veya uzatma varsa "45+2'". Yoksa boş. Dil-notr. */
    private String _minuteStr(FixtureEvent e) {
        final Integer min = e.getTimeElapsed();
        final Integer extra = e.getTimeExtra();
        if (min == null) return "";
        return (extra != null && extra > 0) ? "%d+%d'".formatted(min, extra) : "%d'".formatted(min);
    }

    private String _score(Fixture f) {
        final Integer h = f.getHomeGoals(), a = f.getAwayGoals();
        return (h != null && a != null) ? "%d - %d".formatted(h, a) : "";
    }

    /** TR takim adi — nameTr tercih, yoksa name. */
    private String _tr(Team t) {
        if (t == null) return "?";
        if (t.getNameTr() != null && !t.getNameTr().isBlank()) return t.getNameTr();
        return t.getName() != null ? t.getName() : "?";
    }

    /** EN takim adi — name tercih, yoksa nameTr. */
    private String _en(Team t) {
        if (t == null) return "?";
        if (t.getName() != null && !t.getName().isBlank()) return t.getName();
        return t.getNameTr() != null ? t.getNameTr() : "?";
    }
}
