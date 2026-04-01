package com.github.kyanbrix.utils;

import com.github.kyanbrix.Main;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Config {

    private static final Logger log = LoggerFactory.getLogger(Config.class);
    Properties properties;

    public Config() {

        this.properties = new Properties();

    }

    public String getKey(@NotNull String key) {

        try (InputStream input = Main.class.getResourceAsStream("config.properties")){
            properties.load(input);

            return properties.getProperty(key);
        }catch (IOException e) {
            log.error("Cannot access properties file",e);
        }

        return "error";

    }
}
