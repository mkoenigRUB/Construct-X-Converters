package org.constructx.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Discovers and loads Construct-X ontology modules from a file-system
 * root directory (the ontology repository layout).
 *
 * <p>Resolves the root from, in order of priority:
 * <ol>
 *   <li>explicit path passed to the constructor</li>
 *   <li>environment variable {@code CONX_ONTOLOGY_ROOT}</li>
 *   <li>system property {@code conx.ontology.root}</li>
 *   <li>default: {@code ../..} (legacy layout where the ontology root is
 *       two levels above the converter module directory)</li>
 * </ol>
 *
 * <p>Provides:
 * <ul>
 *   <li>module discovery (directory names = module IDs)</li>
 *   <li>version resolution from {@code MANIFEST.json}</li>
 *   <li>versioned IRI construction ({@code conformsTo})</li>
 *   <li>TTL file loading for SHACL validation (ontology graph)</li>
 * </ul>
 */
public final class OntologyRegistry {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String OWL_VERSION_INFO = "http://www.w3.org/2002/07/owl#versionInfo";

    /** Resolved absolute path to the ontology root directory. */
    private final Path root;

    /** module id → version string from MANIFEST.json (may be empty). */
    private final Map<String, String> manifestVersions = new HashMap<>();

    /** All discovered module directories (relative to root). */
    private final List<Path> moduleDirs = new ArrayList<>();

    /**
     * Creates a registry for the given ontology root.
     *
     * @param rootPath absolute or relative path to the ontology root
     *                 (the directory containing {@code MANIFEST.json} and
     *                 the module sub-directories); must exist
     * @throws IllegalStateException if the directory does not exist
     */
    public OntologyRegistry(Path rootPath) {
        this.root = rootPath.toAbsolutePath().normalize();
        if (!Files.isDirectory(this.root)) {
            throw new IllegalStateException("Ontology root does not exist: " + this.root);
        }
        discoverModules();
        loadManifestVersions();
    }

    /**
     * Creates a registry using the default resolution order:
     * env var {@code CONX_ONTOLOGY_ROOT} → sysprop {@code conx.ontology.root}
     * → {@code ../..}.
     */
    public OntologyRegistry() {
        this(resolveDefaultRoot());
    }

    /**
     * Returns the resolved absolute path to the ontology root.
     */
    public Path root() {
        return root;
    }

    /**
     * Returns the version for the given module id as declared in
     * {@code MANIFEST.json}; {@code null} if not present.
     *
     * @param moduleId module id (directory name, e.g. {@code gaeb-boq})
     */
    public String versionOf(String moduleId) {
        return manifestVersions.get(moduleId);
    }

    /**
     * Returns a map of all module ids to their MANIFEST versions.
     */
    public Map<String, String> allVersions() {
        return Map.copyOf(manifestVersions);
    }

    /**
     * Builds the versioned IRI for a module's ontology (the IRI used in
     * {@code conformsTo} statements).
     *
     * <p>Convention: {@code https://www.construct-x.org/ontology/<moduleId>/<version>}
     *
     * @param moduleId module id (e.g. {@code gaeb-boq})
     * @return versioned IRI, or {@code null} if the module has no version
     */
    public String versionedIri(String moduleId) {
        String version = versionOf(moduleId);
        if (version == null) {
            return null;
        }
        return "https://www.construct-x.org/ontology/" + moduleId + "/" + version;
    }

    /**
     * Builds the versioned IRI for a module's SHACL shapes file.
     *
     * <p>Convention: {@code https://www.construct-x.org/shapes/<moduleId>/<version>}
     *
     * @param moduleId module id
     * @return versioned IRI, or {@code null} if no version is known
     */
    public String versionedShapesIri(String moduleId) {
        String version = versionOf(moduleId);
        if (version == null) {
            return null;
        }
        return "https://www.construct-x.org/shapes/" + moduleId + "/" + version;
    }

