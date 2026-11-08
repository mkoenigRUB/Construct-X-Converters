package org.constructx.sdk;

/**
 * Deterministic IRI generation for converted Construct-X entities.
 *
 * <p>IRI scheme: {@code {baseIri}/{entityType}/{identifier}} where the
 * identifier is a stable identifier from the source system (GAEB XML ID,
 * MS Project task ID, etc.). The base IRI is configurable so that
 * different projects or data-exchange phases can be kept in separate
 * namespaces.
 *
 * <p>Usage:
 * <pre>{@code
 * IriFactory factory = new IriFactory("https://data.example.org/project-1");
 * String iri = factory.iri("activity", "A100");
 * // -> https://data.example.org/project-1/activity/A100
 * }</pre>
 */
public final class IriFactory {

    /** Default base IRI for GAEB BoQ conversions (legacy compatibility). */
    public static final String DEFAULT_BASE_IRI = "https://data.construct-x.org/gaeb";

    private final String baseIri;

    public IriFactory() {
        this(DEFAULT_BASE_IRI);
    }

    public IriFactory(String baseIri) {
        if (baseIri == null || baseIri.isBlank()) {
            throw new IllegalArgumentException("baseIri must not be null or blank");
        }
        this.baseIri = stripTrailingSlash(baseIri);
    }

    /**
     * Builds a deterministic IRI for the given entity type and identifier.
     *
     * @param entityType short segment (e.g. {@code "boq"}, {@code "activity"},
     *                   {@code "section"}); must not be {@code null}
     * @param identifier stable identifier from the source system
     * @return IRI in the form {@code {baseIri}/{entityType}/{identifier}}
     */
    public String iri(String entityType, String identifier) {
        if (entityType == null || identifier == null) {
            throw new IllegalArgumentException("entityType and identifier must not be null");
        }
        return baseIri + "/" + entityType + "/" + identifier;
    }

    /**
     * Returns the configured base IRI (without trailing slash).
     */
    public String baseIri() {
        return baseIri;
    }

    // ── GAEB-specific convenience methods (legacy) ──────────────────────────

    /** IRI for a BillOfQuantities root. */
    public String boq(String id) {
        return iri("boq", id);
    }

    /** IRI for a BoQ section. */
    public String section(String id) {
        return iri("section", id);
    }

    /** IRI for a BoQ item (position). */
    public String item(String id) {
        return iri("item", id);
    }

    /** IRI for a quantity split of an item. */
    public String quantitySplit(String itemId, int index) {
        return iri("quantity-split", itemId + "-split-" + index);
    }

    /** IRI for a unit price component of an item. */
    public String unitPriceComponent(String itemId, int index) {
        return iri("unit-price-component", itemId + "-upc-" + index);
    }

    /** IRI for a catalog assignment. */
    public String catalogAssignment(String ownerId, int index) {
        return iri("catalog-assignment", ownerId + "-ca-" + index);
    }

    /** IRI for an external object reference (catalog + code). */
    public String externalObjectReference(String catalogId, String catalogCode) {
        return iri("external-object-reference", catalogId + ":" + catalogCode);
    }

    // ── MS Project convenience methods ───────────────────────────────────────

    /** IRI for a construction schedule. */
    public String schedule(String slug) {
        return iri("schedule", slug);
    }

    /** IRI for a schedule activity. */
    public String activity(String id) {
        return iri("activity", id);
    }

    /** IRI for a precedence relationship. */
    public String precedenceRelationship(String id) {
        return iri("precedence-relationship", id);
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
