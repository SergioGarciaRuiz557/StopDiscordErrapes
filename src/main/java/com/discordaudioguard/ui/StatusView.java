package com.discordaudioguard.ui;

import com.discordaudioguard.audio.engine.AudioEngineState;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

public final class StatusView extends HBox {
    private final Label state = new Label("Detenido");
    private final Label message = new Label("Listo");
    public StatusView() { setSpacing(12); getStyleClass().add("section"); getChildren().addAll(new Label("Estado:"), state, message); }
    public void update(AudioEngineState value, String text) {
        state.setText(value.displayName()); message.setText(text);
        state.getStyleClass().removeAll("status-running", "status-error");
        if (value == AudioEngineState.RUNNING) state.getStyleClass().add("status-running");
        if (value == AudioEngineState.ERROR) state.getStyleClass().add("status-error");
    }
}
