package com.discordaudioguard.ui;

import com.discordaudioguard.audio.processing.ProcessingParameters;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * Root visual composition of the main window.
 *
 * <p>It arranges the header, device selection, actions, DSP controls, meters, and
 * status inside a scrollable area. It contains no application logic: subcomponents
 * and buttons are exposed so {@link MainViewController} can connect behavior.</p>
 */
public final class MainView extends BorderPane {
    /** Audio-route selection panel. */
    private final DeviceSelectionView devices = new DeviceSelectionView();
    /** Dynamics editor initialized with persisted configuration. */
    private final DynamicsControlsView dynamics;
    /** Real-time meters and diagnostics. */
    private final LevelMeterView meters = new LevelMeterView();
    /** Summary of the engine lifecycle. */
    private final StatusView status = new StatusView();
    /** Action that opens devices and starts the stream. */
    private final Button start = new Button("Iniciar procesamiento");
    /** Action that requests stopping the active stream. */
    private final Button stop = new Button("Detener");
    /** Action that restores recommended DSP parameters. */
    private final Button reset = new Button("Restablecer valores");

    /**
     * Builds the scene graph and configures resizing behavior.
     *
     * @param initial DSP parameters displayed when the window opens
     */
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

    /**
     * Returns the route-selection section.
     *
     * @return device section
     */
    public DeviceSelectionView devices() { return devices; }

    /**
     * Returns the protection controls.
     *
     * @return DSP parameter editor
     */
    public DynamicsControlsView dynamics() { return dynamics; }

    /**
     * Returns visual telemetry.
     *
     * @return meter section
     */
    public LevelMeterView meters() { return meters; }

    /**
     * Returns the status bar.
     *
     * @return status section
     */
    public StatusView status() { return status; }

    /**
     * Returns the start action.
     *
     * @return start button
     */
    public Button startButton() { return start; }

    /**
     * Returns the stop action.
     *
     * @return stop button
     */
    public Button stopButton() { return stop; }

    /**
     * Returns the reset action.
     *
     * @return defaults button
     */
    public Button resetButton() { return reset; }
}
