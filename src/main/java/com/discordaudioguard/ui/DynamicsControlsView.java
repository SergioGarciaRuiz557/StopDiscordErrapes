package com.discordaudioguard.ui;

import com.discordaudioguard.audio.processing.ProcessingParameters;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.function.Consumer;

/**
 * Visual editor for all compression and protection parameters.
 *
 * <p>Every user change reconstructs a complete immutable {@link ProcessingParameters}.
 * {@code loading} distinguishes a programmatic update from an edit: restoring or
 * loading values moves many controls, but the consumer does not receive a sequence of
 * inconsistent partial states.</p>
 */
public final class DynamicsControlsView extends VBox {
    /** Switches for both DSP stages and the global direct path. */
    private final CheckBox compressorEnabled = new CheckBox("Compresor activado");
    private final CheckBox limiterEnabled = new CheckBox("Limitador activado");
    private final CheckBox bypass = new CheckBox("Bypass completo");
    /** Main compressor threshold, ratio, and attack controls. */
    private final Slider threshold = slider(-60, 0), ratio = slider(1, 20), attack = slider(0.1, 100);
    /** Compressor release, knee, and makeup-gain controls. */
    private final Slider compressorRelease = slider(20, 2000), knee = slider(0, 24), makeup = slider(-12, 12);
    /** Limiter ceiling, lookahead, and release controls. */
    private final Slider ceiling = slider(-20, -0.1), lookahead = slider(0, 20), limiterRelease = slider(20, 1000);
    /** Final attenuation shared by dry and wet paths. */
    private final Slider maximumOutput = slider(-30, 0);
    /** Replaceable consumer that safely discards events by default. */
    private Consumer<ProcessingParameters> listener = ignored -> {};
    /** Guard that suppresses events while multiple controls are loaded. */
    private boolean loading;

    /**
     * Builds and populates the panel with a consistent configuration.
     *
     * @param initial parameters to display initially
     */
    public DynamicsControlsView(ProcessingParameters initial) {
        getStyleClass().add("section"); setSpacing(10);
        Label title = new Label("Dinámica y protección"); title.getStyleClass().add("section-title");
        HBox switches = new HBox(18, compressorEnabled, limiterEnabled, bypass);
        GridPane controls = new GridPane(); controls.setHgap(10); controls.setVgap(7);
        ColumnConstraints c1 = new ColumnConstraints(); c1.setMinWidth(145);
        ColumnConstraints c2 = new ColumnConstraints(); c2.setHgrow(Priority.ALWAYS); c2.setMinWidth(170);
        ColumnConstraints c3 = new ColumnConstraints(); c3.setMinWidth(72);
        ColumnConstraints c4 = new ColumnConstraints(); c4.setHgrow(Priority.ALWAYS); c4.setMinWidth(170);
        controls.getColumnConstraints().addAll(c1, c2, c3, c4);
        addControl(controls, 0, 0, "Threshold (dBFS)", threshold);
        addControl(controls, 0, 2, "Ceiling (dBFS)", ceiling);
        addControl(controls, 1, 0, "Ratio", ratio);
        addControl(controls, 1, 2, "Lookahead (ms)", lookahead);
        addControl(controls, 2, 0, "Attack (ms)", attack);
        addControl(controls, 2, 2, "Release limitador (ms)", limiterRelease);
        addControl(controls, 3, 0, "Release compresor (ms)", compressorRelease);
        addControl(controls, 3, 2, "Ganancia máxima (dB)", maximumOutput);
        addControl(controls, 4, 0, "Knee (dB)", knee);
        addControl(controls, 5, 0, "Makeup (dB)", makeup);
        getChildren().addAll(title, switches, controls);
        installListeners();
        setParameters(initial);
    }

    /** Connects every editable property to one publication mechanism. */
    private void installListeners() {
        ChangeListener<Object> changed = (observable, oldValue, newValue) -> publish();
        compressorEnabled.selectedProperty().addListener(changed); limiterEnabled.selectedProperty().addListener(changed);
        bypass.selectedProperty().addListener(changed);
        for (Slider control : new Slider[]{threshold, ratio, attack, compressorRelease, knee, makeup,
                ceiling, lookahead, limiterRelease, maximumOutput}) control.valueProperty().addListener(changed);
    }

    /** Publishes a snapshot only when the change originates from the user. */
    private void publish() { if (!loading) listener.accept(parameters()); }

    /**
     * Installs the destination for editing events.
     *
     * @param value consumer receiving complete parameters after every edit
     */
    public void setOnParametersChanged(Consumer<ProcessingParameters> value) { listener = value; }

    /**
     * Reconstructs the immutable model from visible values.
     *
     * @return complete parameters already validated by their constructors
     */
    public ProcessingParameters parameters() {
        return new ProcessingParameters(
                new ProcessingParameters.CompressorSettings(compressorEnabled.isSelected(), threshold.getValue(),
                        ratio.getValue(), attack.getValue(), compressorRelease.getValue(), knee.getValue(), makeup.getValue()),
                new ProcessingParameters.LimiterSettings(limiterEnabled.isSelected(), ceiling.getValue(),
                        lookahead.getValue(), limiterRelease.getValue()), bypass.isSelected(), maximumOutput.getValue());
    }

    /**
     * Loads every control without firing the listener for intermediate states.
     *
     * @param value parameters to display
     */
    public void setParameters(ProcessingParameters value) {
        loading = true;
        compressorEnabled.setSelected(value.compressor().enabled()); threshold.setValue(value.compressor().thresholdDb());
        ratio.setValue(value.compressor().ratio()); attack.setValue(value.compressor().attackMs());
        compressorRelease.setValue(value.compressor().releaseMs()); knee.setValue(value.compressor().kneeDb());
        makeup.setValue(value.compressor().makeupGainDb()); limiterEnabled.setSelected(value.limiter().enabled());
        ceiling.setValue(value.limiter().ceilingDb()); lookahead.setValue(value.limiter().lookaheadMs());
        limiterRelease.setValue(value.limiter().releaseMs()); bypass.setSelected(value.bypass());
        maximumOutput.setValue(value.maximumOutputGainDb());
        loading = false;
    }

    /** Creates an expandable slider with bounds matching the validated model. */
    private static Slider slider(double min, double max) { Slider value = new Slider(min, max, min); value.setMaxWidth(Double.MAX_VALUE); return value; }

    /** Adds a label, slider, and bound numeric reading to a grid. */
    private static void addControl(GridPane grid, int row, int column, String text, Slider slider) {
        Label label = new Label(text); Label value = new Label(); value.setMinWidth(58);
        value.textProperty().bind(slider.valueProperty().asString("%.1f"));
        HBox field = new HBox(8, slider, value); HBox.setHgrow(slider, Priority.ALWAYS);
        grid.add(label, column, row); grid.add(field, column + 1, row);
    }
}
