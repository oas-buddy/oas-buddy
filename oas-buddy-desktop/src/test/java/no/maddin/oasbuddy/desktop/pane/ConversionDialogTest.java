package no.maddin.oasbuddy.desktop.pane;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import no.maddin.oasbuddy.core.convert.Conflict;
import no.maddin.oasbuddy.core.document.OasVersion;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The lossy-conversion question: every conflict listed, one per row, and an explicit "Convert anyway". */
class ConversionDialogTest extends ApplicationTest {

    private static final List<Conflict> CONFLICTS = List.of(
            new Conflict("webhooks", "webhooks does not exist in OAS 3.0", "kept as x-oasbuddy-webhooks"),
            new Conflict("info → summary", "info.summary does not exist in OAS 3.0",
                    "kept as x-oasbuddy-summary; fold it into the description"));

    private Stage stage;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        stage.show();
    }

    @Test
    void eachConflictIsOneLineLocationFirst() {
        assertEquals("""
                webhooks: webhooks does not exist in OAS 3.0 — kept as x-oasbuddy-webhooks
                info → summary: info.summary does not exist in OAS 3.0 — kept as x-oasbuddy-summary; \
                fold it into the description""", ConversionDialog.describe(CONFLICTS));
    }

    @Test
    void theQuestionNamesTheTargetAndCountsTheConflicts() {
        assertEquals("Convert to OpenAPI 3.0? 2 things cannot be carried over as they are.",
                ConversionDialog.question(OasVersion.V3_0, 2));
        assertEquals("Convert to OpenAPI 3.1? 1 thing cannot be carried over as they are.",
                ConversionDialog.question(OasVersion.V3_1, 1));
    }

    @Test
    void theContentListsOneRowPerConflict() {
        AtomicReference<Node> content = new AtomicReference<>();
        interact(() -> {
            content.set(ConversionDialog.content(ConversionDialog.describe(CONFLICTS)));
            stage.setScene(new Scene(new StackPane(content.get()), 600, 300));
        });

        ListView<?> list = (ListView<?>) content.get();
        assertEquals(ConversionDialog.LIST_ID, list.getId());
        assertEquals(2, list.getItems().size());
        List<String> rows = lookup(".list-cell").queryAll().stream()
                .map(cell -> ((ListCell<?>) cell).getText())
                .filter(Objects::nonNull)
                .toList();
        assertEquals(ConversionDialog.describe(CONFLICTS).lines().toList(), rows);
        assertTrue(rows.stream().allMatch(row -> !row.isBlank()));
    }

    @Test
    void theDialogOffersConvertAnywayOrCancel() {
        AtomicReference<Alert> alert = new AtomicReference<>();
        interact(() -> alert.set(ConversionDialog.alert(
                ConversionDialog.question(OasVersion.V3_0, 2), ConversionDialog.describe(CONFLICTS))));

        assertEquals(List.of(ConversionDialog.ACTION, ButtonType.CANCEL.getText()),
                alert.get().getButtonTypes().stream().map(ButtonType::getText).toList());
        assertEquals("Convert to OpenAPI 3.0? 2 things cannot be carried over as they are.",
                alert.get().getHeaderText());
        assertEquals(ConversionDialog.LIST_ID, alert.get().getDialogPane().getContent().getId());
    }
}
