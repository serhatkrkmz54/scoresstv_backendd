package com.scorestv.football.translation;

import com.scorestv.common.ApiException;
import com.scorestv.football.domain.Country;
import com.scorestv.football.domain.CountryRepository;
import com.scorestv.football.domain.League;
import com.scorestv.football.domain.LeagueRepository;
import com.scorestv.football.domain.Team;
import com.scorestv.football.domain.TeamRepository;
import com.scorestv.football.domain.TranslatableName;
import com.scorestv.football.domain.Venue;
import com.scorestv.football.domain.VenueRepository;
import com.scorestv.football.translation.dto.TranslationImportResult;
import com.scorestv.football.translation.dto.TranslationRowView;
import com.scorestv.football.translation.dto.TranslationStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Türkçe çevirilerin (name_tr) okunması, Excel için derlenmesi ve içe
 * aktarılması. Varlık tipine göre doğru repository'yi seçer; ortak mantık
 * {@link TranslatableName} arayüzü üzerinden jenerik yürütülür.
 *
 * <p>Çeviri akışı yalnızca {@code name_tr} alanına dokunur — API alanlarına
 * (name, logo, vb.) asla yazmaz. Tersine, senkron upsert'i de {@code name_tr}'ye
 * dokunmaz; iki akış birbirinin verisini ezmez.
 */
@Service
public class TranslationService {

    private static final Logger log = LoggerFactory.getLogger(TranslationService.class);

    /** Bir içe aktarım yanıtında listelenecek azami satır hatası sayısı. */
    private static final int MAX_ERRORS = 50;

    /** Excel export'ta satırlar İngilizce ada göre sıralanır. */
    private static final Sort BY_NAME = Sort.by(Sort.Direction.ASC, "name");

    /** Sabit ilk üç kolon başlığı (id / İngilizce ad / Türkçe ad). */
    private static final List<String> BASE_HEADERS =
            List.of("id", "Ad (İngilizce)", "Türkçe Ad — DOLDURUN");

    /** Türkçe adın yazılacağı kolon indeksi. */
    private static final int EDITABLE_COLUMN = 2;

    private final CountryRepository countryRepository;
    private final LeagueRepository leagueRepository;
    private final TeamRepository teamRepository;
    private final VenueRepository venueRepository;

    public TranslationService(CountryRepository countryRepository,
                              LeagueRepository leagueRepository,
                              TeamRepository teamRepository,
                              VenueRepository venueRepository) {
        this.countryRepository = countryRepository;
        this.leagueRepository = leagueRepository;
        this.teamRepository = teamRepository;
        this.venueRepository = venueRepository;
    }

    /**
     * Bir tipin tüm kayıtlarını Excel için tipten bağımsız tabloya derler.
     * Satırlar İngilizce ada göre sıralıdır; bağlam kolonları çevirmene yardım
     * amaçlıdır (yalnızca okuma).
     */
    @Transactional(readOnly = true)
    public ExportSheet buildExportSheet(TranslationType type) {
        List<String> headers = new ArrayList<>(BASE_HEADERS);
        List<List<String>> rows = new ArrayList<>();

        switch (type) {
            case COUNTRIES -> {
                for (Country c : countryRepository.findAll(BY_NAME)) {
                    rows.add(baseRow(c));
                }
            }
            case LEAGUES -> {
                headers.add("Tür");
                headers.add("Ülke");
                for (League l : leagueRepository.findAll(BY_NAME)) {
                    rows.add(baseRow(l, l.getType(), l.getCountryName()));
                }
            }
            case TEAMS -> {
                headers.add("Ülke");
                for (Team t : teamRepository.findAll(BY_NAME)) {
                    rows.add(baseRow(t, t.getCountry()));
                }
            }
            case VENUES -> {
                headers.add("Şehir");
                for (Venue v : venueRepository.findAll(BY_NAME)) {
                    rows.add(baseRow(v, v.getCity()));
                }
            }
        }

        log.info("Çeviri export'u derlendi: tip={} satır={}", type.getPath(), rows.size());
        return new ExportSheet(type.getSingular(), headers, rows, EDITABLE_COLUMN);
    }

    /** id / İngilizce ad / Türkçe ad + (varsa) bağlam kolonlarından satır kurar. */
    private static List<String> baseRow(TranslatableName entity, String... context) {
        List<String> row = new ArrayList<>(3 + context.length);
        row.add(String.valueOf(entity.getId()));
        row.add(nullToEmpty(entity.getName()));
        row.add(nullToEmpty(entity.getNameTr()));
        for (String value : context) {
            row.add(nullToEmpty(value));
        }
        return row;
    }

    /**
     * Excel'den okunan düzenleme satırlarını uygular — yalnızca {@code name_tr}
     * güncellenir. Boş Türkçe hücreler atlanır (yanlışlıkla çeviri silinmez).
     */
    @Transactional
    public TranslationImportResult applyImport(TranslationType type, List<RowEdit> edits) {
        TranslationImportResult result = switch (type) {
            case COUNTRIES ->
                    apply(type, edits, countryRepository::findById, countryRepository::save);
            case LEAGUES ->
                    apply(type, edits, leagueRepository::findById, leagueRepository::save);
            case TEAMS ->
                    apply(type, edits, teamRepository::findById, teamRepository::save);
            case VENUES ->
                    apply(type, edits, venueRepository::findById, venueRepository::save);
        };
        log.info("Çeviri içe aktarımı: tip={} satır={} güncellenen={} atlanan={} bulunamayan={}",
                result.type(), result.totalRows(), result.updated(),
                result.skipped(), result.notFound());
        return result;
    }