    /**
     * Returns the path to the main ontology TTL file of the given module
     * (the file that is NOT a shapes file, vocabulary, or example).
     *
     * @param moduleId module id
     * @return Optional path to the main TTL file
     */
    public Optional<Path> mainOntologyFile(String moduleId) {
        Path moduleDir = root.resolve(moduleId);
        if (!Files.isDirectory(moduleDir)) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.list(moduleDir)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".ttl"))
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return !name.contains("-shapes")
                                && !name.contains("vocab")
                                && !name.contains("example")
                                && !name.contains("link");
                    })
                    .findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Returns all SHACL profile TTL files for the given module.
     *
     * @param moduleId module id
     * @return list of paths (possibly empty)
     */
    public List<Path> shapeFiles(String moduleId) {
        Path moduleDir = root.resolve(moduleId);
        if (!Files.isDirectory(moduleDir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(moduleDir)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".ttl"))
                    .filter(p -> p.getFileName().toString().contains("-shapes"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * Loads all non-shape, non-example TTL files from the given module
     * directories into a single Jena ontology model (used as the
     * "ontology graph" for SHACL validation with {@code sh:class}
     * and {@code sh:in} constraints).
     *
     * @param moduleIds module ids to load
     * @return populated ontology model (caller must close it)
     */
    public OntModel loadOntologyGraph(String... moduleIds) {
        OntModel model = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
        for (String moduleId : moduleIds) {
            Path moduleDir = root.resolve(moduleId);
            if (!Files.isDirectory(moduleDir)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(moduleDir)) {
                for (Path file : files
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".ttl"))
                        .filter(p -> !p.getFileName().toString().contains("-shapes"))
                        .filter(p -> !p.getFileName().toString().toLowerCase(Locale.ROOT).contains("example"))
                        .toList()) {
                    RDFDataMgr.read(model, file.toUri().toString());
                }
            } catch (IOException e) {
                // skip unreadable module
            }
        }
        return model;
    }

    /**
     * Returns the absolute path to the given module directory.
     *
     * @param moduleId module id
     * @throws IllegalStateException if the module does not exist
     */
    public Path moduleDir(String moduleId) {
        Path dir = root.resolve(moduleId);
        if (!Files.isDirectory(dir)) {
            throw new IllegalStateException("Unknown module: " + moduleId);
        }
        return dir;
    }

    /**
     * Returns all discovered module directory names (sorted).
     */
    public List<String> moduleIds() {
        return moduleDirs.stream()
                .map(p -> p.getFileName().toString())
                .sorted()
                .toList();
    }

    // ── internal ─────────────────────────────────────────────────────────────

    private void discoverModules() {
        try (Stream<Path> entries = Files.list(root)) {
            for (Path dir : entries.filter(Files::isDirectory).toList()) {
                String name = dir.getFileName().toString();
                if (!name.startsWith(".") && !name.equals("documentation")
                        && !name.equals("examples") && !name.equals("contexts")
                        && !name.equals("link-ontologies") && !name.equals("prompts")
                        && !name.equals("usecase")) {
                    moduleDirs.add(dir);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot list ontology root " + root, e);
        }
    }

    private void loadManifestVersions() {
        Path manifest = root.resolve("MANIFEST.json");
        if (!Files.isRegularFile(manifest)) {
            return;
        }
        try (InputStream in = Files.newInputStream(manifest)) {
            JsonNode root2 = MAPPER.readTree(in);
            root2.fields().forEachRemaining(category -> {
                if (category.getValue().isObject()) {
                    category.getValue().fields().forEachRemaining(entry -> {
                        if (entry.getValue().isTextual()) {
                            manifestVersions.put(entry.getKey(), entry.getValue().textValue());
                        }
                    });
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read MANIFEST.json: " + e.getMessage(), e);
        }
    }

    private static Path resolveDefaultRoot() {
        String env = System.getenv("CONX_ONTOLOGY_ROOT");
        if (env != null && !env.isBlank()) {
            return Path.of(env);
        }
        String prop = System.getProperty("conx.ontology.root");
        if (prop != null && !prop.isBlank()) {
            return Path.of(prop);
        }
        return Path.of("..", "..");
    }
}
