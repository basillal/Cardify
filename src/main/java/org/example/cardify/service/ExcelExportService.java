package org.example.cardify.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.cardify.model.SpreadsheetRow;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ExcelExportService {
    public void writeRows(Path targetFile, List<String> headers, List<SpreadsheetRow> rows) {
        try (Workbook workbook = new XSSFWorkbook(); OutputStream outputStream = Files.newOutputStream(targetFile)) {
            Sheet sheet = workbook.createSheet("Card Data");
            CellStyle headerStyle = createHeaderStyle(workbook);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }

            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                SpreadsheetRow row = rows.get(rowIndex);
                Row excelRow = sheet.createRow(rowIndex + 1);
                for (int colIndex = 0; colIndex < headers.size(); colIndex++) {
                    String header = headers.get(colIndex);
                    String value = header.equals("Status") ? row.getStatus() : row.getValue(header);
                    excelRow.createCell(colIndex).setCellValue(value != null ? value : "");
                }
            }

            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(outputStream);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write workbook to " + targetFile, exception);
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor((short) 0x0);
        style.setFont(font);
        style.setFillForegroundColor((short) 0x4F81BD);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }
}
