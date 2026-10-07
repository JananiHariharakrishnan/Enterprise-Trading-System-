package com.tradingsystem.architecture;

import com.tradingsystem.service.OrderLogic;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayeringArchitectureTest {

    private static final String[] FORBIDDEN_CONTROLLER_IMPORTS = new String[] {
            "com.tradingsystem.mapper",
            "java.sql",
            "javax.sql",
            "javax.persistence",
            "jakarta.persistence",
            "org.apache.ibatis",
            "org.mybatis",
            "org.springframework.jdbc"
    };

    private static final String[] FORBIDDEN_SPRINT5_REFERENCES = new String[] {
            "org.springframework",
            "jakarta.servlet",
            "javax.servlet",
            "org.apache.ibatis",
            "org.mybatis"
    };

    @Test
    void controllers_do_not_depend_on_mapper_sql_or_persistence_types() throws IOException {
        Path controllerPath = Path.of("src", "main", "java", "com", "tradingsystem", "controller");
        assertTrue(Files.exists(controllerPath), "Controller package path does not exist");

        try (Stream<Path> paths = Files.walk(controllerPath)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                 .filter(path -> !path.getFileName().toString().equals("SignalController.java"))
                 .forEach(path -> assertFileContainsNoForbiddenImports(path, FORBIDDEN_CONTROLLER_IMPORTS));
        }
    }

    @Test
    void services_do_not_accept_http_or_request_or_status_parameters() {
        Class<?>[] serviceTypes = new Class<?>[] {
                com.tradingsystem.service.OrderService.class,
                com.tradingsystem.service.AccountService.class,
                com.tradingsystem.service.Impl.OrderServiceImpl.class,
                com.tradingsystem.service.Impl.AccountServiceImpl.class,
                OrderLogic.class
        };

        for (Class<?> serviceType : serviceTypes) {
            for (Method method : serviceType.getDeclaredMethods()) {
                for (Parameter parameter : method.getParameters()) {
                    Class<?> parameterType = parameter.getType();
                    String fqcn = parameterType.getName();
                    String simpleName = parameterType.getSimpleName();

                    assertFalse(
                            fqcn.startsWith("jakarta.servlet")
                                    || fqcn.startsWith("javax.servlet")
                                    || fqcn.equals("org.springframework.http.HttpStatus")
                                    || fqcn.equals("org.springframework.http.ResponseEntity")
                                    || simpleName.endsWith("Request"),
                            () -> "Forbidden parameter type on "
                                    + serviceType.getName()
                                    + "#"
                                    + method.getName()
                                    + ": "
                                    + fqcn
                    );
                }
            }
        }
    }

    @Test
    void sprint5_domain_artifact_has_no_servlet_spring_or_mybatis_dependency() throws IOException {
        Path[] sprint5Paths = new Path[] {
                Path.of("src", "main", "java", "com", "tradingsystem", "model"),
                Path.of("src", "main", "java", "com", "tradingsystem", "enums"),
                Path.of("src", "main", "java", "com", "tradingsystem", "exception"),
                Path.of("src", "main", "java", "com", "tradingsystem", "service", "command")
        };

        for (Path path : sprint5Paths) {
            assertTrue(Files.exists(path), "Expected Sprint 5 path does not exist: " + path);
            if (Files.isDirectory(path)) {
                try (Stream<Path> paths = Files.walk(path)) {
                    paths.filter(p -> p.toString().endsWith(".java"))
                            .forEach(p -> assertFileContainsNoForbiddenImports(p, FORBIDDEN_SPRINT5_REFERENCES));
                }
            } else {
                assertFileContainsNoForbiddenImports(path, FORBIDDEN_SPRINT5_REFERENCES);
            }
        }
    }

    private static void assertFileContainsNoForbiddenImports(Path path, String[] forbiddenPrefixes) {
        String content;
        try {
            content = Files.readString(path);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        for (String forbiddenPrefix : forbiddenPrefixes) {
            assertFalse(
                    content.contains("import " + forbiddenPrefix),
                    () -> "Forbidden dependency in " + path + ": " + forbiddenPrefix
            );
        }
    }
}





