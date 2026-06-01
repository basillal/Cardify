package org.example.cardify.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.PDFPageable;
import org.example.cardify.model.SpreadsheetRow;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import java.awt.print.PrinterJob;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;

import javafx.application.Platform;

public class PrinterService {
    private final HtmlTemplateService htmlTemplateService = new HtmlTemplateService();
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    public List<String> listPrinterNames() {
        return Arrays.stream(PrinterJob.lookupPrintServices())
                .map(PrintService::getName)
                .toList();
    }

    public String getDefaultPrinterName() {
        PrintService defaultService = PrinterJob.getPrinterJob().getPrintService();
        if (defaultService == null) {
            defaultService = PrintServiceLookup.lookupDefaultPrintService();
        }
        return defaultService == null ? null : defaultService.getName();
    }

    public void printRows(String printerName, String htmlTemplate, List<SpreadsheetRow> rows) {
        printRows(printerName, htmlTemplate, rows, java.util.Map.of());
    }

    public void printRows(String printerName, String htmlTemplate, List<SpreadsheetRow> rows, java.util.Map<String, String> qrMappings) {
        Thread printThread = new Thread(() -> {
            for (SpreadsheetRow row : rows) {
                Platform.runLater(() -> row.setStatus("Printing"));
                try {
                    printSingleRow(printerName, htmlTemplate, row, qrMappings);
                    Platform.runLater(() -> row.setStatus("Printed"));
                } catch (RuntimeException exception) {
                    Platform.runLater(() -> row.setStatus("Error"));
                }
            }
        }, "cardify-print-worker");
        printThread.setDaemon(true);
        printThread.start();
    }

    private void printSingleRow(String printerName, String htmlTemplate, SpreadsheetRow row, java.util.Map<String, String> qrMappings) {
        String renderedHtml = htmlTemplateService.renderTemplate(htmlTemplate, row.asMap(), qrMappings);
        Path tempPdf = createTempPdf(renderedHtml);

        try {
            if (IS_WINDOWS) {
                printWithWindowsShell(printerName, tempPdf);
            } else {
                try (PDDocument document = PDDocument.load(tempPdf.toFile())) {
                    printDocumentWithRetry(printerName, document);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to render PDF for printing", exception);
        } finally {
            deleteTempPdf(tempPdf);
        }
    }

    private void printDocumentWithRetry(String printerName, PDDocument document) {
        int maxAttempts = 3;
        long delayMillis = 1200L;
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                PrinterJob job = PrinterJob.getPrinterJob();
                job.setJobName("Cardify - " + printerName);
                PrintService printService = findPrintService(printerName)
                        .orElseThrow(() -> new IllegalStateException("No available printer found for: " + printerName));

                job.setPrintService(printService);
                job.setPageable(new PDFPageable(document));
                job.print();
                return;
            } catch (java.awt.print.PrinterException exception) {
                lastFailure = new IllegalStateException("Unable to print PDF to " + printerName + " (attempt " + attempt + " of " + maxAttempts + ")", exception);
                if (attempt < maxAttempts) {
                    waitBeforeRetry(delayMillis);
                }
            } catch (RuntimeException exception) {
                lastFailure = exception;
                if (attempt < maxAttempts) {
                    waitBeforeRetry(delayMillis);
                }
            }
        }

        throw lastFailure == null ? new IllegalStateException("Unable to print PDF to " + printerName) : lastFailure;
    }

    private void printWithWindowsShell(String printerName, Path tempPdf) {
        int maxAttempts = 3;
        long delayMillis = 1500L;
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Process process = new ProcessBuilder(
                        "cmd",
                        "/c",
                        "powershell",
                        "-NoProfile",
                        "-Command",
                        "$pdf = Resolve-Path -LiteralPath '" + escapeForPowerShell(tempPdf) + "'; " +
                                "$printer = '" + escapeForPowerShell(printerName) + "'; " +
                                "Start-Process -FilePath $pdf -Verb PrintTo -ArgumentList $printer -WindowStyle Hidden -PassThru | Out-Null"
                ).redirectErrorStream(true).start();

                boolean finished = process.waitFor(30, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    throw new IllegalStateException("Windows print command timed out for " + printerName);
                }

                int exitCode = process.exitValue();
                if (exitCode == 0) {
                    return;
                }

                throw new IllegalStateException("Windows print command failed with exit code " + exitCode + " for " + printerName);
            } catch (IOException exception) {
                lastFailure = new IllegalStateException("Unable to launch Windows print command for " + printerName + " (attempt " + attempt + " of " + maxAttempts + ")", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Printing was interrupted while waiting for Windows print command", exception);
            } catch (RuntimeException exception) {
                lastFailure = exception;
            }

            if (attempt < maxAttempts) {
                waitBeforeRetry(delayMillis);
            }
        }

