package com.scorestv.mobile.notify;

import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureEvent;
import com.scorestv.football.domain.Team;
import org.springframework.stereotype.Component;

/**
 * Mobile push notification icin TR mesaj govdesi uretici.
 *
 * <p>Suanlik sadece TR — gelecekte device locale'a gore EN versiyon eklenebilir
 * (MobileDeviceToken.locale alanini kullanir).
 *
 * <p>Mesaj formati: kisa, dikkat cekici, oyuncu adi/skor ile.
 * - "⚽ GOL! Galatasaray 1-0 öne geçti"
 * - "Icardi, 78. dakikada"
 */
@Component
public class NotificationMessageBuilder {

    public record NotificationMessage(String title, String body) {}

    public NotificationMessage buildEventMessage(
            Fixture fixture, FixtureEvent event, String mobileType) {
        return switch (mobileType) {
            case "gol" -> _buildGoal(fixture, event);
            case "kirmizi" -> _buildRedCard(fixture, event);
            case "penalti" -> _buildPenaltyOrVar(fixture, event);
            default -> new NotificationMessage("ScoresTV", "Yeni olay");
        };
    }

    public NotificationMessage buildKickoffMessage(Fixture fixture) {
        return new NotificationMessage(
                "▶ Maç başladı!",
                "%s - %s maçında ilk düdük çaldı".formatted(
                        _name(fixture.getHomeTeam()),
                        _name(fixture.getAwayTeam())));
    }

    public NotificationMessage buildHalftimeMessage(Fixture fixture) {
        final Integer h = fixture.getHomeGoals();
        final Integer a = fixture.getAwayGoals();
        final String score = (h != null && a != null) ? "%d - %d".formatted(h, a) : "";
        return new NotificationMessage(
                "⏸ İlk yarı bitti",
                "%s %s %s".formatted(
                        _name(fixture.getHomeTeam()), score, _name(fixture.getAwayTeam()))
                        .trim());
    }

    public NotificationMessage buildSecondHalfMessage(Fixture fixture) {
        return new NotificationMessage(
                "▶ İkinci yarı başladı",
                "%s - %s maçında ikinci yarı başladı".formatted(
                        _name(fixture.getHomeTeam()),
                        _name(fixture.getAwayTeam())));
    }

    public NotificationMessage buildLineupMessage(Fixture fixture) {
        return new NotificationMessage(
                "📋 İlk 11 açıklandı",
                "%s - %s maçının ilk 11'i belli oldu".formatted(
                        _name(fixture.getHomeTeam()),
                        _name(fixture.getAwayTeam())));
    }

    public NotificationMessage buildFinalMessage(Fixture fixture) {
        final Integer h = fixture.getHomeGoals();
        final Integer a = fixture.getAwayGoals();
        final String score = (h != null && a != null) ? "%d - %d".formatted(h, a) : "";
        return new NotificationMessage(
                "🏁 Maç bitti",
                "%s %s %s".formatted(
                        _name(fixture.getHomeTeam()), score, _name(fixture.getAwayTeam()))
                        .trim());
    }

    /// Skor degisiminden ANINDA gol bildirimi. Golcu biliniyorsa eklenir;
    /// bilinmiyorsa skor-only (rekabet icin beklemeden hizli gonderim).
    ///
    /// A-Faz5 rotuş: dakika her durumda gosterilir — golcu null olsa bile
    /// "12'" formatinda body'de yer alir (kart/penaltiyla tutarli).
    public NotificationMessage buildScoreGoal(
            Fixture fixture, String scorerName, Integer minute) {
        final String home = _name(fixture.getHomeTeam());
        final String away = _name(fixture.getAwayTeam());
        final Integer h = fixture.getHomeGoals();
        final Integer a = fixture.getAwayGoals();
        final String title = (h != null && a != null)
                ? "⚽ GOL! %s %d-%d %s".formatted(home, h, a, away)
                : "⚽ GOL! %s - %s".formatted(home, away);
        final String body;
        if (scorerName != null && !scorerName.isBlank()) {
            // Golcu + dakika (varsa): "Icardi 78'"
            body = minute != null
                    ? "%s %d'".formatted(scorerName, minute)
                    : scorerName;
        } else if (minute != null) {
            // Golcu yok ama dakika var: "12. dakika · Galatasaray - Fenerbahçe"
            body = "%d'  •  %s - %s".formatted(minute, home, away);
        } else {
            body = "%s - %s".formatted(home, away);
        }
        return new NotificationMessage(title, body);
    }

