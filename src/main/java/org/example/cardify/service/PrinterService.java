package org.example.cardify.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.PDFPageable;
import org.apache.pdfbox.printing.PDFPrintable;
import org.apache.pdfbox.printing.Scaling;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.example.cardify.model.SpreadsheetRow;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import java.awt.print.Book;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.PrinterJob;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;
import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.MediaPrintableArea;
import javax.print.attribute.standard.MediaSize;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.OrientationRequested;
import javax.print.attribute.standard.Sides;

import javafx.application.Platform;

public class PrinterService {
    // Cardify prints only fixed-size ID cards.
    // The PDF renderer and print dispatch paths are optimized for card-sized output.
    private final HtmlTemplateService htmlTemplateService = new HtmlTemplateService();

    // Card page dimensions in millimetres — configurable from the UI.
    // Defaults to the standard CR-80 size: 53.98 mm × 85.60 mm.
    // Must match the @page CSS rule injected by addPdfPageStyles() so the PDF
    // page is exactly the same size as the negotiated PageFormat (scale = 1.0).
    private float cardWidthMm  = 53.98f;
    private float cardHeightMm = 85.60f;

    /** Called by the UI when the user changes the page-size configuration. */
    public void setCardSizeMm(float widthMm, float heightMm) {
        this.cardWidthMm  = widthMm;
        this.cardHeightMm = heightMm;
        appendLog(String.format("PrinterService: card size updated to %.2f × %.2f mm", widthMm, heightMm));
    }

    public float getCardWidthMm()  { return cardWidthMm; }
    public float getCardHeightMm() { return cardHeightMm; }

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

    public void printCards(String printerName, String htmlTemplate, List<SpreadsheetRow> rows) {
        printCards(printerName, htmlTemplate, rows, java.util.Map.of(), null);
    }

    public void printCards(String printerName, String htmlTemplate, List<SpreadsheetRow> rows, java.util.Map<String, String> qrMappings) {
        printCards(printerName, htmlTemplate, rows, qrMappings, null);
    }

    public void printCards(String printerName, String htmlTemplate, List<SpreadsheetRow> rows, java.util.Map<String, String> qrMappings, Path templatePath) {
        Thread printThread = new Thread(() -> {
            appendLog("Starting printCards: printer='" + printerName + "', cards=" + rows.size());
            appendLog("Available printers: " + String.join(", ", listPrinterNames()));
            appendLog("System default printer: " + getDefaultPrinterName());
            for (SpreadsheetRow row : rows) {
                Platform.runLater(() -> row.setStatus("Printing"));
                try {
                    printSingleCard(printerName, htmlTemplate, row, qrMappings, templatePath);
                    Platform.runLater(() -> row.setStatus("Printed"));
                } catch (Throwable exception) {
                    // Catch Throwable (not just RuntimeException) to ensure JVM Errors such as
                    // NoClassDefFoundError — which occur when required modules are absent from the
                    // jpackage runtime image — are logged and surfaced instead of silently dying.
                    appendLog("PrinterService: failed to print card for printer '" + printerName + "': "
                            + exception.getClass().getName() + ": " + exception.getMessage());
                    appendStackTrace(exception);
                    Platform.runLater(() -> row.setStatus("Error"));
                }
            }
        }, "cardify-print-worker");
        printThread.setDaemon(true);
        printThread.start();
    }

    private void printSingleCard(String printerName, String htmlTemplate, SpreadsheetRow row, java.util.Map<String, String> qrMappings, Path templatePath) {
        appendLog("printSingleRow: printer='" + printerName + "', rowStatus='" + row.getStatus() + "', values=" + row.asMap());
        String renderedHtml = htmlTemplateService.renderTemplate(htmlTemplate, row.asMap(), qrMappings);
        String baseUri = templatePath == null ? null : templatePath.getParent().toUri().toString();
        Path tempPdf = createTempPdf(renderedHtml, baseUri);

        try {
            System.out.println("printSingleCard: generated PDF for card -> " + tempPdf.toAbsolutePath());
            if (isVirtualPdfPrinter(printerName)) {
                appendLog("printSingleCard: detected virtual PDF printer, saving PDF instead of printing.");
                Path saved = savePdfToDocuments(tempPdf);
                appendLog("Saved PDF to: " + saved.toAbsolutePath());
                return;
            }
            appendLog("printSingleCard: sending PDF to printer '" + printerName + "'.");
            printDocumentWithRetry(printerName, tempPdf);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to render PDF for printing", exception);
        } finally {
            deleteTempPdf(tempPdf);
        }
    }

