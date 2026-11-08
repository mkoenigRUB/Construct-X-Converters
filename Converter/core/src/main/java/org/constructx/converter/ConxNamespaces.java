package org.constructx.converter;

/**
 * Namespace constants for the Construct-X GAEB BoQ ontology and its
 * controlled vocabulary, mirroring gaeb-boq.ttl (v0.5.0) and
 * boq-price-component-type-vocab.ttl (v0.1.0).
 *
 * <p>Module-specific term IRIs for the GAEB BoQ module. The shared
 * namespace bases (all modules, standard namespaces) live in
 * {@link org.constructx.sdk.ConxNamespaces}.
 */
public final class ConxNamespaces {

    public static final String GAEB_BOQ = "https://www.construct-x.org/ontology/gaeb-boq#";
    public static final String BOQ_PRICE_COMPONENT_TYPE =
            "https://www.construct-x.org/vocab/boq-price-component-type#";

    public static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";

    // Classes
    public static final String BILL_OF_QUANTITIES = GAEB_BOQ + "BillOfQuantities";
    public static final String BOQ_SECTION = GAEB_BOQ + "BoQSection";
    public static final String BOQ_ITEM = GAEB_BOQ + "BoQItem";
    public static final String QUANTITY_SPLIT = GAEB_BOQ + "QuantitySplit";
    public static final String UNIT_PRICE_COMPONENT = GAEB_BOQ + "UnitPriceComponent";
    public static final String CATALOG_ASSIGNMENT = GAEB_BOQ + "CatalogAssignment";
    public static final String EXTERNAL_OBJECT_REFERENCE = GAEB_BOQ + "ExternalObjectReference";

    // Datatype properties
    public static final String P_IDENTIFIER = GAEB_BOQ + "identifier";
    public static final String P_NAME = GAEB_BOQ + "name";
    public static final String P_ORDINAL_NUMBER = GAEB_BOQ + "ordinalNumber";
    public static final String P_HIERARCHY_LEVEL_NAME = GAEB_BOQ + "hierarchyLevelName";
    public static final String P_LEVEL_NUMBER = GAEB_BOQ + "levelNumber";
    public static final String P_POSITION_COUNTER = GAEB_BOQ + "positionCounter";
    public static final String P_ORDINAL_INDEX = GAEB_BOQ + "ordinalIndex";
    public static final String P_SHORT_TEXT = GAEB_BOQ + "shortText";
    public static final String P_LONG_TEXT = GAEB_BOQ + "longText";
    public static final String P_QUANTITY = GAEB_BOQ + "quantity";
    public static final String P_UNIT_CODE = GAEB_BOQ + "unitCode";
    public static final String P_SPLITS_ARE_EXHAUSTIVE = GAEB_BOQ + "splitsAreExhaustive";
    public static final String P_CURRENCY_CODE = GAEB_BOQ + "currencyCode";
    public static final String P_UNIT_PRICE = GAEB_BOQ + "unitPrice";
    public static final String P_TOTAL_AMOUNT = GAEB_BOQ + "totalAmount";
    public static final String P_AMOUNT = GAEB_BOQ + "amount";
    public static final String P_CATALOG_IDENTIFIER = GAEB_BOQ + "catalogIdentifier";
    public static final String P_EXTERNAL_IDENTIFIER = GAEB_BOQ + "externalIdentifier";
    public static final String P_SOURCE_SYSTEM = GAEB_BOQ + "sourceSystem";
    public static final String P_OBJECT_TYPE = GAEB_BOQ + "objectType";

    // Object properties
    public static final String P_HAS_ROOT_SECTION = GAEB_BOQ + "hasRootSection";
    public static final String P_IS_ROOT_SECTION_OF = GAEB_BOQ + "isRootSectionOf";
    public static final String P_HAS_SUB_SECTION = GAEB_BOQ + "hasSubSection";
    public static final String P_IS_SUB_SECTION_OF = GAEB_BOQ + "isSubSectionOf";
    public static final String P_HAS_DIRECT_ITEM = GAEB_BOQ + "hasDirectItem";
    public static final String P_IS_DIRECT_ITEM_OF = GAEB_BOQ + "isDirectItemOf";
    public static final String P_HAS_ITEM = GAEB_BOQ + "hasItem";
    public static final String P_BELONGS_TO_SECTION = GAEB_BOQ + "belongsToSection";
    public static final String P_HAS_QUANTITY_SPLIT = GAEB_BOQ + "hasQuantitySplit";
    public static final String P_IS_QUANTITY_SPLIT_OF = GAEB_BOQ + "isQuantitySplitOf";
    public static final String P_HAS_UNIT_PRICE_COMPONENT = GAEB_BOQ + "hasUnitPriceComponent";
    public static final String P_IS_UNIT_PRICE_COMPONENT_OF = GAEB_BOQ + "isUnitPriceComponentOf";
    public static final String P_HAS_CATALOG_ASSIGNMENT = GAEB_BOQ + "hasCatalogAssignment";
    public static final String P_IS_ASSIGNMENT_OF = GAEB_BOQ + "isAssignmentOf";
    public static final String P_REFERENCES_EXTERNAL_OBJECT = GAEB_BOQ + "referencesExternalObject";
    public static final String P_HAS_PRICE_COMPONENT_TYPE = GAEB_BOQ + "hasPriceComponentType";

    // Price component type concepts (boq-price-component-type vocabulary)
    public static final String PC_LABOR = BOQ_PRICE_COMPONENT_TYPE + "Labor";
    public static final String PC_MATERIALS = BOQ_PRICE_COMPONENT_TYPE + "Materials";
    public static final String PC_EQUIPMENT = BOQ_PRICE_COMPONENT_TYPE + "Equipment";
    public static final String PC_OTHER = BOQ_PRICE_COMPONENT_TYPE + "Other";
    public static final String PC_UNKNOWN = BOQ_PRICE_COMPONENT_TYPE + "Unknown";

    private ConxNamespaces() {
    }
}
