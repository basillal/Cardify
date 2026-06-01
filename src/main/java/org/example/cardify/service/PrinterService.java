package org.example.cardify.service;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.web.WebView;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import org.example.cardify.model.SpreadsheetRow;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
                Platform.runLater(() -> row.setStatus("Printing"));
                try {
                    printSingleRow(printerName, htmlTemplate, row, qrMappings);
                    Platform.runLater(() -> row.setStatus("Printed"));
                } catch (RuntimeException ex) {
                    Platform.runLater(() -> row.setStatus("Error"));
                }
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
                // Attach the WebView to a scene so CSS/layout are fully realized before printing.
                Scene offscreenScene = new Scene(new Group(webView), 1024, 640);
                offscreenScene.getRoot().applyCss();
                offscreenScene.getRoot().layout();

                String renderedHtml = htmlTemplateService.renderTemplate(htmlTemplate, row.asMap(), qrMappings);
                String printableHtml = addPrintResetStyles(renderedHtml);
                webView.getEngine().getLoadWorker().stateProperty().addListener((observable, oldState, newState) -> {
                    if (newState == Worker.State.SUCCEEDED) {
                        // Defer printing by one JavaFX pulse so WebView layout/images are fully ready.
                        Platform.runLater(() -> {
                            try {
                                Printer selectedPrinter = Printer.getAllPrinters().stream()
                                        .filter(candidate -> Objects.equals(candidate.getName(), printerName))
                                        .findFirst()
                                        .orElse(Printer.getDefaultPrinter());
                                if (selectedPrinter == null) {
                                    failure.set(new IllegalStateException("No available printer found for: " + printerName));
                                    return;
                                }

                                PrinterJob job = PrinterJob.createPrinterJob();
                                if (job == null) {
                                    failure.set(new IllegalStateException("Unable to create printer job for " + printerName));
                                    return;
                                }
                                job.setPrinter(selectedPrinter);

                                PageLayout pageLayout = selectedPrinter.createPageLayout(
                                        selectedPrinter.getDefaultPageLayout().getPaper(),
                                        PageOrientation.LANDSCAPE,
                                        0,
                                        0,
                                        0,
                                        0);
                                job.getJobSettings().setPageLayout(pageLayout);
                                webView.applyCss();
                                webView.layout();

                                webView.getEngine().print(job);
                                boolean jobEnded = job.endJob();
                                if (!jobEnded) {
                                    failure.set(new IllegalStateException("Printer rejected the page for " + selectedPrinter.getName()));
                                }
                            } catch (RuntimeException exception) {
                                failure.set(exception);
                            } finally {
                                latch.countDown();
                            }
                        });
                    } else if (newState == Worker.State.FAILED || newState == Worker.State.CANCELLED) {
                        failure.set(new IllegalStateException("HTML rendering failed before printing"));
                        latch.countDown();
                    }
                });

                webView.getEngine().loadContent(printableHtml, "text/html");
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
            boolean finished = latch.await(40, TimeUnit.SECONDS);
            if (!finished) {
                throw new IllegalStateException("Printing timed out while waiting for WebView render/print completion");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Printing was interrupted", exception);
        }
    }

    private String addPrintResetStyles(String html) {
        String printReset = "<style>@page { margin: 0; } html, body { margin: 0; padding: 0; } </style>";
        if (html == null || html.isBlank()) {
            return printReset;
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
