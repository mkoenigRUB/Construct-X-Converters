package org.constructx.sdk;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SHACL validation helper for Construct-X data against the SHACL profiles
 * of the Construct-X ontology package.
 *
 * <p>Usage:
 * <pre>{@code
 * OntologyRegistry registry = new OntologyRegistry();
 * ShaclValidator validator = new ShaclValidator(registry);
 *
 * Model dataModel = ...; // the data to validate
 * ValidationResult result = validator.validate(
 *         dataModel,
 *         registry.shapeFiles("gaeb-boq"),
 *         List.of("gaeb-boq"));
 *
 * if (!result.isValid()) {
 *     result.violations().forEach(v ->
 *         System.out.println(v.severity() + " " + v.message()));
 * }
 * }</pre>
 */
public final class ShaclValidatorHelper {

    private static final String SHACL = "http://www.w3.org/ns/shacl#";

    private final OntologyRegistry registry;

    public ShaclValidatorHelper(OntologyRegistry registry) {
        this.registry = registry;
    }

    /**
     * Validates the given data model against the given SHACL profile files.
     *
     * @param dataModel   the RDF model to validate
     * @param shapeFiles  the SHACL profile TTL files to load (from
     *                    {@link OntologyRegistry#shapeFiles(String)})
     * @param moduleIds   module ids whose ontology files should be added
     *                    to the data graph (needed for {@code sh:class}
     *                    and {@code sh:in} constraints); pass the relevant
     *                    module and its vocabulary modules
     * @return validation result with all violations
     */
    public ValidationResult validate(Model dataModel,
                                     List<Path> shapeFiles,
                                     List<String> moduleIds) {
        Model data = dataModel;
        Model shapesModel = ModelFactory.createDefaultModel();
        List<ValidationViolation> violations;
        try {
            // Load ontology files into the data graph (for sh:class / sh:in)
            if (moduleIds != null && !moduleIds.isEmpty()) {
                var ontologyGraph = registry.loadOntologyGraph(moduleIds.toArray(new String[0]));
                data = ModelFactory.createDefaultModel();
                data.add(dataModel);
                data.add(ontologyGraph);
            }

            // Load shape files
            for (Path shapeFile : shapeFiles) {
                Model shapeDocument = ModelFactory.createDefaultModel();
                RDFDataMgr.read(shapeDocument, shapeFile.toUri().toString());
                shapesModel.add(shapeDocument);
                shapeDocument.close();
            }
            removeDuplicateMessages(shapesModel);

            // Validate
            Shapes shapes = ShaclValidator.get().parse(shapesModel.getGraph());
            ValidationReport report = ShaclValidator.get().validate(shapes, data.getGraph());
            violations = readViolations(report);
        } finally {
            shapesModel.close();
            if (data != dataModel) {
                data.close();
            }
        }
        return new ValidationResult(violations.isEmpty(), violations.size(), violations);
    }

    private static void removeDuplicateMessages(Model model) {
        var message = ResourceFactory.createProperty(SHACL + "message");
        Set<Resource> seen = new HashSet<>();
        model.listStatements(null, message, (RDFNode) null).toList().stream()
                .filter(statement -> !seen.add(statement.getSubject()))
                .forEach(model::remove);
    }

    private static List<ValidationViolation> readViolations(ValidationReport report) {
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

    // ── result types ─────────────────────────────────────────────────────────

    public record ValidationViolation(
            String severity,
            String focusNode,
            String path,
            String sourceShape,
            String message) {
    }

    public record ValidationResult(
            boolean valid,
            int violationCount,
            List<ValidationViolation> violations) {

        public static ValidationResult conform() {
            return new ValidationResult(true, 0, List.of());
        }
    }
}
