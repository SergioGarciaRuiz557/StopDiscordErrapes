package com.discordaudioguard.ui;

import com.discordaudioguard.application.*;
import com.discordaudioguard.audio.engine.AudioEngineState;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.control.Alert;

/**
 * Adapter connecting JavaFX events to application use cases.
 *
 * <p>It registers button actions and DSP changes, transfers states that may originate
 * in audio to the JavaFX thread, and updates meters at a limited cadence. The controller
 * does not own the engine; closing it only stops its visual timer.</p>
 */
public final class MainViewController implements AutoCloseable {
    /** 40 ms interval, equivalent to roughly 25 refreshes per second at most. */
    private static final long UI_INTERVAL_NANOS = 40_000_000L;
    /** Facade for application operations and state. */
    private final ApplicationController controller;
    /** Visual tree to which events and state are applied. */
    private final MainView view;
    /** JavaFX pulse used to sample telemetry without flooding the UI. */
    private final AnimationTimer meterTimer;

    /**
     * Connects view and application, starts metric refresh, and performs the first scan.
     *
     * @param controller already-initialized application controller
     * @param view already-built main view
     */
    public MainViewController(ApplicationController controller, MainView view) {
        this.controller = controller; this.view = view;
        view.devices().refreshButton().setOnAction(event -> refreshDevices());
        view.startButton().setOnAction(event -> start());
        view.stopButton().setOnAction(event -> controller.stop());
        view.resetButton().setOnAction(event -> { view.dynamics().setParameters(com.discordaudioguard.audio.processing.ProcessingParameters.DEFAULT); controller.resetParameters(); });
        view.dynamics().setOnParametersChanged(controller::updateParameters);
        // The engine publishes in the background; all node access must return to JavaFX.
        controller.addStateListener(state -> Platform.runLater(() -> applyState(state)));
        meterTimer = new AnimationTimer() {
            private long lastUpdate;
            @Override public void handle(long now) {
                if (now - lastUpdate >= UI_INTERVAL_NANOS) { view.meters().update(controller.metricsSnapshot()); lastUpdate = now; }
            }
        };
        meterTimer.start();
        refreshDevices();
    }

    /** Requests a fresh inventory and turns driver failures into a dialog. */
    private void refreshDevices() {
        try { controller.refreshDevices(); applyState(controller.state()); }
        catch (RuntimeException error) { showError("No se pudieron enumerar los dispositivos", error); }
    }

    /** Reads visible selections and attempts startup, displaying actionable errors. */
    private void start() {
        try { controller.start(view.devices().inputSelector().getValue(), view.devices().outputSelector().getValue()); }
        catch (RuntimeException error) { showError("No se pudo iniciar el procesamiento", error); }
    }

    /**
     * Renders a snapshot and locks selectors while the engine owns devices.
     *
     * @param state complete state to display
     */
    private void applyState(ApplicationState state) {
        view.status().update(state.engineState(), state.statusMessage());
        view.devices().setDevices(state.inputDevices(), state.outputDevices(),
                controller.configuration().inputDeviceId(), controller.configuration().outputDeviceId());
        boolean active = state.engineState() != AudioEngineState.STOPPED && state.engineState() != AudioEngineState.ERROR;
        view.startButton().setDisable(active); view.stopButton().setDisable(!active);
        view.devices().inputSelector().setDisable(active); view.devices().outputSelector().setDisable(active);
    }

    /** Presents an operational exception in a readable modal dialog. */
    private static void showError(String heading, RuntimeException error) {
        Alert alert = new Alert(Alert.AlertType.ERROR); alert.setTitle("Discord Audio Guard");
        alert.setHeaderText(heading); alert.setContentText(error.getMessage()); alert.showAndWait();
    }

    /** Stops visual updates when the window is dismantled. */
    @Override public void close() { meterTimer.stop(); }
}
