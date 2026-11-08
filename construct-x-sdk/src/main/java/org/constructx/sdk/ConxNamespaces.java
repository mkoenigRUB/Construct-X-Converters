package org.constructx.sdk;

/**
 * Base namespace IRIs for all Construct-X modules and the standard
 * namespaces used in the exchange format.
 *
 * <p>These are the unversioned IRI bases that appear in
 * {@code vann:preferredNamespaceUri} declarations of the ontology files.
 * Versioned IRIs (e.g. {@code …/ontology/gaeb-boq/0.5.0}) are used for
 * {@code conformsTo} statements and are resolved via
 * {@link OntologyRegistry}.
 *
 * <p>Module-specific class and property IRIs (e.g.
 * {@code conx-gaeb:BillOfQuantities}) are intentionally NOT defined here;
 * each converter module defines its own constants on top of these bases.
 */
public final class ConxNamespaces {

    // ── Standard namespaces ──────────────────────────────────────────────────

    public static final String RDF      = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    public static final String RDFS     = "http://www.w3.org/2000/01/rdf-schema#";
    public static final String XSD      = "http://www.w3.org/2001/XMLSchema#";
    public static final String OWL      = "http://www.w3.org/2002/07/owl#";
    public static final String DCTERMS  = "http://purl.org/dc/terms/";
    public static final String DCAT     = "http://www.w3.org/ns/dcat#";
    public static final String PROV     = "http://www.w3.org/ns/prov#";
    public static final String SHACL    = "http://www.w3.org/ns/shacl#";
    public static final String VANN     = "http://purl.org/vocab/vann/";

    public static final String RDF_TYPE = RDF + "type";

    // QUDT 2.1
    public static final String QUDT     = "https://qudt.org/vocab/qudt/";
    public static final String UNIT     = "https://qudt.org/vocab/unit/";
    public static final String QK       = "https://qudt.org/vocab/quantitykind/";
    public static final String SYS      = "https://qudt.org/vocab/system/";

    // ── Construct-X domain ontologies (unversioned bases) ─────────────────────

    public static final String CONSTRUCTION_SCHEDULE     = "https://www.construct-x.org/ontology/construction-schedule#";
    public static final String BREAKDOWN_STRUCTURE       = "https://www.construct-x.org/ontology/breakdown-structure#";
    public static final String GAEB_BOQ                  = "https://www.construct-x.org/ontology/gaeb-boq#";
    public static final String CONSTRUCTION_SITE         = "https://www.construct-x.org/ontology/construction-site#";
    public static final String CONSTRUCTION_DELIVERY     = "https://www.construct-x.org/ontology/construction-delivery#";
    public static final String CONSTRUCTION_PROGRESS     = "https://www.construct-x.org/ontology/construction-progress-report#";
    public static final String BUILT_ASSET               = "https://www.construct-x.org/ontology/built-asset#";
    public static final String NOTIFICATION_CONTENT      = "https://www.construct-x.org/ontology/notification-content#";

    // ── Construct-X link ontologies (unversioned bases) ───────────────────────

    public static final String SCHEDULE_BREAKDOWN_LINK   = "https://www.construct-x.org/ontology/schedule-breakdown-link#";
    public static final String SCHEDULE_BOQ_LINK         = "https://www.construct-x.org/ontology/schedule-boq-link#";
    public static final String SCHEDULE_PROGRESS_LINK    = "https://www.construct-x.org/ontology/schedule-progress-report-link#";
    public static final String SCHEDULE_DELIVERY_LINK    = "https://www.construct-x.org/ontology/schedule-delivery-link#";
    public static final String SCHEDULE_SITE_LINK        = "https://www.construct-x.org/ontology/schedule-site-link#";
    public static final String SITE_BREAKDOWN_LINK       = "https://www.construct-x.org/ontology/site-breakdown-link#";
    public static final String SITE_BUILT_ASSET_LINK     = "https://www.construct-x.org/ontology/site-built-asset-link#";
    public static final String DELIVERY_BOQ_LINK         = "https://www.construct-x.org/ontology/delivery-boq-link#";
    public static final String DELIVERY_SITE_LINK        = "https://www.construct-x.org/ontology/delivery-site-link#";
    public static final String BUILT_ASSET_BREAKDOWN     = "https://www.construct-x.org/ontology/built-asset-breakdown-link#";

