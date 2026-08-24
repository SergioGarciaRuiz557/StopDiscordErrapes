package com.discordaudioguard.ui;

import com.discordaudioguard.audio.device.AudioDeviceDescriptor;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;

/**
 * JavaFX section for choosing capture and playback endpoints.
 *
 * <p>The view neither enumerates hardware nor starts audio; it only exposes controls
 * and renders the descriptors it receives. On refresh, it first tries to preserve the
 * visible selection and otherwise restores persisted identifiers.</p>
 */
public final class DeviceSelectionView extends VBox {
    /** Selector for capture-capable devices. */
    private final ComboBox<AudioDeviceDescriptor> input = new ComboBox<>();
    /** Selector for playback-capable devices. */
    private final ComboBox<AudioDeviceDescriptor> output = new ComboBox<>();
    /** Explicit action for querying system mixers again. */
    private final Button refresh = new Button("Actualizar dispositivos");

    /** Builds the section, labels, and width-responsive grid. */
    public DeviceSelectionView() {
        getStyleClass().add("section"); setSpacing(10);
        Label title = new Label("Dispositivos"); title.getStyleClass().add("section-title");
        input.setMaxWidth(Double.MAX_VALUE); output.setMaxWidth(Double.MAX_VALUE);
        GridPane grid = new GridPane(); grid.setHgap(10); grid.setVgap(8);
        ColumnConstraints labels = new ColumnConstraints(); labels.setMinWidth(70);
        ColumnConstraints fields = new ColumnConstraints(); fields.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labels, fields);
        grid.addRow(0, new Label("Entrada"), input);
        grid.addRow(1, new Label("Salida"), output);
        HBox footer = new HBox(12, refresh, new Label("48.000 Hz · PCM 16-bit · estéreo · bloque de 128 frames"));
        getChildren().addAll(title, grid, footer);
    }

    /**
     * Exposes the capture control to the view controller.
     *
     * @return input selector
     */
    public ComboBox<AudioDeviceDescriptor> inputSelector() { return input; }

    /**
     * Exposes the playback control to the view controller.
     *
     * @return output selector
     */
    public ComboBox<AudioDeviceDescriptor> outputSelector() { return output; }

    /**
     * Exposes the refresh action to the view controller.
     *
     * @return refresh button
     */
    public Button refreshButton() { return refresh; }

    /**
     * Replaces both inventories and restores selections when they still exist.
     *
     * @param inputs devices available for capture
     * @param outputs devices available for playback
     * @param selectedInputId remembered input when the control had no selection
     * @param selectedOutputId remembered output when the control had no selection
     */
    public void setDevices(List<AudioDeviceDescriptor> inputs, List<AudioDeviceDescriptor> outputs,
                           String selectedInputId, String selectedOutputId) {
        String currentInput = input.getValue() == null ? selectedInputId : input.getValue().id();
        String currentOutput = output.getValue() == null ? selectedOutputId : output.getValue().id();
        input.getItems().setAll(inputs); output.getItems().setAll(outputs);
        select(input, currentInput); select(output, currentOutput);
    }

    /** Selects by persistent identity without assuming a list position. */
    private static void select(ComboBox<AudioDeviceDescriptor> combo, String id) {
        if (id == null) return;
        combo.getItems().stream().filter(device -> id.equals(device.id())).findFirst().ifPresent(combo::setValue);
    }
}
