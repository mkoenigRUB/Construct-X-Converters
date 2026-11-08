package org.constructx.msproject;

import org.constructx.sdk.PlainJsonModelWriter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class MsProjectService {

    private static final String SHACL = "http://www.w3.org/ns/shacl#";

    private final Path ontologyRoot;
    private Model ontologyGraph;
    private Model shapesModel;

    public MsProjectService(@Value("${constructx.ontology-root:..}") String ontologyRoot) {
        this.ontologyRoot = Path.of(ontologyRoot).toAbsolutePath().normalize();
    }

    @PostConstruct
    void init() throws IOException {
        Path scheduleDir = ontologyRoot.resolve("construction-schedule");
        if (!Files.isDirectory(scheduleDir)) {
            throw new IllegalStateException("construction-schedule ontology directory not found: " + scheduleDir);
        }
        ontologyGraph = ModelFactory.createDefaultModel();
        try (Stream<Path> files = Files.walk(scheduleDir)) {
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
        shapesModel = ModelFactory.createDefaultModel();
        try (Stream<Path> files = Files.walk(scheduleDir)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().contains("-shapes"))
                    .filter(path -> path.toString().endsWith(".ttl"))
                    .sorted()
                    .toList()) {
                Model module = readTurtle(file);
                shapesModel.add(module);
                module.close();
            }
        }
        removeDuplicateMessages(shapesModel);
    }

    public ConversionResult convert(byte[] xml, String format, String scheduleType, String iriBase, boolean envelope) throws Exception {
        OutputFormat outputFormat = OutputFormat.parse(format);
        MsProjectConverter converter = new MsProjectConverter(iriBase, scheduleType);
        Model model = converter.convert(xml);
        String content = switch (outputFormat) {
            case TURTLE -> converter.toTtl(model);
            case JSON_LD -> converter.toJsonLd(model);
            case PLAIN_JSON -> PlainJsonModelWriter.writeEnvelopeOrNodes(
                    model, envelope ? rootEnvelopeSpec(converter, model) : null);
        };
        String fileName = "schedule"
                + (envelope && outputFormat == OutputFormat.PLAIN_JSON ? "-package" : "")
                + "." + outputFormat.extension();
        return new ConversionResult(content, fileName, outputFormat.mediaType(), model.size());
    }

    /** Envelope spec derived from the root schedule entity, or {@code null}. */
    private static PlainJsonModelWriter.EnvelopeSpec rootEnvelopeSpec(MsProjectConverter converter, Model model) {
        Resource root = converter.getRootSchedule();
        if (root == null) {
            return null;
        }
        return new PlainJsonModelWriter.EnvelopeSpec(
                root.getURI(),
                PlainJsonModelWriter.identifierOf(model, root),
                PlainJsonModelWriter.typeLocalName(root),
                MsProjectConverter.CONFORMS_TO);
    }

    public ValidationResult validate(byte[] xml, String scheduleType, String iriBase) throws Exception {
        MsProjectConverter converter = new MsProjectConverter(iriBase, scheduleType);
        Model data = converter.convert(xml);
        data.add(ontologyGraph);
        try {
            Shapes shapes = ShaclValidator.get().parse(shapesModel.getGraph());
            ValidationReport report = ShaclValidator.get().validate(shapes, data.getGraph());
            List<ValidationViolation> violations = readViolations(report);
            return new ValidationResult("construction-schedule", "Construction Schedule",
                    violations.isEmpty(), violations.size(), violations);
        } finally {
            data.close();
        }
    }

    private static Model readTurtle(Path file) {
        Model model = ModelFactory.createDefaultModel();
        try (var in = Files.newInputStream(file)) {
            RDFDataMgr.read(model, in, file.toAbsolutePath().toString(), Lang.TURTLE);
            return model;
        } catch (Exception e) {
            model.close();
            throw new IllegalStateException("Could not parse ontology file " + file, e);
        }
    }

    private static List<ValidationViolation> readViolations(ValidationReport report) {
        Model reportModel = report.getModel();
        Resource resultType = ResourceFactory.createResource(SHACL + "ValidationResult");
        Property resultMessage = ResourceFactory.createProperty(SHACL + "resultMessage");
        Property focusNode = ResourceFactory.createProperty(SHACL + "focusNode");
        Property resultPath = ResourceFactory.createProperty(SHACL + "resultPath");
        Property severity = ResourceFactory.createProperty(SHACL + "resultSeverity");

        List<ValidationViolation> violations = new ArrayList<>();
        for (Resource result : reportModel.listResourcesWithProperty(
                org.apache.jena.vocabulary.RDF.type, resultType).toList()) {
            violations.add(new ValidationViolation(
                    value(result, severity),
                    value(result, resultMessage),
                    value(result, focusNode),
                    value(result, resultPath)));
        }
        return violations;
    }

    private static String value(Resource resource, Property property) {
        if (!resource.hasProperty(property)) {
            return "";
        }
        StmtIterator iterator = resource.listProperties(property);
        try {
            if (!iterator.hasNext()) {
                return "";
            }
            var node = iterator.next().getObject();
            return node.isLiteral() ? node.asLiteral().getString() : node.toString();
        } finally {
            iterator.close();
        }
    }

    private static void removeDuplicateMessages(Model model) {
        Property message = ResourceFactory.createProperty(SHACL + "message");
        Set<Resource> seen = new HashSet<>();
        model.listStatements(null, message, (org.apache.jena.rdf.model.RDFNode) null).toList().stream()
                .filter(statement -> !seen.add(statement.getSubject()))
                .forEach(model::remove);
    }

    public record ConversionResult(String content, String fileName, String mediaType, long tripleCount) {
    }

    public record ValidationResult(String profileId, String profileName, boolean valid,
                                   int violationCount, List<ValidationViolation> violations) {
    }

    public record ValidationViolation(String severity, String message, String focusNode, String path) {
    }
}
