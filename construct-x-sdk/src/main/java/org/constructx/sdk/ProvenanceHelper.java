package org.constructx.sdk;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.DCTerms;

import java.time.Instant;

/**
 * Helpers for PROV-O ({@code http://www.w3.org/ns/prov#}) provenance
 * annotations on Construct-X exchange data.
 *
 * <p>Jena ships no PROV vocabulary class, so the IRI constants are
 * declared locally.
 *
 * <p>Usage:
 * <pre>{@code
 * Model model = ...;
 * Resource resultEntity = model.createResource("https://data.example.org/boq/BOQ-01");
 * Resource sourceFile = model.createResource("https://data.example.org/files/input.xml");
 *
 * ProvenanceHelper.annotateConversion(model,
 *         "https://data.example.org/boq/BOQ-01",
 *         "https://data.example.org/files/input.xml",
 *         "gaeb-boq", "0.5.0", Instant.now());
 * }</pre>
 */
public final class ProvenanceHelper {

    /** PROV namespace. */
    public static final String PROV_NS = "http://www.w3.org/ns/prov#";

    /** {@code prov:wasGeneratedBy}. */
    public static final String PROV_WAS_GENERATED_BY = PROV_NS + "wasGeneratedBy";
    /** {@code prov:wasDerivedFrom}. */
    public static final String PROV_WAS_DERIVED_FROM = PROV_NS + "wasDerivedFrom";
    /** {@code prov:used}. */
    public static final String PROV_USED = PROV_NS + "used";

    private ProvenanceHelper() {
    }

    /**
     * Adds a {@code prov:wasGeneratedBy} annotation linking an entity to
     * the activity that generated it.
     *
     * @param model    RDF model to add the annotation to
     * @param entity   the generated entity (e.g. a converted BoQ item)
     * @param activity the PROV activity that generated the entity
     */
    public static void addGeneratedBy(Model model, Resource entity, Resource activity) {
        entity.addProperty(property(model, PROV_WAS_GENERATED_BY), activity);
    }

    /**
     * Adds {@code prov:wasDerivedFrom} from {@code derivedEntity} to
     * {@code sourceEntity}.
     *
     * @param model         RDF model
     * @param derivedEntity the entity that was derived
     * @param sourceEntity  the original entity
     */
    public static void addDerivedFrom(Model model, Resource derivedEntity, Resource sourceEntity) {
        derivedEntity.addProperty(property(model, PROV_WAS_DERIVED_FROM), sourceEntity);
    }

    /**
     * Annotates a PROV activity with:
     * <ul>
     *   <li>{@code dcterms:title} — human-readable description</li>
     *   <li>{@code dcterms:created} — timestamp of the activity</li>
     *   <li>{@code prov:used} — the source file/entity the activity used</li>
     * </ul>
     *
     * @param model      RDF model
     * @param activity   the activity resource to annotate
     * @param usedEntity the entity the activity used (e.g. source file)
     * @param module     ontology module id (e.g. {@code "gaeb-boq"})
     * @param version    ontology version used (e.g. {@code "0.5.0"})
     * @param timestamp  when the activity took place
     */
    public static void addActivity(Model model, Resource activity,
                                   Resource usedEntity,
                                   String module, String version,
                                   Instant timestamp) {
        activity.addProperty(DCTerms.title,
                "Construct-X conversion: " + module + " " + version);
        activity.addProperty(DCTerms.created,
                model.createTypedLiteral(timestamp.toString(), XSDDatatype.XSDdateTime.getURI()));
        activity.addProperty(property(model, PROV_USED), usedEntity);
    }

    /**
     * Convenience: creates and annotates a fresh PROV activity resource in
     * the given model and links the result entity via {@code prov:wasGeneratedBy}.
     *
     * @param model       RDF model
     * @param resultIri   IRI of the converted result entity
     * @param sourceIri   IRI of the source file
     * @param module      ontology module id
     * @param version     ontology version
     * @param timestamp   when the conversion took place
     * @return the newly created activity resource
     */
    public static Resource annotateConversion(Model model, String resultIri,
                                              String sourceIri,
                                              String module, String version,
                                              Instant timestamp) {
        String activityIri = "https://data.construct-x.org/prov/"
                + module + "/" + version + "-" + timestamp.toEpochMilli();
        Resource activity = model.createResource(activityIri);
        Resource result = model.createResource(resultIri);
        Resource source = model.createResource(sourceIri);

        addGeneratedBy(model, result, activity);
        addActivity(model, activity, source, module, version, timestamp);
        return activity;
    }

    private static Property property(Model model, String iri) {
        return model.createProperty(iri);
    }
}
