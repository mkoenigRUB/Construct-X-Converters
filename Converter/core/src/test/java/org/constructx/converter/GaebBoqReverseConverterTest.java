package org.constructx.converter;

import org.apache.jena.rdf.model.Model;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip test: GAEB XML -&gt; RDF (GaebBoqConverter) -&gt; GAEB XML
 * (GaebBoqReverseConverter). The reconstructed document must be XSD-valid
 * and re-convert to an RDF model with the same entity data.
 */
class GaebBoqReverseConverterTest {

    private static final File EXAMPLE =
            new File("src/test/resources/gaeb-x80-example.xml");

    private Model forwardConvert() throws Exception {
        return new GaebBoqConverter().convert(EXAMPLE);
    }

    @Test
    void roundTripProducesXsdValidXml() throws Exception {
        Model model = forwardConvert();
        assertNotNull(model);
        assertTrue(model.size() > 0);

        GaebBoqReverseConverter.Result result =
                GaebBoqReverseConverter.convert(model,
                        GaebBoqReverseConverter.HeaderOverrides.of(
                                "Testprojekt Alpha", "EUR"));

        // The synthesized header must be reported as a warning.
        assertTrue(result.warnings().stream()
                        .anyMatch(w -> w.contains("synthesized")),
                "expected a header-synthesis warning, got: " + result.warnings());

        // The generated document must pass the official GAEB XSD.
        GaebXmlValidator validator = new GaebXmlValidator();
        File tmp = tempFile(result.xml(), ".xml");
        List<GaebXmlValidator.ValidationError> errors = validator.validate(tmp);
        assertTrue(errors.isEmpty(), "XSD errors: " + errors);

        // All entities of the example must be present again.
        for (String id : new String[]{"BOQ-001", "SEC-01", "SEC-02", "SEC-03",
                "SEC-04", "ITM-001", "ITM-002", "ITM-003", "ITM-004"}) {
            assertTrue(result.xml().contains("\"" + id + "\""),
                    "missing GAEB ID " + id);
        }
        // Structure: 4 items, 4 categories.
        assertEquals(4, countOccurrences(result.xml(), "<Item "));
        assertEquals(4, countOccurrences(result.xml(), "<BoQCtgy "));
    }

    @Test
    void roundTripPreservesItemData() throws Exception {
        Model model = forwardConvert();
        GaebBoqReverseConverter.Result result =
                GaebBoqReverseConverter.convert(model, null);
        String xml = result.xml();

        // Item 01: quantity, splits (absolute), unit, price, components, total
        assertTrue(xml.contains("<Qty>125.500</Qty>"));
        assertEquals(2, countOccurrences(xml, "<QtySplit>"));
        assertTrue(xml.contains("<QU>m3</QU>"));
        assertTrue(xml.contains("<UP>45.800</UP>"));
        assertTrue(xml.contains("<UPComp1>12.500</UPComp1>"));
        assertTrue(xml.contains("<UPComp2>20.300</UPComp2>"));
        assertTrue(xml.contains("<UPComp3>13.000</UPComp3>"));
        assertTrue(xml.contains("<IT>5747.90</IT>"));
        assertTrue(xml.contains("RNoIndex=\"1\""));

        // Order numbers: nested section 01 -> 01 -> 001 / 002, root 02 -> 001
        assertTrue(xml.contains("RNoPart=\"01\""));
        assertTrue(xml.contains("RNoPart=\"001\""));
        assertTrue(xml.contains("RNoPart=\"002\""));
        assertTrue(xml.contains("RNoPart=\"02\""));

        // Catalog assignments at item and split level
        assertTrue(xml.contains("<CtlgID>STLB-Bau</CtlgID>"));
        assertEquals(2, countOccurrences(xml, "<CtlgID>"));

        // Texts (long and short) of item 01
        assertTrue(xml.contains("Aushub für Flachgründung"));
        assertTrue(xml.contains("<span>Aushub Flachgründung</span>"));

        // Price component labels and count
        assertTrue(xml.contains("<NoUPComps>3</NoUPComps>"));
        assertTrue(xml.contains("<LblUPComp1 Type=\"Wages\">Lohn</LblUPComp1>"));
    }

