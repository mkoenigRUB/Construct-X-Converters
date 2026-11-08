package org.constructx.sdk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ConxCurieRegister}.
 */
class ConxCurieRegisterTest {

    // ── encode ───────────────────────────────────────────────────────────────

    @Test
    void encodeGaebClassIri() {
        String iri = "https://www.construct-x.org/ontology/gaeb-boq#BillOfQuantities";
        assertEquals("conx-gaeb:BillOfQuantities", ConxCurieRegister.encode(iri));
    }

    @Test
    void encodeXsdDatatype() {
        assertEquals("xsd:decimal", ConxCurieRegister.encode("http://www.w3.org/2001/XMLSchema#decimal"));
    }

    @Test
    void encodeRdfType() {
        assertEquals("rdf:type", ConxCurieRegister.encode("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"));
    }

    @Test
    void encodeScheduleActivity() {
        String iri = "https://www.construct-x.org/ontology/construction-schedule#Activity";
        assertEquals("conx-cs:Activity", ConxCurieRegister.encode(iri));
    }

    @Test
    void encodeUnregisteredIriStaysUnchanged() {
        String iri = "https://data.example.org/project-1/boq/BOQ-01";
        assertEquals(iri, ConxCurieRegister.encode(iri));
    }

    @Test
    void encodeNullReturnsNull() {
        assertNull(ConxCurieRegister.encode(null));
    }

    @Test
    void encodeEmptyReturnsEmpty() {
        assertEquals("", ConxCurieRegister.encode(""));
    }

    @Test
    void encodeVocabularyConcept() {
        String iri = "https://www.construct-x.org/vocab/boq-price-component-type#Labor";
        assertEquals("conx-pct:Labor", ConxCurieRegister.encode(iri));
    }

    @Test
    void encodeLinkOntologyIri() {
        String iri = "https://www.construct-x.org/ontology/schedule-breakdown-link#hasWbsNode";
        assertEquals("conx-sbl:hasWbsNode", ConxCurieRegister.encode(iri));
    }

    @Test
    void encodeSiteEquipmentType() {
        String iri = "https://www.construct-x.org/vocab/construction-site/equipment-type#Crane";
        assertEquals("conx-eq:Crane", ConxCurieRegister.encode(iri));
    }

    @Test
    void encodeNotificationType() {
        String iri = "https://www.construct-x.org/vocab/notification-content/notification-type#TaskRequested";
        assertEquals("conx-nt:TaskRequested", ConxCurieRegister.encode(iri));
    }

    @Test
    void encodeProvNamespace() {
        assertEquals("prov:wasGeneratedBy",
                ConxCurieRegister.encode("http://www.w3.org/ns/prov#wasGeneratedBy"));
    }

    @Test
    void encodeQudtUnit() {
        assertEquals("unit:M",
                ConxCurieRegister.encode("https://qudt.org/vocab/unit/M"));
    }

    // ── decode ───────────────────────────────────────────────────────────────

    @Test
    void decodeGaebCurie() {
        String iri = "https://www.construct-x.org/ontology/gaeb-boq#BillOfQuantities";
        assertEquals(iri, ConxCurieRegister.decode("conx-gaeb:BillOfQuantities"));
    }

    @Test
    void decodeXsdCurie() {
        assertEquals("http://www.w3.org/2001/XMLSchema#decimal",
                ConxCurieRegister.decode("xsd:decimal"));
    }

    @Test
    void decodeFullIriStaysUnchanged() {
        String iri = "https://data.example.org/project-1/boq/BOQ-01";
        assertEquals(iri, ConxCurieRegister.decode(iri));
    }

    @Test
    void decodeBlankNodeLabelStaysUnchanged() {
        assertEquals("_:b0", ConxCurieRegister.decode("_:b0"));
    }

    @Test
    void decodePlainStringStaysUnchanged() {
        assertEquals("some plain string", ConxCurieRegister.decode("some plain string"));
    }

    @Test
    void decodeNullReturnsNull() {
        assertNull(ConxCurieRegister.decode(null));
    }

    @Test
    void decodeUnknownPrefixStaysUnchanged() {
        assertEquals("unknown:Term", ConxCurieRegister.decode("unknown:Term"));
    }

    // ── localName ────────────────────────────────────────────────────────────

    @Test
    void localNameHashIri() {
        assertEquals("BillOfQuantities",
                ConxCurieRegister.localName("https://www.construct-x.org/ontology/gaeb-boq#BillOfQuantities"));
    }

    @Test
    void localNameSlashIri() {
        assertEquals("M",
                ConxCurieRegister.localName("https://qudt.org/vocab/unit/M"));
    }

    @Test
    void localNameOfPureStringReturnsLastSegment() {
        // last '/' in "https://data.example.org/project-1" is before "project-1"
        assertEquals("project-1", ConxCurieRegister.localName("https://data.example.org/project-1"));
    }

    // ── prefixFor / namespaceFor ─────────────────────────────────────────────

    @Test
    void prefixForGaebIri() {
        assertEquals("conx-gaeb",
                ConxCurieRegister.prefixFor("https://www.construct-x.org/ontology/gaeb-boq#BillOfQuantities"));
    }

    @Test
    void prefixForUnregisteredIriIsNull() {
        assertNull(ConxCurieRegister.prefixFor("https://data.example.org/project-1/boq/BOQ-01"));
    }

    @Test
    void namespaceForGaebPrefix() {
        assertEquals("https://www.construct-x.org/ontology/gaeb-boq#",
                ConxCurieRegister.namespaceFor("conx-gaeb"));
    }

    @Test
    void namespaceForUnknownPrefixIsNull() {
        assertNull(ConxCurieRegister.namespaceFor("unknown"));
    }
}
