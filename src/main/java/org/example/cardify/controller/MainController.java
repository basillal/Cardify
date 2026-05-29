package org.example.cardify.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.example.cardify.model.SpreadsheetRow;
import org.example.cardify.service.ExcelImportService;
import org.example.cardify.service.ExcelTemplateService;
import org.example.cardify.service.HtmlTemplateService;
import org.example.cardify.service.PrinterService;
import org.example.cardify.util.UiDialog;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainController {
    private final Stage stage;
    private final BorderPane root = new BorderPane();

    private final HtmlTemplateService htmlTemplateService = new HtmlTemplateService();
    private final ExcelTemplateService excelTemplateService = new ExcelTemplateService();
    private final ExcelImportService excelImportService = new ExcelImportService();
    private final PrinterService printerService = new PrinterService();

    private final ObservableList<SpreadsheetRow> allRows = FXCollections.observableArrayList();
    private final FilteredList<SpreadsheetRow> filteredRows = new FilteredList<>(allRows, row -> true);

    private final TableView<SpreadsheetRow> tableView = new TableView<>();
    private final TextArea placeholdersArea = new TextArea();
    private final Label templateStatus = new Label("No HTML template loaded");
    private final Label excelStatus = new Label("No Excel file loaded");
    private final Label rowCountLabel = new Label("0 rows");
    private final ChoiceBox<String> printerChoice = new ChoiceBox<>();
    private final TextField searchField = new TextField();

    private File htmlTemplateFile;
    private List<String> currentHeaders = List.of();

    public MainController(Stage stage) {
        this.stage = stage;
    }

    public Parent getRoot() {
        return root;
    }

    public void initialize() {
        buildLayout();
        refreshPrinters();
        installSearch();
    }

    private void buildLayout() {
        root.setTop(buildHeader());
        root.setCenter(buildContent());
        root.setBottom(buildFooter());
        root.getStyleClass().add("app-root");
    }

    private Node buildHeader() {
        Label title = new Label("Cardify Desktop Studio");
        title.getStyleClass().add("app-title");

        Label subtitle = new Label("Upload an HTML card template, generate an Excel data sheet, import filled rows, then print selected ID cards to a connected printer.");
        subtitle.getStyleClass().add("app-subtitle");
        subtitle.setWrapText(true);

        VBox textBlock = new VBox(6, title, subtitle);
        textBlock.getStyleClass().add("header-block");

        HBox header = new HBox(textBlock);
        header.getStyleClass().add("app-header");
        header.setPadding(new Insets(28));
        return header;
    }

    private Node buildContent() {
        VBox templateSection = new VBox(14,
                createSectionHeader("1. Template to Excel", "Upload an HTML template containing placeholders like {{name}} or {{photo}}."),
                buildTemplateControls(),
                buildTemplatePreview()
        );
        templateSection.getStyleClass().add("content-card");

        VBox dataSection = new VBox(14,
                createSectionHeader("2. Data Import and Print", "Load the filled Excel sheet, select rows, then print to the active printer."),
                buildDataControls(),
                buildTablePanel()
        );
        dataSection.getStyleClass().add("content-card");

        VBox sections = new VBox(18, templateSection, dataSection);
        sections.setPadding(new Insets(24));
        sections.setFillWidth(true);
        ScrollPane scrollPane = new ScrollPane(sections);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.getStyleClass().add("content-scroll");
        return scrollPane;
    }

    private Node buildFooter() {
        HBox footer = new HBox(12);
        footer.setPadding(new Insets(12, 24, 18, 24));
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.getStyleClass().add("app-footer");

        Label printerLabel = new Label("Printer");
        printerLabel.getStyleClass().add("footer-label");

        printerChoice.getStyleClass().add("printer-choice");
        printerChoice.setPrefWidth(320);

        Button refreshPrintersButton = new Button("Refresh Printers");
        refreshPrintersButton.setOnAction(event -> refreshPrinters());

        Label help = new Label("Image fields should point to local image files; they will be converted to printable HTML data URLs during rendering.");
        help.getStyleClass().add("footer-help");
        help.setWrapText(true);
        HBox.setHgrow(help, Priority.ALWAYS);

        footer.getChildren().addAll(printerLabel, printerChoice, refreshPrintersButton, help);
        return footer;
    }

    private Node buildTemplateControls() {
        Button uploadButton = new Button("Upload HTML Template");
        uploadButton.setOnAction(event -> uploadTemplate());

        Button generateButton = new Button("Download Excel Template");
        generateButton.setOnAction(event -> downloadExcelTemplate());

        templateStatus.getStyleClass().add("status-label");
        VBox statusBlock = new VBox(6, new Label("Template status"), templateStatus);

        HBox controls = new HBox(12, uploadButton, generateButton, statusBlock);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.setPadding(new Insets(4, 0, 0, 0));
        return controls;
    }

    private Node buildTemplatePreview() {
        placeholdersArea.setEditable(false);
        placeholdersArea.setPrefRowCount(7);
        placeholdersArea.setPromptText("Detected placeholders will appear here");

        Label label = new Label("Detected placeholders");
        VBox box = new VBox(8, label, placeholdersArea);
        VBox.setVgrow(placeholdersArea, Priority.ALWAYS);
        return box;
    }

    private Node buildDataControls() {
        Button uploadExcelButton = new Button("Upload Filled Excel");
        uploadExcelButton.setOnAction(event -> uploadExcel());

        Button printButton = new Button("Print Selected Rows");
        printButton.setOnAction(event -> printSelectedRows());

        Button selectAllButton = new Button("Select All");
        selectAllButton.setOnAction(event -> tableView.getSelectionModel().selectAll());

        Button clearSelectionButton = new Button("Clear Selection");
        clearSelectionButton.setOnAction(event -> tableView.getSelectionModel().clearSelection());

        searchField.setPromptText("Filter rows by any column value");
        searchField.setPrefWidth(320);

        VBox statusBlock = new VBox(6, new Label("Import status"), excelStatus, rowCountLabel);
        HBox controls = new HBox(12, uploadExcelButton, printButton, selectAllButton, clearSelectionButton, searchField, statusBlock);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.setPadding(new Insets(4, 0, 0, 0));
        HBox.setHgrow(searchField, Priority.ALWAYS);
        return controls;
    }

    private Node buildTablePanel() {
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tableView.setPlaceholder(new Label("Import an Excel file to see rows here"));
        tableView.getStyleClass().add("data-table");
        VBox.setVgrow(tableView, Priority.ALWAYS);

        Label hint = new Label("Click one or more rows, then print. Each selected row becomes one ID card output.");
        hint.getStyleClass().add("table-hint");
        return new VBox(8, hint, tableView);
    }

    private void installSearch() {
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            String query = newValue == null ? "" : newValue.trim().toLowerCase();
            filteredRows.setPredicate(row -> {
                if (query.isBlank()) {
                    return true;
                }
                for (String header : currentHeaders) {
                    String value = row.getValue(header);
                    if (value != null && value.toLowerCase().contains(query)) {
                        return true;
                    }
                }
                return false;
            });
            rowCountLabel.setText(filteredRows.size() + " rows");
        });
    }

    public void refreshPrinters() {
        List<String> printers = printerService.listPrinterNames();
        printerChoice.getItems().setAll(printers);
        if (!printers.isEmpty() && printerChoice.getValue() == null) {
            printerChoice.setValue(printers.get(0));
        }
        if (printers.isEmpty()) {
            printerChoice.setValue(null);
        }
    }

    private void uploadTemplate() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select HTML Template");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("HTML Files", "*.html", "*.htm"));
        File selected = chooser.showOpenDialog(stage);
        if (selected == null) {
            return;
        }

        htmlTemplateFile = selected;
        Set<String> placeholders = htmlTemplateService.extractPlaceholders(selected.toPath());
        placeholdersArea.setText(String.join(System.lineSeparator(), placeholders));
        templateStatus.setText(selected.getName() + " loaded with " + placeholders.size() + " placeholder(s)");
    }

    private void downloadExcelTemplate() {
        if (htmlTemplateFile == null) {
            UiDialog.warn(stage, "Upload a template first", "Select an HTML file before generating the Excel template.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Excel Template");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Workbook", "*.xlsx"));
        String baseName = htmlTemplateFile.getName().replaceAll("\\.html?$", "");
        chooser.setInitialFileName(baseName + "-data-template.xlsx");
        File saveFile = chooser.showSaveDialog(stage);
        if (saveFile == null) {
            return;
        }

        List<String> placeholders = htmlTemplateService.extractPlaceholders(htmlTemplateFile.toPath()).stream().toList();
        excelTemplateService.writeTemplate(saveFile.toPath(), placeholders);
        UiDialog.info(stage, "Excel template created", "Saved to:\n" + saveFile.getAbsolutePath());
    }

    private void uploadExcel() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Filled Excel File");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Workbook", "*.xlsx"));
        File selected = chooser.showOpenDialog(stage);
        if (selected == null) {
            return;
        }

        List<SpreadsheetRow> rows = excelImportService.readRows(selected.toPath());
        if (rows.isEmpty()) {
            UiDialog.warn(stage, "No data found", "The selected workbook does not contain any data rows.");
            return;
        }

        allRows.setAll(rows);
        currentHeaders = new ArrayList<>(rows.get(0).headers());
        rebuildColumns();
        filteredRows.setPredicate(row -> true);
        tableView.setItems(filteredRows);
        rowCountLabel.setText(filteredRows.size() + " rows");
        excelStatus.setText(selected.getName() + " loaded with " + rows.size() + " row(s)");
    }

    private void rebuildColumns() {
        tableView.getColumns().clear();
        for (String header : currentHeaders) {
            TableColumn<SpreadsheetRow, String> column = new TableColumn<>(header);
            column.setCellValueFactory(cellData -> cellData.getValue().valueProperty(header));
            column.setMinWidth(140);
            tableView.getColumns().add(column);
        }
    }

    private void printSelectedRows() {
        if (htmlTemplateFile == null) {
            UiDialog.warn(stage, "Missing template", "Upload an HTML template before printing.");
            return;
        }
        if (tableView.getSelectionModel().getSelectedItems().isEmpty()) {
            UiDialog.warn(stage, "Nothing selected", "Select one or more rows to print.");
            return;
        }

        String printerName = printerChoice.getValue();
        if (printerName == null || printerName.isBlank()) {
            UiDialog.warn(stage, "No printer selected", "Choose a connected printer from the footer bar.");
            return;
        }

        List<SpreadsheetRow> selectedRows = new ArrayList<>(tableView.getSelectionModel().getSelectedItems());
        String htmlTemplate = htmlTemplateService.readTemplate(htmlTemplateFile.toPath());
        printerService.printRows(printerName, htmlTemplate, selectedRows);
        UiDialog.info(stage, "Print job started", "Sent " + selectedRows.size() + " row(s) to " + printerName + ".");
    }

    private VBox createSectionHeader(String title, String description) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("section-title");

        Label descriptionLabel = new Label(description);
        descriptionLabel.getStyleClass().add("section-description");
        descriptionLabel.setWrapText(true);

        return new VBox(4, titleLabel, descriptionLabel);
    }
}
