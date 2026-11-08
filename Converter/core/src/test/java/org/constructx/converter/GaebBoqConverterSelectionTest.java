package org.constructx.converter;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the Bauposten-Auswahl (selective position conversion) of the
 * GAEB forward converter: selection tree, item selection, section selection
 * (whole subtree) and the legacy full conversion.
 */
class GaebBoqConverterSelectionTest {

    private static final File EXAMPLE =
            new File("src/test/resources/gaeb-x80-example.xml");

    private static final String ONT = ConxNamespaces.GAEB_BOQ;

    private Model fullModel() throws Exception {
        return new GaebBoqConverter().convert(EXAMPLE);
    }

    private long countOf(Model model, String classLocal) {
        return model.listResourcesWithProperty(org.apache.jena.vocabulary.RDF.type,
                model.createResource(ONT + classLocal)).toList().size();
    }

    @Test
    void selectionTreeContainsAllSectionsAndItems() throws Exception {
        List<GaebBoqConverter.BoQSelectionEntry> tree =
                new GaebBoqConverter().selectionTree(EXAMPLE);

        assertEquals(2, tree.size(), "two root sections");

        GaebBoqConverter.BoQSelectionEntry sec01 = tree.get(0);
        assertEquals(GaebBoqConverter.BoQSelectionEntry.SECTION, sec01.kind());
        assertEquals("SEC-01", sec01.id());
        assertEquals("01", sec01.ordinalNumber());
        assertEquals("Aushubarbeiten", sec01.name());
        assertEquals(3, sec01.itemCount());
        assertEquals(2, sec01.children().size(), "two sub-sections");

        GaebBoqConverter.BoQSelectionEntry sec02 = sec01.children().get(0);
        assertEquals("0101", sec02.ordinalNumber());
        assertEquals("Flachgründung", sec02.name());
        assertEquals(2, sec02.itemCount());

        GaebBoqConverter.BoQSelectionEntry itm1 = sec02.children().get(0);
        assertEquals(GaebBoqConverter.BoQSelectionEntry.ITEM, itm1.kind());
        assertEquals("ITM-001", itm1.id());
        assertEquals("0101001", itm1.ordinalNumber());
        assertEquals("Aushub Flachgründung", itm1.name());
        assertEquals(1, itm1.itemCount());

        GaebBoqConverter.BoQSelectionEntry sec04 = tree.get(1);
        assertEquals("02", sec04.ordinalNumber());
        assertEquals("Ortsentwässerung", sec04.name());
        assertEquals(1, sec04.itemCount());
    }

    @Test
    void selectionOfSingleItemConvertsOnlyThatItem() throws Exception {
        Model model = new GaebBoqConverter().convert(EXAMPLE, List.of("0101001"));

        assertEquals(1, countOf(model, "BoQItem"), "only the selected item");
        assertTrue(hasItemIdentifier(model, "ITM-001"));
        assertFalse(hasItemIdentifier(model, "ITM-002"));
        assertFalse(hasItemIdentifier(model, "ITM-004"));

        // The containing sections stay as structure nodes.
        assertTrue(hasSectionIdentifier(model, "SEC-01"));
        assertTrue(hasSectionIdentifier(model, "SEC-02"));
        // Subtrees without selected entries are dropped.
        assertFalse(hasSectionIdentifier(model, "SEC-03"), "Tiefgründung dropped");
        assertFalse(hasSectionIdentifier(model, "SEC-04"), "Ortsentwässerung dropped");
    }

    @Test
    void selectionOfSectionConvertsWholeSubtree() throws Exception {
        Model model = new GaebBoqConverter().convert(EXAMPLE, List.of("01"));

        assertEquals(3, countOf(model, "BoQItem"), "subtree of section 01");
        assertTrue(hasItemIdentifier(model, "ITM-001"));
        assertTrue(hasItemIdentifier(model, "ITM-002"));
        assertTrue(hasItemIdentifier(model, "ITM-003"));
        assertFalse(hasItemIdentifier(model, "ITM-004"), "section 02 not selected");
        assertFalse(hasSectionIdentifier(model, "SEC-04"), "section 02 dropped");
    }

    @Test
    void selectionOfMultipleItemsConvertsOnlyThose() throws Exception {
        Model model = new GaebBoqConverter().convert(EXAMPLE, List.of("0101001", "02001"));

        assertEquals(2, countOf(model, "BoQItem"));
        assertTrue(hasItemIdentifier(model, "ITM-001"));
        assertTrue(hasItemIdentifier(model, "ITM-004"));
        assertFalse(hasItemIdentifier(model, "ITM-002"));
        assertFalse(hasItemIdentifier(model, "ITM-003"));
    }

    @Test
    void selectionKeepsItemData() throws Exception {
        Model model = new GaebBoqConverter().convert(EXAMPLE, List.of("0101001"));
        Resource item = model.createResource("https://data.construct-x.org/gaeb/item/ITM-001");
        Property pQuantity = model.createProperty(ConxNamespaces.P_QUANTITY);
        assertEquals("125.500", item.getProperty(pQuantity).getString());
        assertEquals("0101001", item.getProperty(
                model.createProperty(ConxNamespaces.P_ORDINAL_NUMBER)).getString());
        // Price components and splits of the selected item survive.
        assertEquals(3, item.listProperties(
                model.createProperty(ConxNamespaces.P_HAS_UNIT_PRICE_COMPONENT)).toList().size());
    }

    @Test
    void emptySelectionKeepsFullConversion() throws Exception {
        Model full = fullModel();
        Model empty = new GaebBoqConverter().convert(EXAMPLE, List.of());
        assertEquals(countOf(full, "BoQItem"), countOf(empty, "BoQItem"));
        assertEquals(full.size(), empty.size());
    }

    @Test
    void selectionIgnoresUnknownOrdinalNumbers() throws Exception {
        Model model = new GaebBoqConverter().convert(EXAMPLE, List.of("99999"));
        assertEquals(0, countOf(model, "BoQItem"), "nothing matches the selection");
        // The BoQ entity itself is still created.
        assertEquals(1, countOf(model, "BillOfQuantities"));
    }

    private boolean hasItemIdentifier(Model model, String identifier) {
        for (Resource resource : model.listResourcesWithProperty(org.apache.jena.vocabulary.RDF.type,
                model.createResource(ConxNamespaces.BOQ_ITEM)).toList()) {
            if (identifier.equals(firstLiteral(resource, ConxNamespaces.P_IDENTIFIER, model))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSectionIdentifier(Model model, String identifier) {
        for (Resource resource : model.listResourcesWithProperty(org.apache.jena.vocabulary.RDF.type,
                model.createResource(ConxNamespaces.BOQ_SECTION)).toList()) {
            if (identifier.equals(firstLiteral(resource, ConxNamespaces.P_IDENTIFIER, model))) {
                return true;
            }
        }
        return false;
    }

    private static String firstLiteral(Resource resource, String propertyIri, Model model) {
        var statement = resource.getProperty(model.createProperty(propertyIri));
        return statement != null && statement.getObject().isLiteral()
                ? statement.getObject().asLiteral().getString()
                : null;
    }
}
