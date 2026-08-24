package com.discordaudioguard.ui;

import com.discordaudioguard.audio.engine.AudioMetrics;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * Level and diagnostic panel periodically updated from immutable metrics.
 *
 * <p>Peaks are visually mapped from -60..0 dBFS to 0..1. Reduction uses 0..30 dB and
 * displays a dedicated indicator while the limiter is active. The panel does not query
 * the engine; it receives snapshots to keep the UI separated.</p>
 */
public final class LevelMeterView extends VBox {
    /** Bars for peaks, total reduction, and input/output channels. */
    private final ProgressBar inputLeft = meter(), inputRight = meter(), outputLeft = meter(), outputRight = meter(), reduction = meter();
    /** Numeric readings for the four peaks. */
    private final Label inputLeftValue = new Label(), inputRightValue = new Label(), outputLeftValue = new Label(), outputRightValue = new Label();
    /** Reduction reading and conditional limiting indicator. */
    private final Label reductionValue = new Label(), limiting = new Label("LIMITANDO");
    /** Text summary of cost, buffer, synchronization, and latency. */
    private final Label diagnostics = new Label();

    /** Builds the meter grid and diagnostic footer. */
    public LevelMeterView() {
        getStyleClass().add("section"); setSpacing(8);
        Label title = new Label("Medidores"); title.getStyleClass().add("section-title");
        GridPane grid = new GridPane(); grid.setHgap(10); grid.setVgap(7);
        ColumnConstraints name = new ColumnConstraints(); name.setMinWidth(90);
        ColumnConstraints bar = new ColumnConstraints(); bar.setHgrow(Priority.ALWAYS);
        ColumnConstraints number = new ColumnConstraints(); number.setMinWidth(74);
        grid.getColumnConstraints().addAll(name, bar, number);
        row(grid, 0, "Entrada L", inputLeft, inputLeftValue); row(grid, 1, "Entrada R", inputRight, inputRightValue);
        row(grid, 2, "Salida L", outputLeft, outputLeftValue); row(grid, 3, "Salida R", outputRight, outputRightValue);
        row(grid, 4, "Reducción", reduction, reductionValue);
        limiting.getStyleClass().add("limiting"); limiting.setVisible(false);
        HBox footer = new HBox(12, limiting, diagnostics);
        getChildren().addAll(title, grid, footer);
    }

    /**
     * Renders a complete snapshot; must be invoked on the JavaFX thread.
     *
     * @param value most recent engine metrics
     */
    public void update(AudioMetrics.Snapshot value) {
        setDb(inputLeft, inputLeftValue, value.inputPeakLeftDb()); setDb(inputRight, inputRightValue, value.inputPeakRightDb());
        setDb(outputLeft, outputLeftValue, value.outputPeakLeftDb()); setDb(outputRight, outputRightValue, value.outputPeakRightDb());
        reduction.setProgress(Math.min(1.0, value.totalReductionDb() / 30.0));
        reductionValue.setText(String.format("%.1f dB", value.totalReductionDb()));
        limiting.setVisible(value.limiterReductionDb() > 0.1);
        diagnostics.setText(String.format("Bloques: %,d · CPU DSP: %.1f%% · búfer: %.1f ms · sincronía: %+.0f ppm · latencia: %.1f ms",
                value.blocksProcessed(), value.processingBudgetPercent(), value.adaptiveBufferMillis(),
                value.playbackRateCorrectionPpm(), value.estimatedLatencyMillis()));
    }

    /** Creates an initially empty bar that uses all available width. */
    private static ProgressBar meter() { ProgressBar bar = new ProgressBar(0); bar.setMaxWidth(Double.MAX_VALUE); return bar; }

    /** Adds a consistent row containing a name, bar, and numeric reading. */
    private static void row(GridPane grid, int row, String name, ProgressBar bar, Label value) {
        grid.addRow(row, new Label(name), bar, value);
    }
    /** Converts a dBFS level into normalized progress and text floored at -60 dBFS. */
    private static void setDb(ProgressBar bar, Label label, double db) {
        bar.setProgress(Math.max(0.0, Math.min(1.0, (db + 60.0) / 60.0)));
        label.setText(String.format("%.1f dBFS", Math.max(-60, db)));
    }
}
