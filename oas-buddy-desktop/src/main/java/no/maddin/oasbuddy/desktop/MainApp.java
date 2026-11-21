package no.maddin.oasbuddy.desktop;

import no.maddin.oasbuddy.core.convert.ConversionResult;
import no.maddin.oasbuddy.core.convert.OasConverter;
import no.maddin.oasbuddy.core.document.DocumentFormat;
import no.maddin.oasbuddy.core.document.DocumentReader;
import no.maddin.oasbuddy.core.document.DocumentWriter;
import no.maddin.oasbuddy.core.document.OasDocument;
import no.maddin.oasbuddy.core.document.OasVersion;
import no.maddin.oasbuddy.core.model.HttpMethod;
import no.maddin.oasbuddy.core.model.Operation;
import no.maddin.oasbuddy.core.model.PathItem;
import no.maddin.oasbuddy.core.validation.OasValidator;
import no.maddin.oasbuddy.core.validation.ValidationMessage;
import no.maddin.oasbuddy.core.validation.ValidationSeverity;
import no.maddin.oasbuddy.desktop.pane.AboutDialog;
import no.maddin.oasbuddy.desktop.pane.InfoPane;
import no.maddin.oasbuddy.desktop.pane.OperationPane;
import no.maddin.oasbuddy.desktop.pane.PathItemPane;
import no.maddin.oasbuddy.desktop.pane.PathRemoval;
import no.maddin.oasbuddy.desktop.pane.PathsPane;
import no.maddin.oasbuddy.desktop.pane.SchemaPane;
import no.maddin.oasbuddy.desktop.pane.PathRename;
import no.maddin.oasbuddy.desktop.pane.RemovalConfirmation;
import no.maddin.oasbuddy.desktop.pane.CallbackPane;
import no.maddin.oasbuddy.desktop.pane.ComponentKind;
import no.maddin.oasbuddy.desktop.pane.ComponentKinds;
import no.maddin.oasbuddy.desktop.pane.ComponentListPane;
import no.maddin.oasbuddy.desktop.pane.ComponentRemoval;
import no.maddin.oasbuddy.desktop.pane.ComponentCatalog;
import no.maddin.oasbuddy.desktop.pane.ConversionDialog;
import no.maddin.oasbuddy.desktop.pane.ExamplePane;
import no.maddin.oasbuddy.desktop.pane.HeaderPane;
import no.maddin.oasbuddy.desktop.pane.ImportOnlyPane;
import no.maddin.oasbuddy.desktop.pane.LinkPane;
import no.maddin.oasbuddy.desktop.pane.ParameterPane;
import no.maddin.oasbuddy.desktop.pane.ResponsePane;
import no.maddin.oasbuddy.desktop.pane.SchemaRemoval;
import no.maddin.oasbuddy.desktop.pane.SchemaRename;
import no.maddin.oasbuddy.desktop.pane.SchemasPane;
import no.maddin.oasbuddy.desktop.pane.SecurityPane;
import no.maddin.oasbuddy.desktop.pane.SecuritySchemeCatalog;
import no.maddin.oasbuddy.desktop.pane.SecuritySchemePane;
import no.maddin.oasbuddy.desktop.pane.SecuritySchemeRemoval;
import no.maddin.oasbuddy.desktop.pane.SecuritySchemesPane;
import no.maddin.oasbuddy.desktop.pane.ServersPane;
import no.maddin.oasbuddy.desktop.pane.TagCatalog;
import no.maddin.oasbuddy.desktop.pane.TagRemoval;
import no.maddin.oasbuddy.desktop.pane.TagsPane;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Styles;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.prefs.Preferences;

public class MainApp extends Application {

    private static final Preferences PREFS = Preferences.userNodeForPackage(MainApp.class);
    private static final String THEME_PREF_KEY = "theme";
    private static final String THEME_DARK = "dark";
    private static final String THEME_LIGHT = "light";
    private static final String APP_NAME = "OAS Buddy";
    /** Outline labels, also the keys {@link #refreshAndSelect} finds a node by. */
    private static final String SCHEMAS = "Schemas";
    private static final String PATHS = "Paths";

    private final OasValidator validator = new OasValidator();
    private final OasConverter converter = OasConverter.standard();

