package com.github.kyanbrix.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Reads configuration once at startup. Lookup order for a key such as {@code lavalink.password}:
 * <ol>
 *     <li>environment variable {@code LAVALINK_PASSWORD}</li>
 *     <li>{@code config.properties} in the working directory</li>
 *     <li>{@code config.properties} on the classpath (src/main/resources)</li>
 * </ol>
 */
public final class Config {

    private static final Logger log = LoggerFactory.getLogger(Config.class);
    private static final String FILE_NAME = "config.properties";
    private static final Properties PROPERTIES = load();

    private Config() {
    }

    private static Properties load() {

        Properties properties = new Properties();

        try (InputStream input = Config.class.getClassLoader().getResourceAsStream(FILE_NAME)) {
            if (input != null) properties.load(input);
        } catch (IOException e) {
            log.error("Cannot read {} from the classpath", FILE_NAME, e);
        }

        // A file next to the jar overrides the bundled one, so secrets don't have to be packaged
        Path external = Path.of(FILE_NAME);
        if (Files.isRegularFile(external)) {
            try (Reader reader = Files.newBufferedReader(external)) {
                properties.load(reader);
            } catch (IOException e) {
                log.error("Cannot read {}", external.toAbsolutePath(), e);
            }
        }

        return properties;
    }

    public static String get(String key) {

        String env = System.getenv(key.toUpperCase(Locale.ROOT).replace('.', '_'));
        if (env != null && !env.isBlank()) return env.strip();

        String value = PROPERTIES.getProperty(key);
        return value == null || value.isBlank() ? null : value.strip();
    }

    public static String get(String key, String defaultValue) {
        String value = get(key);
        return value != null ? value : defaultValue;
    }

    public static String require(String key) {
        String value = get(key);
        if (value == null) {
            throw new IllegalStateException("Missing required config '" + key + "'. Set it in " + FILE_NAME
                    + " or as the environment variable " + key.toUpperCase(Locale.ROOT).replace('.', '_'));
        }
        return value;
    }

    public static int getInt(String key, int defaultValue) {
        String value = get(key);
        if (value == null) return defaultValue;

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Config '{}' is not a number ({}), using {}", key, value, defaultValue);
            return defaultValue;
        }
    }

}