    // ── Construct-X SKOS vocabularies (unversioned bases) ─────────────────────

    public static final String VOCAB_SCHEDULE_TYPE           = "https://www.construct-x.org/vocab/construction-schedule/schedule-type#";
    public static final String VOCAB_ACTIVITY_STATUS         = "https://www.construct-x.org/vocab/construction-schedule/activity-status#";
    public static final String VOCAB_PRECEDENCE_REL_TYPE     = "https://www.construct-x.org/vocab/construction-schedule/precedence-relationship-type#";
    public static final String VOCAB_WBS_NODE_TYPE           = "https://www.construct-x.org/vocab/breakdown-structure/de-wbs-node-type#";
    public static final String VOCAB_LBS_NODE_TYPE           = "https://www.construct-x.org/vocab/breakdown-structure/de-lbs-node-type#";
    public static final String VOCAB_SBS_NODE_TYPE           = "https://www.construct-x.org/vocab/breakdown-structure/de-sbs-node-type#";
    public static final String VOCAB_CBS_NODE_TYPE           = "https://www.construct-x.org/vocab/breakdown-structure/de-cbs-node-type#";
    public static final String VOCAB_PRICE_COMPONENT_TYPE    = "https://www.construct-x.org/vocab/boq-price-component-type#";
    public static final String VOCAB_AREA_TYPE               = "https://www.construct-x.org/vocab/construction-site/area-type#";
    public static final String VOCAB_EQUIPMENT_TYPE          = "https://www.construct-x.org/vocab/construction-site/equipment-type#";
    public static final String VOCAB_FACILITY_TYPE           = "https://www.construct-x.org/vocab/construction-site/facility-type#";
    public static final String VOCAB_UTILITY_TYPE            = "https://www.construct-x.org/vocab/construction-site/utility-type#";
    public static final String VOCAB_AREA_ALLOC_STATUS       = "https://www.construct-x.org/vocab/construction-site/area-allocation-status#";
    public static final String VOCAB_OPERATION_TYPE          = "https://www.construct-x.org/vocab/construction-delivery/operation-type#";
    public static final String VOCAB_REQUIREMENT_STATUS      = "https://www.construct-x.org/vocab/construction-delivery/requirement-status#";
    public static final String VOCAB_NOTIFICATION_TYPE       = "https://www.construct-x.org/vocab/notification-content/notification-type#";
    public static final String VOCAB_SUBJECT_ROLE            = "https://www.construct-x.org/vocab/notification-content/subject-role#";
    public static final String VOCAB_TASK_STATUS             = "https://www.construct-x.org/vocab/notification-content/task-status#";
    public static final String VOCAB_TASK_TYPE               = "https://www.construct-x.org/vocab/notification-content/task-type#";
    public static final String VOCAB_ASSET_TYPE              = "https://www.construct-x.org/vocab/built-asset/asset-type#";
    public static final String VOCAB_SPATIAL_STRUCTURE_TYPE  = "https://www.construct-x.org/vocab/built-asset/spatial-structure-type#";

    // ── Construct-X SHACL shape namespaces ────────────────────────────────────

    public static final String SHAPES_GAEB_BOQ = "https://www.construct-x.org/shapes/gaeb-boq#";

    private ConxNamespaces() {
    }

    /**
     * Builds a full IRI from a base namespace and a local name.
     *
     * @param base      unversioned namespace base (ends with {@code #} or {@code /})
     * @param localName local name of the term
     * @return full IRI
     */
    public static String iri(String base, String localName) {
        if (base == null || localName == null) {
            throw new IllegalArgumentException("base and localName must not be null");
        }
        return base + localName;
    }
}
