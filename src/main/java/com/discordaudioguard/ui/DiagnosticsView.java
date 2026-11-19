package com.discordaudioguard.ui;

import com.discordaudioguard.audio.engine.AudioDiagnostics;
import com.discordaudioguard.audio.engine.AudioEngineState;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.nio.file.Path;

/** Controls and sampled queue estimates; device polling never runs on the FX thread. */
public final class DiagnosticsView extends VBox {
    private final CheckBox enabled = new CheckBox("Guardar diagnóstico de latencia");
    private final Button mark = new Button("Marcar retraso");
    private final Button restartOutput = new Button("Reiniciar solo la salida");
    private final Button openFolder = new Button("Abrir registros");
    private final Label queues = new Label("Las mediciones aparecerán al iniciar el procesamiento.");
    private final Label status = new Label("Se guardará un archivo por cada inicio del procesamiento.");
    private final Label file = new Label();
    private Path currentFile;
    private int requestedMarker;

    public DiagnosticsView() {
        getStyleClass().add("section"); setSpacing(7);
        Label title = new Label("Diagnóstico de latencia"); title.getStyleClass().add("section-title");
        enabled.setSelected(true); mark.setDisable(true); restartOutput.setDisable(true);
        Label help = new Label("Si notas retraso, márcalo y pulsa «Reiniciar solo la salida». " +
                "Oirás un breve corte mientras la entrada sigue abierta. El registro guarda métricas, sin grabar conversaciones.");
        Label limits = new Label("La estimación incluye las colas visibles y el lookahead; " +
                "no mide la latencia completa hasta los auriculares.");
        for (Label label : new Label[]{help, limits, queues, status, file}) label.setWrapText(true);
        FlowPane actions = new FlowPane(12, 8, enabled, mark, restartOutput, openFolder);
        getChildren().addAll(title, actions, help, queues, limits, status, file);
    }

    public boolean isEnabled() { return enabled.isSelected(); }
    public Button markButton() { return mark; }
    public Button restartOutputButton() { return restartOutput; }
    public Button openFolderButton() { return openFolder; }
    public void markerRequested(int number) { requestedMarker = number; }

    public void update(AudioDiagnostics.Snapshot value, AudioEngineState state, boolean restartingOutput) {
        boolean active = state != AudioEngineState.STOPPED && state != AudioEngineState.ERROR;
        enabled.setDisable(active);
        restartOutput.setDisable(state != AudioEngineState.RUNNING || restartingOutput);
        if (!java.util.Objects.equals(currentFile, value.file())) {
            currentFile = value.file(); requestedMarker = 0;
        }
        boolean stale = value.recording() && value.sampleNanos() != 0 &&
                System.nanoTime() - value.sampleNanos() > 2_000_000_000L;
        mark.setDisable(state != AudioEngineState.RUNNING || !value.recording() || stale);
        if (value.file() == null) {
            queues.setText("Las mediciones aparecerán al iniciar con el diagnóstico activado.");
            status.setText(enabled.isSelected() ? "Se guardará un archivo por cada inicio del procesamiento." : value.status());
            file.setText("");
            return;
        }
        queues.setText("Entrada: " + millis(value.inputMillis()) + " · Interno: " + millis(value.adaptiveMillis()) +
                " · Salida: " + millis(value.outputMillis()) +
                "\nPendiente visible + lookahead: " + millis(value.visiblePlusDspMillis()) +
                (stale ? " · Lectura sin actualizar" : value.recording() ? "" : " · Última lectura"));
        status.setText(value.status() + " · Marcas guardadas: " + value.savedMarkers() +
                (requestedMarker > value.savedMarkers() ? " · Marca " + requestedMarker + " pendiente" : ""));
        file.setText("Archivo: " + value.file().getFileName());
    }

    private static String millis(double value) {
        return Double.isFinite(value) ? String.format("%.1f ms", value) : "no disponible";
    }
}
