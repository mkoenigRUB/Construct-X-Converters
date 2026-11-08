package org.constructx.sdk;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

/**
 * Reads Construct-X RDF input in all three supported serializations
 * (Turtle, JSON-LD and the plain-JSON exchange format) into a Jena model.
 *
 * <p>CURIEs in the plain-JSON input (keys, values, datatypes) are resolved
 * through {@link ConxCurieRegister}; full IRIs pass through unchanged.
 */
public final class RdfInput {

    /** Supported input serializations. */
    public enum RdfFormat {
        TTL("ttl"), JSONLD("jsonld"), PLAIN_JSON("plainjson");

        private final String param;

        RdfFormat(String param) {
            this.param = param;
        }

        public String param() {
            return param;
        }

        public static RdfFormat parse(String param) {
            if (param == null) {
                return TTL;
            }
            String p = param.trim().toLowerCase(java.util.Locale.ROOT);
            return switch (p) {
                case "ttl", "turtle" -> TTL;
                case "jsonld", "json-ld", "json_ld" -> JSONLD;
                case "plainjson", "plain", "plain-json", "plain_json" -> PLAIN_JSON;
                default -> throw new IllegalArgumentException(
                        "Unsupported input format: " + param + " (use 'ttl', 'jsonld' or 'plainjson')");
            };
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BASE = "urn:construct-x:uploaded-data:";

    private RdfInput() {
    }

    /**
     * Reads the given bytes as the given RDF format into a new model.
     *
     * @throws Exception on unparseable input or unknown format
     */
    public static Model read(byte[] data, RdfFormat format) throws Exception {
        switch (format) {
            case TTL -> {
                Model model = ModelFactory.createDefaultModel();
                try (var in = new ByteArrayInputStream(data)) {
                    RDFDataMgr.read(model, in, BASE, Lang.TURTLE);
                    return model;
                } catch (Exception e) {
                    model.close();
                    throw e;
                }
            }
            case JSONLD -> {
                Model model = ModelFactory.createDefaultModel();
                try (var in = new ByteArrayInputStream(data)) {
                    RDFDataMgr.read(model, in, BASE, Lang.JSONLD);
                    return model;
                } catch (Exception e) {
                    model.close();
                    throw e;
                }
            }
            case PLAIN_JSON -> {
                return readPlainJson(new String(data, StandardCharsets.UTF_8));
            }
            default -> throw new IllegalArgumentException("Unsupported format: " + format);
        }
    }

    /**
     * Reads a plain-JSON document (format version 2: {@code meta} +
     * {@code nodes} list, or the exchange package envelope with
     * {@code content.entities}) into a Jena model.
     */
    public static Model readPlainJson(String text) throws Exception {
        JsonNode root;
        try {
            root = MAPPER.readTree(text);
        } catch (Exception e) {
            throw new IllegalArgumentException("Plain JSON could not be parsed: " + e.getMessage(), e);
        }
        JsonNode nodes = root.get("nodes");
        if (nodes == null) {
            JsonNode content = root.get("content");
            if (content != null) {
                nodes = content.get("entities");
            }
        }
        if (nodes == null || !nodes.isArray() || nodes.isEmpty()) {
            throw new IllegalArgumentException(
                    "No entities found: expected a 'nodes' array or 'content.entities' array");
        }
        Model model = ModelFactory.createDefaultModel();
        for (JsonNode node : nodes) {
            readNode(model, node);
        }
        return model;
    }

    private static void readNode(Model model, JsonNode node) {
        JsonNode idNode = node.get("id");
        if (idNode == null || !idNode.isTextual()) {
            throw new IllegalArgumentException("Node without textual 'id': " + node);
        }
        String id = idNode.asText();
        Resource subject;
        if (id.startsWith("_:")) {
            subject = model.createResource(id.substring(2));
        } else {
            subject = model.createResource(ConxCurieRegister.decode(id));
        }
        JsonNode typeNode = node.get("type");
        if (typeNode != null) {
            if (typeNode.isTextual()) {
                addType(model, subject, typeNode.asText());
            } else if (typeNode.isArray()) {
                for (JsonNode t : typeNode) {
                    addType(model, subject, t.asText());
                }
            }
        }
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if ("id".equals(field) || "type".equals(field)) {
                continue;
            }
            Property property = model.createProperty(ConxCurieRegister.decode(field));
            JsonNode value = node.get(field);
            if (value.isArray()) {
                for (JsonNode v : value) {
                    addValue(model, subject, property, v);
                }
            } else {
                addValue(model, subject, property, value);
            }
        }
    }

    private static void addType(Model model, Resource subject, String type) {
        if (type.isBlank()) {
            return;
        }
        subject.addProperty(model.createProperty(ConxNamespaces.RDF_TYPE),
                model.createResource(ConxCurieRegister.decode(type)));
    }

    private static void addValue(Model model, Resource subject, Property property, JsonNode value) {
        if (value.isTextual()) {
            subject.addProperty(property, model.createLiteral(value.asText()));
            return;
        }
        if (value.isBoolean()) {
            subject.addProperty(property,
                    model.createTypedLiteral(value.asBoolean(),
                            org.apache.jena.datatypes.xsd.XSDDatatype.XSDboolean));
            return;
        }
        if (value.isNumber()) {
            subject.addProperty(property, model.createTypedLiteral(
                    value.toString(), org.apache.jena.datatypes.xsd.XSDDatatype.XSDdecimal));
            return;
        }
        if (value.isObject()) {
            JsonNode v = value.get("value");
            String lexical = v != null ? v.asText() : value.toString();
            JsonNode language = value.get("language");
            if (language != null && language.isTextual() && !language.asText().isBlank()) {
                subject.addProperty(property, model.createLiteral(lexical, language.asText()));
                return;
            }
            JsonNode datatype = value.get("datatype");
            if (datatype != null && datatype.isTextual() && !datatype.asText().isBlank()) {
                subject.addProperty(property, model.createTypedLiteral(
                        lexical, ConxCurieRegister.decode(datatype.asText())));
                return;
            }
            subject.addProperty(property, model.createLiteral(lexical));
            return;
        }
        subject.addProperty(property, model.createLiteral(value.toString()));
    }
}
