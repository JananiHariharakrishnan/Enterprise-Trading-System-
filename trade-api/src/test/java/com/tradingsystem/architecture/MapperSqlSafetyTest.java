package com.tradingsystem.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapperSqlSafetyTest {

    @Test
    void mapper_sql_uses_bind_parameters_and_never_uses_text_interpolation() throws IOException {
        Set<String> allowlist = loadAllowlist();

        Path xmlMapperPath = Path.of("src", "main", "resources", "mapper");
        assertTrue(Files.exists(xmlMapperPath), "Mapper XML directory does not exist");

        try (Stream<Path> paths = Files.walk(xmlMapperPath)) {
            paths.filter(path -> path.toString().endsWith(".xml"))
                    .forEach(path -> assertNoTextInterpolation(path, allowlist));
        }

        Path mapperJavaPath = Path.of("src", "main", "java", "com", "tradingsystem", "mapper");
        assertTrue(Files.exists(mapperJavaPath), "Mapper Java package does not exist");

        try (Stream<Path> paths = Files.walk(mapperJavaPath)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> assertNoTextInterpolation(path, allowlist));
        }
    }

    private static Set<String> loadAllowlist() {
        Properties properties = new Properties();
        Path manifestPath = Path.of("manifest.env");
        if (!Files.exists(manifestPath)) {
            return Set.of();
        }

        try (Stream<String> lines = Files.lines(manifestPath)) {
            lines.filter(line -> !line.isBlank() && !line.startsWith("#") && line.contains("="))
                    .forEach(line -> {
                        int splitIndex = line.indexOf('=');
                        properties.setProperty(
                                line.substring(0, splitIndex).trim(),
                                line.substring(splitIndex + 1).trim()
                        );
                    });
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        String csv = properties.getProperty("MAPPER_SQL_TEXT_ALLOWLIST", "").trim();
        String checkDescription = properties.getProperty("MAPPER_SQL_TEXT_ALLOWLIST_CHECK", "").trim();

        if (!csv.isEmpty()) {
            assertFalse(checkDescription.isEmpty(),
                    "manifest.env must describe fixed-list validation in MAPPER_SQL_TEXT_ALLOWLIST_CHECK when allowlist is used");
        }

        if (csv.isEmpty()) {
            return Set.of();
        }

        return new HashSet<>(Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
    }

    private static void assertNoTextInterpolation(Path path, Set<String> allowlist) {
        String normalizedPath = path.toString().replace('\\', '/');
        if (allowlist.contains(normalizedPath)) {
            return;
        }

        String content;
        try {
            content = Files.readString(path);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        assertFalse(
                content.contains("${"),
                () -> "Forbidden text interpolation (${...}) in mapper file: " + normalizedPath
        );
    }
}

