package com.scorestv.football.translation;

import com.scorestv.common.ApiException;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Çeviri tablolarını {@code .xlsx} dosyasına yazan ve doldurulmuş dosyayı geri
 * okuyan POI tabanlı servis. Varlık tipini bilmez — yalnızca {@link ExportSheet}
 * jenerik tablosuyla ve sabit kolon düzeniyle çalışır.
 *
 * <p>Kolon düzeni (hem yazma hem okuma): 0 = id, 1 = İngilizce ad,
 * 2 = Türkçe ad (kullanıcı doldurur), 3+ = bağlam.
 */
@Service
public class TranslationExcelService {

    private static final Logger log = LoggerFactory.getLogger(TranslationExcelService.class);

    /** İçe aktarımda id'nin okunduğu kolon. */
    private static final int ID_COLUMN = 0;
    /** İçe aktarımda Türkçe adın okunduğu kolon. */
    private static final int NAME_TR_COLUMN = 2;

    /**
     * Çeviri tablosunu {@code .xlsx} bayt dizisine dönüştürür.
     */
    public byte[] write(ExportSheet data) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet(data.sheetName());

            CellStyle headerStyle = headerStyle(workbook, IndexedColors.GREY_25_PERCENT);
            CellStyle editHeaderStyle = headerStyle(workbook, IndexedColors.LIGHT_GREEN);
            CellStyle editCellStyle = filledStyle(workbook, IndexedColors.LEMON_CHIFFON);
            CellStyle readonlyStyle = filledStyle(workbook, null);

            List<String> headers = data.headers();
            int editIndex = data.editableColumnIndex();

            // Başlık satırı.
            Row header = sheet.createRow(0);
            for (int c = 0; c < headers.size(); c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(headers.get(c));
                cell.setCellStyle(c == editIndex ? editHeaderStyle : headerStyle);
            }

            // Veri satırları.
            List<List<String>> rows = data.rows();
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                List<String> values = rows.get(r);
                for (int c = 0; c < values.size(); c++) {
                    Cell cell = row.createCell(c);
                    writeCell(cell, c, values.get(c));
                    cell.setCellStyle(c == editIndex ? editCellStyle : readonlyStyle);
                }
            }

            // Başlık satırı kaydırırken görünür kalsın.
            sheet.createFreezePane(0, 1);

            // Sabit kolon genişlikleri — autoSize binlerce satırda yavaş kalır.
            for (int c = 0; c < headers.size(); c++) {
                int chars = switch (c) {
                    case ID_COLUMN -> 12;
                    case 1, NAME_TR_COLUMN -> 42;
                    default -> 26;
                };
                sheet.setColumnWidth(c, chars * 256);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            log.error("Çeviri Excel'i üretilemedi: sayfa={}", data.sheetName(), ex);
            throw ApiException.badRequest("Excel dosyası oluşturulamadı.");
        }
    }

    /**
     * Doldurulmuş {@code .xlsx} akışını okuyup düzenleme satırlarına çevirir.
     * Başlık satırı (0) atlanır; id'si boş satırlar yok sayılır.
     */
    public List<RowEdit> read(InputStream in) {
        List<RowEdit> edits = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw ApiException.badRequest("Excel dosyasında sayfa bulunamadı.");
            }
            int lastRow = sheet.getLastRowNum();
            for (int r = 1; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Long id = readId(row.getCell(ID_COLUMN), formatter);
                if (id == null) {
                    continue;   // id'siz satır = boş satır
                }
                String nameTr = readText(row.getCell(NAME_TR_COLUMN), formatter);
                edits.add(new RowEdit(id, nameTr, r + 1));
            }
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            log.warn("Çeviri Excel'i okunamadı: {}", ex.getMessage());
            throw ApiException.badRequest(
                    "Excel dosyası okunamadı. Geçerli, doldurulmuş bir .xlsx dosyası yükleyin.");
        }
        return edits;
    }

    /** id kolonunu sayısal, diğerlerini metin olarak yazar (Excel uyarısı çıkmaz). */
    private static void writeCell(Cell cell, int column, String value) {
        String safe = value == null ? "" : value;
        if (column == ID_COLUMN) {
            try {
                cell.setCellValue(Long.parseLong(safe.trim()));
                return;
            } catch (NumberFormatException ignored) {
                // sayı değilse metin olarak yaz
            }
        }
        cell.setCellValue(safe);
    }

    /** Hücreden long id okur; boş/geçersizse null döner. */
    private static Long readId(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return (long) cell.getNumericCellValue();
        }
        String text = formatter.formatCellValue(cell).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Hücreden kırpılmış metin okur; boşsa "" döner. */
    private static String readText(Cell cell, DataFormatter formatter) {
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    /** Kalın, dolgulu başlık stili. */
    private static CellStyle headerStyle(Workbook workbook, IndexedColors background) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(background.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        applyBorders(style);
        return style;
    }

    /** Veri hücresi stili; {@code background} null ise dolgusuz. */
    private static CellStyle filledStyle(Workbook workbook, IndexedColors background) {
        CellStyle style = workbook.createCellStyle();
        if (background != null) {
            style.setFillForegroundColor(background.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        applyBorders(style);
        return style;
    }

    private static void applyBorders(CellStyle style) {
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }
}
