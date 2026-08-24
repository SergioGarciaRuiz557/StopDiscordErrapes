package com.discordaudioguard.ui;

import com.discordaudioguard.audio.engine.AudioEngineState;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/** Compact bar presenting operational state, message, and color semantics. */
public final class StatusView extends HBox {
    /** Localized engine-state label. */
    private final Label state = new Label("Detenido");
    /** Contextual explanation of the transition or latest operation. */
    private final Label message = new Label("Listo");

    /** Builds a section-styled row with its initial values. */
    public StatusView() { setSpacing(12); getStyleClass().add("section"); getChildren().addAll(new Label("Estado:"), state, message); }

    /**
     * Updates text and CSS classes, clearing previous styles before applying the new one.
     *
     * @param value operational state that determines label and color
     * @param text readable detail associated with the transition
     */
    public void update(AudioEngineState value, String text) {
        state.setText(value.displayName()); message.setText(text);
        state.getStyleClass().removeAll("status-running", "status-error");
        if (value == AudioEngineState.RUNNING) state.getStyleClass().add("status-running");
        if (value == AudioEngineState.ERROR) state.getStyleClass().add("status-error");
    }
}
