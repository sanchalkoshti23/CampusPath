package com.campuspath.util;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;

final class DatabaseConfigurationStore {
    private DatabaseConfigurationStore() {
    }

    static void save(DatabaseConfiguration configuration, Path requestedPath) {
        Path temporaryFile = null;
        try {
            Path configFile = requestedPath.toAbsolutePath().normalize();
            Path directory = configFile.getParent();
            if (directory == null) {
                throw new IOException("A parent directory is required");
            }
            Files.createDirectories(directory);
            if (Files.exists(configFile)) {
                securePermissions(configFile);
            }

            temporaryFile = Files.createTempFile(directory, ".campuspath-db-", ".tmp");
            securePermissions(temporaryFile);
            Properties properties = new Properties();
            properties.setProperty("CAMPUSPATH_DB_URL", configuration.url());
            properties.setProperty("CAMPUSPATH_DB_USER", configuration.username());
            properties.setProperty("CAMPUSPATH_DB_PASSWORD", configuration.password());
            try (Writer writer = Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8)) {
                properties.store(writer, "CampusPath local database configuration");
            }

            try {
                Files.move(
                        temporaryFile, configFile,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, configFile, StandardCopyOption.REPLACE_EXISTING);
            }
            securePermissions(configFile);
        } catch (IOException | RuntimeException exception) {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException cleanupException) {
                    exception.addSuppressed(cleanupException);
                }
            }
            throw new IllegalStateException(
                    "Could not securely save the CampusPath database configuration. "
                            + "Check that the current Windows user can write to the application configuration folder."
            );
        }
    }

    static void securePermissions(Path path) throws IOException {
        AclFileAttributeView view = Files.getFileAttributeView(
                path, AclFileAttributeView.class
        );
        if (view == null) {
            throw new IOException("Windows ACL permissions are not supported for this path");
        }
        UserPrincipal owner = Files.getOwner(path);
        AclEntry ownerAccess = AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                .build();
        view.setAcl(List.of(ownerAccess));
    }
}
