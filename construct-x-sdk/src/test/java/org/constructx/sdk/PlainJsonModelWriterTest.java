package org.constructx.sdk;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PlainJsonModelWriter}.
 */
class PlainJsonModelWriterTest {

    private static final String GAEB = "https://www.construct-x.org/ontology/gaeb-boq#";
    private static final String XSD_DECIMAL = "http://www.w3.org/2001/XMLSchema#decimal";
    private static final String XSD_BOOLEAN = "http://www.w3.org/2001/XMLSchema#boolean";

    private Model sampleModel() {
        Model model = ModelFactory.createDefaultModel();
        Resource boq = model.createResource("https://data.example.org/gaeb/boq/BOQ-01");
        boq.addProperty(RDF.type, model.createResource(GAEB + "BillOfQuantities"));
        boq.addProperty(model.createProperty(GAEB + "identifier"), "BOQ-01");
        boq.addProperty(model.createProperty(GAEB + "name"), "Test BoQ");
        boq.addProperty(model.createProperty(GAEB + "quantity"),
                model.createTypedLiteral("100.0", org.apache.jena.datatypes.xsd.XSDDatatype.XSDdecimal));
        boq.addProperty(model.createProperty(GAEB + "splitsAreExhaustive"),
                model.createTypedLiteral(true, org.apache.jena.datatypes.xsd.XSDDatatype.XSDboolean));
        boq.addProperty(model.createProperty(GAEB + "longText"),
                model.createLiteral("Langer Text", "de"));
        boq.addProperty(model.createProperty(GAEB + "hasDirectItem"),
                model.createResource("https://data.example.org/gaeb/item/03.02.010"));
        return model;
    }

    @Test
    void writeContainsMetaBlock() {
        Model model = sampleModel();
        try {
            String json = PlainJsonModelWriter.write(model);
            assertTrue(json.contains("\"format\": \"construct-x-plain-json\""));
            assertTrue(json.contains("\"version\": 2"));
            assertTrue(json.contains("\"tripleCount\": " + model.size()));
        } finally {
            model.close();
        }
    }

    @Test
    void writeContainsNodeWithCurieTypes() {
        Model model = sampleModel();
        try {
            String json = PlainJsonModelWriter.write(model);
            assertTrue(json.contains("\"id\": \"https://data.example.org/gaeb/boq/BOQ-01\""));
            assertTrue(json.contains("\"type\": \"conx-gaeb:BillOfQuantities\""));
            assertTrue(json.contains("\"conx-gaeb:identifier\": \"BOQ-01\""));
            assertTrue(json.contains("\"conx-gaeb:name\": \"Test BoQ\""));
        } finally {
            model.close();
        }
    }

    @Test
    void writeTypedDecimalAsObject() {
        Model model = sampleModel();
        try {
            String json = PlainJsonModelWriter.write(model);
            assertTrue(json.contains("\"conx-gaeb:quantity\": {"));
            assertTrue(json.contains("\"value\": \"100.0\""));
            assertTrue(json.contains("\"datatype\": \"xsd:decimal\""));
        } finally {
            model.close();
        }
    }

    @Test
    void writeBooleanAsNative() {
        Model model = sampleModel();
        try {
            String json = PlainJsonModelWriter.write(model);
            assertTrue(json.contains("\"conx-gaeb:splitsAreExhaustive\": true"));
        } finally {
            model.close();
        }
    }

    @Test
    void writeLanguageTaggedLiteralAsObject() {
        Model model = sampleModel();
        try {
            String json = PlainJsonModelWriter.write(model);
            assertTrue(json.contains("\"conx-gaeb:longText\": {"));
            assertTrue(json.contains("\"value\": \"Langer Text\""));
            assertTrue(json.contains("\"language\": \"de\""));
        } finally {
            model.close();
        }
    }

    @Test
    void writeResourceReferenceAsIri() {
        Model model = sampleModel();
        try {
            String json = PlainJsonModelWriter.write(model);
            assertTrue(json.contains(
                    "\"conx-gaeb:hasDirectItem\": \"https://data.example.org/gaeb/item/03.02.010\""));
        } finally {
            model.close();
        }
    }

    @Test
    void writeEnvelopeContainsRequiredFields() {
        Model model = sampleModel();
        try {
            PlainJsonModelWriter.EnvelopeSpec spec = new PlainJsonModelWriter.EnvelopeSpec(
                    "https://data.example.org/gaeb/boq/BOQ-01",
                    "BOQ-01",
                    "BillOfQuantities",
                    List.of("https://www.construct-x.org/ontology/gaeb-boq/0.5.0"));
            String json = PlainJsonModelWriter.writeEnvelope(model, spec);
            assertTrue(json.contains("\"id\": \"https://data.example.org/gaeb/boq/BOQ-01\""));
            assertTrue(json.contains("\"identifier\": \"BOQ-01\""));
            assertTrue(json.contains("\"type\": \"BillOfQuantities\""));
            assertTrue(json.contains("\"conformsTo\":"));
            assertTrue(json.contains("\"entities\":"));
        } finally {
            model.close();
        }
    }

    @Test
    void writeEnvelopeOrNodesWithNullSpecWritesPlainNodes() {
        Model model = sampleModel();
        try {
            String withEnvelope = PlainJsonModelWriter.writeEnvelopeOrNodes(model,
                    new PlainJsonModelWriter.EnvelopeSpec("id", "id", "type", List.of()));
            String without = PlainJsonModelWriter.writeEnvelopeOrNodes(model, null);
            assertTrue(withEnvelope.contains("\"semanticModel\""));
            assertFalse(without.contains("\"semanticModel\""));
        } finally {
            model.close();
        }
    }

    @Test
    void identifierOfFindsIdentifierProperty() {
        Model model = sampleModel();
        try {
            Resource boq = model.getResource("https://data.example.org/gaeb/boq/BOQ-01");
            assertEquals("BOQ-01", PlainJsonModelWriter.identifierOf(model, boq));
        } finally {
            model.close();
        }
    }

    @Test
    void identifierOfFallsBackToLocalName() {
        Model model = ModelFactory.createDefaultModel();
        try {
            Resource item = model.createResource("https://data.example.org/gaeb/item/03.02.010");
            assertEquals("03.02.010", PlainJsonModelWriter.identifierOf(model, item));
        } finally {
            model.close();
        }
    }

    @Test
    void typeLocalNameReturnsLocalTypeName() {
        Model model = sampleModel();
        try {
            Resource boq = model.getResource("https://data.example.org/gaeb/boq/BOQ-01");
            assertEquals("BillOfQuantities", PlainJsonModelWriter.typeLocalName(boq));
        } finally {
            model.close();
        }
    }

    @Test
    void typeLocalNameUntypedResourceReturnsResource() {
        Model model = ModelFactory.createDefaultModel();
        try {
            Resource untyped = model.createResource("https://data.example.org/something");
            assertEquals("Resource", PlainJsonModelWriter.typeLocalName(untyped));
        } finally {
            model.close();
        }
    }

    @Test
    void roundtripModelToPlainJsonAndBack() throws Exception {
        Model original = sampleModel();
        String json;
        long tripleCount;
        try {
            tripleCount = original.size();
            json = PlainJsonModelWriter.write(original);
        } finally {
            original.close();
        }
        Model restored = RdfInput.read(json.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                RdfInput.RdfFormat.PLAIN_JSON);
        try {
            assertEquals(tripleCount, restored.size(),
                    "Triple count should be identical after roundtrip");
        } finally {
            restored.close();
        }
    }
}
