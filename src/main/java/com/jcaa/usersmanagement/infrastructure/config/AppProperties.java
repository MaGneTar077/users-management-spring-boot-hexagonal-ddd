package com.jcaa.usersmanagement.infrastructure.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class AppProperties {

    private final Properties properties = new Properties();

    public AppProperties() {
        try (InputStream input = getClass()
                .getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException e) {
            throw new RuntimeException("No se pudo cargar application.properties", e);
        }
    }

    public String get(String key) {
        return properties.getProperty(key);
    }

    public int getInt(String key) {
        String value = get(key);
        if (value == null) return 0;
        return Integer.parseInt(value);
    }
}