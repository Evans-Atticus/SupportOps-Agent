package com.example.supportops;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LayeringArchitectureTests {
    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/example/supportops");
    private static final Path MODULE_ROOT = SOURCE_ROOT.resolve("module");
    private static final Pattern MODULE_IMPORT = Pattern.compile(
            "import\\s+com\\.example\\.supportops\\.module\\.([a-z0-9_]+)\\.");

    @Test
    void modulesMustNotDependOnOtherModulesInternals() throws IOException {
        for (Path moduleRoot : childDirectories(MODULE_ROOT)) {
            String moduleName = moduleRoot.getFileName().toString();
            // diagnosis 是用例组合根：它按白名单编排 auth、ticket、business，其他业务模块仍禁止横向依赖。
            if ("diagnosis".equals(moduleName) || "ai".equals(moduleName)) {
                continue;
            }
            for (Path sourceFile : javaFiles(moduleRoot)) {
                Matcher matcher = MODULE_IMPORT.matcher(read(sourceFile));
                while (matcher.find()) {
                    assertEquals(moduleName, matcher.group(1),
                            () -> sourceFile + " must not import another module's internal package");
                }
            }
        }
    }

    @Test
    void controllersMustOnlyUseServiceAndApiModels() throws IOException {
        assertLayerDoesNotContain("controller", List.of(
                ".manager.", ".dao.", ".service.impl.", ".model.bo.", ".model.dataobject."
        ));
    }

    @Test
    void servicesMustNotBypassApplicationBoundary() throws IOException {
        assertLayerDoesNotContain("service", List.of(
                ".controller.", ".dao.mapper.", ".dao.dataobject.",
                "org.springframework.jdbc.core.JdbcTemplate"
        ));
    }

    @Test
    void managersMustNotDependOnWebOrServiceLayers() throws IOException {
        assertLayerDoesNotContain("manager", List.of(
                ".controller.", ".service.", ".model.dto.", ".model.vo."
        ));
    }

    @Test
    void daoMustRemainFreeOfBusinessOrWebOrchestration() throws IOException {
        assertLayerDoesNotContain("dao", List.of(
                ".controller.", ".service.", ".manager.", ".model.dto.", ".model.vo."
        ));
    }

    @Test
    void sharedInfrastructureMustNotDependOnFeatureModules() throws IOException {
        for (String sharedLayer : List.of("common", "config", "infrastructure")) {
            Path root = SOURCE_ROOT.resolve(sharedLayer);
            for (Path sourceFile : javaFiles(root)) {
                assertFalse(read(sourceFile).contains("import com.example.supportops.module."),
                        () -> sourceFile + " must not depend on a feature module");
            }
        }
    }

    @Test
    void sourceMustNotUseWildcardImportsOrUntypedBusinessMaps() throws IOException {
        for (Path sourceFile : javaFiles(SOURCE_ROOT)) {
            String source = read(sourceFile);
            assertFalse(source.matches("(?s).*import\\s+[^;]*\\*;.*"),
                    () -> sourceFile + " must not use wildcard imports");
            if (sourceFile.startsWith(MODULE_ROOT.resolve("business"))) {
                assertFalse(source.contains("Map<String, Object>"),
                        () -> sourceFile + " must use a typed business query model");
            }
        }
    }

    private void assertLayerDoesNotContain(String layer, List<String> forbiddenDependencies) throws IOException {
        String marker = java.io.File.separator + layer + java.io.File.separator;
        for (Path sourceFile : javaFiles(MODULE_ROOT)) {
            if (!sourceFile.toString().contains(marker)) {
                continue;
            }
            String source = read(sourceFile);
            for (String forbiddenDependency : forbiddenDependencies) {
                assertFalse(source.contains(forbiddenDependency),
                        () -> sourceFile + " must not depend on " + forbiddenDependency);
            }
        }
    }

    private List<Path> childDirectories(Path root) throws IOException {
        try (var paths = Files.list(root)) {
            return paths.filter(Files::isDirectory).toList();
        }
    }

    private List<Path> javaFiles(Path root) throws IOException {
        if (!Files.exists(root)) {
            return List.of();
        }
        try (var paths = Files.walk(root)) {
            return paths.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read " + path, exception);
        }
    }
}
