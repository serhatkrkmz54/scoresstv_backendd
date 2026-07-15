package com.scorestv.football;

import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * API-Football'dan İngilizce gelen <b>sabit sözcük kümelerini</b> (maç durumu,
 * lig türü, saha zemini, olay, tahmin yorumu, puan durumu açıklaması) hedef
 * dile çevirir.
 *
 * <p>Bu değerler özel isim değildir; az sayıda ve sabittir. Bu yüzden satır
 * bazlı {@code name_tr} kolonu yerine {@code messages_tr.properties} /
 * {@code messages_en.properties} içindeki tek bir sözlükten çevrilir. Anahtar
 * bulunamazsa API'nin İngilizce kaynak değerine düşülür — yeni bir kod
 * eklendiğinde site çalışmaya devam eder.
 */
@Component
public class FootballMessages {

    private static final Locale TURKISH = Locale.of("tr");
    private static final Locale ENGLISH = Locale.ENGLISH;

    private final MessageSource messageSource;

    public FootballMessages(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /** Locale seçimi: turkish=true → TR; aksi halde EN. */
    private static Locale locale(boolean turkish) {
        return turkish ? TURKISH : ENGLISH;
    }

    /**
     * Maç durumunun uzun metni. {@code statusShort} koduna ("FT", "1H", ...)
     * göre dile çevrilir. Anahtar yoksa {@code englishLong} kaynak değere
     * düşülür.
     *
     * @param statusShort API-Football durum kodu — çeviri anahtarı
     * @param englishLong API'den gelen İngilizce uzun metin — yedek değer
     * @param turkish     Türkçe isteniyorsa true; aksi halde EN
     */
    public String statusText(String statusShort, String englishLong, boolean turkish) {
        if (statusShort == null || statusShort.isBlank()) {
            return englishLong;
        }
        return messageSource.getMessage(
                "football.status." + statusShort.trim(), null, englishLong, locale(turkish));
    }

    /**
     * Lig türünün metni ("League" / "Cup"). Hedef dile çevirir; bilinmeyen
     * tür kaynak değerle döner.
     */
    public String leagueType(String type, boolean turkish) {
        if (type == null || type.isBlank()) {
            return type;
        }
        return messageSource.getMessage(
                "football.leagueType." + type.trim(), null, type, locale(turkish));
    }

    /**
     * Saha zemininin metni ("grass", "artificial turf", ...). Anahtar normalize
     * edilir (küçük harf, boşluk → tire); bilinmeyen zemin kaynak değerle döner.
     */
    public String surface(String surface, boolean turkish) {
        if (surface == null || surface.isBlank()) {
            return surface;
        }
        return messageSource.getMessage(
                "football.surface." + slug(surface), null, surface, locale(turkish));
    }

    /**
     * Maç turu / aşaması ("Regular Season - 17", "Round of 16", "Final"...).
     *
     * <p>Algoritma: önce tüm değerin slug'ıyla doğrudan çevirisini ara
     * (örn. "Round of 16" → "Son 16"). Bulunamazsa ve değer
     * {@code "ÖNEK - SAYI"} kalıbındaysa öneki ayrı bir anahtar olarak çevirip
     * sayıyı {@code {0}} yer tutucusuna geçir (örn. "Regular Season - 17" →
     * "Hafta 17"). İkisi de yoksa İngilizce kaynak değerine düşülür — yeni
     * bir round değeri çıksa bile site çalışmaya devam eder.
     */
    public String roundText(String round, boolean turkish) {
        if (round == null || round.isBlank()) {
            return round;
        }
        Locale loc = locale(turkish);
        String trimmed = round.trim();

        // 1) Tüm değerin doğrudan çevirisi (Quarter-finals, Round of 16, Final...).
        try {
            String msg = messageSource.getMessage(
                    "football.round." + slug(trimmed), null, loc);
            // Sablon anahtari (orn. group-stage="Grup Maçı {0}") SAYISIZ bir round
            // degeriyle (bare "Group Stage") eslesirse {0} bos kalir → "{0}" ekranda
            // gorunur. Placeholder'i temizle. Sayili durum ("Group Stage - 1")
            // adim 1'de eslesmez, adim 2'de {0} sayiyla doldurulur.
            return msg.contains("{0}") ? msg.replace("{0}", "").trim() : msg;
        } catch (NoSuchMessageException ignored) {
            // sonraki adıma geç
        }

        // 2) "ÖNEK - SAYI" deseni (Regular Season - 17, Group Stage - 3...).
        int sep = trimmed.lastIndexOf(" - ");
        if (sep > 0) {
            String tail = trimmed.substring(sep + 3).trim();
            if (!tail.isEmpty() && tail.chars().allMatch(Character::isDigit)) {
                String prefix = trimmed.substring(0, sep);
                try {
                    return messageSource.getMessage(
                            "football.round." + slug(prefix),
                            new Object[]{tail}, loc);
                } catch (NoSuchMessageException ignored) {
                    // sonraki adıma geç
                }
            }
        }

        // 2b) "ÖNEK SAYI/HARF" deseni BOŞLUKLA (Round 2, Group C, Group A...).
        // Sondaki token sayı ya da tek harf ise öneki çevir, token'ı {0}'a geçir.
        // ("Round of 16", "Group Stage" gibi tamlar adım 1'de zaten yakalanır.)
        int sp = trimmed.lastIndexOf(' ');
        if (sp > 0) {
            String tail = trimmed.substring(sp + 1).trim();
            boolean isNum = !tail.isEmpty() && tail.chars().allMatch(Character::isDigit);
            boolean isLetter = tail.length() == 1 && Character.isLetter(tail.charAt(0));
            if (isNum || isLetter) {
                String prefix = trimmed.substring(0, sp);
                try {
                    return messageSource.getMessage(
                            "football.round." + slug(prefix), new Object[]{tail}, loc);
                } catch (NoSuchMessageException ignored) {
                    // sonraki adıma geç
                }
            }
        }

        // 3) Bilinmeyen — API kaynak metnine düş.
        return round;
    }

    /**
     * Olay türü ("Goal", "Card", "Subst", "Var") — anahtar API'nin verdiği
     * tipin kendisidir. Bilinmeyen tip kaynak değerle döner.
     */
    public String eventType(String type, boolean turkish) {
        if (type == null || type.isBlank()) {
            return type;
        }
        return messageSource.getMessage(
                "football.event.type." + type.trim(), null, type, locale(turkish));
    }

    /**
     * Olay alt-detayı ("Normal Goal", "Penalty", "Yellow Card", ...). Anahtar
     * slug'lanmış halidir. "Substitution 3" gibi numara eki olan değerlerde
     * tüm metin için anahtar aranır; bulunamazsa numara çıkarılıp temiz
     * "substitution" anahtarı denenir.
     */
    public String eventDetail(String detail, boolean turkish) {
        if (detail == null || detail.isBlank()) {
            return detail;
        }
        String trimmed = detail.trim();
        try {
            return messageSource.getMessage(
                    "football.event.detail." + slug(trimmed), null, locale(turkish));
        } catch (NoSuchMessageException ignored) {
            // "Substitution 3" -> "Substitution"
            int sp = trimmed.lastIndexOf(' ');
            if (sp > 0) {
                String head = trimmed.substring(0, sp);
                try {
                    return messageSource.getMessage(
                            "football.event.detail." + slug(head), null, locale(turkish));
                } catch (NoSuchMessageException ignored2) {
                    // fall through
                }
            }
            return detail;
        }
    }

    /**
     * Maç istatistik tipi ("Shots on Goal", "Ball Possession", "Passes %", ...).
     *
     * <p>API farkli format kullanir: bazi key boslukla ("Shots on Goal"),
     * bazi snake_case ("expected_goals"), bazi yuzde isareti ("Passes %").
     * Slug normalize edilir (lowercase, bosluk/underscore/% → tire) ve
     * {@code football.statistic.type.<slug>} anahtari aranir. Bilinmeyen
     * tip kaynak deger ile doner — yeni stat eklendiginde site bozulmaz.
     */
    public String statisticType(String type, boolean turkish) {
        if (type == null || type.isBlank()) {
            return type;
        }
        // ONEMLI: "%" -> "percent" donusumu slug'DAN ONCE yapilmali. Aksi halde
        // slug() '%' karakterini (alfasayisal olmayan) tireye cevirip atiyor ve
        // "Passes %" -> "passes" oluyordu; boylece "passes-percent" anahtari
        // bulunamayip ham "Passes %" donuyordu (TR'de cevrilmiyordu). Once
        // metinde % -> " percent", sonra slug.
        String key = slug(type.replace("%", " percent"));
        return messageSource.getMessage(
                "football.statistic.type." + key, null, type, locale(turkish));
    }

    /**
     * Tahmin yorumu ("Win or draw", "Win", "Draw", ...). API'nin döndürdüğü
     * serbest metni slug'layıp anahtar olarak arar. Bulunamazsa kaynak metin.
     */
    public String predictionComment(String comment, boolean turkish) {
        if (comment == null || comment.isBlank()) {
            return comment;
        }
        return messageSource.getMessage(
                "football.prediction.comment." + slug(comment),
                null, comment, locale(turkish));
    }

    /**
     * Puan durumu açıklaması. API çok çeşitli format döner:
     * <ul>
     *   <li>Basit: {@code "Promotion"}, {@code "Relegation"}, {@code "Playoffs"}</li>
     *   <li>Önek + yarışma: {@code "Promotion - Champions League"}</li>
     *   <li>Önek + yarışma + aşama: {@code "Promotion - Champions League (Group Stage)"}</li>
     *   <li>Yeni CL/EL format: {@code "Promotion - Champions League (Play Offs: 1/16-finals)"}</li>
     * </ul>
     *
     * <p>Algoritma 3 katmanlı:
     * <ol>
     *   <li>Tüm değerin slug'ıyla doğrudan çeviri (önceden tanımlı sözlük).
     *       Yönetici özel bir varyant için anahtar ekleyebilir.</li>
     *   <li>Bulunamazsa pattern parser: önek/yarışma/aşama parçalarını ayrı
     *       ayrı çevirip birleştirir. Bu sayede yeni varyantlar otomatik
     *       işlenir (örn. "Promotion - Conference League (Play-offs)").</li>
     *   <li>Hâlâ çözülememişse kaynak (İngilizce) metin döner.</li>
     * </ol>
     *
     * <p>EN dilinde kaynak metin zaten İngilizce; parser çalıştırmadan
     * doğrudan döneriz.
     */
    public String standingDescription(String description, boolean turkish) {
        if (description == null || description.isBlank()) {
            return description;
        }
        String trimmed = description.trim();

        // 1) Doğrudan sözlük eşleşmesi
        try {
            return messageSource.getMessage(
                    "football.standing.description." + slug(trimmed),
                    null, locale(turkish));
        } catch (NoSuchMessageException ignored) {
            // sonraki adıma geç
        }

        // 2) EN için kaynak metin zaten İngilizce; parser gereksiz
        if (!turkish) {
            return description;
        }

        // 3) Akıllı parser — TR için
        String parsed = parseStandingDescriptionTr(trimmed);
        return parsed != null ? parsed : description;
    }

    /**
     * "Önek - Yarışma (Aşama)" desenini Türkçe parçalara ayırıp birleştirir.
     * Hiçbir parça eşleşmezse {@code null} döner (kaynak metin tutulur).
     *
     * <p>Desteklenen önekler: Promotion, Relegation, Qualification.<br>
     * Desteklenen yarışmalar: Champions League, Europa League, Europa
     * Conference League / Conference League, Championship, League One, League
     * Two, 2. Bundesliga, Premier League, Liga, Serie A, vb. (genişletilebilir).<br>
     * Desteklenen aşamalar: Group Stage, Qualifying, Play-offs, Play Offs:
     * 1/16-finals, Knockout Stage, vb.
     */
    private static String parseStandingDescriptionTr(String desc) {
        String prefix = null;
        String rest = desc;

        // Önek tespiti
        if (rest.startsWith("Promotion - ")) {
            prefix = "Yükselme";
            rest = rest.substring("Promotion - ".length());
        } else if (rest.startsWith("Relegation - ")) {
            prefix = "Küme Düşme";
            rest = rest.substring("Relegation - ".length());
        } else if (rest.startsWith("Qualification - ")) {
            prefix = "Eleme";
            rest = rest.substring("Qualification - ".length());
        } else if (rest.equalsIgnoreCase("Relegation")) {
            return "Küme Düşme";
        } else if (rest.equalsIgnoreCase("Promotion")) {
            return "Yükselme";
        } else if (rest.equalsIgnoreCase("Playoffs") || rest.equalsIgnoreCase("Play-offs")) {
            return "Play-off";
        }

        // Parantez içi aşamayı ayır
        String phase = null;
        String competition = rest;
        int parenStart = rest.indexOf('(');
        int parenEnd = rest.indexOf(')');
        if (parenStart > 0 && parenEnd > parenStart) {
            competition = rest.substring(0, parenStart).trim();
            phase = rest.substring(parenStart + 1, parenEnd).trim();
        }

        String competitionTr = translateCompetition(competition);
        String phaseTr = phase != null ? translatePhase(phase) : null;

        // Hiç önek + parça çevirisi olmadıysa null
        if (prefix == null && competitionTr == null && phaseTr == null) {
            return null;
        }

        StringBuilder out = new StringBuilder();
        if (prefix != null) {
            out.append(prefix).append(" - ");
        }
        out.append(competitionTr != null ? competitionTr : competition);
        if (phaseTr != null && !phaseTr.isBlank()) {
            out.append(" (").append(phaseTr).append(")");
        } else if (phase != null && !phase.isBlank()) {
            out.append(" (").append(phase).append(")");
        }
        return out.toString();
    }

    /** Yarışma adını Türkçe karşılığına çevirir; bilinmeyen ise null. */
    private static String translateCompetition(String name) {
        if (name == null) return null;
        return switch (name.toLowerCase(Locale.ROOT).trim()) {
            case "champions league" -> "Şampiyonlar Ligi";
            case "europa league" -> "Avrupa Ligi";
            case "europa conference league", "conference league" -> "Konferans Ligi";
            case "premier league" -> "Premier League";
            case "championship" -> "Championship (İngiltere 2. Lig)";
            case "league one" -> "League One (İngiltere 3. Lig)";
            case "league two" -> "League Two (İngiltere 4. Lig)";
            case "2. bundesliga" -> "2. Bundesliga";
            case "3. liga" -> "3. Liga";
            case "serie b" -> "Serie B";
            case "la liga 2", "segunda división", "segunda division" -> "La Liga 2";
            case "ligue 2" -> "Ligue 2";
            case "1. lig" -> "1. Lig";
            case "tff 1. lig" -> "TFF 1. Lig";
            case "turkish cup" -> "Türkiye Kupası";
            case "fa cup" -> "FA Cup";
            case "dfb pokal" -> "Almanya Kupası";
            case "coupe de france" -> "Fransa Kupası";
            case "coppa italia" -> "İtalya Kupası";
            case "copa del rey" -> "İspanya Kupası";
            default -> null;
        };
    }

    /** Aşama metnini Türkçe karşılığına çevirir; bilinmeyen ise null. */
    private static String translatePhase(String phase) {
        if (phase == null) return null;
        String key = phase.toLowerCase(Locale.ROOT).trim();
        return switch (key) {
            case "group stage" -> "Grup";
            case "qualifying", "qualification" -> "Eleme";
            case "play offs", "play-offs", "playoffs" -> "Play-off";
            case "play offs: 1/16-finals" -> "Play-off (1/16 Final)";
            case "play offs: 1/8-finals" -> "Play-off (Son 16)";
            case "knockout stage" -> "Eleme Aşaması";
            case "knockout round play-offs" -> "Eleme Play-off";
            case "round of 16" -> "Son 16";
            case "quarter-finals", "quarter finals" -> "Çeyrek Final";
            case "semi-finals", "semi finals" -> "Yarı Final";
            case "final" -> "Final";
            default -> null;
        };
    }

    /**
     * Sakatlık türü: "Missing Fixture" / "Questionable". Sözlükten çevrilir;
     * bulunamazsa kaynak metin.
     */
    public String injuryType(String type, boolean turkish) {
        if (type == null || type.isBlank()) {
            return type;
        }
        return messageSource.getMessage(
                "football.injury.type." + slug(type), null, type, locale(turkish));
    }

    /**
     * Sakatlık/yokluk sebebi. API çok çeşitli metin döner:
     * <ul>
     *   <li>Yaralanma: "Thigh Injury", "Knee Injury", "Hamstring Injury", ...</li>
     *   <li>Ceza: "Red Card", "Yellow Card Suspension", "Suspended"</li>
     *   <li>Diğer: "Coach Decision", "National Selection", "Illness", "Personal Reason"</li>
     *   <li>Ameliyat: "Knee Surgery", "Achilles Tendon Surgery", ...</li>
     * </ul>
     *
     * <p>3 katmanlı strateji:
     * <ol>
     *   <li>Tam sözlük eşleşmesi (admin override mümkün)</li>
     *   <li>EN için kaynak metin (zaten İngilizce)</li>
     *   <li>TR için akıllı parser: "X Injury/Surgery/Knock/Strain/Sprain"
     *       desenini parçalara ayırıp her birini çevirir</li>
     * </ol>
     */
    public String injuryReason(String reason, boolean turkish) {
        if (reason == null || reason.isBlank()) {
            return reason;
        }
        String trimmed = reason.trim();

        // 1) Tam sözlük eşleşmesi
        try {
            return messageSource.getMessage(
                    "football.injury.reason." + slug(trimmed), null, locale(turkish));
        } catch (NoSuchMessageException ignored) {
            // sonraki adıma
        }
        // 2) EN için kaynak metin
        if (!turkish) {
            return reason;
        }
        // 3) TR için akıllı parser
        String parsed = parseInjuryReasonTr(trimmed);
        return parsed != null ? parsed : reason;
    }

    /**
     * "{Body Part} {Type}" desenini Türkçe parçalara ayırıp birleştirir.
     * Örnekler:
     * <ul>
     *   <li>"Thigh Injury" → "Adale Sakatlığı"</li>
     *   <li>"Achilles Tendon Surgery" → "Aşil Tendon Ameliyatı"</li>
     *   <li>"Knee Knock" → "Diz Darbesi"</li>
     * </ul>
     */
    private static String parseInjuryReasonTr(String reason) {
        // Bilinen sonek tiplerini ara
        String[] suffixes = {
                "Surgery", "Injury", "Knock", "Hit", "Strain", "Sprain",
                "Suspension"
        };
        for (String suffix : suffixes) {
            String lowerSuffix = " " + suffix.toLowerCase(Locale.ROOT);
            if (reason.toLowerCase(Locale.ROOT).endsWith(lowerSuffix)) {
                String bodyPart = reason.substring(0,
                        reason.length() - lowerSuffix.length()).trim();
                String partTr = translateBodyPart(bodyPart);
                String suffixTr = translateInjurySuffix(suffix);
                if (partTr != null && suffixTr != null) {
                    return partTr + " " + suffixTr;
                }
                // Sonek tanındı ama body part tanınmadı → kaynak body part + TR sonek
                if (suffixTr != null) {
                    return bodyPart + " " + suffixTr;
                }
            }
        }
        return null;  // hiç desen yakalanmadı
    }

    /** Vücut bölgesini Türkçe karşılığına çevirir; bilinmeyen null. */
    private static String translateBodyPart(String part) {
        if (part == null) return null;
        return switch (part.toLowerCase(Locale.ROOT).trim()) {
            case "thigh", "muscle" -> "Adale";
            case "hamstring" -> "Arka Adale";
            case "knee" -> "Diz";
            case "ankle" -> "Ayak Bileği";
            case "foot" -> "Ayak";
            case "calf" -> "Baldır";
            case "groin" -> "Kasık";
            case "back" -> "Sırt";
            case "head" -> "Baş";
            case "shoulder" -> "Omuz";
            case "hip" -> "Kalça";
            case "achilles tendon", "achilles" -> "Aşil Tendon";
            case "cruciate ligament", "acl" -> "Çapraz Bağ";
            case "meniscus" -> "Menisküs";
            case "rib" -> "Kaburga";
            case "chest" -> "Göğüs";
            case "neck" -> "Boyun";
            case "wrist" -> "Bilek";
            case "hand" -> "El";
            case "arm" -> "Kol";
            case "elbow" -> "Dirsek";
            case "face" -> "Yüz";
            case "nose" -> "Burun";
            case "eye" -> "Göz";
            case "toe" -> "Ayak Parmağı";
            case "finger" -> "Parmak";
            case "yellow card" -> "Sarı Kart";
            case "injury" -> "Sakatlık";
            default -> null;
        };
    }

    /** Yaralanma tipini Türkçe karşılığına çevirir. */
    private static String translateInjurySuffix(String suffix) {
        return switch (suffix.toLowerCase(Locale.ROOT)) {
            case "injury" -> "Sakatlığı";
            case "surgery" -> "Ameliyatı";
            case "knock" -> "Darbesi";
            case "hit" -> "Darbesi";
            case "strain" -> "Gerilimi";
            case "sprain" -> "Burkulması";
            case "suspension" -> "Cezası";
            default -> null;
        };
    }

    /**
     * Grup adı çevirisi: "Group A" → "Grup A", "Group 1" → "Grup 1" (TR);
     * "Group A" → "Group A" (EN, dokunulmaz). Grup harfi/sayısı korunur.
     * "Group" prefix'i yoksa değer aynen döner ("Normal" gibi).
     */
    public String standingGroupName(String groupName, boolean turkish) {
        if (groupName == null || groupName.isBlank()) {
            return groupName;
        }
        String trimmed = groupName.trim();
        if (!turkish) {
            return trimmed;  // EN: kaynak değer zaten dogru ("Group A").
        }
        // TR: "Group" -> "Grup" replacement, kalanı koru.
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("group ")) {
            return "Grup " + trimmed.substring(6);
        }
        if (trimmed.equalsIgnoreCase("normal") || trimmed.equalsIgnoreCase("group")) {
            return "Sıralama";
        }
        return trimmed;
    }

