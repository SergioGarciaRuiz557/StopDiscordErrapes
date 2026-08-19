package com.discordaudioguard.ui;

import com.discordaudioguard.audio.processing.ProcessingParameters;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public final class MainView extends BorderPane {
    private final DeviceSelectionView devices = new DeviceSelectionView();
    private final DynamicsControlsView dynamics;
    private final LevelMeterView meters = new LevelMeterView();
    private final StatusView status = new StatusView();
    private final Button start = new Button("Iniciar procesamiento");
    private final Button stop = new Button("Detener");
    private final Button reset = new Button("Restablecer valores");

    public MainView(ProcessingParameters initial) {
        dynamics = new DynamicsControlsView(initial);
        Label title = new Label("Discord Audio Guard"); title.getStyleClass().add("title");
        Label subtitle = new Label("Protección en tiempo real frente a picos de volumen en Discord");
        VBox header = new VBox(3, title, subtitle); header.setPadding(new Insets(16, 18, 10, 18));
        HBox actions = new HBox(10, start, stop, reset); stop.setDisable(true);
        VBox content = new VBox(12, devices, actions, dynamics, meters, status); content.setPadding(new Insets(8, 18, 18, 18));
        ScrollPane scroll = new ScrollPane(content); scroll.setFitToWidth(true); scroll.setPannable(true);
        setTop(header); setCenter(scroll);
    }

    public DeviceSelectionView devices() { return devices; }
    public DynamicsControlsView dynamics() { return dynamics; }
    public LevelMeterView meters() { return meters; }
    public StatusView status() { return status; }
    public Button startButton() { return start; }
    public Button stopButton() { return stop; }
    public Button resetButton() { return reset; }
}