    /** Tipten bağımsız içe aktarım çekirdeği. */
    private <T extends TranslatableName> TranslationImportResult apply(
            TranslationType type,
            List<RowEdit> edits,
            Function<Long, Optional<T>> finder,
            Consumer<T> saver) {

        int updated = 0;
        int skipped = 0;
        int notFound = 0;
        List<String> errors = new ArrayList<>();
        int maxLen = type.getMaxNameTrLength();

        for (RowEdit edit : edits) {
            String value = edit.nameTr() == null ? "" : edit.nameTr().trim();
            if (value.isEmpty()) {
                // Boş Türkçe hücre = değişiklik yok; mevcut çeviriyi korur.
                skipped++;
                continue;
            }
            if (value.length() > maxLen) {
                addError(errors, "Satır " + edit.excelRow() + ": Türkçe ad çok uzun ("
                        + value.length() + " karakter; en fazla " + maxLen + ").");
                skipped++;
                continue;
            }
            Optional<T> found = finder.apply(edit.id());
            if (found.isEmpty()) {
                addError(errors, "Satır " + edit.excelRow() + ": id=" + edit.id()
                        + " veritabanında bulunamadı.");
                notFound++;
                continue;
            }
            T entity = found.get();
            entity.setNameTr(value);
            saver.accept(entity);
            updated++;
        }
        return new TranslationImportResult(
                type.getPath(), edits.size(), updated, skipped, notFound, errors);
    }

    /**
     * Tek bir varlığın Türkçe adını günceller. {@code nameTr} boş/null ise
     * mevcut çeviri temizlenir (name_tr = null).
     *
     * @throws ApiException 400 — ad çok uzunsa, 404 — varlık bulunamazsa
     */
    @Transactional
    public TranslationRowView updateOne(TranslationType type, long id, String nameTr) {
        String value = (nameTr == null || nameTr.isBlank()) ? null : nameTr.trim();
        if (value != null && value.length() > type.getMaxNameTrLength()) {
            throw ApiException.badRequest("Türkçe ad çok uzun; en fazla "
                    + type.getMaxNameTrLength() + " karakter olabilir.");
        }
        TranslatableName entity = switch (type) {
            case COUNTRIES ->
                    saveOne(type, countryRepository.findById(id), value, countryRepository::save);
            case LEAGUES ->
                    saveOne(type, leagueRepository.findById(id), value, leagueRepository::save);
            case TEAMS ->
                    saveOne(type, teamRepository.findById(id), value, teamRepository::save);
            case VENUES ->
                    saveOne(type, venueRepository.findById(id), value, venueRepository::save);
        };
        return new TranslationRowView(
                type.getPath(), entity.getId(), entity.getName(), entity.getNameTr());
    }

    /** Bulunan varlığın name_tr'sini ayarlayıp kaydeder; yoksa 404 fırlatır. */
    private static <T extends TranslatableName> T saveOne(TranslationType type,
                                                          Optional<T> found,
                                                          String value,
                                                          Consumer<T> saver) {
        T entity = found.orElseThrow(() ->
                ApiException.notFound(type.getSingular() + " bulunamadı."));
        entity.setNameTr(value);
        saver.accept(entity);
        return entity;
    }

    /** Tüm tiplerin Türkçe çeviri ilerlemesini döner. */
    @Transactional(readOnly = true)
    public TranslationStatusResponse status() {
        List<TranslationStatusResponse.TypeStatus> list = new ArrayList<>();
        list.add(typeStatus(TranslationType.COUNTRIES,
                countryRepository.count(), countryRepository.countByNameTrIsNotNull()));
        list.add(typeStatus(TranslationType.LEAGUES,
                leagueRepository.count(), leagueRepository.countByNameTrIsNotNull()));
        list.add(typeStatus(TranslationType.TEAMS,
                teamRepository.count(), teamRepository.countByNameTrIsNotNull()));
        list.add(typeStatus(TranslationType.VENUES,
                venueRepository.count(), venueRepository.countByNameTrIsNotNull()));
        return new TranslationStatusResponse(list);
    }

    private static TranslationStatusResponse.TypeStatus typeStatus(
            TranslationType type, long total, long translated) {
        return new TranslationStatusResponse.TypeStatus(
                type.getPath(), type.getLabel(), total, translated, total - translated);
    }

    /** Hata listesini {@link #MAX_ERRORS} ile sınırlar; aşılınca tek özet satır ekler. */
    private static void addError(List<String> errors, String message) {
        if (errors.size() < MAX_ERRORS) {
            errors.add(message);
        } else if (errors.size() == MAX_ERRORS) {
            errors.add("... daha fazla hata var; yalnızca ilk "
                    + MAX_ERRORS + " tanesi gösteriliyor.");
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
