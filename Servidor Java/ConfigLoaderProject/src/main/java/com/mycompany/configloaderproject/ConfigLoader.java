/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

/**
 *
 * @author Estudiante_MCA
 */

package com.mycompany.configloaderproject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigLoader {
    private Properties properties;

    public ConfigLoader() {
        properties = new Properties();
        try (InputStream input = ConfigLoader.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                throw new IOException("No se pudo encontrar config.properties en el classpath");
            }
            properties.load(input);
        } catch (IOException e) {
            throw new RuntimeException("Error al cargar config.properties", e);
        }
    }

    public String getProperty(String key) {
        return properties.getProperty(key);
    }
}