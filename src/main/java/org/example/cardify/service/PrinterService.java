package org.example.cardify.service;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.web.WebView;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import org.example.cardify.model.SpreadsheetRow;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class PrinterService {
    private final HtmlTemplateService htmlTemplateService = new HtmlTemplateService();

    public List<String> listPrinterNames() {
        return Printer.getAllPrinters().stream().map(Printer::getName).toList();
    }

    public void printRows(String printerName, String htmlTemplate, List<SpreadsheetRow> rows) {
        printRows(printerName, htmlTemplate, rows, java.util.Map.of());
    }

    public void printRows(String printerName, String htmlTemplate, List<SpreadsheetRow> rows, java.util.Map<String, String> qrMappings) {
        Thread printThread = new Thread(() -> {
            for (SpreadsheetRow row : rows) {
                printSingleRow(printerName, htmlTemplate, row, qrMappings);
            }
        }, "cardify-print-worker");
        printThread.setDaemon(true);
        printThread.start();
    }

    private void printSingleRow(String printerName, String htmlTemplate, SpreadsheetRow row, java.util.Map<String, String> qrMappings) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<RuntimeException> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                WebView webView = new WebView();
                webView.setPrefSize(1024, 640);
                webView.setMinSize(1024, 640);
                webView.setMaxSize(1024, 640);

                String renderedHtml = htmlTemplateService.renderTemplate(htmlTemplate, row.asMap(), qrMappings);
                webView.getEngine().getLoadWorker().stateProperty().addListener((observable, oldState, newState) -> {
                    if (newState == Worker.State.SUCCEEDED) {
                        try {
                            Printer printer = Printer.getAllPrinters().stream()
                                    .filter(candidate -> Objects.equals(candidate.getName(), printerName))
                                    .findFirst()
                                    .orElse(Printer.getDefaultPrinter());
                            PrinterJob job = PrinterJob.createPrinterJob(printer);
                            if (job == null) {
                                failure.set(new IllegalStateException("Unable to create printer job for " + printerName));
                                return;
                            }
                            job.getJobSettings().setPageLayout(printer.getDefaultPageLayout());
                            boolean printed = job.printPage(webView);
                            if (printed) {
                                job.endJob();
                            } else {
                                job.cancelJob();
                                failure.set(new IllegalStateException("Printer rejected the page for " + printerName));
                            }
                        } catch (RuntimeException exception) {
                            failure.set(exception);
                        } finally {
                            latch.countDown();
                        }
                    } else if (newState == Worker.State.FAILED || newState == Worker.State.CANCELLED) {
                        failure.set(new IllegalStateException("HTML rendering failed before printing"));
                        latch.countDown();
                    }
                });

                webView.getEngine().loadContent(renderedHtml, "text/html");
            } catch (RuntimeException exception) {
                failure.set(exception);
                latch.countDown();
            }
        });

        awaitLatch(latch);
        RuntimeException runtimeException = failure.get();
        if (runtimeException != null) {
            throw runtimeException;
        }
    }

    private void awaitLatch(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Printing was interrupted", exception);
        }
    }
}
