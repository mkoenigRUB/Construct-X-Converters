package org.constructx.converter;

import org.constructx.sdk.IriFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates the converter output against the Construct-X SHACL profiles.
 * The ontology and vocabulary files are included in the data graph so that
 * class hierarchies (e.g. {@code BoQItem} ⊑ {@code CatalogAssignableEntity})
 * and {@code skos:Concept} declarations are known to the validator.
 */
class GaebBoqShaclValidationTest {

    /**
     * Ontology root for the GAEB BoQ vocabulary files. Defaults to the
     * <code>ontologies</code> git submodule at the repository root (pinned
     * commit, see <code>.gitmodules</code>). Can be overridden via the
     * <code>CONX_ONTOLOGY_ROOT</code> env var (matching the runtime services)
     * or a <code>conx.ontology.root</code> system property.
     */
    private static final Path ONTOLOGY_DIR = ontologyDir().resolve("gaeb-boq");

    private static Path ontologyDir() {
        String env = System.getenv("CONX_ONTOLOGY_ROOT");
        if (env != null && !env.isBlank()) {
            return Paths.get(env);
        }
        String prop = System.getProperty("conx.ontology.root");
        if (prop != null && !prop.isBlank()) {
            return Paths.get(prop);
        }
        return Paths.get("..", "..", "ontologies");
    }

    @Test
    void generatedTtlConformsToCoreShapes() throws Exception {
        Model data = readConverterOutput();
        addData(data, ONTOLOGY_DIR.resolve("gaeb-boq.ttl"));
        addData(data, ONTOLOGY_DIR.resolve("boq-price-component-type-vocab.ttl"));

        ValidationReport report = validate(data, ONTOLOGY_DIR.resolve("gaeb-boq-shapes-core.ttl"));
        assertTrue(report.conforms(), "SHACL core violations: " + report.getEntries());
    }

    @Test
    void generatedTtlConformsToPriceComponentTypeShapes() throws Exception {
        Model data = readConverterOutput();
        addData(data, ONTOLOGY_DIR.resolve("gaeb-boq.ttl"));
        addData(data, ONTOLOGY_DIR.resolve("boq-price-component-type-vocab.ttl"));

        ValidationReport report = validate(data,
                ONTOLOGY_DIR.resolve("gaeb-boq-shapes-price-component-type.ttl"));
        assertTrue(report.conforms(), "SHACL price-component-type violations: " + report.getEntries());
    }

    private static Model readConverterOutput() throws Exception {
        GaebBoqConverter converter = new GaebBoqConverter(
                new IriFactory("https://data.construct-x.org/gaeb"), "EUR");
        return converter.convert(
                Paths.get("src", "test", "resources", "gaeb-x80-example.xml").toFile());
    }

    private static void addData(Model model, Path file) throws Exception {
        model.read(new ByteArrayInputStream(
                Files.readString(file).getBytes(StandardCharsets.UTF_8)), null, "TTL");
    }

    private static ValidationReport validate(Model data, Path shapesFile) throws Exception {
        Model shapesModel = ModelFactory.createDefaultModel();
        shapesModel.read(new ByteArrayInputStream(
                Files.readString(shapesFile).getBytes(StandardCharsets.UTF_8)), null, "TTL");

        // Jena 4.10's SHACL parser rejects multiple sh:message per node;
        // keep only the first message of each subject.
        var shMsg = shapesModel.createProperty("http://www.w3.org/ns/shacl#message");
        Set<org.apache.jena.rdf.model.Resource> seen = new HashSet<>();
        shapesModel.listStatements(null, shMsg, (RDFNode) null).toList()
                .stream()
                .filter(s -> !seen.add(s.getSubject()))
                .forEach(shapesModel::remove);

        ShaclValidator validator = ShaclValidator.get();
        Shapes shapes = validator.parse(shapesModel.getGraph());
        return validator.validate(shapes, data.getGraph());
    }
}
