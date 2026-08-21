package com.discordaudioguard.ui;

import com.discordaudioguard.audio.engine.AudioMetrics;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public final class LevelMeterView extends VBox {
    private final ProgressBar inputLeft = meter(), inputRight = meter(), outputLeft = meter(), outputRight = meter(), reduction = meter();
    private final Label inputLeftValue = new Label(), inputRightValue = new Label(), outputLeftValue = new Label(), outputRightValue = new Label();
    private final Label reductionValue = new Label(), limiting = new Label("LIMITANDO");
    private final Label diagnostics = new Label();

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

    private static ProgressBar meter() { ProgressBar bar = new ProgressBar(0); bar.setMaxWidth(Double.MAX_VALUE); return bar; }
    private static void row(GridPane grid, int row, String name, ProgressBar bar, Label value) {
        grid.addRow(row, new Label(name), bar, value);
    }
    private static void setDb(ProgressBar bar, Label label, double db) {
        bar.setProgress(Math.max(0.0, Math.min(1.0, (db + 60.0) / 60.0)));
        label.setText(String.format("%.1f dBFS", Math.max(-60, db)));
    }
}