    @Test
    void roundTripReconvertsToSameModel() throws Exception {
        Model original = forwardConvert();
        GaebBoqReverseConverter.Result result =
                GaebBoqReverseConverter.convert(original, null);

        GaebBoqConverter secondConverter = new GaebBoqConverter();
        File tmp = tempFile(result.xml(), ".xml");
        Model reconverted = secondConverter.convert(tmp);

        assertEquals(original.size(), reconverted.size(),
                "triple count mismatch:\noriginal=" + original.size()
                        + "\nreconverted=" + reconverted.size());

        // Spot-check key entity data survives the second conversion.
        org.apache.jena.rdf.model.Resource item = reconverted.getResource(
                "https://data.construct-x.org/gaeb/item/ITM-001");
        assertEquals(1, item.listProperties(
                reconverted.createProperty(ConxNamespaces.P_QUANTITY)).toList().size());
        assertEquals("125.500", item.getProperty(
                reconverted.createProperty(ConxNamespaces.P_QUANTITY))
                .getString());
        assertEquals("m3", item.getProperty(
                reconverted.createProperty(ConxNamespaces.P_UNIT_CODE)).getString());
        assertEquals("0101001", item.getProperty(
                reconverted.createProperty(ConxNamespaces.P_ORDINAL_NUMBER)).getString());
    }

    @Test
    void rejectsModelWithoutBillOfQuantities() {
        org.apache.jena.rdf.model.Model model =
                org.apache.jena.rdf.model.ModelFactory.createDefaultModel();
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> GaebBoqReverseConverter.convert(model, null));
    }

    @Test
    void strictToleratesLossyApproximations() throws Exception {
        // The example model round-trips with only lossy approximations
        // (percentage splits as absolute values, synthesized header) ->
        // strict mode must still succeed.
        Model model = forwardConvert();
        GaebBoqReverseConverter.Result result =
                GaebBoqReverseConverter.convert(model, null, true);
        assertTrue(result.xml().contains("<GAEB"));
        assertTrue(result.warnings().stream()
                        .anyMatch(w -> w.contains("synthesized")),
                "approximation warnings remain reported: " + result.warnings());
    }

    @Test
    void strictAbortsOnDataLoss() throws Exception {
        // Model with an item that has no ordinal number: the reverse
        // converter must skip it (data loss) -> strict mode aborts.
        org.apache.jena.rdf.model.Model model =
                org.apache.jena.rdf.model.ModelFactory.createDefaultModel();
        org.apache.jena.rdf.model.Resource boq = model.createResource(
                "https://data.construct-x.org/gaeb/boq/BOQ-001");
        boq.addProperty(org.apache.jena.vocabulary.RDF.type,
                model.createResource(ConxNamespaces.BILL_OF_QUANTITIES));
        boq.addProperty(model.createProperty(ConxNamespaces.P_IDENTIFIER), "BOQ-001");
        boq.addProperty(model.createProperty(ConxNamespaces.P_NAME), "Test");
        org.apache.jena.rdf.model.Resource section = model.createResource(
                "https://data.construct-x.org/gaeb/section/SEC-01");
        section.addProperty(org.apache.jena.vocabulary.RDF.type,
                model.createResource(ConxNamespaces.BOQ_SECTION));
        section.addProperty(model.createProperty(ConxNamespaces.P_IDENTIFIER), "SEC-01");
        section.addProperty(model.createProperty(ConxNamespaces.P_ORDINAL_NUMBER), "01");
        boq.addProperty(model.createProperty(ConxNamespaces.P_HAS_ROOT_SECTION), section);
        org.apache.jena.rdf.model.Resource item = model.createResource(
                "https://data.construct-x.org/gaeb/item/ITM-001");
        item.addProperty(org.apache.jena.vocabulary.RDF.type,
                model.createResource(ConxNamespaces.BOQ_ITEM));
        item.addProperty(model.createProperty(ConxNamespaces.P_IDENTIFIER), "ITM-001");
        item.addProperty(model.createProperty(ConxNamespaces.P_QUANTITY), "10.000");
        section.addProperty(model.createProperty(ConxNamespaces.P_HAS_ITEM), item);

        IllegalArgumentException e = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> GaebBoqReverseConverter.convert(model, null, true));
        assertTrue(e.getMessage().contains("Strict mode"),
                "expected a strict-mode abort message but got: " + e.getMessage());

        // Pragmatic mode on the same model still converts (with the warning).
        GaebBoqReverseConverter.Result pragmatic =
                GaebBoqReverseConverter.convert(model, null);
        assertTrue(pragmatic.warnings().stream()
                        .anyMatch(w -> w.contains("no ordinal number")),
                "expected the no-ordinal warning, got: " + pragmatic.warnings());
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    private static File tempFile(String content, String suffix) throws Exception {
        File tmp = File.createTempFile("gaeb-reverse", suffix);
        tmp.deleteOnExit();
        Files.writeString(tmp.toPath(), content);
        return tmp;
    }
}
