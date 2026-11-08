package org.constructx.sdk;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the strict-mode warning classification shared by the reverse
 * GAEB and MS Project converters.
 */
class ConxStrictTest {

    @Test
    void classifiesDataLossWarningsAsStrict() {
        assertTrue(ConxStrict.isStrict(
                "Item ITM-001: no ordinal number in the model; using RNoPart \"000\"."));
        assertTrue(ConxStrict.isStrict(
                "Item ITM-002: ordinal number 02001 does not extend the parent order number 01; "
                        + "writing it verbatim as RNoPart."));
        assertTrue(ConxStrict.isStrict(
                "QuantitySplit ITM-001-SPLIT-1 has no quantity in the model; skipped."));
        assertTrue(ConxStrict.isStrict(
                "Item ITM-001: unit price component ITM-001-UPC5 could not be mapped to a UPComp slot; skipped."));
        assertTrue(ConxStrict.isStrict(
                "Item ITM-001 has quantity splits but no quantity; splits were skipped "
                        + "(GAEB requires Qty before QtySplit)."));
        assertTrue(ConxStrict.isStrict(
                "CatalogAssignment CA-1 is incomplete in the model; skipped."));
        assertTrue(ConxStrict.isStrict(
                "Relationship 'REL-1-2': missing predecessor or successor – skipped."));
        assertTrue(ConxStrict.isStrict(
                "Relationship 'REL-1-2': unknown successor 'https://data.example/x/T99' – skipped."));
        assertTrue(ConxStrict.isStrict(
                "Activity 'T9': no T<n> identifier – using ordinal 9."));
        assertTrue(ConxStrict.isStrict(
                "Identifier \"SEC 01\" is not a valid/unique GAEB ID; using \"SEC_01\"."));
        assertTrue(ConxStrict.isStrict(
                "Activity 'T3': unknown status 'OnHold' – assuming 0% complete."));
        assertTrue(ConxStrict.isStrict(
                "Schedule without name – using placeholder 'MS Project Plan'."));
        assertTrue(ConxStrict.isStrict(
                "Relationship 'REL-2-3': missing relationship type – using FinishToStart (0)."));
        assertTrue(ConxStrict.isStrict(
                "Relationship 'REL-2-3': unreadable lag 'PT99X' – using 0."));
    }

    @Test
    void classifiesLossyApproximationsAsTolerated() {
        // Synthesized GAEB header: nothing lost from the model.
        assertFalse(ConxStrict.isStrict(
                "GAEBInfo/PrjInfo/Award are not part of the RDF model and were synthesized "
                        + "with default values."));
        // InProgress → 50%: lossy approximation, data preserved at coarser granularity.
        assertFalse(ConxStrict.isStrict(
                "Activity 'T2': status 'InProgress' cannot be represented exactly – assuming 50% complete."));
        // Stored level mismatch: nesting depth wins, section is still converted.
        assertFalse(ConxStrict.isStrict(
                "Section SEC-02: stored level 3 does not match nesting depth 2; using nesting depth."));
    }

    @Test
    void strictWarningsFiltersInOrder() {
        List<String> warnings = List.of(
                "GAEBInfo/PrjInfo/Award are not part of the RDF model and were synthesized "
                        + "with default values.",
                "QuantitySplit SPLIT-1 has no quantity in the model; skipped.",
                "Item ITM-001: no ordinal number in the model; using RNoPart \"000\".");
        assertEquals(List.of(
                "QuantitySplit SPLIT-1 has no quantity in the model; skipped.",
                "Item ITM-001: no ordinal number in the model; using RNoPart \"000\"."),
                ConxStrict.strictWarnings(warnings));
    }

    @Test
    void failureMessageListsStrictWarnings() {
        List<String> warnings = List.of(
                "GAEBInfo/PrjInfo/Award are not part of the RDF model and were synthesized "
                        + "with default values.",
                "QuantitySplit SPLIT-1 has no quantity in the model; skipped.");
        String message = ConxStrict.failureMessage(warnings);
        assertTrue(message.contains("1 lossy step(s)"));
        assertTrue(message.contains("SPLIT-1 has no quantity"));
        assertFalse(message.contains("synthesized"));
    }
}
