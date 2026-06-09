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
        return new LoadResult<>(root, controller);
    }

    private FXMLLoader createLoader(String fxmlPath) throws IOException {
        FXMLLoader loader = new FXMLLoader();
        loader.setControllerFactory(context::getBean);
        URL url = getClass().getResource(fxmlPath);
        if (url == null) throw new IOException("FXML no encontrado: " + fxmlPath);
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