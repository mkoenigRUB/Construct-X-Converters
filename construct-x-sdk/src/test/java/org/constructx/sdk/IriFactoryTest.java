package org.constructx.sdk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link IriFactory}.
 */
class IriFactoryTest {

    @Test
    void defaultBaseIri() {
        IriFactory factory = new IriFactory();
        assertEquals("https://data.construct-x.org/gaeb", factory.baseIri());
    }

    @Test
    void customBaseIri() {
        IriFactory factory = new IriFactory("https://data.example.org/project-1");
        assertEquals("https://data.example.org/project-1", factory.baseIri());
    }

    @Test
    void trailingSlashIsStripped() {
        IriFactory factory = new IriFactory("https://data.example.org/project-1/");
        assertEquals("https://data.example.org/project-1", factory.baseIri());
    }

    @Test
    void genericIri() {
        IriFactory factory = new IriFactory("https://data.example.org/project-1");
        assertEquals("https://data.example.org/project-1/activity/A100",
                factory.iri("activity", "A100"));
    }

    @Test
    void gaebBoqIri() {
        IriFactory factory = new IriFactory("https://data.example.org/gaeb");
        assertEquals("https://data.example.org/gaeb/boq/BOQ-01", factory.boq("BOQ-01"));
    }

    @Test
    void gaebSectionIri() {
        IriFactory factory = new IriFactory("https://data.example.org/gaeb");
        assertEquals("https://data.example.org/gaeb/section/03.01", factory.section("03.01"));
    }

    @Test
    void gaebItemIri() {
        IriFactory factory = new IriFactory("https://data.example.org/gaeb");
        assertEquals("https://data.example.org/gaeb/item/03.02.010", factory.item("03.02.010"));
    }

    @Test
    void gaebQuantitySplitIri() {
        IriFactory factory = new IriFactory("https://data.example.org/gaeb");
        assertEquals("https://data.example.org/gaeb/quantity-split/03.02.010-split-0",
                factory.quantitySplit("03.02.010", 0));
    }

    @Test
    void gaebUnitPriceComponentIri() {
        IriFactory factory = new IriFactory("https://data.example.org/gaeb");
        assertEquals("https://data.example.org/gaeb/unit-price-component/03.02.010-upc-1",
                factory.unitPriceComponent("03.02.010", 1));
    }

    @Test
    void gaebCatalogAssignmentIri() {
        IriFactory factory = new IriFactory("https://data.example.org/gaeb");
        assertEquals("https://data.example.org/gaeb/catalog-assignment/03.02.010-ca-0",
                factory.catalogAssignment("03.02.010", 0));
    }

    @Test
    void gaebExternalObjectReferenceIri() {
        IriFactory factory = new IriFactory("https://data.example.org/gaeb");
        assertEquals("https://data.example.org/gaeb/external-object-reference/ETW:12345",
                factory.externalObjectReference("ETW", "12345"));
    }

    @Test
    void scheduleIri() {
        IriFactory factory = new IriFactory("https://data.example.org/schedule");
        assertEquals("https://data.example.org/schedule/schedule/proj-1",
                factory.schedule("proj-1"));
    }

    @Test
    void activityIri() {
        IriFactory factory = new IriFactory("https://data.example.org/schedule");
        assertEquals("https://data.example.org/schedule/activity/A100",
                factory.activity("A100"));
    }

    @Test
    void precedenceRelationshipIri() {
        IriFactory factory = new IriFactory("https://data.example.org/schedule");
        assertEquals("https://data.example.org/schedule/precedence-relationship/rel-001",
                factory.precedenceRelationship("rel-001"));
    }

    @Test
    void nullBaseIriThrows() {
        assertThrows(IllegalArgumentException.class, () -> new IriFactory(null));
    }

    @Test
    void blankBaseIriThrows() {
        assertThrows(IllegalArgumentException.class, () -> new IriFactory("  "));
    }

    @Test
    void nullEntityTypeThrows() {
        IriFactory factory = new IriFactory("https://data.example.org");
        assertThrows(IllegalArgumentException.class, () -> factory.iri(null, "A100"));
    }

    @Test
    void nullIdentifierThrows() {
        IriFactory factory = new IriFactory("https://data.example.org");
        assertThrows(IllegalArgumentException.class, () -> factory.iri("activity", null));
    }
}