    public boolean isVirtualPdfPrinter(String printerName) {
        if (printerName == null) {
            return false;
        }
        String normalized = printerName.toLowerCase();
        return normalized.contains("pdf")
                || normalized.contains("print to pdf")
                || normalized.contains("microsoft print")
                || normalized.contains("pdf printer")
                || normalized.contains("fax");
    }

    private Path savePdfToDocuments(Path pdfPath) throws IOException {
        String userHome = System.getProperty("user.home");
        Path outputDir = Path.of(userHome, "Documents", "Cardify PDF Output");
        Files.createDirectories(outputDir);
        String fileName = "Cardify-" + System.currentTimeMillis() + ".pdf";
        Path target = outputDir.resolve(fileName);
        Files.copy(pdfPath, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    private void printDocumentWithRetry(String printerName, Path pdfPath) throws IOException {
        int maxAttempts = 3;
        long delayMillis = 1200L;
        RuntimeException lastFailure = null;

        appendLog("printDocumentWithRetry: starting retries for printer='" + printerName + "'.");

        // Read page count once. A 2-page PDF means front + back of the same card
        // and requires Sides.TWO_SIDED_LONG_EDGE so the driver applies the correct
        // duplex coordinate transform to page 2 (back side).
        int pdfPageCount = getPdfPageCount(pdfPath);
        appendLog("printDocumentWithRetry: PDF has " + pdfPageCount + " page(s)");

        PrintService printService = findPrintService(printerName)
                .orElseThrow(() -> new IllegalStateException("No available printer found for: " + printerName));
        
        boolean printerSupportsImages = printerSupportsImageFormats(printService);
        boolean printerSupportsPdf = printerSupportsPdfFormat(printService);
        
        appendLog("Printer format support: PDF=" + printerSupportsPdf + ", Images=" + printerSupportsImages);
        System.out.println("PrinterService: printer support PDF=" + printerSupportsPdf + ", Images=" + printerSupportsImages + ", printer=" + printService.getName());

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            appendLog("printDocumentWithRetry: attempt " + attempt + " for printer='" + printerName + "'.");
            System.out.println("PrinterService: print attempt " + attempt + " for printer=" + printService.getName());

            // First, try the page-based PrinterJob path for card output.
            try {
                System.out.println("PrinterService: trying PrinterJob/PDFPageable for printer=" + printService.getName());
                appendLog("Attempting PrinterJob/PDFPageable to: " + printService.getName());
                printPdfThroughPrinterJob(printService, pdfPath);
                appendLog("PrinterJob/PDFPageable completed to: " + printService.getName());
                System.out.println("PrinterService: PrinterJob/PDFPageable completed to: " + printService.getName());
                return;
            } catch (java.awt.print.PrinterException exception) {
                appendLog("PrinterJob/PDFPageable failed for '" + printerName + "' (attempt " + attempt + "): " + exception.getMessage());
                appendStackTrace(exception);
            }

            // Next, try sending raw PDF bytes via the Java Print Service.
            try {
                appendLog("Attempting DocPrintJob to: " + printService.getName());
                logSupportedFlavors(printService);
                PrintRequestAttributeSet attrs = new HashPrintRequestAttributeSet();
                byte[] pdfBytes = Files.readAllBytes(pdfPath);
                appendLog("printDocumentWithRetry: PDF bytes loaded (" + pdfBytes.length + " bytes).");
                System.out.println("PrinterService: trying raw PDF DocPrintJob for printer=" + printService.getName());

                try {
                    DocFlavor flavor = DocFlavor.BYTE_ARRAY.PDF;
                    Doc doc = new SimpleDoc(pdfBytes, flavor, null);
                    DocPrintJob job = printService.createPrintJob();
                    job.print(doc, attrs);
                    appendLog("DocPrintJob (PDF) submitted to: " + printService.getName() + " (job completed successfully)");
                    System.out.println("PrinterService: DocPrintJob PDF submitted to: " + printService.getName());
                    return;
                } catch (PrintException pex) {
                    appendLog("DocPrintJob PDF flavor failed for '" + printerName + "' (attempt " + attempt + "): " + pex.getMessage());
                    appendStackTrace(pex);
                    System.out.println("PrinterService: DocPrintJob PDF failed for printer=" + printService.getName() + ": " + pex.getMessage());
                }

                try {
                    DocFlavor flavor = DocFlavor.BYTE_ARRAY.AUTOSENSE;
                    Doc doc = new SimpleDoc(pdfBytes, flavor, null);
                    DocPrintJob job = printService.createPrintJob();
                    job.print(doc, attrs);
                    appendLog("DocPrintJob (AUTOSENSE) submitted to: " + printService.getName() + " (job completed successfully)");
                    System.out.println("PrinterService: DocPrintJob AUTOSENSE submitted to: " + printService.getName());
                    return;
                } catch (PrintException pex) {
                    appendLog("DocPrintJob AUTOSENSE flavor failed for '" + printerName + "' (attempt " + attempt + "): " + pex.getMessage());
                    appendStackTrace(pex);
                    System.out.println("PrinterService: DocPrintJob AUTOSENSE failed for printer=" + printService.getName() + ": " + pex.getMessage());
                }
            } catch (IOException ioex) {
                appendLog("IOException preparing DocPrintJob for '" + printerName + "': " + ioex.getMessage());
                appendStackTrace(ioex);
                System.out.println("PrinterService: IOException preparing DocPrintJob for printer=" + printService.getName() + ": " + ioex.getMessage());
            } catch (RuntimeException runtimeEx) {
                appendLog("DocPrintJob runtime failure for '" + printerName + "' (attempt " + attempt + "): " + runtimeEx.getMessage());
                appendStackTrace(runtimeEx);
                System.out.println("PrinterService: Runtime failure in DocPrintJob for printer=" + printService.getName() + ": " + runtimeEx.getMessage());
            }

            // Fallback: use PDFBox/PrinterJob with driver-negotiated PageFormat
            try {
                appendLog("Fallback printing using PrinterJob (rasterized) to: " + printService.getName());
                printPdfAsImageBook(printService, pdfPath);
                appendLog("PrinterJob completed to: " + printService.getName());
                return;
            } catch (java.awt.print.PrinterException exception) {
                appendLog("PrinterException while printing to '" + printerName + "' (attempt " + attempt + "): " + exception.getMessage());
                appendStackTrace(exception);
                lastFailure = new IllegalStateException("Unable to print PDF to " + printerName + " (attempt " + attempt + " of " + maxAttempts + ")", exception);
            } catch (RuntimeException exception) {
                appendLog("RuntimeException while printing to '" + printerName + "' (attempt " + attempt + "): " + exception.getMessage());
                appendStackTrace(exception);
                lastFailure = exception;
            }

            if (attempt < maxAttempts) {
                waitBeforeRetry(delayMillis);
            }
        }

        if (printerSupportsImages && !printerSupportsPdf) {
            appendLog("Printer did not accept PDF directly; falling back to image conversion.");
            try {
                printPdfAsImages(printerName, printService, pdfPath);
                return;
            } catch (RuntimeException e) {
                appendLog("Image printing failed: " + e.getMessage());
                appendStackTrace(e);
                lastFailure = e;
            }
        }

        appendLog("Attempting final PDFPrintable fallback for printer='" + printerName + "'.");
        try {
            appendLog("Final fallback printing using PrinterJob (rasterized) to: " + printService.getName());
            printPdfAsImageBook(printService, pdfPath);
            appendLog("PrinterJob completed to: " + printService.getName());
            return;
        } catch (java.awt.print.PrinterException exception) {
            appendLog("PrinterException while printing to '" + printerName + "' in final fallback: " + exception.getMessage());
            appendStackTrace(exception);
            lastFailure = new IllegalStateException("Unable to print PDF to " + printerName + " in final fallback", exception);
        } catch (RuntimeException exception) {
            appendLog("RuntimeException while printing to '" + printerName + "' in final fallback: " + exception.getMessage());
            appendStackTrace(exception);
            lastFailure = exception;
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

    private void appendLog(String message) {
        try {
            String base = System.getenv("APPDATA");
            if (base == null || base.isBlank()) {
                base = System.getProperty("user.home");
            }
            Path dir = Path.of(base, "Cardify", "logs");
            Files.createDirectories(dir);
            Path log = dir.resolve("print.log");
            String time = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
            String entry = time + " - " + message + System.lineSeparator();
            Files.writeString(log, entry, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }

    private void appendStackTrace(Throwable t) {
        try {
            String base = System.getenv("APPDATA");
            if (base == null || base.isBlank()) {
                base = System.getProperty("user.home");
            }
            Path dir = Path.of(base, "Cardify", "logs");
            Files.createDirectories(dir);
            Path log = dir.resolve("print.log");
            String time = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
            StringBuilder sb = new StringBuilder();
            sb.append(time).append(" - ").append(t.toString()).append(System.lineSeparator());
            for (StackTraceElement el : t.getStackTrace()) {
                sb.append("    at ").append(el.toString()).append(System.lineSeparator());
            }
            Files.writeString(log, sb.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }

    /**
     * Renders {@code htmlTemplate} for the given {@code row} and writes the resulting PDF to
     * {@code destPath}. Intended for the "Download PDF" feature in the Settings tab.
     *
     * @param htmlTemplate the raw HTML (with placeholders) to render
     * @param row          spreadsheet row whose values fill the placeholders
     * @param qrMappings   column-to-QR-content mapping (may be empty)
     * @param templatePath path to the template file (used to resolve relative image URLs); may be null
     * @param destPath     where to write the output PDF
     * @throws IOException if the file cannot be written
     */
    public void exportRowAsPdf(String htmlTemplate, SpreadsheetRow row,
                               java.util.Map<String, String> qrMappings,
                               Path templatePath,
                               Path destPath) throws IOException {
        String renderedHtml = htmlTemplateService.renderTemplate(htmlTemplate, row.asMap(), qrMappings);
        String baseUri = templatePath == null ? null : templatePath.getParent().toUri().toString();
        Path tempPdf = createTempPdf(renderedHtml, baseUri);
        try {
            Files.copy(tempPdf, destPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            deleteTempPdf(tempPdf);
        }
    }

    private Path createTempPdf(String html, String baseUri) {
        try {
            Path tempFile = Files.createTempFile("cardify-print-", ".pdf");
            String rendered = addPdfPageStyles(html == null ? "" : html);

            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(rendered, baseUri);
            try (var outputStream = Files.newOutputStream(tempFile)) {
                builder.toStream(outputStream);
                try {
                    builder.run();
                } catch (Exception renderEx) {
                    throw new IllegalStateException("PDF rendering failed", renderEx);
                }
            }

            // Emit debug info to stdout for easier reproduction
            System.out.println("PrinterService: generated PDF -> " + tempFile.toAbsolutePath());

            try {
                long size = Files.size(tempFile);
                if (size <= 0L) {
                    throw new IllegalStateException("Generated PDF is empty (0 bytes)");
                }
                // Verify the file actually starts with the PDF magic bytes (%PDF-)
                byte[] magic = new byte[5];
                try (var in = Files.newInputStream(tempFile)) {
                    int read = in.read(magic);
                    if (read < 5 || magic[0] != '%' || magic[1] != 'P' || magic[2] != 'D' || magic[3] != 'F' || magic[4] != '-') {
                        // Save debug HTML to logs dir for investigation before throwing
                        saveDebugHtml(rendered, tempFile.getFileName().toString());
                        throw new IllegalStateException("Generated file is not a valid PDF (missing %%PDF- header); check Cardify logs for debug HTML");
                    }
                }
            } catch (IOException ioe) {
                throw new IllegalStateException("Unable to verify generated PDF", ioe);
            }

            return tempFile;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create PDF output", exception);
        }
    }

    /**
     * Saves the rendered HTML to the Cardify logs directory for debugging when PDF generation fails.
     * Using the logs directory (not temp) avoids leaving confusingly-named files in %TEMP%.
     */
    private void saveDebugHtml(String html, String sourceName) {
        try {
            String base = System.getenv("APPDATA");
            if (base == null || base.isBlank()) {
                base = System.getProperty("user.home");
            }
            Path dir = Path.of(base, "Cardify", "logs");
            Files.createDirectories(dir);
            Path debugFile = dir.resolve("debug-" + sourceName + ".html");
            Files.writeString(debugFile, html == null ? "" : html, StandardCharsets.UTF_8);
            appendLog("saveDebugHtml: debug HTML written to " + debugFile.toAbsolutePath());
            System.out.println("PrinterService: debug HTML -> " + debugFile.toAbsolutePath());
        } catch (Exception ignored) {
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
            return;
        }

        // Delete the temp PDF file
        try {
            Files.deleteIfExists(tempPdf);
        } catch (IOException ignored) {
            tempPdf.toFile().deleteOnExit();
        }

        // Also clean up any stale .pdf.html debug files with the same base name that may
        // have been left by a previous version of the app. These files confuse users
        // because Windows Explorer hides extensions and shows "name.pdf.html" as "name.pdf".
        Path legacyDebugHtml = Path.of(tempPdf.toString() + ".html");
        try {
            Files.deleteIfExists(legacyDebugHtml);
        } catch (IOException ignored) {
            legacyDebugHtml.toFile().deleteOnExit();
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

    private void logSupportedFlavors(PrintService printService) {
        try {
            DocFlavor[] flavors = (DocFlavor[]) printService.getSupportedDocFlavors();
            if (flavors == null || flavors.length == 0) {
                appendLog("  Printer supports: (no flavors reported)");
                return;
            }
            StringBuilder sb = new StringBuilder("  Printer supports: ");
            for (int i = 0; i < flavors.length && i < 10; i++) {
                if (i > 0) sb.append(", ");
                sb.append(flavors[i].getMimeType());
            }
            if (flavors.length > 10) {
                sb.append(", ... and ").append(flavors.length - 10).append(" more");
            }
            appendLog(sb.toString());
        } catch (Exception e) {
            appendLog("  Unable to query supported flavors: " + e.getMessage());
        }
    }

    private boolean printerSupportsImageFormats(PrintService printService) {
        try {
            DocFlavor[] flavors = (DocFlavor[]) printService.getSupportedDocFlavors();
            if (flavors == null) return false;
            for (DocFlavor flavor : flavors) {
                String mimeType = flavor.getMimeType();
                if (mimeType.startsWith("image/")) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean printerSupportsPdfFormat(PrintService printService) {
        try {
            DocFlavor[] flavors = (DocFlavor[]) printService.getSupportedDocFlavors();
            if (flavors == null) return false;
            for (DocFlavor flavor : flavors) {
                if ("application/pdf".equals(flavor.getMimeType())) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Queries the printer's supported {@link MediaSizeName} catalog and returns the entry
     * whose physical dimensions are closest to the configured card size within a 5 mm tolerance
     * in either orientation.
     *
     * <p>Finding the right {@code MediaSizeName} is essential: on Windows the JRE translates
     * it to the correct {@code DEVMODE.dmPaperSize} value, which is what actually tells the
     * printer driver to target the correct card stock. {@link MediaPrintableArea} alone only
     * describes the printable area <em>within</em> the already-selected paper and does not
     * change which paper the driver uses.
     */
    private Optional<MediaSizeName> findCardMediaSizeName(PrintService printService) {
        try {
            Object supported = printService.getSupportedAttributeValues(MediaSizeName.class, null, null);
            if (!(supported instanceof MediaSizeName[])) {
                appendLog("findCardMediaSizeName: printer returned no MediaSizeName catalog");
                return Optional.empty();
            }
            float tolerance = 5.0f; // mm
            for (MediaSizeName name : (MediaSizeName[]) supported) {
                MediaSize size = MediaSize.getMediaSizeForName(name);
                if (size == null) continue;
                float w = size.getX(MediaPrintableArea.MM);
                float h = size.getY(MediaPrintableArea.MM);
                // Accept match in either portrait or landscape orientation
                boolean portraitMatch  = Math.abs(w - cardWidthMm)  <= tolerance
                                      && Math.abs(h - cardHeightMm) <= tolerance;
                boolean landscapeMatch = Math.abs(w - cardHeightMm) <= tolerance
                                      && Math.abs(h - cardWidthMm)  <= tolerance;
                if (portraitMatch || landscapeMatch) {
                    appendLog("findCardMediaSizeName: matched '" + name + "' (" + w + "×" + h + " mm)");
                    return Optional.of(name);
                }
            }
            appendLog("findCardMediaSizeName: no media name matched configured card dimensions (" + cardWidthMm + "×" + cardHeightMm + " mm)");
        } catch (Exception e) {
            appendLog("findCardMediaSizeName: error querying media catalog: " + e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Builds a {@link PrintRequestAttributeSet} that tells the printer driver to target the
     * configured card size in portrait orientation. Includes:
     * <ul>
     *   <li>The printer's own {@link MediaSizeName} for the card (if discoverable), which causes
     *       the Windows JRE to embed the correct {@code DEVMODE.dmPaperSize} in the print
     *       ticket.
     *   <li>{@link MediaPrintableArea} covering the full card (zero margins) as a fallback
     *       hint when no named media is found.
     *   <li>{@link OrientationRequested#PORTRAIT} so the driver lays the content portrait.
     * </ul>
     */
    private PrintRequestAttributeSet buildCardPrintAttributes(PrintService printService) {
        PrintRequestAttributeSet attrs = new HashPrintRequestAttributeSet();
        // Primary hint: named media size → maps to DEVMODE.dmPaperSize on Windows
        findCardMediaSizeName(printService).ifPresent(attrs::add);
        // Secondary hint: explicit printable area (covers drivers that check this instead)
        attrs.add(new MediaPrintableArea(0, 0, cardWidthMm, cardHeightMm, MediaPrintableArea.MM));
        attrs.add(OrientationRequested.PORTRAIT);
        return attrs;
    }

    /**
     * Uses {@link PrinterJob#getPageFormat(PrintRequestAttributeSet)} to ask the JRE to
     * negotiate a {@link PageFormat} that the current printer driver will actually accept
     * for the given attributes. This is the correct approach on Windows because it lets the
     * JRE translate our JPS attributes (including {@link MediaSizeName}) into a DEVMODE and
     * return the PageFormat the driver will honour — rather than us hardcoding one that the
     * GDI DC may ignore.
     *
     * <p>After negotiation we force:
     * <ul>
     *   <li>Portrait orientation — our PDF is portrait; a landscape DEVMODE would cause
     *       non-uniform stretching in X vs Y, producing incorrect proportions.</li>
     *   <li>Zero-margin imageable area — card printers print edge-to-edge.</li>
     * </ul>
     *
     * <p>If the driver ignores our attributes and returns a much-larger page (e.g. Letter/A4)
     * we fall back to an explicit CR-80-sized {@link Paper} so content is not stretched to
     * fill a full-sized sheet.
     */
    private PageFormat negotiatePageFormat(PrinterJob job, PrintRequestAttributeSet attrs) {
        final double MM_TO_PT  = 72.0 / 25.4;
        final double cardW     = cardWidthMm  * MM_TO_PT;
        final double cardH     = cardHeightMm * MM_TO_PT;
        final double maxCardPt = Math.max(cardW, cardH) * 1.5; // sanity threshold

        PageFormat pf = job.getPageFormat(attrs);
        double pw = pf.getPaper().getWidth();
        double ph = pf.getPaper().getHeight();
        appendLog(String.format("negotiatePageFormat: driver returned %.1f×%.1f mm (%.0f×%.0f pt), orientation=%d",
                pw * 25.4 / 72, ph * 25.4 / 72, pw, ph, pf.getOrientation()));

        // Sanity check: reject if the driver ignored our hint and returned a full-sized sheet
        if (Math.max(pw, ph) > maxCardPt) {
            appendLog("negotiatePageFormat: driver returned non-card paper; using hardcoded CR-80 dimensions");
            Paper paper = new Paper();
            paper.setSize(cardW, cardH);
            paper.setImageableArea(0, 0, cardW, cardH);
            pf = new PageFormat();
            pf.setPaper(paper);
            pw = cardW;
            ph = cardH;
        }

        // If the driver returned landscape CR-80 (wider than tall), swap to portrait so our
        // portrait PDF fills the card correctly without X≠Y scaling distortion.
        if (pw > ph) {
            appendLog("negotiatePageFormat: driver PageFormat is landscape; swapping to portrait");
            Paper paper = pf.getPaper();
            paper.setSize(ph, pw);
            paper.setImageableArea(0, 0, ph, pw);
            pf.setPaper(paper);
        }

        // Force portrait orientation and zero margins (full-bleed card printing)
        pf.setOrientation(PageFormat.PORTRAIT);
        Paper paper = pf.getPaper();
        paper.setImageableArea(0, 0, paper.getWidth(), paper.getHeight());
        pf.setPaper(paper);

        appendLog(String.format("negotiatePageFormat: final PageFormat %.1f×%.1f mm, portrait, zero-margin",
                pf.getPaper().getWidth() * 25.4 / 72, pf.getPaper().getHeight() * 25.4 / 72));
        return pf;
    }

    private void printPdfThroughPrinterJob(PrintService printService, Path pdfPath) throws IOException, java.awt.print.PrinterException {
        printPdfAsImageBook(printService, pdfPath);
    }

    private void printPdfAsImageBook(PrintService printService, Path pdfPath) throws IOException, java.awt.print.PrinterException {
        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setJobName("Cardify - " + printService.getName());
            job.setPrintService(printService);
            int pageCount = document.getNumberOfPages();
            
            // Build attributes and negotiate page format
            PrintRequestAttributeSet cardAttrs = buildCardPrintAttributes(printService, pageCount);
            PageFormat pageFormat = negotiatePageFormat(job, cardAttrs);
            
            // Render PDF pages to BufferedImages for high-fidelity raster printing
            appendLog("printPdfAsImageBook: rendering " + pageCount + " pages to images for raster printing");
            PDFRenderer renderer = new PDFRenderer(document);
            java.util.List<BufferedImage> images = new java.util.ArrayList<>();
            float scale = 4.0f; // 288 DPI for crisp card output
            for (int i = 0; i < pageCount; i++) {
                images.add(renderer.renderImage(i, scale));
            }
            
            Book book = new Book();
            book.append(new java.awt.print.Printable() {
                @Override
                public int print(java.awt.Graphics graphics, PageFormat pf, int pageIndex) {
                    if (pageIndex >= images.size()) {
                        return NO_SUCH_PAGE;
                    }
                    java.awt.Graphics2D g2d = (java.awt.Graphics2D) graphics;
                    g2d.translate(pf.getImageableX(), pf.getImageableY());
                    
                    BufferedImage img = images.get(pageIndex);
                    // Draw the image filling the imageable area perfectly
                    g2d.drawImage(img, 0, 0, (int) pf.getImageableWidth(), (int) pf.getImageableHeight(), null);
                    return PAGE_EXISTS;
                }
            }, pageFormat, pageCount);
            
            job.setPageable(book);
            job.print(cardAttrs);
            appendLog("printPdfAsImageBook completed to: " + printService.getName());
        }
    }

    private void printPdfAsImages(String printerName, PrintService printService, Path pdfPath) throws IOException {
        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();
            appendLog("Converting PDF to images: " + pageCount + " page(s)");

            for (int pageNum = 0; pageNum < pageCount; pageNum++) {
                // Get PDF page dimensions
                org.apache.pdfbox.pdmodel.PDPage page = document.getPage(pageNum);
                org.apache.pdfbox.pdmodel.common.PDRectangle mediaBox = page.getMediaBox();
                float pageWidthPts = mediaBox.getWidth();
                float pageHeightPts = mediaBox.getHeight();
                
                // Render at 4x scale to match card printer output size
                // This produces high-quality images at proper dimensions for card printing
                float scale = 4.0f;
                BufferedImage image = renderer.renderImage(pageNum, scale);
                
                int imgWidth = image.getWidth();
                int imgHeight = image.getHeight();
                appendLog("Rendered page " + (pageNum + 1) + " at " + imgWidth + "x" + imgHeight + " pixels (scale: " + scale + "x, PDF: " + (int)pageWidthPts + "x" + (int)pageHeightPts + "pt)");
                
                Path imagePath = Files.createTempFile("cardify-print-image-", ".png");
                ImageIO.write(image, "png", imagePath.toFile());

                // Submit image to printer
                try (var fis = Files.newInputStream(imagePath)) {
                    DocFlavor flavor = DocFlavor.INPUT_STREAM.PNG;
                    Doc doc = new SimpleDoc(fis, flavor, null);
                    DocPrintJob job = printService.createPrintJob();
                    PrintRequestAttributeSet attrs = buildCardPrintAttributes(printService);
                    job.print(doc, attrs);
                    appendLog("Image page " + (pageNum + 1) + " submitted to: " + printService.getName());
                } catch (PrintException pex) {
                    appendLog("Failed to print image page " + (pageNum + 1) + ": " + pex.getMessage());
                    try {
                        Files.delete(imagePath);
                    } catch (Exception ignored) {}
                    throw new IOException("Unable to print page " + (pageNum + 1) + " as image", pex);
                }

                // Clean up temp image
                try {
                    Files.delete(imagePath);
                } catch (IOException ignored) {}
            }
        }
    }

    /**
     * Overload that adds the correct {@link Sides} attribute based on the number of PDF pages.
     *
     * <p>A 2-page PDF means the template has a front and a back side of the same card.
     * Without {@link Sides#TWO_SIDED_LONG_EDGE}, the Windows driver does not know that
     * page 2 is the back of the same physical card and may apply an incorrect (or no)
     * duplex coordinate transform, causing some fields on the back to appear misplaced
     * even though the PDF is visually correct.
     *
     * <p>Portrait CR-80 duplex flips on the long (85.6 mm) edge — book style — so both
     * sides share the same top edge when the card is held upright.
     */
    private PrintRequestAttributeSet buildCardPrintAttributes(PrintService printService, int pageCount) {
        PrintRequestAttributeSet attrs = buildCardPrintAttributes(printService);
        if (pageCount >= 2) {
            attrs.add(Sides.TWO_SIDED_LONG_EDGE);
            appendLog("buildCardPrintAttributes: " + pageCount + "-page PDF → Sides.TWO_SIDED_LONG_EDGE");
        } else {
            attrs.add(Sides.ONE_SIDED);
            appendLog("buildCardPrintAttributes: single-page PDF → Sides.ONE_SIDED");
        }
        return attrs;
    }

    /** Reads the PDF page count without keeping the document open. Falls back to 1 on error. */
    private int getPdfPageCount(Path pdfPath) {
        try (PDDocument doc = PDDocument.load(pdfPath.toFile())) {
            return doc.getNumberOfPages();
        } catch (Exception e) {
            appendLog("getPdfPageCount: error reading: " + e.getMessage());
            return 1;
        }
    }

    private String addPdfPageStyles(String html) {
        // Use the user-configured card dimensions so the generated PDF page matches the
        // negotiated PageFormat exactly. SCALE_TO_FIT then produces scale=1.0 with zero
        // centering offset, ensuring identical positioning on both sides of the card.
        String cardSize = String.format("%.2fmm %.2fmm", cardWidthMm, cardHeightMm);

        // Injected LAST inside <head> so our @page rule comes after any @page rule already
        // in the template — the last rule in the cascade always wins for @page.
        // html/body are clamped to the exact card dimensions so any overflowing content is
        // clipped rather than generating extra blank space at the top or bottom.
        String printReset = "<style>"
                + "@page { size: " + cardSize + "; margin: 0; } "
                + "html, body { margin: 0 !important; padding: 0 !important; }"
                + "</style>";
        appendLog("addPdfPageStyles: using card size " + cardSize);

        if (html == null || html.isBlank()) {
            return "<html><head>" + printReset + "</head><body></body></html>";
        }

        String lowerHtml = html.toLowerCase();

        // Prefer injecting just before </head> so our rule is the last @page in the cascade
        int headCloseIndex = lowerHtml.lastIndexOf("</head>");
        if (headCloseIndex >= 0) {
            return html.substring(0, headCloseIndex) + printReset + html.substring(headCloseIndex);
        }

        // No </head>: try injecting just after <head>
        int headIndex = lowerHtml.indexOf("<head>");
        if (headIndex >= 0) {
            return html.substring(0, headIndex + 6) + printReset + html.substring(headIndex + 6);
        }

        // No <head> at all: wrap the whole document
        int htmlIndex = lowerHtml.indexOf("<html>");
        if (htmlIndex >= 0) {
            return html.substring(0, htmlIndex + 6) + "<head>" + printReset + "</head>" + html.substring(htmlIndex + 6);
        }

        return "<html><head>" + printReset + "</head><body>" + html + "</body></html>";
    }
}