    /** Overridden by tests, which cannot answer a modal dialog. */
    private RemovalConfirmation confirmation = RemovalConfirmation.dialog();

    /** Overridden by tests, which cannot answer a modal dialog; its button reads "Rename". */
    private RemovalConfirmation renameConfirmation = RemovalConfirmation.dialog("Rename");

    /**
     * Overridden by tests, which cannot answer a modal dialog; asked before an inline definition is
     * replaced by a reference, so its button reads "Replace".
     */
    private RemovalConfirmation replaceConfirmation = RemovalConfirmation.dialog("Replace");

    /** Overridden by tests, which cannot answer a modal dialog; asked before a lossy conversion. */
    private RemovalConfirmation conversionConfirmation = ConversionDialog.dialog();

    /** Overridden by tests, which cannot dismiss a modal dialog. */
    private Runnable aboutAction = this::showAbout;

    /**
     * Overridden by tests, which must never reach the real one: {@code getHostServices()} works
     * even for an application JavaFX did not launch, and hands the url to {@code xdg-open}, which
     * finds the user's running browser over the session bus — even from under Xvfb.
     */
    private Consumer<String> linkOpener = url -> getHostServices().showDocument(url);

    private Stage stage;
    private OasDocument document;
    private Path currentFile;

    private TreeView<OutlineNode> outlineView;
    private MenuItem saveItem;
    private MenuItem saveAsItem;
    private Menu convertMenu;
    /**
     * Where Save As proposes to put a converted document: next to the file it came from, under a
     * name that says the version. A converted document belongs to no file, so the original — for
     * 2.0 the only copy — is never overwritten by a plain Save.
     */
    private Path saveAsSuggestion;
    private BorderPane centerHolder;
    private ListView<String> validationList;

