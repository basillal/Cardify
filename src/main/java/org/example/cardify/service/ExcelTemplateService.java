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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ExcelTemplateService {
    public void writeTemplate(Path targetFile, List<String> headers) {
        try (Workbook workbook = new XSSFWorkbook(); OutputStream outputStream = Files.newOutputStream(targetFile)) {
            Sheet sheet = workbook.createSheet("Card Data");
            Sheet guideSheet = workbook.createSheet("Instructions");

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle instructionStyle = createInstructionStyle(workbook);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }
            if (headers.isEmpty()) {
                Cell cell = headerRow.createCell(0);
                cell.setCellValue("name");
                cell.setCellStyle(headerStyle);
            }

            Row sampleRow = sheet.createRow(1);
            for (int i = 0; i < Math.max(headers.size(), 1); i++) {
                sampleRow.createCell(i).setCellValue("enter data here");
            }

            guideSheet.createRow(0).createCell(0).setCellValue("Cardify workbook instructions");
            guideSheet.createRow(2).createCell(0).setCellValue("1. Fill the Card Data sheet with one row per ID card.");
            guideSheet.createRow(3).createCell(0).setCellValue("2. Use the same placeholder names as the HTML template, for example name, department, photo.");
            guideSheet.createRow(4).createCell(0).setCellValue("3. For image placeholders, enter a local image file path such as /home/user/photos/employee.png.");
            guideSheet.createRow(5).createCell(0).setCellValue("4. For QR placeholders, include a column in Card Data containing the text to encode (use column names starting with 'qr' or 'qr_').");
            guideSheet.createRow(6).createCell(0).setCellValue("5. Import the workbook into the desktop app, select rows, and print.");
            guideSheet.createRow(7).createCell(0).setCellValue("6. In HTML templates, place image placeholders inside src attributes, for example <img src=\"{{photo}}\" />.");

            guideSheet.getRow(0).getCell(0).setCellStyle(headerStyle);
            for (int rowIndex = 2; rowIndex <= 6; rowIndex++) {
                guideSheet.getRow(rowIndex).getCell(0).setCellStyle(instructionStyle);
            }

            for (int i = 0; i < Math.max(headers.size(), 1); i++) {
                sheet.autoSizeColumn(i);
            }
            guideSheet.autoSizeColumn(0);
            workbook.write(outputStream);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write workbook template to " + targetFile, exception);
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

    private CellStyle createInstructionStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        return style;
    }
}
