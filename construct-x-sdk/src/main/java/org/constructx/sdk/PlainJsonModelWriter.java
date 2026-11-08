package org.constructx.sdk;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Serializes a Jena model to plain JSON without any RDF or JSON-LD
 * semantics (no {@code @id}, no {@code @context}, no JSON-LD keywords).
 *
 * <p>Output shape (format version 2, aligned with the Construct-X exchange
 * package convention):
 * <pre>{@code
 * {
 *   "meta": {"format": "construct-x-plain-json", "version": 2, "tripleCount": 42},
 *   "nodes": [
 *     {
 *       "id": "https://data.example.org/boq/BOQ-01",
 *       "type": "conx-gaeb:BillOfQuantities",
 *       "conx-gaeb:identifier": "BOQ-01",
 *       "conx-gaeb:name": "plain RDF literal without language and datatype"
 *     }
 *   ]
 * }
 * </pre>
 *
 * <p>Property keys and datatype values are CURIEs resolved through
 * {@link ConxCurieRegister}; IRIs whose namespace is not registered fall
 * back to their full IRI. Type values are CURIEs as well; a single type is
 * written as a string, multiple types as an array.
 */
public final class PlainJsonModelWriter {

    static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
    static final String XSD_STRING = "http://www.w3.org/2001/XMLSchema#string";
    static final String XSD_BOOLEAN = XSDDatatype.XSDboolean.getURI();

    /**
     * Describes the header fields of an exchange package envelope.
     *
     * @param id         IRI of the package/root entity
     * @param identifier compact identifier of the package/root entity
     * @param type       local type name (e.g. {@code BillOfQuantities})
     * @param conformsTo versioned ontology/shape IRIs the content conforms to
     */
    public record EnvelopeSpec(String id, String identifier, String type, List<String> conformsTo) {
    }

    private PlainJsonModelWriter() {
    }

    /**
     * Writes the whole model as a pretty-printed plain JSON document.
     */
    public static String write(Model model) {
        List<Resource> subjects = sortedSubjects(model);
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"meta\": {\"format\": \"construct-x-plain-json\", \"version\": 2, \"tripleCount\": ")
                .append(model.size()).append("},\n");
        sb.append("  \"nodes\": [");
        if (!subjects.isEmpty()) {
            List<String> nodeStrings = subjects.stream()
                    .map(subject -> "    " + node(model, subject, 4))
                    .toList();
            sb.append('\n').append(String.join(",\n", nodeStrings)).append("\n  ");
        }
        sb.append("]\n}");
        return sb.toString();
    }

    /**
     * Writes the exchange package envelope when {@code spec} is provided,
     * otherwise the plain node-list document.
     */
    public static String writeEnvelopeOrNodes(Model model, EnvelopeSpec spec) {
        return spec == null ? write(model) : writeEnvelope(model, spec);
    }

    /**
     * Writes the model wrapped in the exchange package envelope.
     */
    public static String writeEnvelope(Model model, EnvelopeSpec spec) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"id\": ").append(quote(spec.id())).append(",\n");
        sb.append("  \"identifier\": ").append(quote(spec.identifier())).append(",\n");
        sb.append("  \"semanticModel\": {\n");
        sb.append("    \"type\": ").append(quote(spec.type()));
        if (spec.conformsTo() != null && !spec.conformsTo().isEmpty()) {
            List<String> items = spec.conformsTo().stream().map(PlainJsonModelWriter::quote).toList();
            sb.append(",\n    \"conformsTo\": ").append(joinArray(items, 4));
        }
        sb.append("\n  },\n");
        sb.append("  \"content\": {\n");
        sb.append("    \"entities\": ").append(writeEntities(model)).append("\n");
        sb.append("  }\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Writes only the entity objects as a pretty-printed JSON array.
     */
    public static String writeEntities(Model model) {
        return writeEntities(model, 6);
    }

    static String writeEntities(Model model, int elementIndent) {
        List<Resource> subjects = sortedSubjects(model);
        StringBuilder sb = new StringBuilder("[");
        if (!subjects.isEmpty()) {
            String pad = " ".repeat(elementIndent);
            List<String> nodeStrings = subjects.stream()
                    .map(subject -> pad + node(model, subject, elementIndent))
                    .toList();
            sb.append('\n').append(String.join(",\n", nodeStrings)).append('\n')
                    .append(" ".repeat(elementIndent - 2));
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * The value of the first literal property of {@code entity} whose local
     * name is {@code identifier}; the local name of the entity IRI when
     * none is present.
     */
    public static String identifierOf(Model model, Resource entity) {
        StmtIterator iterator = model.listStatements(entity, (Property) null, (RDFNode) null);
        try {
            while (iterator.hasNext()) {
                Statement statement = iterator.next();
                if ("identifier".equals(ConxCurieRegister.localName(statement.getPredicate().getURI()))
                        && statement.getObject().isLiteral()) {
                    return statement.getObject().asLiteral().getString();
                }
            }
        } finally {
            iterator.close();
        }
        return entity.isURIResource() ? ConxCurieRegister.localName(entity.getURI()) : null;
    }

    /**
     * The local name of the first {@code rdf:type} of {@code entity};
     * {@code "Resource"} when untyped.
     */
    public static String typeLocalName(Resource entity) {
        RDFNode type = entity.getProperty(RDF.type) == null
                ? null : entity.getProperty(RDF.type).getObject();
        if (type != null && type.isResource()) {
            return ConxCurieRegister.localName(type.asResource().getURI());
        }
        return "Resource";
    }

    private static String node(Model model, Resource subject, int indent) {
        List<Statement> statements = new ArrayList<>();
        model.listStatements(subject, (Property) null, (RDFNode) null)
                .forEachRemaining(statements::add);

        Map<String, String> keyToIri = new HashMap<>();
        Set<String> collidingKeys = new HashSet<>();
        for (Statement statement : statements) {
            Property property = statement.getPredicate();
            if (RDF_TYPE.equals(property.getURI())) {
                continue;
            }
            String key = keyFor(property.getURI());
            String previous = keyToIri.putIfAbsent(key, property.getURI());
            if (previous != null && !previous.equals(property.getURI())) {
                collidingKeys.add(key);
            }
        }

        List<String> types = new ArrayList<>();
        Map<String, List<RDFNode>> properties = new TreeMap<>();
        for (Statement statement : statements) {
            Property property = statement.getPredicate();
            if (RDF_TYPE.equals(property.getURI())) {
                types.add(nodeId(statement.getObject()));
                continue;
            }
            String key = keyFor(property.getURI());
            if (collidingKeys.contains(key)) {
                key = property.getURI();
            }
            properties.computeIfAbsent(key, k -> new ArrayList<>()).add(statement.getObject());
        }
        types.sort(Comparator.naturalOrder());

        List<String> fields = new ArrayList<>();
        fields.add("\"id\": " + quote(nodeId(subject)));
        if (types.size() == 1) {
            fields.add("\"type\": " + quote(curie(types.get(0))));
        } else if (types.size() > 1) {
            List<String> items = types.stream().map(t -> quote(curie(t))).toList();
            fields.add("\"type\": " + joinArray(items, 6));
        }
        for (Map.Entry<String, List<RDFNode>> entry : properties.entrySet()) {
            List<RDFNode> values = entry.getValue();
            if (values.size() == 1) {
                fields.add(quote(entry.getKey()) + ": " + value(values.get(0)));
            } else {
                List<String> items = values.stream().map(PlainJsonModelWriter::value).toList();
                fields.add(quote(entry.getKey()) + ": " + joinArray(items, 6));
            }
        }
        String openPad = " ".repeat(indent);
        String fieldPad = " ".repeat(indent + 2);
        return "{\n" + fieldPad + String.join(",\n" + fieldPad, fields) + "\n" + openPad + "}";
    }

    private static String keyFor(String propertyIri) {
        return ConxCurieRegister.encode(propertyIri);
    }

    private static String curie(String iri) {
        return ConxCurieRegister.encode(iri);
    }

    private static String value(RDFNode node) {
        if (node.isLiteral()) {
            Literal literal = node.asLiteral();
            String language = literal.getLanguage();
            String datatype = literal.getDatatypeURI();
            if (XSD_BOOLEAN.equals(datatype)) {
                return "true".equals(literal.getString()) ? "true" : "false";
            }
            if (language != null && !language.isEmpty()) {
                return "{"
                        + "\"value\": " + quote(literal.getString())
                        + ", \"language\": " + quote(language)
                        + "}";
            }
            if (datatype == null || XSD_STRING.equals(datatype)) {
                return quote(literal.getString());
            }
            return "{"
                    + "\"value\": " + quote(literal.getString())
                    + ", \"datatype\": " + quote(curie(datatype))
                    + "}";
        }
        return quote(curie(nodeId(node)));
    }

    private static String nodeId(RDFNode node) {
        Resource resource = node.asResource();
        return resource.isAnon() ? "_:" + resource.getId().getLabelString() : resource.getURI();
    }

    private static List<Resource> sortedSubjects(Model model) {
        List<Resource> subjects = new ArrayList<>();
        model.listSubjects().forEach(subjects::add);
        subjects.sort(Comparator.comparing(
                (Resource resource) -> resource.isAnon()
                        ? "\uFFFF" + resource.getId().getLabelString()
                        : resource.getURI()));
        return subjects;
    }

    private static String joinArray(List<String> items, int indent) {
        String pad = " ".repeat(indent);
        return "[\n" + pad + "  " + String.join(",\n" + pad + "  ", items) + "\n" + pad + "]";
    }

    private static String quote(String text) {
        return "\"" + escape(text) + "\"";
    }

    private static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
