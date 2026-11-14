package phucuong.ht.contract;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the API contract inventory synchronized with the Spring MVC annotations without starting
 * the application context (and therefore without requiring PostgreSQL, Redis, or RabbitMQ).
 */
class EndpointContractMatrixTest {

    private static final String CONTROLLER_BASE_PACKAGE = "phucuong.ht.modules";
    private static final int EXPECTED_CONTROLLER_COUNT = 42;
    private static final int EXPECTED_ROUTE_COUNT = 283;
    private static final String MATRIX_RESOURCE = "endpoint-contract-matrix.psv";
    private static final Pattern ROUTE_KEY = Pattern.compile("^(GET|POST|PUT|PATCH|DELETE) /\\S+$");

    private static final List<StatusColumn> STATUS_COLUMNS = List.of(
            new StatusColumn("success", null),
            new StatusColumn("401", "401"),
            new StatusColumn("403", "403"),
            new StatusColumn("400", "400"),
            new StatusColumn("404", "404"),
            new StatusColumn("409", "409")
    );

    @Test
    void endpointContractMatrixCoversEverySpringMvcMapping() {
        List<Class<?>> controllers = discoverControllers();
        List<EndpointRoute> routes = discoverRoutes(controllers);
        Set<String> discoveredRouteKeys = routes.stream()
                .map(EndpointRoute::key)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<String, ContractEntry> matrix = loadMatrix();

        assertEquals(EXPECTED_CONTROLLER_COUNT, controllers.size(),
                "Controller inventory changed; update the route matrix deliberately.");
        assertEquals(EXPECTED_ROUTE_COUNT, routes.size(),
                "Spring MVC route inventory changed; add an explicit matrix row for every route.");
        assertEquals(routes.size(), discoveredRouteKeys.size(),
                "Two controller methods resolve to the same HTTP method and path: " + routes);
        assertEquals(EXPECTED_ROUTE_COUNT, matrix.size(),
                "The matrix must contain one declarative entry per expected route.");
        assertEquals(discoveredRouteKeys, matrix.keySet(), () -> mismatchMessage(discoveredRouteKeys, matrix.keySet()));
    }

    private static List<Class<?>> discoverControllers() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class, true));

        return scanner.findCandidateComponents(CONTROLLER_BASE_PACKAGE).stream()
                .map(BeanDefinition::getBeanClassName)
                .map(EndpointContractMatrixTest::loadClass)
                .sorted(java.util.Comparator.comparing(Class::getName))
                .toList();
    }

    private static Class<?> loadClass(String className) {
        if (className == null) {
            throw new IllegalStateException("Spring component scan returned a controller without a class name.");
        }

        try {
            return ClassUtils.forName(className, ClassUtils.getDefaultClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Could not load discovered controller " + className, exception);
        }
    }

    private static List<EndpointRoute> discoverRoutes(List<Class<?>> controllers) {
        List<EndpointRoute> routes = new ArrayList<>();

        for (Class<?> controller : controllers) {
            RequestMapping typeMapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
            if (typeMapping == null) {
                throw new IllegalStateException("Controller " + controller.getName() + " has no @RequestMapping.");
            }

            for (Method method : controller.getDeclaredMethods()) {
                RequestMapping methodMapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (methodMapping == null) {
                    continue;
                }

                if (methodMapping.method().length == 0) {
                    throw new IllegalStateException("Handler " + controller.getName() + "#" + method.getName()
                            + " does not declare an HTTP method.");
                }

                for (RequestMethod requestMethod : methodMapping.method()) {
                    for (String typePath : mappingPaths(typeMapping)) {
                        for (String methodPath : mappingPaths(methodMapping)) {
                            routes.add(new EndpointRoute(
                                    controller.getName(),
                                    method.getName(),
                                    requestMethod.name(),
                                    combinePaths(typePath, methodPath)
                            ));
                        }
                    }
                }
            }
        }

        return routes;
    }

    private static List<String> mappingPaths(RequestMapping mapping) {
        String[] paths = mapping.path().length == 0 ? mapping.value() : mapping.path();
        if (paths.length == 0) {
            return List.of("");
        }

        return Arrays.stream(paths)
                .map(String::trim)
                .toList();
    }

    private static String combinePaths(String typePath, String methodPath) {
        String left = normalizePath(typePath);
        String right = normalizePath(methodPath);
        if ("/".equals(left)) {
            return right;
        }
        if ("/".equals(right)) {
            return left;
        }
        return left + right;
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank() || "/".equals(path.trim())) {
            return "/";
        }

        String normalized = path.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        return normalized.endsWith("/") && normalized.length() > 1
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
    }

    private static Map<String, ContractEntry> loadMatrix() {
        InputStream inputStream = EndpointContractMatrixTest.class.getClassLoader()
                .getResourceAsStream(MATRIX_RESOURCE);
        if (inputStream == null) {
            throw new IllegalStateException("Missing test resource " + MATRIX_RESOURCE);
        }

        Map<String, ContractEntry> entries = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                String[] columns = line.split("\\|", -1);
                if (columns.length != STATUS_COLUMNS.size() + 1) {
                    throw new IllegalStateException("Matrix line " + lineNumber
                            + " must have route plus six status-case columns.");
                }

                String route = columns[0].trim();
                if (!ROUTE_KEY.matcher(route).matches()) {
                    throw new IllegalStateException("Matrix line " + lineNumber
                            + " must start with an HTTP method and absolute route: " + route);
                }

                for (int index = 0; index < STATUS_COLUMNS.size(); index++) {
                    validateStatusCase(route, STATUS_COLUMNS.get(index), columns[index + 1].trim(), lineNumber);
                }

                ContractEntry previous = entries.putIfAbsent(route, new ContractEntry(route));
                if (previous != null) {
                    throw new IllegalStateException("Duplicate matrix entry for " + route + " at line " + lineNumber);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + MATRIX_RESOURCE, exception);
        }

        return Collections.unmodifiableMap(entries);
    }

    private static void validateStatusCase(String route, StatusColumn column, String value, int lineNumber) {
        if ("success".equals(column.name())) {
            if (value.matches("2\\d\\d")) {
                return;
            }
            throw new IllegalStateException("Matrix line " + lineNumber + " for " + route
                    + " must declare a 2xx success status.");
        }

        if (column.expectedStatus().equals(value)) {
            return;
        }

        if (value.startsWith("N/A:") && value.substring("N/A:".length()).trim().length() >= 8) {
            return;
        }

        throw new IllegalStateException("Matrix line " + lineNumber + " for " + route + " must declare "
                + column.expectedStatus() + " or an explicit N/A: rationale for the " + column.name() + " case.");
    }

    private static String mismatchMessage(Set<String> discovered, Set<String> declared) {
        Set<String> missing = new TreeSet<>(discovered);
        missing.removeAll(declared);
        Set<String> stale = new TreeSet<>(declared);
        stale.removeAll(discovered);
        return "Matrix drift detected. Missing entries: " + missing + "; stale entries: " + stale;
    }

    private record EndpointRoute(String controller, String handler, String method, String path) {
        private String key() {
            return method + " " + path;
        }
    }

    private record ContractEntry(String route) {
    }

    private record StatusColumn(String name, String expectedStatus) {
    }
}
