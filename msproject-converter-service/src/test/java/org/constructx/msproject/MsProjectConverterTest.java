package org.constructx.msproject;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MsProjectConverterTest {

    private static final String ONT = MsProjectConverter.ONT;

    private final MsProjectConverter converter = new MsProjectConverter();

    private byte[] exampleXml() throws Exception {
        return Files.readAllBytes(Path.of("src", "main", "resources", "examples", "msproject-example.xml"));
    }

    @Test
    void convertsExampleToScheduleModel() throws Exception {
        Model model = converter.convert(exampleXml());
        Resource schedule = model.listResourcesWithProperty(
                RDF.type,
                model.createResource(ONT + "ConstructionSchedule")).nextResource();
        assertTrue(schedule.hasProperty(ResourceFactory.createProperty(ONT + "identifier")));
        assertTrue(schedule.hasProperty(ResourceFactory.createProperty(ONT + "name")));
        assertTrue(schedule.hasProperty(ResourceFactory.createProperty(ONT + "scheduleType")));
        assertEquals(5, schedule.listProperties(ResourceFactory.createProperty(ONT + "hasActivity")).toList().size());
    }

    @Test
    void mapsMilestonesAndActivities() throws Exception {
        Model model = converter.convert(exampleXml());
        long milestones = model.listResourcesWithProperty(
                RDF.type,
                model.createResource(ONT + "Milestone")).toList().size();
        long activities = model.listResourcesWithProperty(
                RDF.type,
                model.createResource(ONT + "Activity")).toList().size();
        assertEquals(1, milestones);
        assertEquals(4, activities);
    }

    @Test
    void mapsPredecessorRelationships() throws Exception {
        Model model = converter.convert(exampleXml());
        long relationships = model.listResourcesWithProperty(
                RDF.type,
                model.createResource(ONT + "PrecedenceRelationship")).toList().size();
        assertEquals(5, relationships);
        // SS relationship with 1440 min lag
        boolean hasSsLag = model.listResourcesWithProperty(
                RDF.type,
                model.createResource(ONT + "PrecedenceRelationship")).toList().stream()
                .anyMatch(rel -> rel.hasProperty(ResourceFactory.createProperty(ONT + "relationshipType"),
                        model.createResource(MsProjectConverter.REL_TYPE_VOCAB + "StartToStart"))
                        && rel.hasProperty(ResourceFactory.createProperty(ONT + "lag"),
                        model.createTypedLiteral("PT1440M", org.apache.jena.datatypes.xsd.XSDDatatype.XSDduration)));
        assertTrue(hasSsLag);
    }

    @Test
    void mapsStatusFromPercentComplete() throws Exception {
        Model model = converter.convert(exampleXml());
        boolean hasCompleted = model.contains(null,
                ResourceFactory.createProperty(ONT + "hasStatus"),
                model.createResource(MsProjectConverter.STATUS_VOCAB + "Completed"));
        boolean hasInProgress = model.contains(null,
                ResourceFactory.createProperty(ONT + "hasStatus"),
                model.createResource(MsProjectConverter.STATUS_VOCAB + "InProgress"));
        assertTrue(hasCompleted);
        assertTrue(hasInProgress);
    }

    @Test
    void serializesTtlAndJsonLd() throws Exception {
        Model model = converter.convert(exampleXml());
        String ttl = converter.toTtl(model);
        assertTrue(ttl.contains("conx-cs:ConstructionSchedule"));
        Model roundTrip = ModelFactory.createDefaultModel();
        RDFDataMgr.read(roundTrip, new ByteArrayInputStream(ttl.getBytes(StandardCharsets.UTF_8)),
                "urn:construct-x:roundtrip:", Lang.TURTLE);
        assertEquals(model.size(), roundTrip.size());

        String jsonLd = converter.toJsonLd(model);
        Model jsonRoundTrip = ModelFactory.createDefaultModel();
        RDFDataMgr.read(jsonRoundTrip, new ByteArrayInputStream(jsonLd.getBytes(StandardCharsets.UTF_8)),
                "urn:construct-x:roundtrip:", Lang.JSONLD);
        assertEquals(model.size(), jsonRoundTrip.size());
    }

    @Test
    void rejectsInvalidNamespace() {
        byte[] xml = "<foo xmlns=\"http://example.com\"><Tasks/></foo>".getBytes(StandardCharsets.UTF_8);
        assertThrows(MsProjectInputException.class, () -> converter.convert(xml));
    }

    @Test
    void rejectsEmptyTasks() throws Exception {
        String xml = """
                <Project xmlns="http://schemas.microsoft.com/project">
                  <Name>Leerer Plan</Name>
                  <Tasks>
                    <Task><UID>0</UID><Name>Summary</Name><IsSummary>1</IsSummary></Task>
                  </Tasks>
                </Project>
                """;
        assertThrows(MsProjectInputException.class, () -> converter.convert(xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsMalformedXml() {
        byte[] xml = "<Project xmlns=\"http://schemas.microsoft.com/project\"><Tasks>".getBytes(StandardCharsets.UTF_8);
        assertThrows(Exception.class, () -> converter.convert(xml));
    }

    @Test
    void customScheduleTypeAndIriBase() throws Exception {
        MsProjectConverter custom = new MsProjectConverter("https://example.org/plan", "DetailedNetwork");
        Model model = custom.convert(exampleXml());
        Resource schedule = model.listResourcesWithProperty(
                RDF.type,
                model.createResource(ONT + "ConstructionSchedule")).nextResource();
        assertTrue(schedule.getURI().startsWith("https://example.org/plan/"));
        assertTrue(schedule.hasProperty(ResourceFactory.createProperty(ONT + "scheduleType"),
                model.createResource(MsProjectConverter.SCHEDULE_TYPE_VOCAB + "DetailedNetwork")));
    }
}