    private NotificationMessage _buildGoal(Fixture fixture, FixtureEvent event) {
        final Long scoringTeamId =
                event.getTeam() != null ? event.getTeam().getId() : null;
        final String scoringTeam = _name(event.getTeam());
        final String homeName = _name(fixture.getHomeTeam());
        final String awayName = _name(fixture.getAwayTeam());
        final Integer h = fixture.getHomeGoals();
        final Integer a = fixture.getAwayGoals();

        final String title;
        if (h != null && a != null) {
            title = "⚽ GOL! %s %d-%d %s".formatted(homeName, h, a, awayName);
        } else {
            title = "⚽ GOL! %s attı".formatted(scoringTeam);
        }
        final String body = _playerLine(event);
        return new NotificationMessage(title, body);
    }

    private NotificationMessage _buildRedCard(Fixture fixture, FixtureEvent event) {
        final String team = _name(event.getTeam());
        final String title = "🟥 Kırmızı kart! %s".formatted(team);
        final String body = _playerLine(event);
        return new NotificationMessage(title, body);
    }

    /**
     * "penalti" tipi hem gerçek penaltı olaylarını (type=Goal) hem de penaltı/gol
     * ile ilgili VAR kararlarını (type=Var) kapsar. Burada olayın asıl tipine
     * göre uygun açıklayıcı metne yönlendiririz.
     */
    private NotificationMessage _buildPenaltyOrVar(Fixture fixture, FixtureEvent event) {
        final String type = event.getType() == null ? "" : event.getType().toLowerCase();
        if ("var".equals(type)) {
            return _buildVar(fixture, event);
        }
        return _buildPenalty(fixture, event);
    }

    /**
     * Penaltı olayı (type=Goal). Penaltıdan GOL artık "gol" tipine düştüğü için
     * (skor bildirimi gönderir) burada genelde KAÇAN penaltı işlenir; iptal/diğer
     * için yedek başlıklar tutulur.
     */
    private NotificationMessage _buildPenalty(Fixture fixture, FixtureEvent event) {
        final String detail = event.getDetail() == null
                ? "" : event.getDetail().toLowerCase();
        final String team = _name(event.getTeam());
        final String title;
        if (detail.contains("missed")) {
            title = "🚫 Penaltı kaçtı! %s".formatted(team);
        } else if (detail.contains("cancel")) {
            title = "⚡ Penaltı iptal edildi — %s".formatted(team);
        } else {
            title = "⚡ Penaltı! %s".formatted(team);
        }
        final String body = _playerLine(event);
        return new NotificationMessage(title, body);
    }

    /**
     * VAR kararı (type=Var) — penaltı veya gol ile ilgili kararları açıkça
     * "VAR" etiketiyle bildirir. Diğer VAR olayları (kart upgrade vb.)
     * dispatcher tarafından zaten elenir.
     */
    private NotificationMessage _buildVar(Fixture fixture, FixtureEvent event) {
        final String detail = event.getDetail() == null
                ? "" : event.getDetail().toLowerCase();
        final String title;
        if (detail.contains("penalty")) {
            title = detail.contains("cancel")
                    ? "📺 VAR: Penaltı iptal edildi"
                    : "📺 VAR: Penaltı verildi";
        } else if (detail.contains("goal")) {
            title = (detail.contains("cancel") || detail.contains("disallow"))
                    ? "📺 VAR: Gol iptal edildi"
                    : "📺 VAR: Gol onaylandı";
        } else {
            title = "📺 VAR kararı";
        }
        return new NotificationMessage(title, _varBody(fixture, event));
    }

    /** VAR gövdesi: oyuncu (varsa) ya da takım + dakika. */
    private String _varBody(Fixture fixture, FixtureEvent event) {
        final String minStr = _minuteStr(event);
        final String who;
        if (event.getPlayerName() != null && !event.getPlayerName().isBlank()) {
            who = event.getPlayerName();
        } else if (event.getTeam() != null) {
            who = _name(event.getTeam());
        } else {
            who = "%s - %s".formatted(
                    _name(fixture.getHomeTeam()), _name(fixture.getAwayTeam()));
        }
        return minStr.isEmpty() ? who : "%s  •  %s".formatted(who, minStr);
    }

    private String _playerLine(FixtureEvent e) {
        final String player = e.getPlayerName();
        final String minStr = _minuteStr(e);
        if (player != null && !player.isBlank()) {
            return minStr.isEmpty() ? player : "%s %s".formatted(player, minStr);
        }
        return minStr.isEmpty() ? "Maçtan yeni gelişme" : minStr;
    }

    /** Dakika metni: "78'" veya uzatma varsa "45+2'". Yoksa boş. */
    private String _minuteStr(FixtureEvent e) {
        final Integer min = e.getTimeElapsed();
        final Integer extra = e.getTimeExtra();
        if (min == null) return "";
        return (extra != null && extra > 0)
                ? "%d+%d'".formatted(min, extra)
                : "%d'".formatted(min);
    }

    private String _name(Team t) {
        if (t == null) return "?";
        if (t.getNameTr() != null && !t.getNameTr().isBlank()) return t.getNameTr();
        return t.getName();
    }
}
