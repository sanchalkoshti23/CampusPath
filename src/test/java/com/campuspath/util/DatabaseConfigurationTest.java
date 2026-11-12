package com.campuspath.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclFileAttributeView;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseConfigurationTest {
    private static final String URL = "jdbc:postgresql://localhost:5432/campuspath_db";

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsAllValuesFromTheOptionalLocalFile() throws IOException {
        Path configFile = writeConfig(
                URL,
                "local-user",
                "local-test-password"
        );

        DatabaseConfiguration configuration = DatabaseConfiguration.load(Map.of(), configFile);

        assertEquals(URL, configuration.url());
        assertEquals("local-user", configuration.username());
        assertEquals("local-test-password", configuration.password());
        assertFalse(configuration.toString().contains("local-test-password"));
    }

    @Test
    void acceptsUtf8BomWrittenByWindowsPowerShell() throws IOException {
        Path configFile = temporaryDirectory.resolve("database.properties");
        Files.writeString(configFile, "\uFEFF" + """
                CAMPUSPATH_DB_URL=%s
                CAMPUSPATH_DB_USER=local-user
                CAMPUSPATH_DB_PASSWORD=local-test-password
                """.formatted(URL));

        DatabaseConfiguration configuration = DatabaseConfiguration.load(Map.of(), configFile);

        assertEquals(URL, configuration.url());
    }

    @Test
    void preservesSpacesAndBackslashesInEscapedPropertiesPasswords() throws IOException {
        String password = " leading \\ slash ";
        String escapedPassword = password.replace("\\", "\\\\").replace(" ", "\\ ");
        Path configFile = temporaryDirectory.resolve("database.properties");
        Files.writeString(configFile, """
                CAMPUSPATH_DB_URL=%s
                CAMPUSPATH_DB_USER=local-user
                CAMPUSPATH_DB_PASSWORD=%s
                """.formatted(URL, escapedPassword));

        DatabaseConfiguration configuration = DatabaseConfiguration.load(Map.of(), configFile);

        assertEquals(password, configuration.password());
    }

    @Test
    void environmentValuesOverrideFileValuesAndMissingEnvironmentValuesFallBack() throws IOException {
        Path configFile = writeConfig(
                "jdbc:postgresql://file-host:5432/campuspath_db",
                "file-user",
                "file-test-password"
        );
        Map<String, String> environment = new HashMap<>();
        environment.put("CAMPUSPATH_DB_URL", URL);
        environment.put("CAMPUSPATH_DB_USER", "environment-user");

        DatabaseConfiguration configuration = DatabaseConfiguration.load(environment, configFile);

        assertEquals(URL, configuration.url());
        assertEquals("environment-user", configuration.username());
        assertEquals("file-test-password", configuration.password());
    }

    @Test
    void reportsAllMissingKeysWithConfigurationInstructions() {
        DatabaseConfiguration.MissingConfigurationException exception = assertThrows(
                DatabaseConfiguration.MissingConfigurationException.class,
                () -> DatabaseConfiguration.load(
                        Map.of(),
                        temporaryDirectory.resolve("not-created.properties")
                )
        );

        assertTrue(exception.getMessage().contains("CAMPUSPATH_DB_URL"));
        assertTrue(exception.getMessage().contains("CAMPUSPATH_DB_USER"));
        assertTrue(exception.getMessage().contains("CAMPUSPATH_DB_PASSWORD"));
        assertTrue(exception.getMessage().contains("database.properties"));
    }

    @Test
    void rejectsNonPostgreSqlJdbcUrlsWithoutEchoingTheConfiguredValue() throws IOException {
        Path configFile = writeConfig("jdbc:mysql://secret-host/private", "user", "password");
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> DatabaseConfiguration.load(Map.of(), configFile)
        );
        assertFalse(exception.getMessage().contains("secret-host"));
    }

    @Test
    void createsConfigurationFromWizardFieldsAndValidatesPort() {
        char[] password = "wizard-test-password".toCharArray();

        DatabaseConfiguration configuration = DatabaseConfiguration.create(
                "localhost", "5432", "campuspath_db", "postgres", password
        );

        assertEquals(URL, configuration.url());
        assertEquals("postgres", configuration.username());
        assertEquals("wizard-test-password", configuration.password());
        assertThrows(IllegalArgumentException.class, () -> DatabaseConfiguration.create(
                "localhost", "70000", "campuspath_db", "postgres", password
        ));
    }

    @Test
    void savesConfigurationWithOwnerOnlyAcl() throws IOException {
        Path configFile = temporaryDirectory.resolve("private").resolve("database.properties");
        AclFileAttributeView aclView = Files.getFileAttributeView(
                temporaryDirectory, AclFileAttributeView.class
        );
        assertTrue(aclView != null, "The current platform must support Windows ACLs");

        DatabaseConfiguration configuration = DatabaseConfiguration.create(
                "localhost", "5432", "campuspath_db", "postgres",
                "private-test-password".toCharArray()
        );
        DatabaseConfigurationStore.save(configuration, configFile);

        AclFileAttributeView savedAcl = Files.getFileAttributeView(
                configFile, AclFileAttributeView.class
        );
        assertEquals(1, savedAcl.getAcl().size());
        assertEquals(Files.getOwner(configFile), savedAcl.getAcl().get(0).principal());
        DatabaseConfiguration reloaded = DatabaseConfiguration.load(Map.of(), configFile);
        assertEquals("private-test-password", reloaded.password());
    }

    private Path writeConfig(String url, String username, String password) throws IOException {
        Path configFile = temporaryDirectory.resolve("database.properties");
        Files.writeString(configFile, """
                CAMPUSPATH_DB_URL=%s
                CAMPUSPATH_DB_USER=%s
                CAMPUSPATH_DB_PASSWORD=%s
                """.formatted(url, username, password));
        return configFile;
    }
}
