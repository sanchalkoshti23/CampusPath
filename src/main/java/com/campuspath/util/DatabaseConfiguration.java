package com.campuspath.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

public final class DatabaseConfiguration {
    public static final String CONFIG_FILE_ENV = "CAMPUSPATH_CONFIG_FILE";
    private static final String URL_KEY = "CAMPUSPATH_DB_URL";
    private static final String USER_KEY = "CAMPUSPATH_DB_USER";
    private static final String PASSWORD_KEY = "CAMPUSPATH_DB_PASSWORD";

    private final String url;
    private final String databaseName;
    private final String username;
    private final String password;

    private DatabaseConfiguration(
            String url,
            String databaseName,
            String username,
            String password
    ) {
        this.url = url;
        this.databaseName = databaseName;
        this.username = username;
        this.password = password;
    }

    public static DatabaseConfiguration load() {
        Map<String, String> environment = System.getenv();
        String configuredPath = nonBlank(environment.get(CONFIG_FILE_ENV));
        Path configFile;
        boolean explicitlyConfigured = configuredPath != null;
        try {
            if (explicitlyConfigured) {
                configFile = Path.of(configuredPath);
            } else {
                configFile = defaultConfigPath();
                if (!Files.exists(configFile) && Files.exists(legacyConfigPath())) {
                    configFile = legacyConfigPath();
                }
            }
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    CONFIG_FILE_ENV + " must point to a valid local properties file path"
            );
        }
        return load(environment, configFile, explicitlyConfigured, true);
    }

    public static DatabaseConfiguration create(
            String host,
            String portText,
            String databaseName,
            String username,
            char[] password
    ) {
        Objects.requireNonNull(password, "password");
        String normalizedHost = validateHost(host);
        int port;
        try {
            port = Integer.parseInt(Objects.requireNonNull(portText, "portText").trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("PostgreSQL port must be a whole number");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("PostgreSQL port must be between 1 and 65535");
        }
        String normalizedDatabase = Objects.requireNonNull(databaseName, "databaseName").trim();
        if (!normalizedDatabase.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException(
                    "Database name may contain only letters, numbers, '.', '_' and '-'"
            );
        }
        String normalizedUsername = Objects.requireNonNull(username, "username").trim();
        if (normalizedUsername.isEmpty()) {
            throw new IllegalArgumentException("PostgreSQL username must not be blank");
        }
        if (password.length == 0 || new String(password).isBlank()) {
            throw new IllegalArgumentException("PostgreSQL password must not be blank");
        }
        String url = "jdbc:postgresql://" + normalizedHost + ":" + port + "/"
                + normalizedDatabase;
        return new DatabaseConfiguration(
                url, normalizedDatabase, normalizedUsername, new String(password)
        );
    }

    public static Path defaultConfigPath() {
        String appData = nonBlank(System.getenv("APPDATA"));
        if (appData != null) {
            return Path.of(appData, "CampusPath", "database.properties");
        }
        return Path.of(
                System.getProperty("user.home"),
                "AppData", "Roaming", "CampusPath", "database.properties"
        );
    }

    public static Path saveConfigPath() {
        String configuredPath = nonBlank(System.getenv(CONFIG_FILE_ENV));
        if (configuredPath != null) {
            try {
                return Path.of(configuredPath);
            } catch (RuntimeException exception) {
                throw new IllegalStateException(
                        CONFIG_FILE_ENV + " must point to a valid local properties file path"
                );
            }
        }
        return defaultConfigPath();
    }

    private static Path legacyConfigPath() {
        return Path.of(System.getProperty("user.home"), ".campuspath", "database.properties");
    }

    static DatabaseConfiguration load(
            Map<String, String> environment,
            Path configFile
    ) {
        return load(environment, configFile, false, false);
    }

    private static DatabaseConfiguration load(
            Map<String, String> environment,
            Path configFile,
            boolean explicitlyConfigured,
            boolean secureExistingFile
    ) {
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(configFile, "configFile");
        Properties fileValues = new Properties();
        if (Files.exists(configFile)) {
            try (BufferedReader reader = Files.newBufferedReader(
                    configFile, StandardCharsets.UTF_8
            )) {
                if (secureExistingFile) {
                    DatabaseConfigurationStore.securePermissions(configFile);
                }
                reader.mark(1);
                if (reader.read() != '\uFEFF') {
                    reader.reset();
                }
                fileValues.load(reader);
            } catch (IOException | RuntimeException exception) {
                throw new IllegalStateException(
                        "Could not read the local CampusPath database configuration. "
                                + "Check the file and its read permissions; do not paste its contents into support messages."
                );
            }
        } else if (explicitlyConfigured) {
            throw new MissingConfigurationException(
                    "The file named by " + CONFIG_FILE_ENV
                            + " could not be found. Check the configured local file path."
            );
        }

        String url = value(environment, fileValues, URL_KEY);
        String username = value(environment, fileValues, USER_KEY);
        String password = value(environment, fileValues, PASSWORD_KEY);
        ArrayList<String> missing = new ArrayList<>();
        if (url == null) missing.add(URL_KEY);
        if (username == null) missing.add(USER_KEY);
        if (password == null) missing.add(PASSWORD_KEY);
        if (!missing.isEmpty()) {
            throw new MissingConfigurationException(
                    "CampusPath database configuration is incomplete. Set "
                            + String.join(", ", missing)
                            + " as environment variables, or add the same keys to "
                            + "%APPDATA%\\CampusPath\\database.properties. "
                            + "Environment variables override values in that file."
            );
        }
        if (!url.startsWith("jdbc:postgresql:")) {
            throw new IllegalStateException(
                    URL_KEY + " must be a PostgreSQL JDBC URL for campuspath_db"
            );
        }
        String databaseName = databaseNameFromUrl(url);
        if (databaseName == null || databaseName.isBlank()) {
            throw new IllegalStateException(
                    URL_KEY + " must include a PostgreSQL database name"
            );
        }
        return new DatabaseConfiguration(url, databaseName, username, password);
    }

    private static String validateHost(String host) {
        String value = Objects.requireNonNull(host, "host").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("PostgreSQL host must not be blank");
        }
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        if (value.contains(":")) {
            if (!value.matches("(?i)[0-9a-f:.%a-z0-9_-]+")) {
                throw new IllegalArgumentException("Enter a valid PostgreSQL host name or IP address");
            }
            return "[" + value + "]";
        }
        if (!value.matches("(?i)[a-z0-9.-]+")) {
            throw new IllegalArgumentException("Enter a valid PostgreSQL host name or IP address");
        }
        return value;
    }

    private static String databaseNameFromUrl(String url) {
        String location = url.substring("jdbc:postgresql:".length());
        int queryIndex = location.indexOf('?');
        if (queryIndex >= 0) {
            location = location.substring(0, queryIndex);
        }
        int slash = location.lastIndexOf('/');
        if (slash >= 0) {
            return location.substring(slash + 1);
        }
        int colon = location.lastIndexOf(':');
        return colon >= 0 ? location.substring(colon + 1) : location;
    }

    private static String value(
            Map<String, String> environment,
            Properties fileValues,
            String key
    ) {
        String fromEnvironment = nonBlank(environment.get(key));
        if (fromEnvironment != null) {
            return key.equals(PASSWORD_KEY) ? fromEnvironment : fromEnvironment.trim();
        }
        String fromFile = nonBlank(fileValues.getProperty(key));
        if (fromFile == null) {
            return null;
        }
        return key.equals(PASSWORD_KEY) ? fromFile : fromFile.trim();
    }

    private static String nonBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    String url() {
        return url;
    }

    String databaseName() {
        return databaseName;
    }

    String username() {
        return username;
    }

    String password() {
        return password;
    }

    public void save() {
        DatabaseConfigurationStore.save(this, saveConfigPath());
    }

    public static final class MissingConfigurationException extends IllegalStateException {
        private MissingConfigurationException(String message) {
            super(message);
        }
    }

    @Override
    public String toString() {
        return "DatabaseConfiguration[url=<redacted>, databaseName=<redacted>, "
                + "username=<redacted>, password=<redacted>]";
    }
}
