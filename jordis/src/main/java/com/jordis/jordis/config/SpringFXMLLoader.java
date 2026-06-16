package com.jordis.jordis.config;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;

@Component
public class SpringFXMLLoader {

    private final ApplicationContext context;

    public SpringFXMLLoader(ApplicationContext context) {
        this.context = context;
    }

    public <T> T load(String fxmlPath) throws IOException {
        FXMLLoader loader = createLoader(fxmlPath);
        return loader.load();
    }

    public <C> LoadResult<C> loadWithController(String fxmlPath) throws IOException {
        FXMLLoader loader = createLoader(fxmlPath);
        Parent root = loader.load();
        C controller = loader.getController();
        if (controller == null) {
            throw new IOException(
                    "El controller es null para: " + fxmlPath
                            + ". Verifica que el fx:controller esté correcto en el FXML.");
        }
        return new LoadResult<>(root, controller);
    }

    private FXMLLoader createLoader(String fxmlPath) throws IOException {
        URL url = getClass().getResource(fxmlPath);
        if (url == null) {
            throw new IOException("FXML no encontrado en classpath: " + fxmlPath
                    + ". Verifica que el archivo exista en src/main/resources" + fxmlPath);
        }
        FXMLLoader loader = new FXMLLoader();
        loader.setControllerFactory(context::getBean);
        loader.setLocation(url);
        return loader;
    }

    public static class LoadResult<C> {
        public final Parent root;
        public final C controller;
        public LoadResult(Parent root, C controller) {
            this.root = root;
            this.controller = controller;
        }
    }
}