    @Override
    public void start(Stage primaryStage) {
        boolean darkTheme = THEME_DARK.equals(PREFS.get(THEME_PREF_KEY, THEME_LIGHT));
        setTheme(darkTheme);

        this.stage = primaryStage;
        this.document = OasDocument.newDocument(DocumentFormat.YAML);

        BorderPane root = new BorderPane();
        root.setTop(buildMenuBar(darkTheme));

        outlineView = new TreeView<>();
        outlineView.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            if (newItem != null && newItem.getValue() != null && newItem.getValue().paneSupplier() != null) {
                showPane(newItem.getValue().paneSupplier().get());
            }
        });
        outlineView.setPrefWidth(240);
        outlineView.getStyleClass().add(Styles.DENSE);

        centerHolder = new BorderPane();
        centerHolder.getStyleClass().add(Styles.BG_SUBTLE);

        Label validationHeading = new Label("Validation");
        validationHeading.getStyleClass().add(Styles.TITLE_4);
        Button validateButton = new Button("Validate");
        validateButton.getStyleClass().add(Styles.ACCENT);
        validateButton.setOnAction(e -> runValidation());
        HBox validationHeader = new HBox(8, validationHeading, spacer(), validateButton);
        validationHeader.setAlignment(Pos.CENTER_LEFT);

        validationList = new ListView<>();
        validationList.setPrefHeight(120);

        VBox validationBox = new VBox(6, validationHeader, validationList);
        validationBox.setPadding(new Insets(10, 12, 12, 12));

        root.setLeft(outlineView);
        root.setCenter(centerHolder);
        root.setBottom(new VBox(new Separator(), validationBox));

        refreshOutline();

        updateWindowState();
        stage.getIcons().addAll(AppIcon.icons(16, 32, 48, 64, 128, 256));
        stage.setScene(new Scene(root, 1100, 750));
        stage.show();
    }

    private static Node spacer() {
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private MenuBar buildMenuBar(boolean darkTheme) {
        // 3.0 comes first and takes the shortcut: it is the default, as for the document shown on start.
        MenuItem new30 = new MenuItem(OasVersion.V3_0.label());
        new30.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN));
        new30.setOnAction(e -> newDocument(OasVersion.V3_0));
        MenuItem new31 = new MenuItem(OasVersion.V3_1.label());
        new31.setOnAction(e -> newDocument(OasVersion.V3_1));
        Menu newMenu = new Menu("New", null, new30, new31);

        MenuItem open = new MenuItem("Open...");
        open.setOnAction(e -> openFile());

        saveItem = new MenuItem("Save");
        saveItem.setOnAction(e -> save());

        saveAsItem = new MenuItem("Save As...");
        saveAsItem.setOnAction(e -> saveAs());

        MenuItem exit = new MenuItem("Exit");
        exit.setOnAction(e -> Platform.exit());

        Menu fileMenu = new Menu("File", null, newMenu, open, saveItem, saveAsItem, exit);

        ToggleGroup themeGroup = new ToggleGroup();
        RadioMenuItem lightThemeItem = new RadioMenuItem("Light");
        lightThemeItem.setToggleGroup(themeGroup);
        lightThemeItem.setSelected(!darkTheme);
        lightThemeItem.setOnAction(e -> setTheme(false));

        RadioMenuItem darkThemeItem = new RadioMenuItem("Dark");
        darkThemeItem.setToggleGroup(themeGroup);
        darkThemeItem.setSelected(darkTheme);
        darkThemeItem.setOnAction(e -> setTheme(true));

        Menu viewMenu = new Menu("View", null, lightThemeItem, darkThemeItem);

        convertMenu = new Menu("Convert to");
        Menu documentMenu = new Menu("Document", null, convertMenu);

        MenuItem about = new MenuItem("About OAS Buddy");
        about.setOnAction(e -> aboutAction.run());

        Menu helpMenu = new Menu("Help", null, about);

        return new MenuBar(fileMenu, documentMenu, viewMenu, helpMenu);
    }

    private void showAbout() {
        AboutDialog.show(stage, AppInfo.load(), this::browse);
    }

    /**
     * Hands a url to the desktop's own browser.
     *
     * <p>A desktop with no registered handler fails at {@code showDocument}. A dead About link is
     * not worth a stack trace in the user's face, so this logs and returns. Package-private so the
     * tests can exercise that with a failing {@link #setLinkOpener link opener}.
     */
    void browse(String url) {
        try {
            linkOpener.accept(url);
        } catch (RuntimeException e) {
            System.getLogger(MainApp.class.getName())
                    .log(System.Logger.Level.WARNING, "could not open " + url + " in a browser", e);
        }
    }

    private void setTheme(boolean dark) {
        Application.setUserAgentStylesheet(dark
                ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet());
        PREFS.put(THEME_PREF_KEY, dark ? THEME_DARK : THEME_LIGHT);
    }

    private void openFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open OpenAPI document");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("OpenAPI files", "*.yaml", "*.yml", "*.json"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        var file = chooser.showOpenDialog(stage);
        if (file != null) {
            open(file.toPath());
        }
    }

    /** Package-private so tests can open a file without the native chooser, which TestFX cannot drive. */
    void open(Path file) {
        try {
            document = DocumentReader.read(file);
            currentFile = file;
            saveAsSuggestion = null;
            refreshOutline();
            validationList.getItems().clear();
            updateWindowState();
        } catch (Exception e) {
            showError("Could not open file", e.getMessage());
        }
    }

    /** A Swagger 2.0 document is import-only: it is never written, only converted. */
    private boolean isImportOnly() {
        return document.getVersion().orElse(null) == OasVersion.V2_0;
    }

    private void save() {
        if (isImportOnly()) {
            return;
        }
        if (currentFile == null) {
            saveAs();
            return;
        }
        writeToFile(currentFile);
    }

    /**
     * The filters are split per format, unlike Open's single combined one: the chosen file name is
     * what decides the format written (see {@code DocumentWriter.write(OasDocument, Path)}), so the
     * dialog has to offer the extensions apart rather than lump them together. The document's
     * current format is preselected, which is also what makes the platform append the right
     * extension to a name typed without one.
     */
    private void saveAs() {
        if (isImportOnly()) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save OpenAPI document");
        var yamlFilter = new FileChooser.ExtensionFilter("YAML files", "*.yaml", "*.yml");
        var jsonFilter = new FileChooser.ExtensionFilter("JSON files", "*.json");
        chooser.getExtensionFilters().addAll(
                yamlFilter, jsonFilter, new FileChooser.ExtensionFilter("All files", "*.*"));
        chooser.setSelectedExtensionFilter(
                document.getFormat() == DocumentFormat.JSON ? jsonFilter : yamlFilter);
        if (saveAsSuggestion != null) {
            chooser.setInitialDirectory(saveAsSuggestion.toAbsolutePath().getParent().toFile());
            chooser.setInitialFileName(saveAsSuggestion.getFileName().toString());
        }
        var file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }
        currentFile = file.toPath();
        saveAsSuggestion = null;
        writeToFile(currentFile);
    }

    private void writeToFile(Path file) {
        try {
            DocumentWriter.write(document, file);
            updateWindowState();
            runValidation();
        } catch (IOException | UnsupportedOperationException e) {
            showError("Could not save file", e.getMessage());
        }
    }

    /**
     * Both severities are shown, prefixed so they stay tellable apart at a glance; the fallback
     * text only appears when there are neither errors nor warnings, so it stays accurate now that a
     * warning-only document also gets messages here.
     */
    private void runValidation() {
        var result = validator.validate(document);
        List<String> messages = result.messages().stream().map(MainApp::describe).toList();
        validationList.getItems().setAll(messages.isEmpty() ? List.of("No validation errors.") : messages);
    }

    private static String describe(ValidationMessage message) {
        String prefix = message.severity() == ValidationSeverity.WARNING ? "Warning: " : "Error: ";
        return prefix + message.message();
    }

    private void showPane(Node pane) {
        centerHolder.setCenter(new ScrollPane(pane));
    }

    /** Package-private so tests can rebuild the outline after seeding the document. */
    void refreshOutline() {
        TreeItem<OutlineNode> root = new TreeItem<>(new OutlineNode(titleOrDefault(), null));
        root.setExpanded(true);
        if (isImportOnly()) {
            showImportOnly(root);
            return;
        }

        root.getChildren().add(new TreeItem<>(
                new OutlineNode("Info", () -> InfoPane.build(document.getInfo(), document.getExternalDocs(),
                        document.getVersion().orElse(OasVersion.V3_0)))));
        root.getChildren().add(new TreeItem<>(
                new OutlineNode("Servers", () -> ServersPane.build(document))));
        root.getChildren().add(new TreeItem<>(new OutlineNode(
                "Tags", () -> TagsPane.build(document, this::refreshOutline, this::removeTag))));

        TreeItem<OutlineNode> pathsItem = new TreeItem<>(new OutlineNode(
                PATHS, () -> PathsPane.build(document, this::refreshOutline, this::removePath)));
        pathsItem.setExpanded(true);
        for (String path : document.getPaths().pathNames()) {
            PathItem pathItem = document.getPaths().getPathItem(path);
            TreeItem<OutlineNode> pathNode = new TreeItem<>(new OutlineNode(path,
                    () -> PathItemPane.build(document, path, this::refreshOutline, this::renamePath,
                            replaceConfirmation, () -> removePath(path), method -> removeOperation(path, method))));
            // a path whose value is not an object has no operations to list; its node still shows
            var operations = pathItem == null ? java.util.Map.<HttpMethod, Operation>of() : pathItem.getOperations();
            for (var entry : operations.entrySet()) {
                HttpMethod method = entry.getKey();
                Operation operation = entry.getValue();
                Supplier<List<String>> schemaNames = () -> document.getComponents().getSchemas().names();
                pathNode.getChildren().add(new TreeItem<>(new OutlineNode(method.name(),
                        () -> OperationPane.build(operation, schemaNames,
                                TagCatalog.of(document), SecuritySchemeCatalog.of(document),
                                ComponentCatalog.of(document), replaceConfirmation,
                                () -> removeOperation(path, method)))));
            }
            pathsItem.getChildren().add(pathNode);
        }
        root.getChildren().add(pathsItem);

        root.getChildren().add(section(SCHEMAS,
                () -> SchemasPane.build(document, this::refreshOutline, this::removeSchema),
                document.getComponents().getSchemas().names(),
                name -> SchemaPane.build(document, name, this::renameSchema, this::removeSchema)));

        Supplier<List<String>> schemaNames = () -> document.getComponents().getSchemas().names();
        root.getChildren().addAll(List.of(
                componentSection(ComponentKinds.RESPONSES, (name, remove) ->
                        ResponsePane.build(document, name, schemaNames, replaceConfirmation, remove)),
                componentSection(ComponentKinds.PARAMETERS, (name, remove) ->
                        ParameterPane.build(document, name, replaceConfirmation, remove)),
                componentSection(ComponentKinds.EXAMPLES, (name, remove) ->
                        ExamplePane.build(document, name, remove)),
                componentSection(ComponentKinds.HEADERS, (name, remove) ->
                        HeaderPane.build(document, name, remove)),
                componentSection(ComponentKinds.LINKS, (name, remove) ->
                        LinkPane.build(document, name, remove)),
                componentSection(ComponentKinds.CALLBACKS, (name, remove) ->
                        CallbackPane.build(document, name, schemaNames, replaceConfirmation, remove))));

        root.getChildren().add(section("Security Schemes",
                () -> SecuritySchemesPane.build(document, this::refreshOutline, this::removeSecurityScheme),
                document.getComponents().getSecuritySchemes().names(),
                name -> SecuritySchemePane.build(document, name, confirmation, this::removeSecurityScheme)));

        root.getChildren().add(new TreeItem<>(new OutlineNode(
                "Security", () -> SecurityPane.build(document, this::refreshOutline))));

        outlineView.setRoot(root);
        outlineView.setShowRoot(true);
    }

    /**
     * A 2.0 document gets one outline node and no editor panes: the 3.x panes would offer what a 2.0
     * file cannot hold. The node is selected, so its note shows straight away.
     */
    private void showImportOnly(TreeItem<OutlineNode> root) {
        TreeItem<OutlineNode> note = new TreeItem<>(new OutlineNode(
                OasVersion.V2_0.label() + " (read-only)", () -> ImportOnlyPane.build(document, this::convert)));
        root.getChildren().add(note);
        outlineView.setRoot(root);
        outlineView.setShowRoot(true);
        outlineView.getSelectionModel().select(note);
    }

    /** An expanded outline section: its list pane, then one child per entry. */
    private TreeItem<OutlineNode> section(String label, Supplier<Node> list, List<String> names,
                                          Function<String, Node> item) {
        TreeItem<OutlineNode> section = new TreeItem<>(new OutlineNode(label, list));
        section.setExpanded(true);
        for (String name : names) {
            section.getChildren().add(new TreeItem<>(new OutlineNode(name, () -> item.apply(name))));
        }
        return section;
    }

    /** A reusable-component section; {@code item} builds an entry's editor, given the request to remove it. */
    private <T> TreeItem<OutlineNode> componentSection(ComponentKind<T> kind,
                                                       BiFunction<String, Consumer<String>, Node> item) {
        Consumer<String> remove = name -> ComponentRemoval.remove(kind, document, name, confirmation,
                () -> refreshAndSelect(kind.plural()));
        return section(kind.plural(),
                () -> ComponentListPane.build(kind, document, this::refreshOutline, remove),
                kind.access().apply(document.getComponents()).names(),
                name -> item.apply(name, remove));
    }

    private void removeSchema(String schemaName) {
        SchemaRemoval.remove(document, schemaName, confirmation,
                () -> refreshAndSelect(SCHEMAS));
    }

    private void removeTag(String tagName) {
        TagRemoval.remove(document, tagName, confirmation,
                () -> refreshAndSelect("Tags"));
    }

    /** Selects the renamed schema afterwards, so the editor stays on what the user was editing. */
    private boolean renameSchema(String from, String to) {
        return SchemaRename.rename(document, from, to, renameConfirmation,
                () -> refreshAndSelect(SCHEMAS, to));
    }

    /** Selects the renamed path afterwards, so the editor stays on what the user was editing. */
    private boolean renamePath(String from, String to) {
        return PathRename.rename(document, from, to, () -> refreshAndSelect(PATHS, to));
    }

    private void removeSecurityScheme(String schemeName) {
        SecuritySchemeRemoval.remove(document, schemeName, confirmation,
                () -> refreshAndSelect("Security Schemes"));
    }

    private void removePath(String path) {
        PathRemoval.removePath(document, path, confirmation,
                () -> refreshAndSelect(PATHS));
    }

    private void removeOperation(String path, HttpMethod method) {
        PathRemoval.removeOperation(document, path, method, confirmation,
                () -> refreshAndSelect(PATHS, path));
    }

    /**
     * Rebuilds the outline after a removal and selects what is left, so the editor is never left
     * showing something that no longer exists.
     */
    private void refreshAndSelect(String... labels) {
        refreshOutline();
        TreeItem<OutlineNode> item = outlineView.getRoot();
        for (String label : labels) {
            item = item.getChildren().stream()
                    .filter(child -> label.equals(child.getValue().label()))
                    .findFirst()
                    .orElse(null);
            if (item == null) {
                return;
            }
        }
        outlineView.getSelectionModel().select(item);
    }

    /**
     * Replaces the document with a new, empty one of {@code version}. Like Open, it does not ask
     * first: the editor does not track unsaved changes yet. Package-private for the tests.
     */
    void newDocument(OasVersion version) {
        document = OasDocument.newDocument(DocumentFormat.YAML, version);
        currentFile = null;
        saveAsSuggestion = null;
        refreshOutline();
        validationList.getItems().clear();
        updateWindowState();
    }

    /**
     * Converts the document to {@code target}. A conversion that loses or moves nothing happens
     * at once; otherwise every conflict is listed first, and declining leaves the document as it
     * was. The result belongs to no file, so the next Save asks where to put it.
     * Package-private for the tests.
     */
    void convert(OasVersion target) {
        // Only targets the converter reaches are ever offered, so this does not throw.
        ConversionResult result = converter.convert(document, target);
        if (!result.isLossless() && !conversionConfirmation.confirm(
                ConversionDialog.question(target, result.conflicts().size()),
                ConversionDialog.describe(result.conflicts()))) {
            return;
        }
        if (currentFile != null) {
            saveAsSuggestion = suggestedFile(currentFile, target);
        }
        document = result.converted();
        currentFile = null;
        refreshOutline();
        updateWindowState();
        runValidation();
    }

    /** {@code legacy.yaml} converted to 3.1 is proposed as {@code legacy-3.1.yaml}, beside it. */
    static Path suggestedFile(Path original, OasVersion target) {
        String name = original.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        String version = target.label().substring(target.label().lastIndexOf(' ') + 1);
        return original.resolveSibling(base + "-" + version + extension);
    }

    /**
     * The title, whether the document may be saved — never an import-only one — and the versions it
     * can be converted to.
     */
    private void updateWindowState() {
        stage.setTitle(windowTitle(currentFile, document.getVersion()));
        saveItem.setDisable(isImportOnly());
        saveAsItem.setDisable(isImportOnly());
        convertMenu.getItems().clear();
        document.getVersion().map(converter::targets).orElse(Set.of()).forEach(target -> {
            MenuItem item = new MenuItem(target.label());
            item.setOnAction(e -> convert(target));
            convertMenu.getItems().add(item);
        });
        convertMenu.setDisable(convertMenu.getItems().isEmpty());
    }

    /** {@code api.yaml (OpenAPI 3.1) — OAS Buddy}; a document not yet saved is "Untitled". */
    static String windowTitle(Path file, Optional<OasVersion> version) {
        String name = file == null ? "Untitled" : file.getFileName().toString();
        String declared = version.map(OasVersion::label).orElse("unknown OpenAPI version");
        return name + " (" + declared + ") — " + APP_NAME;
    }

    private String titleOrDefault() {
        String title = document.getInfo().getTitle();
        return title == null || title.isBlank() ? APP_NAME : title;
    }

    private void showError(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(APP_NAME);
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }

    OasDocument getDocument() {
        return document;
    }

    void setConfirmation(RemovalConfirmation confirmation) {
        this.confirmation = confirmation;
    }

    void setRenameConfirmation(RemovalConfirmation renameConfirmation) {
        this.renameConfirmation = renameConfirmation;
    }

    void setReplaceConfirmation(RemovalConfirmation replaceConfirmation) {
        this.replaceConfirmation = replaceConfirmation;
    }

    void setConversionConfirmation(RemovalConfirmation conversionConfirmation) {
        this.conversionConfirmation = conversionConfirmation;
    }

    void setAboutAction(Runnable aboutAction) {
        this.aboutAction = aboutAction;
    }

    void setLinkOpener(Consumer<String> linkOpener) {
        this.linkOpener = linkOpener;
    }

    private record OutlineNode(String label, Supplier<Node> paneSupplier) {
        @Override
        public String toString() {
            return label;
        }
    }
}
