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
import org.example.cardify.model.PageSizePreset;
import org.example.cardify.service.ExcelExportService;
import org.example.cardify.service.ExcelImportService;
import org.example.cardify.service.AppPreferencesService;
import org.example.cardify.service.ExcelTemplateService;
import org.example.cardify.service.HtmlTemplateService;
import org.example.cardify.service.PrinterService;
import org.example.cardify.util.UiDialog;

import javafx.application.Platform;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class MainController {
    private final Stage stage;
    private final BorderPane root = new BorderPane();

    private final HtmlTemplateService htmlTemplateService = new HtmlTemplateService();
    private final ExcelTemplateService excelTemplateService = new ExcelTemplateService();
    private final ExcelImportService excelImportService = new ExcelImportService();
    private final PrinterService printerService = new PrinterService();

    private final ObservableList<SpreadsheetRow> allRows = FXCollections.observableArrayList();
    private final FilteredList<SpreadsheetRow> filteredRows = new FilteredList<>(allRows, row -> true);

    private final TableView<SpreadsheetRow> tableView      = new TableView<>();
    private final TableView<SpreadsheetRow> actionsTableView = new TableView<>();
    private final TextArea placeholdersArea = new TextArea();
    private final Label templateStatus = new Label("No HTML template loaded");
    private final Label excelStatus = new Label("No Excel file loaded");
    private final Label rowCountLabel = new Label("0 rows");
    private final Label selectedCountLabel = new Label("Selected: 0");
    private final CheckBox selectAllCheckBox = new CheckBox();
    private final ChoiceBox<String> printerChoice = new ChoiceBox<>();
    private final Label printerAdviceLabel = new Label();
    private final TextField searchField = new TextField();
    private final ChoiceBox<String> statusFilter = new ChoiceBox<>();
    private final ToggleButton themeToggle = new ToggleButton("Light");
    private final Button removeTemplateButton = new Button("Remove Template");
    private final Button clearDataButton = new Button("Clear Imported Data");
    private final Button rowPreviewButton = new Button("Row Preview");
    private final Button exportExcelButton = new Button("Download Excel");
    private final Button editButton = new Button("Edit Rows");
    private final ComboBox<Path> templateChoice = new ComboBox<>();
    private final ObservableList<Path> templateHistory = FXCollections.observableArrayList();
    private final Label selectedTemplateLabel = new Label("No template selected");
    private final ObservableList<String> templatePlaceholders = FXCollections.observableArrayList();

    private boolean syncingSelection = false;
    private boolean syncingTemplateSelection = false;
    private boolean editingEnabled = false;
    private boolean headerCheckboxIndeterminateClick = false;
    private final ExcelExportService excelExportService = new ExcelExportService();
    private final AppPreferencesService preferences = new AppPreferencesService();

    // Card page-size config fields (live in the footer)
    private final ComboBox<PageSizePreset> pageSizePresetChoice = new ComboBox<>();
    private final TextField cardWidthField  = new TextField();
    private final TextField cardHeightField = new TextField();
    private final Label cardSizeStatusLabel = new Label();

    private Stage previewStage;
    private WebView previewWebView;

    private VBox howToUsePanel;
    private TabPane contentTabPane;

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
        configureTemplateChoice();
        installSearch();
        restoreSavedState();
        restoreCardSizeConfig();
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

        Hyperlink howToUseLink = new Hyperlink("How to Use");
        howToUseLink.getStyleClass().add("help-link");
        howToUseLink.setOnAction(event -> toggleHowToUsePanel());

        VBox textBlock = new VBox(6, title, subtitle);
        textBlock.getStyleClass().add("header-block");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        themeToggle.setOnAction(event -> applyTheme(themeToggle.isSelected()));
        themeToggle.getStyleClass().add("theme-toggle");

        HBox rightActions = new HBox(12, howToUseLink, themeToggle);
        rightActions.setAlignment(Pos.CENTER_RIGHT);

        HBox header = new HBox(textBlock, spacer, rightActions);
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

        // Settings tab groups template, page size, PDF export, and danger zone
        VBox settingsSection = buildSettingsSection();

        Tab dataTab = new Tab("Data");
        dataTab.setContent(dataSection);
        dataTab.setClosable(false);

        Tab settingsTab = new Tab("Settings");
        settingsTab.setContent(settingsSection);
        settingsTab.setClosable(false);

        contentTabPane = new TabPane(dataTab, settingsTab);
        contentTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        contentTabPane.getStyleClass().add("content-tabs");
        contentTabPane.tabMinWidthProperty().bind(contentTabPane.widthProperty().divide(2).subtract(8));
        contentTabPane.tabMaxWidthProperty().bind(contentTabPane.widthProperty().divide(2).subtract(8));

        Button backButton = new Button("Back");
        backButton.setOnAction(event -> hideHowToUsePanel());
        HBox headerRow = new HBox(backButton);
        headerRow.setAlignment(Pos.CENTER_RIGHT);

        howToUsePanel = new VBox(14,
            createSectionHeader("How to Use", "Click the underlined text in the top section to show or hide these instructions."),
            headerRow,
            buildHowToUsePanel()
        );
        howToUsePanel.getStyleClass().add("content-card");
        howToUsePanel.setFillWidth(true);
        howToUsePanel.setVisible(false);
        howToUsePanel.setManaged(false);

        VBox wrapper = new VBox(12, howToUsePanel, contentTabPane);
        wrapper.setPadding(new Insets(24));
        wrapper.setFillWidth(true);
        VBox.setVgrow(contentTabPane, Priority.ALWAYS);
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
        printerChoice.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> updatePrinterAdvice());

        Button refreshPrintersButton = new Button("Refresh Printers");
        refreshPrintersButton.setOnAction(event -> refreshPrinters());
        Button diagnosticsButton = new Button("Show Diagnostics");
        diagnosticsButton.setOnAction(evt -> showDiagnosticsDialog());

        printerAdviceLabel.getStyleClass().add("printer-advice");
        printerAdviceLabel.setWrapText(true);
        printerAdviceLabel.setMaxWidth(300);
        printerAdviceLabel.setText("Select a real printer for actual print jobs; Microsoft Print to PDF saves files instead.");

        Label help = new Label("Image fields should point to local image files; they will be converted to printable HTML data URLs during rendering.");
        help.getStyleClass().add("footer-help");
        help.setWrapText(true);
        HBox.setHgrow(help, Priority.ALWAYS);

        VBox printerBlock = new VBox(4,
                new HBox(8, printerLabel, printerChoice, refreshPrintersButton, diagnosticsButton),
                printerAdviceLabel);

        footer.getChildren().addAll(printerBlock, help);
        return footer;
    }

    // ── Settings tab ────────────────────────────────────────────────

    private VBox buildSettingsSection() {
        VBox content = new VBox(16);
        content.setPadding(new Insets(20));
        content.setFillWidth(true);

        // ─ Section 1 : HTML Template & Page Size ─────────────────────────────────
        content.getChildren().add(buildSettingsPanel(
                "1", "HTML Template & Page Size",
                "Upload your HTML template and set its print size. The application maps the chosen page ratio against your template automatically.",
                "settings-panel-blue",
                buildTemplateControls(),
                buildPageSizeSettingsBlock(),
                buildTemplatePlaceholderArea()));

        // ─ Section 2 : Danger Zone ────────────────────────────────────
        content.getChildren().add(buildDangerZonePanel());

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().add("settings-scroll");
        VBox wrapper = new VBox(scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        wrapper.setFillWidth(true);
        return wrapper;
    }

    /**
     * Builds a visually distinct settings panel card with a numbered accent badge,
     * a title, description, and one or more content nodes.
     */
    private VBox buildSettingsPanel(String number, String title, String description,
                                    String styleClass, Node... contentNodes) {
        // Badge
        Label badge = new Label(number);
        badge.getStyleClass().add("settings-badge");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().addAll("section-title", "settings-panel-title");

        HBox header = new HBox(10, badge, titleLabel);
        header.setAlignment(Pos.CENTER_LEFT);

        Label desc = new Label(description);
        desc.getStyleClass().add("section-description");
        desc.setWrapText(true);

        VBox body = new VBox(12);
        body.getChildren().add(header);
        body.getChildren().add(desc);
        body.getChildren().add(new Separator());
        for (Node n : contentNodes) body.getChildren().add(n);

        body.getStyleClass().addAll("settings-panel", styleClass);
        body.setPadding(new Insets(16));
        body.setFillWidth(true);
        return body;
    }

    /** Page-size preset picker with optional custom W × H fields. */
    private Node buildPageSizeSettingsBlock() {
        Label presetLabel = new Label("Preset:");
        presetLabel.getStyleClass().add("footer-label");

        pageSizePresetChoice.getItems().setAll(PageSizePreset.values());
        pageSizePresetChoice.setPrefWidth(240);
        pageSizePresetChoice.setPromptText("Select preset");

        cardWidthField.setPrefWidth(78);
        cardWidthField.setPromptText("W mm");
        cardHeightField.setPrefWidth(78);
        cardHeightField.setPromptText("H mm");
        Label xLabel = new Label("×");

        Button applyButton = new Button("Apply Custom");
        applyButton.setOnAction(evt -> applyCardSizeConfig());

        HBox customFields = new HBox(6, new Label("Custom size:"), cardWidthField, xLabel, cardHeightField, applyButton);
        customFields.setAlignment(Pos.CENTER_LEFT);
        customFields.setVisible(false);
        customFields.setManaged(false);

        cardSizeStatusLabel.getStyleClass().add("printer-advice");

        pageSizePresetChoice.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) ->
                onPresetSelected(newVal, customFields));

        VBox block = new VBox(10,
                new HBox(10, presetLabel, pageSizePresetChoice),
                customFields,
                cardSizeStatusLabel);
        block.setAlignment(Pos.TOP_LEFT);
        return block;
    }

    /** Danger zone panel — styled as a collapsible red-accent card. */
    private VBox buildDangerZonePanel() {
        Button deleteAllButton = new Button("Delete All Data");
        deleteAllButton.getStyleClass().add("negative-button");
        deleteAllButton.setOnAction(event -> deleteAllDataWithCaptcha());

        Label warn = new Label("Removes saved templates, Excel paths, and imported rows from the application. Actual files on your disk are NOT deleted.");
        warn.getStyleClass().add("section-description");
        warn.setWrapText(true);

        VBox body = new VBox(10, warn, deleteAllButton);

        TitledPane pane = new TitledPane("⚠  Danger Zone", body);
        pane.setExpanded(false);
        pane.setCollapsible(true);
        pane.getStyleClass().add("danger-zone-pane");

        VBox wrapper = new VBox(pane);
        wrapper.getStyleClass().add("settings-panel-danger");
        wrapper.setPadding(new Insets(0));
        return wrapper;
    }



    private Node buildTemplateControls() {
        Button uploadButton = new Button("Upload HTML Template");
        uploadButton.setOnAction(event -> uploadTemplate());

        Button updateButton = new Button("Update Selected Template");
        updateButton.setOnAction(event -> updateSelectedTemplate());

        Button generateButton = new Button("Download Excel Template");
        generateButton.setOnAction(event -> downloadExcelTemplate());

        Button templatePreviewButton = new Button("Template Preview");
        templatePreviewButton.setOnAction(event -> showTemplatePreview());

        removeTemplateButton.setOnAction(event -> deleteSelectedTemplate());
        removeTemplateButton.getStyleClass().add("negative-button");
        removeTemplateButton.setText("Remove Selected Template");
        removeTemplateButton.setDisable(true);

        templateStatus.getStyleClass().add("status-label");
        selectedTemplateLabel.getStyleClass().add("status-label");
        templateChoice.setPrefWidth(360);
        templateChoice.setPromptText("Latest template is selected by default");
        VBox statusBlock = new VBox(6, new Label("Selected template"), templateChoice, selectedTemplateLabel, templateStatus);

        VBox controls = new VBox(12,
            new HBox(12, uploadButton, updateButton, generateButton, templatePreviewButton, removeTemplateButton),
            statusBlock);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.setPadding(new Insets(4, 0, 0, 0));
        controls.getStyleClass().add("control-row");
        return controls;
    }

    /** Placeholder preview area only — no Danger Zone embedded here. */
    private Node buildTemplatePlaceholderArea() {
        placeholdersArea.setEditable(false);
        placeholdersArea.setPrefRowCount(6);
        placeholdersArea.setPromptText("Detected placeholders will appear here after loading a template");
        Label label = new Label("Detected placeholders");
        label.getStyleClass().add("section-description");
        VBox box = new VBox(6, label, placeholdersArea);
        VBox.setVgrow(placeholdersArea, Priority.ALWAYS);
        return box;
    }

    /** @deprecated Use buildSettingsPanel-based flow; kept for compatibility. */
    private Node buildTemplatePreview() {
        return buildTemplatePlaceholderArea();
    }

    private Node buildHowToUsePanel() {
        VBox panel = new VBox(8);
        panel.setPadding(new Insets(0, 14, 0, 14));
        panel.getChildren().addAll(
                buildHelpStep("1. Upload the HTML template first", "Open Template & Excel and choose your HTML file. Use placeholders like {{name}}, {{department}}, and {{photo}} inside the HTML. The file you upload last becomes the active template."),
                buildHelpStep("2. Check the placeholder list", "After upload, Cardify scans the HTML and shows the detected placeholders. If you already saved older templates, use the dropdown to switch to one of them."),
                buildHelpStep("3. Generate the Excel sheet", "Click Download Excel Template. Cardify creates a workbook with the same placeholder names as column headers, so the sheet matches your HTML template."),
                buildHelpStep("4. Fill the Excel workbook", "Enter one ID card per row. Put normal text in normal fields, use local file paths in image fields, and put QR source text in QR-related columns."),
                buildHelpStep("5. Import the completed Excel file", "Go to the Data tab and upload the filled workbook. Cardify reads the rows and shows them in the table with a default Pending status."),
                buildHelpStep("6. Filter and choose rows", "Use the search box to find rows by any column value. Use the status filter to show only Pending, Printing, Printed, or Error rows."),
                buildHelpStep("7. Preview before printing", "Use Template Preview to see the full template output, or select one row and use Row Preview to test a single card first."),
                buildHelpStep("8. Print selected rows", "Select one or more rows, choose a printer, and click Print Selected Rows. Cardify renders the HTML, replaces the {{placeholder}} values, and sends the result to the printer."),
                buildHelpStep("9. Export or manage templates", "Use Export Excel to save the filtered rows, Remove Selected Template to delete one saved template, or the Danger Zone if you want to clear everything after captcha confirmation.")
        );

            ScrollPane scrollPane = new ScrollPane(panel);
            scrollPane.setFitToWidth(true);
            scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            scrollPane.setPrefViewportHeight(420);
            return scrollPane;
    }

    private void toggleHowToUsePanel() {
        if (howToUsePanel == null) {
            return;
        }

        boolean show = !howToUsePanel.isVisible();
        howToUsePanel.setVisible(show);
        howToUsePanel.setManaged(show);
        if (contentTabPane != null) {
            contentTabPane.setVisible(!show);
            contentTabPane.setManaged(!show);
        }
    }

    private void hideHowToUsePanel() {
        if (howToUsePanel == null) {
            return;
        }

        howToUsePanel.setVisible(false);
        howToUsePanel.setManaged(false);
        if (contentTabPane != null) {
            contentTabPane.setVisible(true);
            contentTabPane.setManaged(true);
        }
    }

    private Node buildHelpStep(String title, String description) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("section-title");
        titleLabel.setStyle("-fx-font-size: 13px;");

        Label descriptionLabel = new Label(description);
        descriptionLabel.getStyleClass().add("section-description");
        descriptionLabel.setStyle("-fx-font-size: 11px;");
        descriptionLabel.setWrapText(true);

        VBox stepCard = new VBox(2, titleLabel, descriptionLabel);
        stepCard.getStyleClass().add("content-card");
        stepCard.setFillWidth(true);
        stepCard.setPadding(new Insets(6, 10, 6, 10));
        VBox.setMargin(stepCard, new Insets(4, 2, 4, 2));
        return stepCard;
    }

    private Node buildDataControls() {
        Button uploadExcelButton = new Button("Upload Filled Excel");
        uploadExcelButton.setOnAction(event -> uploadExcel());

        // rowPreviewButton kept as a field (used by updateSelectedCount) but not shown in toolbar.
        // Preview is now accessible per-row via the Actions column.
        rowPreviewButton.setDisable(true);

        Button printButton = new Button("\uD83D\uDDA8  Print Selected Rows");
        printButton.getStyleClass().add("accent-button");
        printButton.setOnAction(event -> printSelectedRows());

        exportExcelButton.setOnAction(event -> exportCurrentRows());
        editButton.setOnAction(event -> toggleEditMode());

        statusFilter.getItems().setAll("All", "Pending", "Printing", "Printed", "Error");
        statusFilter.setValue("All");
        statusFilter.setPrefWidth(140);

        clearDataButton.setOnAction(event -> clearImportedData());
        clearDataButton.getStyleClass().add("negative-button");
        clearDataButton.setDisable(true);

        searchField.setPromptText("Filter rows by any column value");
        searchField.setPrefWidth(320);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Left cluster: filters + data management
        HBox leftCluster = new HBox(10, statusFilter, searchField, uploadExcelButton,
                exportExcelButton, editButton, clearDataButton);
        leftCluster.setAlignment(Pos.CENTER_LEFT);

        // Right cluster: primary action only
        HBox rightCluster = new HBox(10, printButton);
        rightCluster.setAlignment(Pos.CENTER_RIGHT);

        HBox controls = new HBox(leftCluster, spacer, rightCluster);
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

        // ── Fixed Actions sidebar (sticky right) ──────────────────────
        actionsTableView.setItems(filteredRows);
        actionsTableView.getStyleClass().addAll("data-table", "actions-side-table");
        actionsTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        actionsTableView.setEditable(false);
        actionsTableView.setFocusTraversable(false);
        actionsTableView.setMouseTransparent(false);
        actionsTableView.setPlaceholder(new Label(""));
        actionsTableView.setFixedCellSize(tableView.getFixedCellSize());
        buildActionsColumn();

        // Sync vertical scroll after both tables have a skin
        tableView.skinProperty().addListener((obs, o, newSkin) -> {
            if (newSkin != null) Platform.runLater(this::syncTableScrollBars);
        });

        HBox tableRow = new HBox(0, tableView, actionsTableView);
        HBox.setHgrow(tableView, Priority.ALWAYS);
        VBox.setVgrow(tableRow, Priority.ALWAYS);

        HBox statusBar = new HBox(18, excelStatus, rowCountLabel, selectedCountLabel);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        selectedCountLabel.getStyleClass().add("status-label");
        rowCountLabel.getStyleClass().add("status-label");
        excelStatus.getStyleClass().add("status-label");

        Label hint = new Label("Click one or more rows, then print. Each selected row becomes one ID card output.");
        hint.getStyleClass().add("table-hint");
        return new VBox(10, statusBar, hint, tableRow);
    }

    /** Synchronizes the vertical scrollbars of the main and actions tables, hides actions table scrollbars. */
    private void syncTableScrollBars() {
        ScrollBar mainVBar = null;
        ScrollBar actionsVBar = null;
        for (Node n : tableView.lookupAll(".scroll-bar")) {
            if (n instanceof ScrollBar sb && sb.getOrientation() == javafx.geometry.Orientation.VERTICAL) {
                mainVBar = sb;
                break;
            }
        }
        for (Node n : actionsTableView.lookupAll(".scroll-bar")) {
            if (n instanceof ScrollBar sb) {
                if (sb.getOrientation() == javafx.geometry.Orientation.VERTICAL) {
                    actionsVBar = sb;
                }
                sb.setVisible(false);
                sb.setManaged(false);
            }
        }
        if (mainVBar != null && actionsVBar != null) {
            actionsVBar.valueProperty().bindBidirectional(mainVBar.valueProperty());
        }
    }

    /** Builds (or rebuilds) the single Actions column inside the fixed side-table. */
    private void buildActionsColumn() {
        actionsTableView.getColumns().clear();
        TableColumn<SpreadsheetRow, Void> col = new TableColumn<>("Actions");
        col.setPrefWidth(100);
        col.setMinWidth(100);
        col.setMaxWidth(100);
        col.setSortable(false);
        col.setReorderable(false);
        col.setEditable(false);
        col.setCellFactory(c -> new TableCell<>() {
            private final Button previewBtn = new Button("\uD83D\uDC41");
            private final Button pdfBtn     = new Button("\u2913");
            private final HBox   box        = new HBox(4, previewBtn, pdfBtn);
            {
                box.setAlignment(Pos.CENTER);
                previewBtn.getStyleClass().add("row-action-btn");
                pdfBtn.getStyleClass().addAll("row-action-btn", "row-pdf-btn");
                previewBtn.setTooltip(new javafx.scene.control.Tooltip("Preview this row"));
                pdfBtn.setTooltip(new javafx.scene.control.Tooltip("Download PDF for this row"));
                previewBtn.setOnAction(evt -> {
                    SpreadsheetRow row = getTableRow().getItem();
                    if (row != null) previewSingleRow(row);
                });
                pdfBtn.setOnAction(evt -> {
                    SpreadsheetRow row = getTableRow().getItem();
                    if (row != null) exportSingleRowAsPdf(row);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || getTableRow().getItem() == null ? null : box);
            }
        });
        actionsTableView.getColumns().add(col);
    }

    private void installSearch() {
        searchField.textProperty().addListener((observable, oldValue, newValue) -> updateFilters());
        statusFilter.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> updateFilters());
    }

    private void updateFilters() {
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        String statusValue = statusFilter.getValue();
        filteredRows.setPredicate(row -> {
            if (statusValue != null && !statusValue.equals("All") && !statusValue.equalsIgnoreCase(row.getStatus())) {
                return false;
            }
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
    }

    public void refreshPrinters() {
        List<String> printers = printerService.listPrinterNames();
        String currentSelection = printerChoice.getValue();
        String systemDefaultPrinter = printerService.getDefaultPrinterName();

        printerChoice.getItems().setAll(printers);

        if (currentSelection != null && printers.contains(currentSelection)) {
            printerChoice.setValue(currentSelection);
            updatePrinterAdvice();
            logPrinterConnected(currentSelection);
            return;
        }

        if (systemDefaultPrinter != null && printers.contains(systemDefaultPrinter)) {
            printerChoice.setValue(systemDefaultPrinter);
            updatePrinterAdvice();
            logPrinterConnected(systemDefaultPrinter);
            return;
        }

        if (!printers.isEmpty()) {
            printerChoice.setValue(printers.get(0));
            updatePrinterAdvice();
            logPrinterConnected(printers.get(0));
            return;
        }

        printerChoice.setValue(null);
        updatePrinterAdvice();
    }

    /** Emits a log entry when a printer becomes the active/selected printer. */
    private void logPrinterConnected(String printerName) {
        if (printerName == null || printerName.isBlank()) return;
        boolean isVirtual = printerService.isVirtualPdfPrinter(printerName);
        String kind = isVirtual ? "virtual PDF printer" : "physical printer";
        System.out.println("[Cardify] Printer connected/selected: '" + printerName + "' (" + kind + ")");
    }

    private void updatePrinterAdvice() {
        String selectedPrinter = printerChoice.getValue();
        if (selectedPrinter == null) {
            printerAdviceLabel.setText("No printer selected. Please refresh printers or install a printer driver.");
            return;
        }

        if (printerService.isVirtualPdfPrinter(selectedPrinter)) {
            printerAdviceLabel.setText("Selected printer is a PDF printer. Cardify will save output to Documents\\Cardify PDF Output instead of sending a print job.");
        } else {
            printerAdviceLabel.setText("Selected printer should submit a real print job. Make sure the printer is powered on and connected.");
        }
    }

    private void restoreSavedState() {
        loadSavedTemplateHistory();
        String savedExcel = preferences.getSavedExcelPath();
        if (savedExcel != null && !savedExcel.isBlank()) {
            loadExcelFromPath(Path.of(savedExcel));
        }
    }

    /** Loads the persisted preset / card size into the UI and pushes it to PrinterService. */
    private void restoreCardSizeConfig() {
        String templatePath = htmlTemplateFile != null ? htmlTemplateFile.getAbsolutePath() : null;
        String savedPresetName = preferences.getCardPreset(templatePath);
        PageSizePreset preset = PageSizePreset.fromName(savedPresetName);

        // If the saved preset is CUSTOM, restore the raw mm values from prefs
        if (preset.isCustom()) {
            float w = preferences.getCardWidthMm(templatePath);
            float h = preferences.getCardHeightMm(templatePath);
            cardWidthField.setText(String.format(java.util.Locale.US, "%.2f", w));
            cardHeightField.setText(String.format(java.util.Locale.US, "%.2f", h));
        }

        // Setting the ComboBox value fires onPresetSelected which updates the service
        pageSizePresetChoice.setValue(preset);
    }

    /**
     * Called whenever the user picks a different preset in the ComboBox.
     * Immediately applies preset dimensions; for Custom it just shows the fields.
     */
    private void onPresetSelected(PageSizePreset preset, HBox customFields) {
        if (preset == null) return;

        boolean isCustom = preset.isCustom();
        customFields.setVisible(isCustom);
        customFields.setManaged(isCustom);

        if (!isCustom) {
            String templatePath = htmlTemplateFile != null ? htmlTemplateFile.getAbsolutePath() : null;
            float w = preset.getWidthMm();
            float h = preset.getHeightMm();
            cardWidthField.setText(String.format(java.util.Locale.US, "%.2f", w));
            cardHeightField.setText(String.format(java.util.Locale.US, "%.2f", h));
            preferences.saveCardSizeMm(templatePath, w, h);
            preferences.saveCardPreset(templatePath, preset.name());
            printerService.setCardSizeMm(w, h);
            cardSizeStatusLabel.setText(String.format(java.util.Locale.US, "%s  —  %.2f × %.2f mm", preset.getDisplayName(), w, h));
            System.out.println("[Cardify] Page-size preset selected: " + preset.getDisplayName()
                    + " (" + w + " × " + h + " mm)");
        } else {
            cardSizeStatusLabel.setText("Custom — enter width × height and click Apply.");
        }
    }

    /** Validates the custom width/height fields, persists, and updates PrinterService. */
    private void applyCardSizeConfig() {
        try {
            float w = Float.parseFloat(cardWidthField.getText().trim());
            float h = Float.parseFloat(cardHeightField.getText().trim());
            if (w <= 0 || h <= 0) {
                UiDialog.warn(stage, "Invalid size", "Width and height must be positive numbers.");
                return;
            }
            String templatePath = htmlTemplateFile != null ? htmlTemplateFile.getAbsolutePath() : null;
            preferences.saveCardSizeMm(templatePath, w, h);
            preferences.saveCardPreset(templatePath, PageSizePreset.CUSTOM.name());
            printerService.setCardSizeMm(w, h);
            cardSizeStatusLabel.setText(String.format(java.util.Locale.US, "Custom applied: %.2f × %.2f mm", w, h));
            System.out.println("[Cardify] Custom card size applied: " + w + " × " + h + " mm");
        } catch (NumberFormatException ex) {
            UiDialog.warn(stage, "Invalid input", "Please enter numeric values for width and height (e.g. 53.98).");
        }
    }

    /**
     * Exports every currently selected row as a standalone PDF file.
     * The user chooses an output directory via a directory chooser;
     * files are named {@code card_<rowIndex>.pdf}.
     * Runs on a background thread so the UI stays responsive.
     */
    private void exportSelectedAsPdf() {
        List<SpreadsheetRow> selected = tableView.getSelectionModel().getSelectedItems();
        if (selected == null || selected.isEmpty()) {
            UiDialog.warn(stage, "No rows selected",
                    "Please select one or more rows in the Data tab before downloading PDFs.");
            return;
        }

        String templateContent = currentTemplateContent();
        if (templateContent == null || templateContent.isBlank()) {
            UiDialog.warn(stage, "No template loaded",
                    "Please upload an HTML template in the Settings tab before exporting PDFs.");
            return;
        }

        javafx.stage.DirectoryChooser dc = new javafx.stage.DirectoryChooser();
        dc.setTitle("Choose PDF output folder");
        dc.setInitialDirectory(new File(System.getProperty("user.home")));
        File dir = dc.showDialog(stage);
        if (dir == null) return;  // user cancelled

        Path outputDir = dir.toPath();
        Path templatePath = htmlTemplateFile != null ? htmlTemplateFile.toPath() : null;

        // Work list captured before leaving FX thread
        List<SpreadsheetRow> workList = List.copyOf(selected);

        Thread worker = new Thread(() -> {
            int ok = 0, fail = 0;
            for (int i = 0; i < workList.size(); i++) {
                SpreadsheetRow row = workList.get(i);
                // Build a safe filename from the first column value, or fallback to index
                String baseName = "card_" + (i + 1);
                Path dest = outputDir.resolve(baseName + ".pdf");
                try {
                    printerService.exportRowAsPdf(templateContent, row, java.util.Map.of(), templatePath, dest);
                    ok++;
                } catch (Exception ex) {
                    fail++;
                    System.err.println("[Cardify] exportSelectedAsPdf: failed for row " + i + ": " + ex.getMessage());
                }
            }
            int finalOk = ok, finalFail = fail;
            Platform.runLater(() -> {
                String msg = "Exported " + finalOk + " PDF(s) to:\n" + outputDir;
                if (finalFail > 0) msg += "\n(" + finalFail + " row(s) failed — check diagnostics)";
                UiDialog.info(stage, "PDF Export Complete", msg);
                System.out.println("[Cardify] PDF export finished: " + finalOk + " ok, " + finalFail + " failed -> " + outputDir);
            });
        }, "cardify-pdf-export");
        worker.setDaemon(true);
        worker.start();
    }

    /** Returns the raw HTML of the currently loaded template, or {@code null} if none. */
    private String currentTemplateContent() {
        if (htmlTemplateFile == null || !htmlTemplateFile.exists()) return null;
        try {
            return Files.readString(htmlTemplateFile.toPath());
        } catch (IOException ex) {
            return null;
        }
    }

    private void loadTemplateFromPath(Path path) {

        loadTemplateFromPath(path, true);
    }

    private void loadTemplateFromPath(Path path, boolean recordInHistory) {
        if (Files.exists(path) && Files.isRegularFile(path)) {
            Path normalizedPath = path.toAbsolutePath().normalize();
            if (recordInHistory) {
                rememberTemplate(normalizedPath);
            }
            clearCurrentTemplateState();
            htmlTemplateFile = normalizedPath.toFile();
            Set<String> placeholders = htmlTemplateService.extractPlaceholders(normalizedPath);
            templatePlaceholders.setAll(placeholders);
            placeholdersArea.setText(String.join(System.lineSeparator(), placeholders));
            selectedTemplateLabel.setText(normalizedPath.getFileName().toString());
            templateStatus.setText("Loaded with " + placeholders.size() + " placeholder(s)");
            syncTemplateChoice(normalizedPath);
            removeTemplateButton.setDisable(false);
            restoreCardSizeConfig();
        } else {
            clearCurrentTemplateState();
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
                tableView.setItems(filteredRows);
                updateFilters();
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
        row.statusProperty().addListener((obs, oldV, newV) -> {
            if ("Error".equalsIgnoreCase(newV)) {
                // show diagnostics so the user can see the log immediately
                showDiagnosticsDialog();
            }
        });
    }

    private void showDiagnosticsDialog() {
        java.nio.file.Path logPath = java.nio.file.Path.of(System.getenv("APPDATA") == null ? System.getProperty("user.home") : System.getenv("APPDATA"), "Cardify", "logs", "print.log");
        String content = null;
        try {
            if (java.nio.file.Files.exists(logPath)) {
                content = java.nio.file.Files.lines(logPath).collect(java.util.stream.Collectors.joining(System.lineSeparator()));
            }
        } catch (Exception ignored) {
        }
        UiDialog.showDiagnostics(stage, content);
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

    private void updateSelectedTemplate() {
        Path currentTemplate = templateChoice.getValue();
        if (currentTemplate == null) {
            UiDialog.warn(stage, "No template selected", "Choose a template from the dropdown before updating it.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Replacement HTML Template");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("HTML Files", "*.html", "*.htm"));
        File replacement = chooser.showOpenDialog(stage);
        if (replacement == null) {
            return;
        }

        replaceTemplateInHistory(currentTemplate, replacement.toPath());
        loadTemplateFromPath(replacement.toPath(), false);
    }

    private void deleteSelectedTemplate() {
        Path selectedTemplate = templateChoice.getValue();
        if (selectedTemplate == null && htmlTemplateFile != null) {
            selectedTemplate = htmlTemplateFile.toPath();
        }
        if (selectedTemplate == null) {
            UiDialog.warn(stage, "No template selected", "Choose a template from the dropdown before deleting it.");
            return;
        }

        if (!UiDialog.confirm(stage, "Delete Template", "Remove the selected template from the template list?")) {
            return;
        }

        Path normalizedSelectedTemplate = selectedTemplate.toAbsolutePath().normalize();
        templateHistory.removeIf(templatePath -> templatePath.toAbsolutePath().normalize().equals(normalizedSelectedTemplate));
        preferences.saveTemplatePaths(templateHistory.stream().map(Path::toString).toList());

        if (templateHistory.isEmpty()) {
            clearCurrentTemplateState();
            templateChoice.setValue(null);
            return;
        }

        Path latestTemplate = templateHistory.get(0);
        loadTemplateFromPath(latestTemplate, false);
    }

    private void clearTemplate() {
        clearCurrentTemplateState();
    }

    private void clearCurrentTemplateState() {
        htmlTemplateFile = null;
        placeholdersArea.clear();
        templatePlaceholders.clear();
        selectedTemplateLabel.setText("No template selected");
        templateStatus.setText("No HTML template loaded");
        syncingTemplateSelection = true;
        templateChoice.setValue(null);
        syncingTemplateSelection = false;
        removeTemplateButton.setDisable(true);
    }

    private void deleteAllDataWithCaptcha() {
        String captcha = generateCaptchaCode();
        if (!confirmDeleteAllWithCaptcha(captcha)) {
            return;
        }

        // Clear persisted preferences and in-memory lists
        templateHistory.clear();
        preferences.clearSavedTemplatePaths();
        preferences.clearSavedExcelPath();

        clearImportedDataState();
        clearCurrentTemplateState();

        String message = "All saved templates, Excel paths, and imported rows were successfully cleared from the application state.\n" +
                         "Please note: The actual files on your system were not deleted.";

        UiDialog.info(stage, "Delete All completed", message);
    }

    private boolean confirmDeleteAllWithCaptcha(String captcha) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.initOwner(stage);
        dialog.setTitle("Delete All Confirmation");
        dialog.setHeaderText("This will remove saved templates, the saved Excel path, and imported data from the application.");
        dialog.setContentText("Type this code to confirm: " + captcha);

        if (stage != null && stage.getScene() != null) {
            dialog.getDialogPane().getStylesheets().setAll(stage.getScene().getStylesheets());
            boolean isLight = root.getStyleClass().contains("light-theme");
            dialog.getDialogPane().getStyleClass().removeAll("dark-theme", "light-theme");
            dialog.getDialogPane().getStyleClass().add(isLight ? "light-theme" : "dark-theme");
        }

        Optional<String> response = dialog.showAndWait();
        return response.map(answer -> captcha.equalsIgnoreCase(answer.trim())).orElse(false);
    }

    private String generateCaptchaCode() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder code = new StringBuilder(6);
        for (int index = 0; index < 6; index++) {
            code.append(alphabet.charAt(ThreadLocalRandom.current().nextInt(alphabet.length())));
        }
        return code.toString();
    }

    private void clearImportedDataState() {
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
        clearDataButton.setDisable(true);
        rowPreviewButton.setDisable(true);
    }

    private void loadSavedTemplateHistory() {
        List<Path> savedTemplates = preferences.getSavedTemplatePaths().stream()
                .map(Path::of)
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .filter(Files::exists)
                .filter(Files::isRegularFile)
                .toList();

        templateHistory.setAll(savedTemplates);
        if (templateHistory.isEmpty()) {
            clearCurrentTemplateState();
            templateChoice.setValue(null);
            preferences.clearSavedTemplatePaths();
            return;
        }

        Path latestTemplate = templateHistory.get(0);
        loadTemplateFromPath(latestTemplate, false);
        syncTemplateChoice(latestTemplate);
        preferences.saveTemplatePaths(templateHistory.stream().map(Path::toString).toList());
    }

    private void rememberTemplate(Path templatePath) {
        Path normalizedTemplatePath = templatePath.toAbsolutePath().normalize();
        templateHistory.removeIf(existingPath -> existingPath.toAbsolutePath().normalize().equals(normalizedTemplatePath));
        templateHistory.add(0, normalizedTemplatePath);
        preferences.saveTemplatePaths(templateHistory.stream().map(Path::toString).toList());
    }

    private void replaceTemplateInHistory(Path oldTemplate, Path newTemplate) {
        Path normalizedOldTemplate = oldTemplate.toAbsolutePath().normalize();
        Path normalizedNewTemplate = newTemplate.toAbsolutePath().normalize();
        int templateIndex = -1;
        for (int index = 0; index < templateHistory.size(); index++) {
            if (templateHistory.get(index).toAbsolutePath().normalize().equals(normalizedOldTemplate)) {
                templateIndex = index;
                break;
            }
        }

        if (templateIndex >= 0) {
            templateHistory.set(templateIndex, normalizedNewTemplate);
        } else {
            templateHistory.add(0, normalizedNewTemplate);
        }
        preferences.saveTemplatePaths(templateHistory.stream().map(Path::toString).toList());
    }

    private void configureTemplateChoice() {
        templateChoice.setItems(templateHistory);
        templateChoice.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatTemplateDisplay(item));
            }
        });
        templateChoice.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatTemplateDisplay(item));
            }
        });
        templateChoice.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (syncingTemplateSelection || newValue == null) {
                return;
            }
            loadTemplateFromPath(newValue, false);
        });
    }

    private void syncTemplateChoice(Path path) {
        syncingTemplateSelection = true;
        templateChoice.setValue(path);
        syncingTemplateSelection = false;
    }

    private String formatTemplateDisplay(Path path) {
        Path normalizedPath = path.toAbsolutePath().normalize();
        Path fileName = normalizedPath.getFileName();
        if (fileName == null) {
            return normalizedPath.toString();
        }

        Path parent = normalizedPath.getParent();
        if (parent == null) {
            return fileName.toString();
        }
        return fileName + " (" + parent + ")";
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

        // ── Checkbox select column ────────────────────────────────────
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

        // ── Status column ─────────────────────────────────────────────
        TableColumn<SpreadsheetRow, String> statusColumn = new TableColumn<>("Status");
        statusColumn.setCellValueFactory(cellData -> cellData.getValue().statusProperty());
        statusColumn.setCellFactory(ChoiceBoxTableCell.forTableColumn("Pending", "Printing", "Printed", "Error"));
        statusColumn.setPrefWidth(120);
        statusColumn.setEditable(true);
        tableView.getColumns().add(statusColumn);

        // ── Data columns ──────────────────────────────────────────────
        for (String header : currentHeaders) {
            TableColumn<SpreadsheetRow, String> column = new TableColumn<>(header);
            column.setCellValueFactory(cellData -> cellData.getValue().valueProperty(header));
            column.setCellFactory(TextFieldTableCell.forTableColumn());
            column.setEditable(editingEnabled);
            column.setMinWidth(140);
            tableView.getColumns().add(column);
        }
        // Actions column lives in actionsTableView (sticky right) — not here.
    }

    /** Opens a preview window for a specific row (called from Actions column). */
    private void previewSingleRow(SpreadsheetRow row) {
        if (htmlTemplateFile == null) {
            UiDialog.warn(stage, "No template loaded", "Upload an HTML template before previewing.");
            return;
        }
        String htmlTemplate = htmlTemplateService.readTemplate(htmlTemplateFile.toPath());
        openPreviewWindow("Row Preview", htmlTemplateService.renderTemplate(htmlTemplate, row.asMap(), getQrMappings()));
    }

    /** Exports a single row as PDF (called from Actions column). */
    private void exportSingleRowAsPdf(SpreadsheetRow row) {
        String templateContent = currentTemplateContent();
        if (templateContent == null || templateContent.isBlank()) {
            UiDialog.warn(stage, "No template loaded",
                    "Upload an HTML template before exporting PDF.");
            return;
        }
        javafx.stage.DirectoryChooser dc = new javafx.stage.DirectoryChooser();
        dc.setTitle("Choose PDF output folder");
        dc.setInitialDirectory(new File(System.getProperty("user.home")));
        File dir = dc.showDialog(stage);
        if (dir == null) return;

        Path outputDir = dir.toPath();
        Path templatePath = htmlTemplateFile != null ? htmlTemplateFile.toPath() : null;
        String baseName = "card_" + System.currentTimeMillis();
        Path dest = outputDir.resolve(baseName + ".pdf");

        Thread worker = new Thread(() -> {
            try {
                printerService.exportRowAsPdf(templateContent, row, java.util.Map.of(), templatePath, dest);
                Platform.runLater(() -> UiDialog.info(stage, "PDF Saved", "Saved to:\n" + dest));
            } catch (Exception ex) {
                Platform.runLater(() -> UiDialog.error(stage, "PDF Failed", ex.getMessage()));
            }
        }, "cardify-pdf-single");
        worker.setDaemon(true);
        worker.start();
    }

    private void printSelectedRows() {
        if (htmlTemplateFile == null) {
            UiDialog.warn(stage, "Missing template", "Upload an HTML template before printing.");
            return;
        }

        refreshPrinters();

        List<SpreadsheetRow> selectedRows = new ArrayList<>(filteredRows.stream().filter(SpreadsheetRow::isSelected).toList());
        if (selectedRows.isEmpty()) {
            UiDialog.warn(stage, "Nothing selected", "Select one or more rows to print.");
            return;
        }

        String printerName = printerChoice.getValue();
        if (printerName == null || printerName.isBlank()) {
            UiDialog.warn(stage, "No printer selected", "Choose a connected printer from the footer bar.");
            return;
        }

        if (!printerChoice.getItems().contains(printerName)) {
            UiDialog.warn(stage, "Printer unavailable", "The selected printer is no longer available. Please choose another printer and try again.");
            return;
        }

        try {
            Path templatePath = htmlTemplateFile.toPath();
            String htmlTemplate = htmlTemplateService.readTemplate(templatePath);
            printerService.printCards(printerName, htmlTemplate, selectedRows, getQrMappings(), templatePath);
            UiDialog.info(stage, "Print job started", "Sent " + selectedRows.size() + " card(s) to " + printerName + ". Track row status for completion.");
        } catch (RuntimeException exception) {
            UiDialog.error(stage, "Print failed", "Unable to start printing: " + exception.getMessage());
        }
    }

    // QR mapping UI removed: always return no mappings.
    
    private Map<String, String> getQrMappings() {
        return Map.of();
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
