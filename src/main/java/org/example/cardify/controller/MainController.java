package org.example.cardify.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.ChoiceBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.example.cardify.model.SpreadsheetRow;
import org.example.cardify.service.ExcelExportService;
import org.example.cardify.service.ExcelImportService;
import org.example.cardify.service.AppPreferencesService;
import org.example.cardify.service.ExcelTemplateService;
import org.example.cardify.service.HtmlTemplateService;
import org.example.cardify.service.PrinterService;
import org.example.cardify.util.UiDialog;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
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
    private final Label selectedCountLabel = new Label("Selected: 0");
    private final CheckBox selectAllCheckBox = new CheckBox();
    private final ChoiceBox<String> printerChoice = new ChoiceBox<>();
    private final TextField searchField = new TextField();
    private final ToggleButton themeToggle = new ToggleButton("Light");
    private final Button removeTemplateButton = new Button("Remove Template");
    private final Button clearDataButton = new Button("Clear Imported Data");
    private final Button rowPreviewButton = new Button("Row Preview");
    private final Button exportExcelButton = new Button("Download Excel");
    private final Button editButton = new Button("Edit Rows");
    private final VBox qrMappingBox = new VBox(8);
    private final ObservableList<String> templatePlaceholders = FXCollections.observableArrayList();
    private final Map<String, ComboBox<String>> qrMappingSelectors = new LinkedHashMap<>();

    private boolean syncingSelection = false;
    private boolean editingEnabled = false;
    private boolean headerCheckboxIndeterminateClick = false;
    private final ExcelExportService excelExportService = new ExcelExportService();
    private final AppPreferencesService preferences = new AppPreferencesService();

    private Stage previewStage;
    private WebView previewWebView;

    private File htmlTemplateFile;
    private File excelFile;
    private List<String> currentHeaders = List.of();

    public MainController(Stage stage) {
        this.stage = stage;
    }

    public Parent getRoot() {
        return root;
    }

    public void initialize() {
        buildLayout();
        applyTheme(true);
        refreshPrinters();
        installSearch();
        restoreSavedState();
    }

    private void buildLayout() {
        root.setTop(buildHeader());
        root.setCenter(buildContent());
        root.setBottom(buildFooter());
        root.getStyleClass().add("app-root");
    }

    private void applyTheme(boolean light) {
        themeToggle.setSelected(light);
        themeToggle.setText(light ? "Dark" : "Light");

        root.getStyleClass().removeAll("dark-theme", "light-theme");
        root.getStyleClass().add(light ? "light-theme" : "dark-theme");
    }

    private Node buildHeader() {
        Label title = new Label("Cardify Desktop Studio");
        title.getStyleClass().add("app-title");

        Label subtitle = new Label("Streamline your ID card workflow — from data import to print-ready output.");        subtitle.getStyleClass().add("app-subtitle");
        subtitle.setWrapText(true);

        VBox textBlock = new VBox(6, title, subtitle);
        textBlock.getStyleClass().add("header-block");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        themeToggle.setOnAction(event -> applyTheme(themeToggle.isSelected()));
        themeToggle.getStyleClass().add("theme-toggle");

        HBox header = new HBox(textBlock, spacer, themeToggle);
        header.getStyleClass().add("app-header");
        header.setPadding(new Insets(24, 28, 24, 28));
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private Node buildContent() {
        VBox dataSection = new VBox(14,
                createSectionHeader("Data Table and Print", "Most users will print from the loaded Excel rows. Select rows and print from here."),
                buildDataControls(),
                buildTablePanel()
        );
        dataSection.getStyleClass().add("content-card");
        dataSection.setFillWidth(true);

        VBox setupSection = new VBox(14,
                createSectionHeader("Template and Excel Setup", "Upload your HTML template once, then generate or load the Excel sheet for repeated printing."),
                buildTemplateControls(),
                buildTemplatePreview()
        );
        setupSection.getStyleClass().add("content-card");
        setupSection.setFillWidth(true);

        Tab dataTab = new Tab("Data");
        dataTab.setContent(dataSection);
        dataTab.setClosable(false);

        Tab setupTab = new Tab("Template & Excel");
        setupTab.setContent(setupSection);
        setupTab.setClosable(false);

        TabPane tabPane = new TabPane(dataTab, setupTab);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getStyleClass().add("content-tabs");
        tabPane.tabMinWidthProperty().bind(tabPane.widthProperty().divide(2).subtract(8));
        tabPane.tabMaxWidthProperty().bind(tabPane.widthProperty().divide(2).subtract(8));

        VBox wrapper = new VBox(tabPane);
        wrapper.setPadding(new Insets(24));
        wrapper.setFillWidth(true);
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        return wrapper;
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

        Button templatePreviewButton = new Button("Template Preview");
        templatePreviewButton.setOnAction(event -> showTemplatePreview());

        removeTemplateButton.setOnAction(event -> clearTemplate());
        removeTemplateButton.getStyleClass().add("negative-button");
        removeTemplateButton.setDisable(true);

        templateStatus.getStyleClass().add("status-label");
        VBox statusBlock = new VBox(6, new Label("Template status"), templateStatus);

        HBox controls = new HBox(12, uploadButton, generateButton, templatePreviewButton, removeTemplateButton, statusBlock);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.setPadding(new Insets(4, 0, 0, 0));
        controls.getStyleClass().add("control-row");
        return controls;
    }

    private Node buildTemplatePreview() {
        placeholdersArea.setEditable(false);
        placeholdersArea.setPrefRowCount(7);
        placeholdersArea.setPromptText("Detected placeholders will appear here");
        qrMappingBox.getStyleClass().add("qr-mapping-box");
        qrMappingBox.getChildren().setAll(new Label("No QR placeholders detected yet."));

        Label label = new Label("Detected placeholders");
        VBox box = new VBox(8, label, qrMappingBox, placeholdersArea);
        VBox.setVgrow(placeholdersArea, Priority.ALWAYS);
        return box;
    }

    private Node buildDataControls() {
        Button uploadExcelButton = new Button("Upload Filled Excel");
        uploadExcelButton.setOnAction(event -> uploadExcel());

        rowPreviewButton.setOnAction(event -> showRowPreview());
        rowPreviewButton.setDisable(true);

        Button printButton = new Button("Print Selected Rows");
        printButton.getStyleClass().add("accent-button");
        printButton.setOnAction(event -> printSelectedRows());

        exportExcelButton.setOnAction(event -> exportCurrentRows());
        editButton.setOnAction(event -> toggleEditMode());

        clearDataButton.setOnAction(event -> clearImportedData());
        clearDataButton.getStyleClass().add("negative-button");
        clearDataButton.setDisable(true);

        searchField.setPromptText("Filter rows by any column value");
        searchField.setPrefWidth(320);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox controls = new HBox(12, searchField, uploadExcelButton, rowPreviewButton, exportExcelButton, editButton, clearDataButton, spacer, printButton);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.setPadding(new Insets(4, 0, 0, 0));
        controls.getStyleClass().add("control-row");
        return controls;
    }

    private Node buildTablePanel() {
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tableView.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        tableView.setMinWidth(0);
        tableView.setPlaceholder(new Label("Import an Excel file to see rows here"));
        tableView.getStyleClass().add("data-table");
        tableView.setEditable(true);

        tableView.getSelectionModel().getSelectedItems().addListener((ListChangeListener<SpreadsheetRow>) c -> {
            if (syncingSelection) {
                return;
            }
            syncingSelection = true;
            while (c.next()) {
                for (SpreadsheetRow removed : c.getRemoved()) {
                    // If a row is removed from the table selection and it's visible,
                    // ensure the model's selected flag is cleared so header checkbox updates correctly.
                    if (filteredRows.contains(removed)) {
                        removed.selectedProperty().set(false);
                    }
                }
                for (SpreadsheetRow added : c.getAddedSubList()) {
                    if (!added.isSelected()) {
                        added.selectedProperty().set(true);
                    }
                }
            }
            updateSelectedCount();
            syncingSelection = false;
        });

        VBox.setVgrow(tableView, Priority.ALWAYS);

        HBox statusBar = new HBox(18, excelStatus, rowCountLabel, selectedCountLabel);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        selectedCountLabel.getStyleClass().add("status-label");
        rowCountLabel.getStyleClass().add("status-label");
        excelStatus.getStyleClass().add("status-label");

        Label hint = new Label("Click one or more rows, then print. Each selected row becomes one ID card output.");
        hint.getStyleClass().add("table-hint");
        return new VBox(10, statusBar, hint, tableView);
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
            syncFilteredSelection();
            rowCountLabel.setText(filteredRows.size() + " rows");
            updateSelectedCount();
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

    private void restoreSavedState() {
        String savedTemplate = preferences.getSavedTemplatePath();
        if (savedTemplate != null && !savedTemplate.isBlank()) {
            loadTemplateFromPath(Path.of(savedTemplate));
        }
        String savedExcel = preferences.getSavedExcelPath();
        if (savedExcel != null && !savedExcel.isBlank()) {
            loadExcelFromPath(Path.of(savedExcel));
        }
    }

    private void loadTemplateFromPath(Path path) {
        if (Files.exists(path) && Files.isRegularFile(path)) {
            htmlTemplateFile = path.toFile();
            Set<String> placeholders = htmlTemplateService.extractPlaceholders(path);
            templatePlaceholders.setAll(placeholders);
            placeholdersArea.setText(String.join(System.lineSeparator(), placeholders));
            refreshQrMappingControls(placeholders);
            templateStatus.setText(path.getFileName() + " loaded with " + placeholders.size() + " placeholder(s)");
            preferences.saveTemplatePath(path.toAbsolutePath().toString());
            removeTemplateButton.setDisable(false);
        } else {
            htmlTemplateFile = null;
            templatePlaceholders.clear();
            qrMappingSelectors.clear();
            qrMappingBox.getChildren().setAll(new Label("No HTML template loaded"));
            preferences.clearSavedTemplatePath();
        }
    }

    private void loadExcelFromPath(Path path) {
        if (Files.exists(path) && Files.isRegularFile(path)) {
            List<SpreadsheetRow> rows = excelImportService.readRows(path);
            if (!rows.isEmpty()) {
                excelFile = path.toFile();
                rows.forEach(row -> bindRowSelection(row));
                allRows.setAll(rows);
                currentHeaders = new ArrayList<>(rows.get(0).headers());
                rebuildColumns();
                filteredRows.setPredicate(row -> true);
                tableView.setItems(filteredRows);
                rowCountLabel.setText(filteredRows.size() + " rows");
                selectedCountLabel.setText("Selected: " + tableView.getSelectionModel().getSelectedItems().size());
                excelStatus.setText(path.getFileName() + " loaded with " + rows.size() + " row(s)");
                preferences.saveExcelPath(path.toAbsolutePath().toString());
                clearDataButton.setDisable(false);
            }
        } else {
            preferences.clearSavedExcelPath();
        }
    }

    private void bindRowSelection(SpreadsheetRow row) {
        row.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (syncingSelection) {
                return;
            }
            syncingSelection = true;
            if (newValue && !tableView.getSelectionModel().getSelectedItems().contains(row)) {
                tableView.getSelectionModel().select(row);
            } else if (!newValue && tableView.getSelectionModel().getSelectedItems().contains(row)) {
                int index = tableView.getItems().indexOf(row);
                if (index >= 0) {
                    tableView.getSelectionModel().clearSelection(index);
                }
            }
            updateSelectedCount();
            syncingSelection = false;
        });
    }

    private void updateSelectedCount() {
        int selectedCount = (int) allRows.stream().filter(SpreadsheetRow::isSelected).count();
        selectedCountLabel.setText("Selected: " + selectedCount);
        rowPreviewButton.setDisable(selectedCount != 1);
        boolean allSelected = !filteredRows.isEmpty() && filteredRows.stream().allMatch(SpreadsheetRow::isSelected);
        boolean noneSelected = filteredRows.stream().noneMatch(SpreadsheetRow::isSelected);
        selectAllCheckBox.setIndeterminate(!allSelected && !noneSelected);
        selectAllCheckBox.setSelected(allSelected);
    }

    private void toggleEditMode() {
        editingEnabled = !editingEnabled;
        editButton.setText(editingEnabled ? "Edit Mode" : "Edit Rows");
        if (editingEnabled) {
            if (!editButton.getStyleClass().contains("edit-mode")) {
                editButton.getStyleClass().add("edit-mode");
            }
        } else {
            editButton.getStyleClass().remove("edit-mode");
        }
        for (int i = 1; i < tableView.getColumns().size(); i++) {
            tableView.getColumns().get(i).setEditable(editingEnabled);
        }
    }

    private void syncFilteredSelection() {
        syncingSelection = true;
        for (SpreadsheetRow row : filteredRows) {
            if (row.isSelected() && !tableView.getSelectionModel().getSelectedItems().contains(row)) {
                tableView.getSelectionModel().select(row);
            }
        }
        syncingSelection = false;
    }

    private void uploadTemplate() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select HTML Template");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("HTML Files", "*.html", "*.htm"));
        File selected = chooser.showOpenDialog(stage);
        if (selected == null) {
            return;
        }
        loadTemplateFromPath(selected.toPath());
    }

    private void clearTemplate() {
        htmlTemplateFile = null;
        placeholdersArea.clear();
        templatePlaceholders.clear();
        qrMappingSelectors.clear();
        qrMappingBox.getChildren().setAll(new Label("No HTML template loaded"));
        templateStatus.setText("No HTML template loaded");
        preferences.clearSavedTemplatePath();
        removeTemplateButton.setDisable(true);
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

    private void showTemplatePreview() {
        if (htmlTemplateFile == null) {
            UiDialog.warn(stage, "No template loaded", "Upload an HTML template before opening the preview.");
            return;
        }

        String htmlTemplate = htmlTemplateService.readTemplate(htmlTemplateFile.toPath());
        Set<String> placeholders = htmlTemplateService.findPlaceholders(htmlTemplate);
        java.util.LinkedHashMap<String, String> sampleValues = new java.util.LinkedHashMap<>();
        for (String placeholder : placeholders) {
            sampleValues.put(placeholder, "Sample " + placeholder);
        }

        openPreviewWindow("Template Preview", htmlTemplateService.renderTemplate(htmlTemplate, sampleValues, getQrMappings()));
    }

    private void showRowPreview() {
        if (htmlTemplateFile == null) {
            UiDialog.warn(stage, "No template loaded", "Upload an HTML template before opening the preview.");
            return;
        }

        if (tableView.getSelectionModel().getSelectedItems().isEmpty()) {
            UiDialog.warn(stage, "No row selected", "Select a row in the table before opening the row preview.");
            return;
        }

        SpreadsheetRow previewRow = tableView.getSelectionModel().getSelectedItems().get(0);
        String htmlTemplate = htmlTemplateService.readTemplate(htmlTemplateFile.toPath());
        openPreviewWindow("Row Preview", htmlTemplateService.renderTemplate(htmlTemplate, previewRow.asMap(), getQrMappings()));
    }

    private void openPreviewWindow(String title, String renderedHtml) {

        if (previewWebView == null) {
            previewWebView = new WebView();
        }
        previewWebView.getEngine().loadContent(renderedHtml, "text/html");

        if (previewStage == null) {
            BorderPane previewRoot = new BorderPane(previewWebView);
            previewRoot.setPadding(new Insets(12));
            previewRoot.getStyleClass().add("preview-root");

            previewStage = new Stage();
            previewStage.initOwner(stage);
            previewStage.setScene(new Scene(previewRoot, 980, 760));
        }

        previewStage.setTitle(title);
        previewStage.show();
        previewStage.toFront();
    }

    private void uploadExcel() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Filled Excel File");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Workbook", "*.xlsx"));
        File selected = chooser.showOpenDialog(stage);
        if (selected == null) {
            return;
        }
        loadExcelFromPath(selected.toPath());
    }

    private void clearImportedData() {
        if (!UiDialog.confirm(stage,
                "Clear Imported Data",
                "Are you sure you want to remove all imported rows? This action cannot be undone.")) {
            return;
        }

        if (editingEnabled) {
            toggleEditMode();
        }

        excelFile = null;
        allRows.clear();
        currentHeaders = List.of();
        tableView.getColumns().clear();
        tableView.setItems(filteredRows);
        filteredRows.setPredicate(row -> true);
        selectAllCheckBox.setSelected(false);
        selectAllCheckBox.setIndeterminate(false);
        rowCountLabel.setText("0 rows");
        selectedCountLabel.setText("Selected: 0");
        excelStatus.setText("No Excel file loaded");
        preferences.clearSavedExcelPath();
        clearDataButton.setDisable(true);
        rowPreviewButton.setDisable(true);
    }

    private void exportCurrentRows() {
        if (filteredRows.isEmpty()) {
            UiDialog.warn(stage, "Nothing to export", "Load data before exporting to Excel.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Excel Export");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Workbook", "*.xlsx"));
        chooser.setInitialFileName("cardify-export.xlsx");
        File saveFile = chooser.showSaveDialog(stage);
        if (saveFile == null) {
            return;
        }

        List<String> headers = new ArrayList<>(currentHeaders);
        if (!headers.contains("Status")) {
            headers.add(0, "Status");
        }
        excelExportService.writeRows(saveFile.toPath(), headers, new ArrayList<>(filteredRows));
        UiDialog.info(stage, "Export saved", "Saved to:\n" + saveFile.getAbsolutePath());
    }

    private void rebuildColumns() {
        tableView.getColumns().clear();

        TableColumn<SpreadsheetRow, Boolean> selectColumn = new TableColumn<>();
        selectColumn.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
        selectColumn.setCellFactory(CheckBoxTableCell.forTableColumn(selectColumn));
        selectColumn.setEditable(true);
        selectColumn.setPrefWidth(50);
        selectColumn.setText("");
        selectColumn.setStyle("-fx-alignment: CENTER;");
        selectColumn.setSortable(false);
        selectColumn.setReorderable(false);
        selectAllCheckBox.setAllowIndeterminate(true);
        // Toggle visible rows based on their current model state instead of relying on checkbox state.
        selectAllCheckBox.addEventFilter(MouseEvent.MOUSE_PRESSED, evt -> {
            headerCheckboxIndeterminateClick = selectAllCheckBox.isIndeterminate();
        });
        selectAllCheckBox.setOnAction(evt -> {
            boolean currentlyAllSelected = !filteredRows.isEmpty() && filteredRows.stream().allMatch(SpreadsheetRow::isSelected);
            boolean select = !currentlyAllSelected;
            for (SpreadsheetRow row : filteredRows) {
                row.selectedProperty().set(select);
            }
            updateSelectedCount();
            headerCheckboxIndeterminateClick = false;
        });
        selectColumn.setGraphic(selectAllCheckBox);
        tableView.getColumns().add(selectColumn);

        TableColumn<SpreadsheetRow, String> statusColumn = new TableColumn<>("Status");
        statusColumn.setCellValueFactory(cellData -> cellData.getValue().statusProperty());
        statusColumn.setCellFactory(ChoiceBoxTableCell.forTableColumn("Pending", "Printed", "Error"));
        statusColumn.setPrefWidth(120);
        statusColumn.setEditable(editingEnabled);
        tableView.getColumns().add(statusColumn);

        for (String header : currentHeaders) {
            TableColumn<SpreadsheetRow, String> column = new TableColumn<>(header);
            column.setCellValueFactory(cellData -> cellData.getValue().valueProperty(header));
            column.setCellFactory(TextFieldTableCell.forTableColumn());
            column.setEditable(editingEnabled);
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
        printerService.printRows(printerName, htmlTemplate, selectedRows, getQrMappings());
        selectedRows.forEach(row -> row.setStatus("Printed"));
        UiDialog.info(stage, "Print job started", "Sent " + selectedRows.size() + " row(s) to " + printerName + ".");
    }

    private void refreshQrMappingControls(Set<String> placeholders) {
        qrMappingSelectors.clear();
        qrMappingBox.getChildren().clear();

        List<String> qrPlaceholders = placeholders.stream()
                .filter(htmlTemplateService::looksLikeQrPlaceholder)
                .toList();

        if (qrPlaceholders.isEmpty()) {
            qrMappingBox.getChildren().add(new Label("No QR placeholders detected in this template."));
            return;
        }

        Label question = new Label("Which field should be turned into a QR code?");
        question.getStyleClass().add("section-title");
        Label help = new Label("Pick the source column for each QR placeholder. The dropdown shows all template placeholders.");
        help.getStyleClass().add("section-description");
        help.setWrapText(true);
        qrMappingBox.getChildren().addAll(question, help);

        for (String qrPlaceholder : qrPlaceholders) {
            Label fieldLabel = new Label(qrPlaceholder);
            fieldLabel.getStyleClass().add("footer-label");

            ComboBox<String> sourceChoice = new ComboBox<>(templatePlaceholders);
            sourceChoice.getStyleClass().add("qr-source-choice");
            sourceChoice.setMaxWidth(Double.MAX_VALUE);

            if (templatePlaceholders.contains(qrPlaceholder)) {
                sourceChoice.setValue(qrPlaceholder);
            } else if (!templatePlaceholders.isEmpty()) {
                sourceChoice.setValue(templatePlaceholders.get(0));
            }

            qrMappingSelectors.put(qrPlaceholder, sourceChoice);

            HBox mappingRow = new HBox(12, fieldLabel, sourceChoice);
            mappingRow.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(sourceChoice, Priority.ALWAYS);
            qrMappingBox.getChildren().add(mappingRow);
        }
    }

    private Map<String, String> getQrMappings() {
        Map<String, String> mappings = new LinkedHashMap<>();
        for (Map.Entry<String, ComboBox<String>> entry : qrMappingSelectors.entrySet()) {
            String selectedSource = entry.getValue().getValue();
            if (selectedSource != null && !selectedSource.isBlank()) {
                mappings.put(entry.getKey(), selectedSource);
            }
        }
        return mappings;
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