        throw lastFailure == null ? new IllegalStateException("Unable to print PDF to " + printerName) : lastFailure;
    }

    private void waitBeforeRetry(long delayMillis) {
        try {
            TimeUnit.MILLISECONDS.sleep(delayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Printing was interrupted while retrying", exception);
        }
    }

    private String escapeForPowerShell(Path path) {
        return path.toAbsolutePath().toString().replace("'", "''");
    }

    private String escapeForPowerShell(String value) {
        return value.replace("'", "''");
    }

    private Path createTempPdf(String html) {
        try {
            Path tempFile = Files.createTempFile("cardify-print-", ".pdf");
            String rendered = addPdfPageStyles(html == null ? "" : html);
            // Save rendered HTML alongside the temp PDF for debugging
            try {
                Files.writeString(Path.of(tempFile.toString() + ".html"), rendered, StandardCharsets.UTF_8);
            } catch (IOException ignored) {
            }

            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(rendered, null);
            try (var outputStream = Files.newOutputStream(tempFile)) {
                builder.toStream(outputStream);
                try {
                    builder.run();
                } catch (Exception renderEx) {
                    throw new IllegalStateException("PDF rendering failed; debug HTML saved at: " + tempFile + ".html", renderEx);
                }
            }

            // Emit debug info to stdout for easier reproduction
            System.out.println("PrinterService: generated PDF -> " + tempFile.toAbsolutePath());
            System.out.println("PrinterService: debug HTML -> " + Path.of(tempFile.toString() + ".html").toAbsolutePath());

            try {
                long size = Files.size(tempFile);
                if (size <= 0L) {
                    throw new IllegalStateException("Generated PDF is empty; debug HTML saved at: " + tempFile + ".html");
                }
            } catch (IOException ioe) {
                // If we can't measure size, surface an informative error
                throw new IllegalStateException("Unable to verify generated PDF; debug HTML saved at: " + tempFile + ".html", ioe);
            }

            return tempFile;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create PDF output", exception);
        }
    }

    private void deleteTempPdf(Path tempPdf) {
        if (tempPdf == null) {
            return;
        }

        // Allow developers to keep generated PDFs for debugging by setting system property
        boolean keep = "true".equalsIgnoreCase(System.getProperty("cardify.debug.keep", "false"));
        if (keep) {
            System.out.println("PrinterService: keeping PDF for debugging -> " + tempPdf.toAbsolutePath());
            System.out.println("PrinterService: keeping debug HTML -> " + Path.of(tempPdf.toString() + ".html").toAbsolutePath());
            return;
        }

        try {
            Files.deleteIfExists(tempPdf);
        } catch (IOException ignored) {
            tempPdf.toFile().deleteOnExit();
        }
    }

    private Optional<PrintService> findPrintService(String printerName) {
        if (printerName == null || printerName.isBlank()) {
            return Optional.empty();
        }

        return Arrays.stream(PrinterJob.lookupPrintServices())
                .filter(service -> printerName.equals(service.getName()))
                .findFirst();
    }

    private String addPdfPageStyles(String html) {
        String printReset = "<style>@page { size: A4 portrait; margin: 0; } html, body { margin: 0; padding: 0; }</style>";
        if (html == null || html.isBlank()) {
            return "<html><head>" + printReset + "</head><body></body></html>";
        }

        String lowerHtml = html.toLowerCase();
        int headIndex = lowerHtml.indexOf("<head>");
        if (headIndex >= 0) {
            return html.substring(0, headIndex + 6) + printReset + html.substring(headIndex + 6);
        }

        int htmlIndex = lowerHtml.indexOf("<html>");
        if (htmlIndex >= 0) {
            return html.substring(0, htmlIndex + 6) + "<head>" + printReset + "</head>" + html.substring(htmlIndex + 6);
        }

        return "<html><head>" + printReset + "</head><body>" + html + "</body></html>";
    }
}