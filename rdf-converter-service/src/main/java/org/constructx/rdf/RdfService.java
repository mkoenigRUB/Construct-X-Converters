package org.constructx.rdf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.constructx.sdk.PlainJsonModelWriter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RiotException;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class RdfService {
    private static final String SHACL = "http://www.w3.org/ns/shacl#";
    private static final Pattern SAFE_ID = Pattern.compile("[a-z0-9][a-z0-9-]*");

    private final ObjectMapper objectMapper;
    private final Path ontologyRoot;
    private final Map<String, Profile> profiles = new HashMap<>();
    private final Map<String, String> versions = new HashMap<>();
    private Model ontologyGraph;

    public RdfService(ObjectMapper objectMapper,
                      @Value("${constructx.ontology-root:..}") String ontologyRoot) {
        this.objectMapper = objectMapper;
        this.ontologyRoot = Path.of(ontologyRoot).toAbsolutePath().normalize();
    }

    @PostConstruct
    void initialize() {
        try {
            if (!Files.isDirectory(ontologyRoot)) {
                throw new IllegalStateException("Ontology directory does not exist: " + ontologyRoot);
            }
            discoverProfiles();
            if (profiles.isEmpty()) {
                throw new IllegalStateException("No SHACL profiles found under " + ontologyRoot);
            }
            loadManifestVersions();
            loadOntologyGraph();
        } catch (IOException e) {
            throw new IllegalStateException("Could not load Construct-X ontology files from " + ontologyRoot, e);
        }
    }

    public List<ProfileSummary> listProfiles() {
        return profiles.values().stream()
                .sorted(Comparator.comparing(Profile::id))
                .map(profile -> new ProfileSummary(profile.id(), profile.name(),
                        profile.shapeFiles().size(), versions.getOrDefault(profile.id(), "unversioned")))
                .toList();
    }

    public Conversion convert(byte[] input, RdfFormat source, RdfFormat target, String fileName, boolean envelope) {
        if (source == RdfFormat.PLAIN_JSON) {
            throw new RdfInputException(
                    "Plain JSON (ohne RDF) kann hier nur erzeugt, nicht eingelesen werden. "
                            + "Verwenden Sie TTL oder JSON-LD als Quelldatei.");
        }
        Model model = read(input, source);
        try {
            String content = target == RdfFormat.PLAIN_JSON
                    ? PlainJsonModelWriter.writeEnvelopeOrNodes(model, envelope ? genericEnvelopeSpec(model) : null)
                    : write(model, target);
            return new Conversion(content,
                    outputName(fileName, target, target == RdfFormat.PLAIN_JSON && envelope),
                    target.mediaType());
        } finally {
            model.close();
        }
    }

    /**
     * Envelope spec for arbitrary RDF documents: the first (sorted) node
     * serves as package root; {@code conformsTo} carries the versioned
     * IRIs referenced by the document itself (best effort).
     */
    private static PlainJsonModelWriter.EnvelopeSpec genericEnvelopeSpec(Model model) {
        List<String> sortedIris = model.listSubjects().toList().stream()
                .filter(subject -> !subject.isAnon())
                .map(Resource::getURI)
                .sorted()
                .toList();
        if (sortedIris.isEmpty()) {
            return null;
        }
        Resource root = model.createResource(sortedIris.get(0));
        List<String> conformsTo = model.listObjects().toList().stream()
                .filter(object -> object.isResource() && object.asResource().isURIResource())
                .map(object -> object.asResource().getURI())
                .filter(iri -> iri.matches("https?://www\\.construct-x\\.org/(ontology|shapes|vocab)/.*\\d\\.\\d+\\.\\d+"))
                .distinct()
                .sorted()
                .limit(50)
                .toList();
        return new PlainJsonModelWriter.EnvelopeSpec(
                root.getURI(),
                PlainJsonModelWriter.identifierOf(model, root),
                PlainJsonModelWriter.typeLocalName(root),
                conformsTo);
    }

    private static String write(Model model, RdfFormat target) {
        var output = new ByteArrayOutputStream();
        RDFDataMgr.write(output, model, target.lang());
        return output.toString(StandardCharsets.UTF_8);
    }

    public ValidationResult validate(byte[] input, RdfFormat format, String profileId) {
        Profile profile = profiles.get(profileId);
        if (profile == null) {
            throw new RdfInputException("Unbekanntes SHACL-Profil: " + profileId);
        }

        Model data = read(input, format);
        data.add(ontologyGraph);
        try {
            Model shapesModel = ModelFactory.createDefaultModel();
            List<ValidationViolation> violations;
            try {
                for (Path shapeFile : profile.shapeFiles()) {
                    Model shapeDocument = readTurtle(shapeFile);
                    shapesModel.add(shapeDocument);
                    shapeDocument.close();
                }
                removeDuplicateMessages(shapesModel);
                Shapes shapes = ShaclValidator.get().parse(shapesModel.getGraph());
                ValidationReport report = ShaclValidator.get().validate(shapes, data.getGraph());
                violations = readViolations(report);
            } finally {
                shapesModel.close();
            }
            return new ValidationResult(profile.id(), profile.name(),
                    violations.isEmpty(), violations.size(), violations);
        } finally {
            data.close();
        }
    }

    private Model read(byte[] input, RdfFormat format) {
        if (format == RdfFormat.JSON_LD) {
            rejectRemoteContexts(input);
        }
        Model model = ModelFactory.createDefaultModel();
        try {
            RDFDataMgr.read(model, new ByteArrayInputStream(input),
                    "urn:construct-x:uploaded-data:", format.lang());
            return model;
        } catch (RiotException e) {
            model.close();
            throw new RdfInputException("Die Datei ist kein gültiges " + format.id() + "-RDF-Dokument: "
                    + e.getMessage(), e);
        }
    }

    private void rejectRemoteContexts(byte[] input) {
        try {
            JsonNode root = objectMapper.readTree(input);
            if (root == null) {
                throw new RdfInputException("Die JSON-LD-Datei ist leer.");
            }
            if (containsRemoteContext(root)) {
                throw new RdfInputException(
                        "Externe JSON-LD-Kontexte sind aus Sicherheitsgründen nicht erlaubt. "
                                + "Verwenden Sie einen eingebetteten Kontext.");
            }
        } catch (RdfInputException e) {
            throw e;
        } catch (IOException e) {
            throw new RdfInputException("Die JSON-LD-Datei enthält kein gültiges JSON.", e);
        }
    }

    private boolean containsRemoteContext(JsonNode node) {
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                if ("@context".equals(field.getKey()) && hasContextReference(field.getValue())) {
                    return true;
                }
                if ("@import".equals(field.getKey()) && field.getValue().isTextual()) {
                    return true;
                }
                if (containsRemoteContext(field.getValue())) {
                    return true;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                if (containsRemoteContext(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasContextReference(JsonNode node) {
        if (node.isTextual()) {
            return true;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (hasContextReference(child)) {
                    return true;
                }
            }
        } else if (node.isObject()) {
            JsonNode imported = node.get("@import");
            if (imported != null && imported.isTextual()) {
                return true;
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                if ("@context".equals(field.getKey()) && hasContextReference(field.getValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void discoverProfiles() throws IOException {
        try (Stream<Path> directories = Files.list(ontologyRoot)) {
            for (Path directory : directories.filter(Files::isDirectory).toList()) {
                List<Path> shapeFiles;
                try (Stream<Path> files = Files.walk(directory)) {
                    shapeFiles = files.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().contains("-shapes"))
                            .filter(path -> path.toString().endsWith(".ttl"))
                            .sorted()
                            .toList();
                }
                if (shapeFiles.isEmpty()) {
                    continue;
                }
                if (directory.getFileName().toString().equals("link-ontologies")) {
                    for (Path shapeFile : shapeFiles) {
                        String file = shapeFile.getFileName().toString();
                        String id = file.substring(0, file.indexOf("-shapes"));
                        addProfile(id, shapeFile, List.of(shapeFile));
                    }
                } else {
                    String id = directory.getFileName().toString();
                    addProfile(id, directory, shapeFiles);
                }
            }
        }
    }

    private void addProfile(String id, Path directory, List<Path> shapeFiles) {
        if (!SAFE_ID.matcher(id).matches()) {
            throw new IllegalStateException("Invalid profile identifier: " + id);
        }
        String title = id.replace('-', ' ');
        title = Character.toUpperCase(title.charAt(0)) + title.substring(1);
        Path dataDirectory = directory;
        if (directory.getFileName().toString().endsWith(".ttl")) {
            dataDirectory = directory.getParent();
        }
        profiles.put(id, new Profile(id, title, dataDirectory, shapeFiles));
    }

    private void loadOntologyGraph() throws IOException {
        ontologyGraph = ModelFactory.createDefaultModel();
        Set<Path> directories = new HashSet<>();
        profiles.values().forEach(profile -> directories.add(profile.dataDirectory()));
        for (Path directory : directories) {
            try (Stream<Path> files = Files.walk(directory)) {
                for (Path file : files.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".ttl"))
                        .filter(path -> !path.getFileName().toString().contains("-shapes"))
                        .filter(path -> !path.getFileName().toString().toLowerCase(Locale.ROOT).contains("example"))
                        .toList()) {
                    Model module = readTurtle(file);
                    ontologyGraph.add(module);
                    module.close();
                }
            }
        }
    }

    private void loadManifestVersions() throws IOException {
        Path manifest = ontologyRoot.resolve("MANIFEST.json");
        if (!Files.isRegularFile(manifest)) {
            return;
        }
        JsonNode root = objectMapper.readTree(manifest.toFile());
        root.fields().forEachRemaining(category -> {
            if (category.getValue().isObject()) {
                category.getValue().fields().forEachRemaining(entry -> {
                    if (entry.getValue().isTextual()) {
                        versions.put(entry.getKey(), entry.getValue().textValue());
                    }
                });
            }
        });
    }

    private Model readTurtle(Path file) {
        Model model = ModelFactory.createDefaultModel();
        try {
            RDFDataMgr.read(model, file.toUri().toString());
            return model;
        } catch (RuntimeException e) {
            model.close();
            throw new IllegalStateException("Could not parse ontology/profile file " + file, e);
        }
    }

    private List<ValidationViolation> readViolations(ValidationReport report) {
        Model reportModel = report.getModel();
        Resource resultType = ResourceFactory.createResource(SHACL + "ValidationResult");
        var resultMessage = ResourceFactory.createProperty(SHACL + "resultMessage");
        var focusNode = ResourceFactory.createProperty(SHACL + "focusNode");
        var resultPath = ResourceFactory.createProperty(SHACL + "resultPath");
        var severity = ResourceFactory.createProperty(SHACL + "resultSeverity");
        var sourceShape = ResourceFactory.createProperty(SHACL + "sourceShape");

        List<ValidationViolation> violations = new ArrayList<>();
        for (Resource result : reportModel.listResourcesWithProperty(
                org.apache.jena.vocabulary.RDF.type, resultType).toList()) {
            violations.add(new ValidationViolation(
                    value(result, severity),
                    value(result, focusNode),
                    value(result, resultPath),
                    value(result, sourceShape),
                    value(result, resultMessage)));
        }
        reportModel.close();
        return violations;
    }

    private static String value(Resource resource, org.apache.jena.rdf.model.Property property) {
        RDFNode node = resource.getProperty(property) == null
                ? null : resource.getProperty(property).getObject();
        if (node == null) {
            return "";
        }
        return node.isLiteral() ? node.asLiteral().getString() : node.toString();
    }

    private static void removeDuplicateMessages(Model model) {
        var message = ResourceFactory.createProperty(SHACL + "message");
        Set<Resource> seen = new HashSet<>();
        model.listStatements(null, message, (RDFNode) null).toList().stream()
                .filter(statement -> !seen.add(statement.getSubject()))
                .forEach(model::remove);
    }

    private static String outputName(String originalName, RdfFormat target, boolean envelope) {
        String name = originalName == null ? "converted" : Path.of(originalName).getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        base = base.replaceAll("[^A-Za-z0-9._-]", "_");
        return (base.isBlank() ? "converted" : base) + (envelope ? "-package" : "") + target.extension();
    }

    public record ProfileSummary(String id, String name, int shapeFiles, String version) {}
    public record Conversion(String content, String fileName, String mediaType) {}
    public record ValidationViolation(String severity, String focusNode, String path,
                                      String sourceShape, String message) {}
    public record ValidationResult(String profile, String profileName, boolean valid,
                                   int violationCount, List<ValidationViolation> violations) {}
    private record Profile(String id, String name, Path dataDirectory, List<Path> shapeFiles) {}
}
