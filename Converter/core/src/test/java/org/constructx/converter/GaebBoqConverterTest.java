package org.constructx.converter;

import org.constructx.sdk.IriFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GaebBoqConverterTest {

    private static Model model;

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void convert() throws Exception {
        File xml = new File("src/test/resources/gaeb-x80-example.xml");
        GaebBoqConverter converter = new GaebBoqConverter();
        model = converter.convert(xml);
    }

    private static Resource res(String iri) {
        return model.createResource(iri);
    }

    private static Property prop(String iri) {
        return model.createProperty(iri);
    }

    private static Resource byIri(String iri) {
        return model.getResource(iri);
    }

    private static List<Resource> resourcesOf(String classIri) {
        return model.listResourcesWithProperty(prop(ConxNamespaces.RDF_TYPE), res(classIri)).toList();
    }

    @Test
    void convertsBillOfQuantities() {
        List<Resource> boqs = resourcesOf(ConxNamespaces.BILL_OF_QUANTITIES);
        assertEquals(1, boqs.size());
        Resource boq = boqs.get(0);
        assertEquals("BOQ-001",
                boq.getProperty(prop(ConxNamespaces.P_IDENTIFIER)).getString());
        assertEquals("Erdbau und Fundament",
                boq.getProperty(prop(ConxNamespaces.P_NAME)).getString());
        assertEquals("EUR",
                boq.getProperty(prop(ConxNamespaces.P_CURRENCY_CODE)).getString());
    }

    @Test
    void convertsSectionsWithHierarchy() {
        List<Resource> sections = resourcesOf(ConxNamespaces.BOQ_SECTION);
        assertEquals(4, sections.size());

        Resource sec01 = byIri(IriFactory.DEFAULT_BASE_IRI + "/section/SEC-01");
        assertNotNull(sec01.getProperty(prop(ConxNamespaces.P_IDENTIFIER)));
        assertEquals("01", sec01.getProperty(prop(ConxNamespaces.P_ORDINAL_NUMBER)).getString());
        assertEquals(1, sec01.getProperty(prop(ConxNamespaces.P_LEVEL_NUMBER)).getInt());
        assertEquals("Aushubarbeiten",
                sec01.getProperty(prop(ConxNamespaces.P_NAME)).getString());

        // SEC-02 is a subsection of SEC-01 (which has two subsections)
        Resource sec02 = byIri(IriFactory.DEFAULT_BASE_IRI + "/section/SEC-02");
        assertEquals(2, sec02.getProperty(prop(ConxNamespaces.P_LEVEL_NUMBER)).getInt());
        assertEquals(sec01, sec02.getProperty(prop(ConxNamespaces.P_IS_SUB_SECTION_OF)).getResource());
        List<Resource> subSections = sec01.listProperties(prop(ConxNamespaces.P_HAS_SUB_SECTION))
                .toList().stream().map(s -> s.getObject().asResource()).toList();
        assertEquals(2, subSections.size());
        assertTrue(subSections.contains(sec02));

        // SEC-01 is a root section of the BoQ (which has two root sections)
        Resource boq = byIri(IriFactory.DEFAULT_BASE_IRI + "/boq/BOQ-001");
        assertEquals(boq, sec01.getProperty(prop(ConxNamespaces.P_IS_ROOT_SECTION_OF)).getResource());
        List<Resource> rootSections = boq.listProperties(prop(ConxNamespaces.P_HAS_ROOT_SECTION))
                .toList().stream().map(s -> s.getObject().asResource()).toList();
        assertEquals(2, rootSections.size());
        assertTrue(rootSections.contains(sec01));
    }

    @Test
    void convertsItems() {
        List<Resource> items = resourcesOf(ConxNamespaces.BOQ_ITEM);
        assertEquals(4, items.size());

        Resource item = byIri(IriFactory.DEFAULT_BASE_IRI + "/item/ITM-001");
        // Full OZ = concatenation of the section segments (01 + 01) and the
        // item segment (001).
        assertEquals("0101001",
                item.getProperty(prop(ConxNamespaces.P_ORDINAL_NUMBER)).getString());
        assertEquals(1, item.getProperty(prop(ConxNamespaces.P_POSITION_COUNTER)).getInt());
        assertEquals("125.500",
                item.getProperty(prop(ConxNamespaces.P_QUANTITY)).getLiteral().getLexicalForm());
        assertEquals("m3", item.getProperty(prop(ConxNamespaces.P_UNIT_CODE)).getString());
        assertEquals("45.800",
                item.getProperty(prop(ConxNamespaces.P_UNIT_PRICE)).getLiteral().getLexicalForm());
        assertEquals("5747.90",
                item.getProperty(prop(ConxNamespaces.P_TOTAL_AMOUNT)).getLiteral().getLexicalForm());
        assertEquals("Aushub Flachgründung",
                item.getProperty(prop(ConxNamespaces.P_SHORT_TEXT)).getString());

        // Long text is a language-tagged literal
        var longText = item.getProperty(prop(ConxNamespaces.P_LONG_TEXT)).getLiteral();
        assertEquals("de", longText.getLanguage());
        assertTrue(longText.getString().startsWith("Aushub für Flachgründung"));

        // Item belongs to section SEC-02
        Resource sec02 = byIri(IriFactory.DEFAULT_BASE_IRI + "/section/SEC-02");
        assertEquals(sec02, item.getProperty(prop(ConxNamespaces.P_BELONGS_TO_SECTION)).getResource());
    }

    @Test
    void convertsItemlistItems() {
        // ITM-004 is wrapped in an Itemlist element
        Resource item = byIri(IriFactory.DEFAULT_BASE_IRI + "/item/ITM-004");
        assertEquals("02001",
                item.getProperty(prop(ConxNamespaces.P_ORDINAL_NUMBER)).getString());
        Resource sec04 = byIri(IriFactory.DEFAULT_BASE_IRI + "/section/SEC-04");
        assertEquals(sec04, item.getProperty(prop(ConxNamespaces.P_BELONGS_TO_SECTION)).getResource());
    }

    @Test
    void convertsQuantitySplits() {
        Resource item = byIri(IriFactory.DEFAULT_BASE_IRI + "/item/ITM-001");
        List<Resource> splits = item.listProperties(prop(ConxNamespaces.P_HAS_QUANTITY_SPLIT))
                .toList().stream().map(s -> s.getObject().asResource()).toList();
        assertEquals(2, splits.size());

        // One split: absolute quantity 75.5, the other: 50% of 125.5 = 62.75
        List<String> quantities = splits.stream()
                .map(s -> s.getProperty(prop(ConxNamespaces.P_QUANTITY)).getLiteral().getLexicalForm())
                .sorted().toList();
        assertEquals(List.of("62.750", "75.500"), quantities);

        // Both splits reference the item
        for (Resource split : splits) {
            assertEquals(item, split.getProperty(prop(ConxNamespaces.P_IS_QUANTITY_SPLIT_OF)).getResource());
        }

        // 75.5 + 62.75 = 138.25 != 125.5 -> splits are not exhaustive
        assertEquals(false,
                item.getProperty(prop(ConxNamespaces.P_SPLITS_ARE_EXHAUSTIVE)).getLiteral().getBoolean());
    }

    @Test
    void convertsUnitPriceComponents() {
        Resource item = byIri(IriFactory.DEFAULT_BASE_IRI + "/item/ITM-001");
        List<Resource> upcs = item.listProperties(prop(ConxNamespaces.P_HAS_UNIT_PRICE_COMPONENT))
                .toList().stream().map(s -> s.getObject().asResource()).toList();
        assertEquals(3, upcs.size());

        // Find the component typed as Labor (UPComp1 = 12.5)
        Resource upcLabor = upcs.stream()
                .filter(u -> u.getProperty(prop(ConxNamespaces.P_HAS_PRICE_COMPONENT_TYPE)) != null
                        && res(ConxNamespaces.PC_LABOR).equals(u.getProperty(prop(ConxNamespaces.P_HAS_PRICE_COMPONENT_TYPE)).getResource()))
                .findFirst().orElseThrow();
        assertEquals("12.500",
                upcLabor.getProperty(prop(ConxNamespaces.P_AMOUNT)).getLiteral().getLexicalForm());
        assertEquals(item, upcLabor.getProperty(prop(ConxNamespaces.P_IS_UNIT_PRICE_COMPONENT_OF)).getResource());

        // All three amounts are present
        List<String> amounts = upcs.stream()
                .map(u -> u.getProperty(prop(ConxNamespaces.P_AMOUNT)).getLiteral().getLexicalForm())
                .sorted().toList();
        assertEquals(List.of("12.500", "13.000", "20.300"), amounts);
    }

    @Test
    void convertsCatalogAssignments() {
        Resource item = byIri(IriFactory.DEFAULT_BASE_IRI + "/item/ITM-001");
        List<Resource> assigns = item.listProperties(prop(ConxNamespaces.P_HAS_CATALOG_ASSIGNMENT))
                .toList().stream().map(s -> s.getObject().asResource()).toList();
        assertEquals(1, assigns.size());

        Resource assign = assigns.get(0);
        assertEquals("STLB-Bau",
                assign.getProperty(prop(ConxNamespaces.P_CATALOG_IDENTIFIER)).getString());
        assertEquals(item, assign.getProperty(prop(ConxNamespaces.P_IS_ASSIGNMENT_OF)).getResource());

        Resource extRef = assign.getProperty(prop(ConxNamespaces.P_REFERENCES_EXTERNAL_OBJECT))
                .getResource();
        assertEquals("12345678-ABCD-4EF0-9123-456789ABCDEF",
                extRef.getProperty(prop(ConxNamespaces.P_EXTERNAL_IDENTIFIER)).getString());
        assertEquals("GAEB",
                extRef.getProperty(prop(ConxNamespaces.P_SOURCE_SYSTEM)).getString());
    }

    @Test
    void writesValidTurtleFile() throws Exception {
        Path out = tempDir.resolve("gaeb-boq.ttl");
        GaebBoqConverter.writeTtl(model, out);
        String ttl = Files.readString(out);
        assertTrue(ttl.contains("conx-gaeb:BillOfQuantities"));
        assertTrue(ttl.contains("conx-gaeb:BoQSection"));
        assertTrue(ttl.contains("conx-gaeb:BoQItem"));
        assertTrue(ttl.contains("conx-gaeb:QuantitySplit"));
        assertTrue(ttl.contains("conx-gaeb:UnitPriceComponent"));
        assertTrue(ttl.contains("conx-gaeb:CatalogAssignment"));
        assertTrue(ttl.contains("conx-gaeb:ExternalObjectReference"));
    }
}
