package com.jordis.jordis.config;

import javafx.fxml.FXMLLoader;
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
        FXMLLoader loader = new FXMLLoader();
        loader.setControllerFactory(context::getBean);
        URL url = getClass().getResource(fxmlPath);
        if (url == null) {
            throw new IOException("No se encontró el archivo FXML: " + fxmlPath);
        }
        loader.setLocation(url);
        return loader.load();
    }
}