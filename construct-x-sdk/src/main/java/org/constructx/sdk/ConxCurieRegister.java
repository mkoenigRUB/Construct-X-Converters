package org.constructx.sdk;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Static CURIE prefix register for the Construct-X namespace scheme and the
 * standard W3C/DC namespaces used in Construct-X exchange data.
 *
 * <p>The entries mirror the {@code vann:preferredNamespacePrefix} /
 * {@code vann:preferredNamespaceUri} declarations of the Construct-X
 * ontology files (see {@code vann} declarations in the {@code *.ttl}
 * modules) plus the well-known standard namespaces and the QUDT 2.1
 * unit namespaces used in the exchange examples (e.g. {@code unit:M}).
 *
 * <p>IRIs whose namespace is registered are encoded as CURIEs
 * ({@code conx-gaeb:BoQItem}, {@code xsd:decimal}); all other IRIs are
 * returned unchanged (fully qualified), so no information is lost.
 */
public final class ConxCurieRegister {

    /** prefix -> namespace IRI (sorted by descending namespace length for matching). */
    private static final Map<String, String> PREFIX_TO_NS = new HashMap<>();

    /** namespace IRI -> prefix, ordered by descending namespace length. */
    private static final Map<String, String> NS_TO_PREFIX = new TreeMap<>((a, b) -> {
        int byLength = Integer.compare(b.length(), a.length());
        return byLength != 0 ? byLength : a.compareTo(b);
    });