    /**
     * Oyuncu pozisyonu çevirisi (Goalkeeper / Defender / Midfielder / Attacker).
     * Bilinmeyen pozisyon kaynak metinle döner.
     */
    public String playerPosition(String position, boolean turkish) {
        if (position == null || position.isBlank()) {
            return position;
        }
        return messageSource.getMessage(
                "football.position." + slug(position),
                null,
                position,
                locale(turkish));
    }

    /**
     * Transfer turu cevirisi (Transfer / Loan / Free / N/A).
     * Para ile yazilanlar ("€ 1.1M", "$ 500K") evrenseldir, dokunulmaz —
     * onlari fiyat sembollerine bakarak pass-through doneriz.
     */
    public String transferType(String type, boolean turkish) {
        if (type == null || type.isBlank()) {
            return type;
        }
        String trimmed = type.trim();
        // Para birimi simgesi varsa cevirme — "€ 1.1M" / "$ 500K" / "£ 2M"
        if (!trimmed.isEmpty()) {
            char c = trimmed.charAt(0);
            if (c == '€' /*€*/ || c == '$' || c == '£' /*£*/) {
                return trimmed;
            }
        }
        return messageSource.getMessage(
                "football.transfer.type." + slug(trimmed),
                null,
                trimmed,
                locale(turkish));
    }

    /**
     * Kupa yerleşim metni (Winner / 2nd Place / 3rd Place / Runner-up vb.).
     * Coach trophies widget'i icin. Bilinmeyen kaynak metinle döner.
     */
    public String trophyPlace(String place, boolean turkish) {
        if (place == null || place.isBlank()) {
            return place;
        }
        return messageSource.getMessage(
                "football.trophy.place." + slug(place),
                null,
                place,
                locale(turkish));
    }

    /** Metni anahtara çevirir: küçük harf, alfasayısal olmayan → tire. */
    private static String slug(String value) {
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
}
