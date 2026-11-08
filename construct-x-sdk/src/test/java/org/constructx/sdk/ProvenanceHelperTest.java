package org.constructx.sdk;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.DCTerms;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ProvenanceHelper}.
 */
class ProvenanceHelperTest {

    private static final Instant TS = Instant.parse("2026-11-04T12:00:00Z");

    @Test
    void addGeneratedByAddsTriple() {
        Model model = ModelFactory.createDefaultModel();
        try {
            Resource entity = model.createResource("https://data.example.org/boq/BOQ-01");
            Resource activity = model.createResource("https://data.example.org/prov/act-1");
            ProvenanceHelper.addGeneratedBy(model, entity, activity);
            assertTrue(entity.hasProperty(
                    model.createProperty(ProvenanceHelper.PROV_WAS_GENERATED_BY), activity));
        } finally {
            model.close();
        }
    }

    @Test
    void addDerivedFromAddsTriple() {
        Model model = ModelFactory.createDefaultModel();
        try {
            Resource derived = model.createResource("https://data.example.org/d1");
            Resource source = model.createResource("https://data.example.org/s1");
            ProvenanceHelper.addDerivedFrom(model, derived, source);
            assertTrue(derived.hasProperty(
                    model.createProperty(ProvenanceHelper.PROV_WAS_DERIVED_FROM), source));
        } finally {
            model.close();
        }
    }

    @Test
    void addActivityAddsTitleCreatedAndUsed() {
        Model model = ModelFactory.createDefaultModel();
        try {
            Resource activity = model.createResource("https://data.example.org/prov/act-2");
            Resource used = model.createResource("https://data.example.org/files/input.xml");
            ProvenanceHelper.addActivity(model, activity, used, "gaeb-boq", "0.5.0", TS);

            assertTrue(activity.getProperty(DCTerms.title) != null, "dcterms:title missing");
            assertTrue(activity.getProperty(DCTerms.created) != null, "dcterms:created missing");
            assertEquals("Construct-X conversion: gaeb-boq 0.5.0",
                    activity.getProperty(DCTerms.title).getString());
            assertTrue(activity.hasProperty(
                    model.createProperty(ProvenanceHelper.PROV_USED), used), "prov:used missing");
        } finally {
            model.close();
        }
    }

    @Test
    void annotateConversionCreatesActivityAndLinksResult() {
        Model model = ModelFactory.createDefaultModel();
        try {
            Resource activity = ProvenanceHelper.annotateConversion(
                    model,
                    "https://data.example.org/boq/BOQ-01",
                    "https://data.example.org/files/gaeb-input.xml",
                    "gaeb-boq", "0.5.0", TS);

            assertNotNull(activity);
            Resource result = model.createResource("https://data.example.org/boq/BOQ-01");
            assertTrue(result.hasProperty(
                    model.createProperty(ProvenanceHelper.PROV_WAS_GENERATED_BY), activity),
                    "result entity must be linked to the activity via prov:wasGeneratedBy");
            assertTrue(activity.getProperty(DCTerms.title) != null);
        } finally {
            model.close();
        }
    }
}
