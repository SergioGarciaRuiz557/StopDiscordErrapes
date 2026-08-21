package com.discordaudioguard.application;

import com.discordaudioguard.config.ApplicationConfiguration;
import com.discordaudioguard.ui.*;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.util.Objects;

public final class DiscordAudioGuardApplication extends Application {
    private ApplicationController controller;
    private MainViewController viewController;

    @Override
    public void start(Stage stage) {
        controller = new ApplicationController();
        MainView view = new MainView(controller.configuration().processing());
        viewController = new MainViewController(controller, view);
        Scene scene = new Scene(view, 1080, 760);
        scene.getStylesheets().add(getClass().getResource("/com/discordaudioguard/ui/application.css").toExternalForm());
        stage.getIcons().add(new Image(Objects.requireNonNull(
                getClass().getResource("/com/discordaudioguard/ui/app-icon.png")).toExternalForm()));
        stage.setTitle("Discord Audio Guard"); stage.setScene(scene); stage.setMinWidth(850); stage.setMinHeight(650);
        restoreWindow(stage, controller.configuration().window());
        stage.setOnCloseRequest(event -> {
            controller.saveWindow(new ApplicationConfiguration.WindowConfiguration(stage.getX(), stage.getY(),
                    stage.getWidth(), stage.getHeight(), stage.isMaximized()));
            viewController.close(); controller.close();
        });
        stage.show();
        if (controller.configuration().firstRun()) showIntroduction();
    }

    private void showIntroduction() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Primeros pasos"); alert.setHeaderText("Configura el flujo de audio antes de empezar");
        alert.setContentText("1. Instala un dispositivo virtual como VB-CABLE.\n" +
                "2. En Discord, elige “CABLE Input” como salida.\n" +
                "3. Aquí, elige el extremo de grabación (normalmente “CABLE Output”) como entrada.\n" +
                "4. Elige tus auriculares como salida y pulsa “Iniciar procesamiento”.\n\n" +
                "Los nombres pueden variar según el controlador y el idioma de Windows.");
        alert.showAndWait(); controller.markIntroductionShown();
    }

    private static void restoreWindow(Stage stage, ApplicationConfiguration.WindowConfiguration window) {
        stage.setWidth(window.width()); stage.setHeight(window.height());
        if (Double.isFinite(window.x()) && Double.isFinite(window.y())) { stage.setX(window.x()); stage.setY(window.y()); }
        stage.setMaximized(window.maximized());
    }

    public static void main(String[] args) { launch(args); }
}
