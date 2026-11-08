package org.constructx.sdk;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RdfInput}.
 */
class RdfInputTest {

    // ── TTL ──────────────────────────────────────────────────────────────────

    @Test
    void readTtl() throws Exception {
        String ttl = """
                @prefix conx-gaeb: <https://www.construct-x.org/ontology/gaeb-boq#> .
                <https://data.example.org/gaeb/boq/BOQ-01>
                    a conx-gaeb:BillOfQuantities ;
                    conx-gaeb:identifier "BOQ-01" ;
                    conx-gaeb:name "Test-BoQ" .
                """;
        Model model = RdfInput.read(ttl.getBytes(StandardCharsets.UTF_8),
                RdfInput.RdfFormat.TTL);
        try {
            assertTrue(model.size() >= 3);
            Resource boq = model.getResource("https://data.example.org/gaeb/boq/BOQ-01");
            assertTrue(boq.hasProperty(
                    org.apache.jena.vocabulary.RDF.type,
                    model.createResource("https://www.construct-x.org/ontology/gaeb-boq#BillOfQuantities")));
        } finally {
            model.close();
        }
    }

    @Test
    void readInvalidTtlThrows() {
        assertThrows(Exception.class, () ->
                RdfInput.read("<invalid ttl".getBytes(StandardCharsets.UTF_8),
                        RdfInput.RdfFormat.TTL));
    }

    // ── Plain JSON v2 ────────────────────────────────────────────────────────

    @Test
    void readPlainJsonNodes() throws Exception {
        String json = """
                {
                  "meta": {"format": "construct-x-plain-json", "version": 2, "tripleCount": 3},
                  "nodes": [
                    {
                      "id": "https://data.example.org/gaeb/boq/BOQ-01",
                      "type": "conx-gaeb:BillOfQuantities",
                      "conx-gaeb:identifier": "BOQ-01",
                      "conx-gaeb:name": "Test-BoQ",
                      "conx-gaeb:quantity": {"value": "100.0", "datatype": "xsd:decimal"},
                      "conx-gaeb:splitsAreExhaustive": true
                    }
                  ]
                }
                """;
        Model model = RdfInput.read(json.getBytes(StandardCharsets.UTF_8),
                RdfInput.RdfFormat.PLAIN_JSON);
        try {
            Resource boq = model.getResource("https://data.example.org/gaeb/boq/BOQ-01");
            // type
            assertTrue(boq.hasProperty(org.apache.jena.vocabulary.RDF.type,
                    model.createResource("https://www.construct-x.org/ontology/gaeb-boq#BillOfQuantities")));
            // identifier literal
            assertTrue(boq.hasProperty(
                    model.createProperty("https://www.construct-x.org/ontology/gaeb-boq#identifier"),
                    model.createLiteral("BOQ-01")));
            // typed decimal
            assertTrue(boq.hasProperty(
                    model.createProperty("https://www.construct-x.org/ontology/gaeb-boq#quantity"),
                    model.createTypedLiteral("100.0",
                            org.apache.jena.datatypes.xsd.XSDDatatype.XSDdecimal)));
            // boolean
            assertTrue(boq.hasProperty(
                    model.createProperty("https://www.construct-x.org/ontology/gaeb-boq#splitsAreExhaustive"),
                    model.createTypedLiteral(true, org.apache.jena.datatypes.xsd.XSDDatatype.XSDboolean)));
        } finally {
            model.close();
        }
    }

    @Test
    void readPlainJsonEnvelope() throws Exception {
        String json = """
                {
                  "id": "https://data.example.org/gaeb/boq/BOQ-01",
                  "identifier": "BOQ-01",
                  "semanticModel": {"type": "BillOfQuantities", "conformsTo": []},
                  "content": {
                    "entities": [
                      {
                        "id": "https://data.example.org/gaeb/boq/BOQ-01",
                        "type": "conx-gaeb:BillOfQuantities",
                        "conx-gaeb:identifier": "BOQ-01"
                      }
                    ]
                  }
                }
                """;
        Model model = RdfInput.read(json.getBytes(StandardCharsets.UTF_8),
                RdfInput.RdfFormat.PLAIN_JSON);
        try {
            assertTrue(model.size() >= 2);
        } finally {
            model.close();
        }
    }

    @Test
    void readPlainJsonWithoutEntitiesThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                RdfInput.read("{\"nodes\": []}".getBytes(StandardCharsets.UTF_8),
                        RdfInput.RdfFormat.PLAIN_JSON));
    }

    // ── RdfFormat.parse ─────────────────────────────────────────────────────

    @Test
    void parseTtlVariants() {
        assertEquals(RdfInput.RdfFormat.TTL, RdfInput.RdfFormat.parse("ttl"));
        assertEquals(RdfInput.RdfFormat.TTL, RdfInput.RdfFormat.parse("turtle"));
        assertEquals(RdfInput.RdfFormat.TTL, RdfInput.RdfFormat.parse("TTL"));
        assertEquals(RdfInput.RdfFormat.TTL, RdfInput.RdfFormat.parse(null));
    }

    @Test
    void parseJsonldVariants() {
        assertEquals(RdfInput.RdfFormat.JSONLD, RdfInput.RdfFormat.parse("jsonld"));
        assertEquals(RdfInput.RdfFormat.JSONLD, RdfInput.RdfFormat.parse("json-ld"));
        assertEquals(RdfInput.RdfFormat.JSONLD, RdfInput.RdfFormat.parse("json_ld"));
    }

    @Test
    void parsePlainJsonVariants() {
        assertEquals(RdfInput.RdfFormat.PLAIN_JSON, RdfInput.RdfFormat.parse("plainjson"));
        assertEquals(RdfInput.RdfFormat.PLAIN_JSON, RdfInput.RdfFormat.parse("plain-json"));
        assertEquals(RdfInput.RdfFormat.PLAIN_JSON, RdfInput.RdfFormat.parse("plain_json"));
    }

    @Test
    void parseUnknownFormatThrows() {
        assertThrows(IllegalArgumentException.class, () -> RdfInput.RdfFormat.parse("xml"));
    }
}