    static {
        // Construct-X domain ontologies
        register("conx-cs", "https://www.construct-x.org/ontology/construction-schedule#");
        register("conx-bs", "https://www.construct-x.org/ontology/breakdown-structure#");
        register("conx-gaeb", "https://www.construct-x.org/ontology/gaeb-boq#");
        register("conx-site", "https://www.construct-x.org/ontology/construction-site#");
        register("conx-del", "https://www.construct-x.org/ontology/construction-delivery#");
        register("conx-cpr", "https://www.construct-x.org/ontology/construction-progress-report#");
        register("conx-ba", "https://www.construct-x.org/ontology/built-asset#");
        register("conx-nc", "https://www.construct-x.org/ontology/notification-content#");
        // Construct-X link ontologies
        register("conx-sbl", "https://www.construct-x.org/ontology/schedule-breakdown-link#");
        register("conx-sbq", "https://www.construct-x.org/ontology/schedule-boq-link#");
        register("conx-sprl", "https://www.construct-x.org/ontology/schedule-progress-report-link#");
        register("conx-sdl", "https://www.construct-x.org/ontology/schedule-delivery-link#");
        register("conx-ssl", "https://www.construct-x.org/ontology/schedule-site-link#");
        register("conx-sbdl", "https://www.construct-x.org/ontology/site-breakdown-link#");
        register("conx-sbal", "https://www.construct-x.org/ontology/site-built-asset-link#");
        register("conx-dbl", "https://www.construct-x.org/ontology/delivery-boq-link#");
        register("conx-dsl", "https://www.construct-x.org/ontology/delivery-site-link#");
        register("conx-babl", "https://www.construct-x.org/ontology/built-asset-breakdown-link#");
        // Construct-X vocabularies
        register("conx-st", "https://www.construct-x.org/vocab/construction-schedule/schedule-type#");
        register("conx-status", "https://www.construct-x.org/vocab/construction-schedule/activity-status#");
        register("conx-prt", "https://www.construct-x.org/vocab/construction-schedule/precedence-relationship-type#");
        register("conx-de-wbs", "https://www.construct-x.org/vocab/breakdown-structure/de-wbs-node-type#");
        register("conx-de-lbs", "https://www.construct-x.org/vocab/breakdown-structure/de-lbs-node-type#");
        register("conx-de-sbs", "https://www.construct-x.org/vocab/breakdown-structure/de-sbs-node-type#");
        register("conx-de-cbs", "https://www.construct-x.org/vocab/breakdown-structure/de-cbs-node-type#");
        register("conx-pct", "https://www.construct-x.org/vocab/boq-price-component-type#");
        register("conx-area", "https://www.construct-x.org/vocab/construction-site/area-type#");
        register("conx-eq", "https://www.construct-x.org/vocab/construction-site/equipment-type#");
        register("conx-fac", "https://www.construct-x.org/vocab/construction-site/facility-type#");
        register("conx-util", "https://www.construct-x.org/vocab/construction-site/utility-type#");
        register("conx-aas", "https://www.construct-x.org/vocab/construction-site/area-allocation-status#");
        register("conx-dot", "https://www.construct-x.org/vocab/construction-delivery/operation-type#");
        register("conx-drs", "https://www.construct-x.org/vocab/construction-delivery/requirement-status#");
        register("conx-nt", "https://www.construct-x.org/vocab/notification-content/notification-type#");
        register("conx-sr", "https://www.construct-x.org/vocab/notification-content/subject-role#");
        register("conx-ts", "https://www.construct-x.org/vocab/notification-content/task-status#");
        register("conx-tt", "https://www.construct-x.org/vocab/notification-content/task-type#");
        register("conx-bat", "https://www.construct-x.org/vocab/built-asset/asset-type#");
        register("conx-bst", "https://www.construct-x.org/vocab/built-asset/spatial-structure-type#");
        // Construct-X shape namespaces
        register("conx-gaeb-sh", "https://www.construct-x.org/shapes/gaeb-boq#");
        // Standard namespaces
        register("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        register("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
        register("xsd", "http://www.w3.org/2001/XMLSchema#");
        register("owl", "http://www.w3.org/2002/07/owl#");
        register("dcterms", "http://purl.org/dc/terms/");
        register("dcat", "http://www.w3.org/ns/dcat#");
        register("prov", "http://www.w3.org/ns/prov#");
        register("sh", "http://www.w3.org/ns/shacl#");
        register("vann", "http://purl.org/vocab/vann/");
        // QUDT 2.1 namespaces (used for units in the exchange examples)
        register("qudt", "https://qudt.org/vocab/qudt/");
        register("unit", "https://qudt.org/vocab/unit/");
        register("qk", "https://qudt.org/vocab/quantitykind/");
        register("sys", "https://qudt.org/vocab/system/");
    }

    private ConxCurieRegister() {
    }

    private static void register(String prefix, String namespace) {
        PREFIX_TO_NS.put(prefix, namespace);
        NS_TO_PREFIX.put(namespace, prefix);
    }

    /**
     * Encodes an IRI as CURIE ({@code prefix:local}) when its namespace is
     * registered; otherwise returns the IRI unchanged. Non-IRIs such as
     * blank-node labels are returned unchanged as well.
     */
    public static String encode(String iri) {
        if (iri == null || iri.isEmpty()) {
            return iri;
        }
        for (Map.Entry<String, String> entry : NS_TO_PREFIX.entrySet()) {
            if (iri.startsWith(entry.getKey())) {
                return entry.getValue() + ":" + localName(iri);
            }
        }
        return iri;
    }

    /**
     * The local name of an IRI: everything after the last {@code #} or
     * {@code /}; the full IRI when neither is present (or is the last
     * character).
     */
    public static String localName(String iri) {
        int hash = iri.lastIndexOf('#');
        int slash = iri.lastIndexOf('/');
        int index = Math.max(hash, slash);
        if (index >= 0 && index < iri.length() - 1) {
            return iri.substring(index + 1);
        }
        return iri;
    }

    /**
     * Decodes a CURIE ({@code prefix:local}) into a full IRI when its
     * prefix is registered; everything else (full IRIs, blank-node labels
     * like {@code _:b0}, plain strings) is returned unchanged.
     */
    public static String decode(String curie) {
        if (curie == null || curie.isEmpty()) {
            return curie;
        }
        int idx = curie.indexOf(':');
        if (idx > 0) {
            String ns = PREFIX_TO_NS.get(curie.substring(0, idx));
            if (ns != null) {
                return ns + curie.substring(idx + 1);
            }
        }
        return curie;
    }

    /**
     * Returns the registered prefix for the given IRI, or {@code null} if
     * the IRI's namespace is not registered.
     */
    public static String prefixFor(String iri) {
        if (iri == null || iri.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, String> entry : NS_TO_PREFIX.entrySet()) {
            if (iri.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Returns the namespace IRI registered for the given prefix, or
     * {@code null} if the prefix is not registered.
     */
    public static String namespaceFor(String prefix) {
        return PREFIX_TO_NS.get(prefix);
    }
}
