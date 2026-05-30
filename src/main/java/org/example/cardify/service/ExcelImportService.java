package org.example.cardify.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.example.cardify.model.SpreadsheetRow;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExcelImportService {
    public List<SpreadsheetRow> readRows(Path workbookPath) {
        try (InputStream inputStream = Files.newInputStream(workbookPath); Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                return List.of();
            }

            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                return List.of();
            }

            List<String> headers = extractHeaders(headerRow);
            List<SpreadsheetRow> rows = new ArrayList<>();
            DataFormatter formatter = new DataFormatter();

            for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                Map<String, String> values = new LinkedHashMap<>();
                boolean hasValue = false;
                for (int columnIndex = 0; columnIndex < headers.size(); columnIndex++) {
                    Cell cell = row.getCell(columnIndex);
                    String value = cell == null ? "" : formatter.formatCellValue(cell).trim();
                    if (!value.isBlank()) {
                        hasValue = true;
                    }
                    values.put(headers.get(columnIndex), value);
                }
                if (hasValue) {
                    rows.add(new SpreadsheetRow(values));
                }
            }
            return rows;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read workbook: " + workbookPath, exception);
        }
    }

    private List<String> extractHeaders(Row headerRow) {
        List<String> headers = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        for (int columnIndex = 0; columnIndex < headerRow.getLastCellNum(); columnIndex++) {
            Cell cell = headerRow.getCell(columnIndex);
            String value = cell == null ? "" : formatter.formatCellValue(cell).trim();
            if (value.isBlank()) {
                value = "column_" + (columnIndex + 1);
            }
            headers.add(value);
        }
        return headers;
    }
}
