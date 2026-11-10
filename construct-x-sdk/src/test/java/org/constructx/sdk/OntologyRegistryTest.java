package org.constructx.sdk;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link OntologyRegistry} against the pinned {@code ontologies}
 * git submodule at the repository root (resolved via {@code
 * CONX_ONTOLOGY_ROOT} or the default {@code ../ontologies} layout).
 *
 * <p>These tests are skipped when the ontology root is not available
 * (e.g. a clone without <code>git submodule update --init</code>).
 */
class OntologyRegistryTest {

    /**
     * Creates a registry if a usable ontology root is available
     * (root directory exists AND contains MANIFEST.json); otherwise
     * {@code null} and the test is skipped.
     */
    private static OntologyRegistry tryCreate() {
        try {
            OntologyRegistry registry = new OntologyRegistry();
            if (registry.root().resolve("MANIFEST.json").toFile().isFile()) {
                return registry;
            }
            return null;
        } catch (IllegalStateException e) {
            return null;
        }
    }

    private static void skipIfUnavailable(OntologyRegistry registry) {
        if (registry == null) {
            System.out.println("[SKIP] ontology root not available on this machine");
        }
    }

    @Test
    void defaultRootResolves() {
        OntologyRegistry registry = tryCreate();
        skipIfUnavailable(registry);
        if (registry == null) {
            return;
        }
        assertNotNull(registry.root());
        assertTrue(registry.root().toFile().isDirectory());
        assertTrue(registry.moduleIds().contains("gaeb-boq"));
        assertTrue(registry.moduleIds().contains("construction-schedule"));
    }

    @Test
    void manifestVersionsAreLoaded() {
        OntologyRegistry registry = tryCreate();
        if (registry == null) {
            return;
        }
        assertEquals("0.5.0", registry.versionOf("gaeb-boq"));
        assertEquals("0.5.0", registry.versionOf("construction-schedule"));
        assertEquals("0.7.0", registry.versionOf("breakdown-structure"));
        assertEquals("0.1.0", registry.versionOf("construction-progress-report"));
    }

    @Test
    void versionedIriConvention() {
        OntologyRegistry registry = tryCreate();
        if (registry == null) {
            return;
        }
        assertEquals("https://www.construct-x.org/ontology/gaeb-boq/0.5.0",
                registry.versionedIri("gaeb-boq"));
        assertEquals("https://www.construct-x.org/ontology/breakdown-structure/0.7.0",
                registry.versionedIri("breakdown-structure"));
    }

    @Test
    void versionedShapesIriConvention() {
        OntologyRegistry registry = tryCreate();
        if (registry == null) {
            return;
        }
        assertEquals("https://www.construct-x.org/shapes/gaeb-boq/0.5.0",
                registry.versionedShapesIri("gaeb-boq"));
    }

    @Test
    void unknownModuleVersionIsNull() {
        OntologyRegistry registry = tryCreate();
        if (registry == null) {
            return;
        }
        assertNull(registry.versionOf("no-such-module"));
        assertNull(registry.versionedIri("no-such-module"));
    }

    @Test
    void shapeFilesAreDiscovered() {
        OntologyRegistry registry = tryCreate();
        if (registry == null) {
            return;
        }
        var files = registry.shapeFiles("gaeb-boq");
        assertFalse(files.isEmpty(), "gaeb-boq should have at least one SHACL profile");
        for (Path file : files) {
            assertTrue(file.toString().endsWith(".ttl"));
            assertTrue(file.getFileName().toString().contains("-shapes"));
        }
    }

    @Test
    void mainOntologyFileIsFound() {
        OntologyRegistry registry = tryCreate();
        if (registry == null) {
            return;
        }
        var main = registry.mainOntologyFile("construction-schedule");
        assertTrue(main.isPresent());
        assertTrue(main.get().toString().endsWith(".ttl"));
        assertFalse(main.get().getFileName().toString().contains("-shapes"));
    }

    @Test
    void moduleDirResolves() {
        OntologyRegistry registry = tryCreate();
        if (registry == null) {
            return;
        }
        Path dir = registry.moduleDir("gaeb-boq");
        assertTrue(dir.toFile().isDirectory());
        assertThrows(IllegalStateException.class, () -> registry.moduleDir("no-such-module"));
    }

    @Test
    void loadOntologyGraphLoadsTriples() {
        OntologyRegistry registry = tryCreate();
        if (registry == null) {
            return;
        }
        var model = registry.loadOntologyGraph("gaeb-boq");
        try {
            assertTrue(model.size() > 100,
                    "gaeb-boq ontology graph should contain >100 triples, got " + model.size());
        } finally {
            model.close();
        }
    }

    @Test
    void allVersionsContainsAllDomainModules() {
        OntologyRegistry registry = tryCreate();
        if (registry == null) {
            return;
        }
        var versions = registry.allVersions();
        assertTrue(versions.containsKey("gaeb-boq"));
        assertTrue(versions.containsKey("construction-schedule"));
        assertTrue(versions.containsKey("breakdown-structure"));
        assertTrue(versions.containsKey("construction-site"));
        assertTrue(versions.containsKey("construction-delivery"));
        assertTrue(versions.containsKey("built-asset"));
        assertTrue(versions.containsKey("notification-content"));
        assertTrue(versions.containsKey("construction-progress-report"));
    }
}
