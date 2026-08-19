package com.discordaudioguard.ui;

import com.discordaudioguard.application.*;
import com.discordaudioguard.audio.engine.AudioEngineState;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.control.Alert;

public final class MainViewController implements AutoCloseable {
    private static final long UI_INTERVAL_NANOS = 40_000_000L;
    private final ApplicationController controller;
    private final MainView view;
    private final AnimationTimer meterTimer;

    public MainViewController(ApplicationController controller, MainView view) {
        this.controller = controller; this.view = view;
        view.devices().refreshButton().setOnAction(event -> refreshDevices());
        view.startButton().setOnAction(event -> start());
        view.stopButton().setOnAction(event -> controller.stop());
        view.resetButton().setOnAction(event -> { view.dynamics().setParameters(com.discordaudioguard.audio.processing.ProcessingParameters.DEFAULT); controller.resetParameters(); });
        view.dynamics().setOnParametersChanged(controller::updateParameters);
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

    private void refreshDevices() {
        try { controller.refreshDevices(); applyState(controller.state()); }
        catch (RuntimeException error) { showError("No se pudieron enumerar los dispositivos", error); }
    }

    private void start() {
        try { controller.start(view.devices().inputSelector().getValue(), view.devices().outputSelector().getValue()); }
        catch (RuntimeException error) { showError("No se pudo iniciar el procesamiento", error); }
    }

    private void applyState(ApplicationState state) {
        view.status().update(state.engineState(), state.statusMessage());
        view.devices().setDevices(state.inputDevices(), state.outputDevices(),
                controller.configuration().inputDeviceId(), controller.configuration().outputDeviceId());
        boolean active = state.engineState() != AudioEngineState.STOPPED && state.engineState() != AudioEngineState.ERROR;
        view.startButton().setDisable(active); view.stopButton().setDisable(!active);
        view.devices().inputSelector().setDisable(active); view.devices().outputSelector().setDisable(active);
    }

    private static void showError(String heading, RuntimeException error) {
        Alert alert = new Alert(Alert.AlertType.ERROR); alert.setTitle("Discord Audio Guard");
        alert.setHeaderText(heading); alert.setContentText(error.getMessage()); alert.showAndWait();
    }

    @Override public void close() { meterTimer.stop(); }
}
