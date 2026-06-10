package com.scorestv.football.translation;

import com.scorestv.common.ApiException;
import com.scorestv.football.translation.dto.TranslationImportResult;
import com.scorestv.football.translation.dto.TranslationRowView;
import com.scorestv.football.translation.dto.TranslationStatusResponse;
import com.scorestv.football.translation.dto.UpdateTranslationRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * ADMIN'in API-Football varlıklarının Türkçe adlarını (name_tr) elle yönetmesi
 * için endpoint'ler.
 *
 * <p>Akış: bir tipin tablosu {@code /export} ile {@code .xlsx} olarak indirilir,
 * "Türkçe Ad" kolonu Excel'de elle doldurulur, {@code /import} ile geri yüklenir.
 * Tekil düzeltmeler {@code PUT /{type}/{id}} ile yapılır. {@code /status}
 * çeviri ilerlemesini verir.
 *
 * <p>Tüm endpoint'ler ADMIN rolü gerektirir.
 */
@RestController
@RequestMapping("/api/v1/admin/translations")
@PreAuthorize("hasRole('ADMIN')")
public class TranslationAdminController {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final TranslationService translationService;
    private final TranslationExcelService excelService;

    public TranslationAdminController(TranslationService translationService,
                                      TranslationExcelService excelService) {
        this.translationService = translationService;
        this.excelService = excelService;
    }

    /** Tüm tipler için Türkçe çeviri ilerlemesi (toplam / çevrilen / kalan). */
    @GetMapping("/status")
    public TranslationStatusResponse status() {
        return translationService.status();
    }

    /**
     * Bir tipin tüm kayıtlarını {@code .xlsx} olarak indirir. "Türkçe Ad" kolonu
     * Excel'de doldurulup {@link #importFile} ile geri yüklenir.
     *
     * @param type countries | leagues | teams | venues
     */
    @GetMapping("/{type}/export")
    public ResponseEntity<byte[]> export(@PathVariable String type) {
        TranslationType resolved = TranslationType.fromPath(type);
        byte[] body = excelService.write(translationService.buildExportSheet(resolved));
        String filename = "scorestv-ceviri-" + resolved.getPath() + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(XLSX_CONTENT_TYPE))
                .body(body);
    }

    /**
     * Doldurulmuş {@code .xlsx} dosyasını okuyup yalnızca name_tr kolonunu
     * günceller. Boş Türkçe hücreler atlanır; sonuç özeti döner.
     *
     * @param type countries | leagues | teams | venues
     * @param file çok parçalı (multipart) {@code .xlsx} dosyası
     */
    @PostMapping("/{type}/import")
    public TranslationImportResult importFile(@PathVariable String type,
                                              @RequestParam("file") MultipartFile file) {
        TranslationType resolved = TranslationType.fromPath(type);
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Yüklenecek dosya boş.");
        }
        List<RowEdit> edits;
        try (InputStream in = file.getInputStream()) {
            edits = excelService.read(in);
        } catch (IOException ex) {
            throw ApiException.badRequest("Dosya okunamadı.");
        }
        return translationService.applyImport(resolved, edits);
    }

    /**
     * Tek bir varlığın Türkçe adını günceller. İstek gövdesinde {@code nameTr}
     * boş bırakılırsa mevcut çeviri temizlenir.
     *
     * @param type countries | leagues | teams | venues
     * @param id   varlık id'si
     */
    @PutMapping("/{type}/{id}")
    public TranslationRowView updateOne(@PathVariable String type,
                                        @PathVariable long id,
                                        @RequestBody UpdateTranslationRequest request) {
        TranslationType resolved = TranslationType.fromPath(type);
        String nameTr = (request == null) ? null : request.nameTr();
        return translationService.updateOne(resolved, id, nameTr);
    }
}
