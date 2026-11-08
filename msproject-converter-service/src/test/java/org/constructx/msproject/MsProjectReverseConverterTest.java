package org.constructx.msproject;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip tests for the reverse MS Project converter:
 * example XML → model → MS Project XML → model must reproduce the
 * original triples (quality gate: round-trip, not XSD).
 */
class MsProjectReverseConverterTest {

    private static final String ONT = MsProjectConverter.ONT;
    private static final String BASE =
            "https://data.construct-x.org/schedule/doppelboden-feinterminplanung/";

    private byte[] exampleXml() throws Exception {
        return Files.readAllBytes(Path.of("src", "main", "resources", "examples", "msproject-example.xml"));
    }

    @Test
    void roundTripsExampleModel() throws Exception {
        Model model = new MsProjectConverter().convert(exampleXml());
        assertEquals(79, model.size());

        MsProjectReverseConverter.Result result = MsProjectReverseConverter.convert(model);
        assertTrue(result.xml().contains("<Project"));
        assertTrue(result.xml().contains(MsProjectReverseConverter.MS_PROJECT_NS));
        assertTrue(result.xml().contains("<Tasks"));

        Model reforwarded = new MsProjectConverter()
                .convert(result.xml().getBytes(StandardCharsets.UTF_8));
        assertEquals(model.size(), reforwarded.size());
    }

    @Test
    void roundTripsKeepsActivitiesAndMilestones() throws Exception {
        Model m = reforward(new MsProjectConverter().convert(exampleXml()));
        for (String id : new String[]{"T1", "T2", "T3", "T4", "T5"}) {
            Resource activity = m.createResource(BASE + "activity/" + id);
            assertTrue(activity.hasProperty(property("identifier"), m.createLiteral(id)),
                    "missing activity " + id);
        }
        Resource t5 = m.createResource(BASE + "activity/T5");
        assertTrue(t5.hasProperty(RDF.type, m.createResource(ONT + "Milestone")));
        long milestones = m.listResourcesWithProperty(RDF.type,
                m.createResource(ONT + "Milestone")).toList().size();
        assertEquals(1, milestones);
    }

    @Test
    void roundTripsKeepsStatuses() throws Exception {
        Model m = reforward(new MsProjectConverter().convert(exampleXml()));
        assertEquals(MsProjectConverter.STATUS_VOCAB + "Completed", statusOf(m, BASE + "activity/T1"));
        assertEquals(MsProjectConverter.STATUS_VOCAB + "InProgress", statusOf(m, BASE + "activity/T2"));
        assertEquals(MsProjectConverter.STATUS_VOCAB + "Planned", statusOf(m, BASE + "activity/T3"));
        assertEquals(MsProjectConverter.STATUS_VOCAB + "Planned", statusOf(m, BASE + "activity/T5"));
    }

    @Test
    void roundTripsKeepsRelationships() throws Exception {
        Model m = reforward(new MsProjectConverter().convert(exampleXml()));
        long rels = m.listResourcesWithProperty(RDF.type,
                m.createResource(ONT + "PrecedenceRelationship")).toList().size();
        assertEquals(5, rels);

        Resource ss = m.createResource(BASE + "relationship/REL-2-3");
        assertTrue(ss.hasProperty(property("relationshipType"),
                m.createResource(MsProjectConverter.REL_TYPE_VOCAB + "StartToStart")));
        assertTrue(ss.hasProperty(property("lag"),
                m.createTypedLiteral("PT1440M", XSDDatatype.XSDduration)));

        Resource ff = m.createResource(BASE + "relationship/REL-3-4");
        assertTrue(ff.hasProperty(property("relationshipType"),
                m.createResource(MsProjectConverter.REL_TYPE_VOCAB + "FinishToFinish")));

        Resource fs = m.createResource(BASE + "relationship/REL-1-2");
        assertTrue(fs.hasProperty(property("relationshipType"),
                m.createResource(MsProjectConverter.REL_TYPE_VOCAB + "FinishToStart")));
    }

    @Test
    void roundTripWarnsAboutInProgressApproximation() throws Exception {
        MsProjectReverseConverter.Result result =
                MsProjectReverseConverter.convert(new MsProjectConverter().convert(exampleXml()));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("InProgress")),
                "Expected an InProgress approximation warning, got: " + result.warnings());
    }

    @Test
    void throwsWithoutSchedule() {
        Model model = ModelFactory.createDefaultModel();
        Resource activity = model.createResource(BASE + "activity/T1");
        activity.addProperty(RDF.type, model.createResource(ONT + "Activity"));
        assertThrows(IllegalArgumentException.class, () -> MsProjectReverseConverter.convert(model));
    }

    @Test
    void throwsWithoutActivities() {
        Model model = ModelFactory.createDefaultModel();
        Resource schedule = model.createResource(BASE + "schedule/test");
        schedule.addProperty(RDF.type, model.createResource(ONT + "ConstructionSchedule"));
        schedule.addProperty(property("name"), "Leerer Plan");
        assertThrows(IllegalArgumentException.class, () -> MsProjectReverseConverter.convert(model));
    }

    private static Model reforward(Model model) throws Exception {
        MsProjectReverseConverter.Result result = MsProjectReverseConverter.convert(model);
        return new MsProjectConverter().convert(result.xml().getBytes(StandardCharsets.UTF_8));
    }

    private static String statusOf(Model m, String activityIri) {
        StmtIterator it = m.createResource(activityIri).listProperties(property("hasStatus"));
        return it.hasNext() ? it.next().getObject().asResource().getURI() : null;
    }

    private static Property property(String localName) {
        return ResourceFactory.createProperty(ONT + localName);
    }
}
