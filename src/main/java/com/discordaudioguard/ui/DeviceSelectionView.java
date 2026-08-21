package com.discordaudioguard.ui;

import com.discordaudioguard.audio.device.AudioDeviceDescriptor;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;

public final class DeviceSelectionView extends VBox {
    private final ComboBox<AudioDeviceDescriptor> input = new ComboBox<>();
    private final ComboBox<AudioDeviceDescriptor> output = new ComboBox<>();
    private final Button refresh = new Button("Actualizar dispositivos");

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

    public ComboBox<AudioDeviceDescriptor> inputSelector() { return input; }
    public ComboBox<AudioDeviceDescriptor> outputSelector() { return output; }
    public Button refreshButton() { return refresh; }

    public void setDevices(List<AudioDeviceDescriptor> inputs, List<AudioDeviceDescriptor> outputs,
                           String selectedInputId, String selectedOutputId) {
        String currentInput = input.getValue() == null ? selectedInputId : input.getValue().id();
        String currentOutput = output.getValue() == null ? selectedOutputId : output.getValue().id();
        input.getItems().setAll(inputs); output.getItems().setAll(outputs);
        select(input, currentInput); select(output, currentOutput);
    }

    private static void select(ComboBox<AudioDeviceDescriptor> combo, String id) {
        if (id == null) return;
        combo.getItems().stream().filter(device -> id.equals(device.id())).findFirst().ifPresent(combo::setValue);
    }
}
