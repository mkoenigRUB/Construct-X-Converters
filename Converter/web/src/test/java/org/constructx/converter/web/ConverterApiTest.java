package org.constructx.converter.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the converter REST API.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConverterApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointReturnsUp() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void convertersEndpointListsGaebBoq() throws Exception {
        mockMvc.perform(get("/api/v1/converters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("gaeb-boq"))
                .andExpect(jsonPath("$[0].outputFormat").value("text/turtle"));
    }

    @Test
    void exampleEndpointServesGaebXml() throws Exception {
        mockMvc.perform(get("/api/v1/examples/gaeb-boq.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/xml"))
                .andExpect(content().string(containsString("GAEB")));
    }

    @Test
    void convertValidGaebXmlReturnsTtl() throws Exception {
        Path example = Path.of("..", "core", "src", "test", "resources", "gaeb-x80-example.xml");
        byte[] content = Files.readAllBytes(example);
        MockMultipartFile file = new MockMultipartFile(
                "file", "gaeb-x80-example.xml", "application/xml", content);

        mockMvc.perform(multipart("/api/v1/convert/gaeb-boq").file(file))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/turtle"))
                .andExpect(content().string(containsString("conx-gaeb:BillOfQuantities")));
    }

    @Test
    void convertWithJsonFormatReturnsJsonLd() throws Exception {
        Path example = Path.of("..", "core", "src", "test", "resources", "gaeb-x80-example.xml");
        byte[] content = Files.readAllBytes(example);
        MockMultipartFile file = new MockMultipartFile(
                "file", "gaeb-x80-example.xml", "application/xml", content);

        mockMvc.perform(multipart("/api/v1/convert/gaeb-boq")
                        .file(file)
                        .param("format", "json"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/ld+json"))
                .andExpect(content().string(containsString("conx-gaeb:BillOfQuantities")));
    }

    @Test
    void convertWithPlainJsonFormatReturnsPlainJson() throws Exception {
        Path example = Path.of("..", "core", "src", "test", "resources", "gaeb-x80-example.xml");
        byte[] content = Files.readAllBytes(example);
        MockMultipartFile file = new MockMultipartFile(
                "file", "gaeb-x80-example.xml", "application/xml", content);

        mockMvc.perform(multipart("/api/v1/convert/gaeb-boq")
                        .file(file)
                        .param("format", "plainjson"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(content().string(containsString("\"version\": 2")))
                .andExpect(content().string(containsString("\"nodes\"")))
                .andExpect(content().string(containsString("\"conx-gaeb:BillOfQuantities\"")))
                .andExpect(content().string(containsString("\"datatype\": \"xsd:decimal\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("@id"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("@context"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("\"lang\":"))));
    }

    @Test
    void convertWithPlainJsonAndEnvelopeReturnsExchangePackage() throws Exception {
        Path example = Path.of("..", "core", "src", "test", "resources", "gaeb-x80-example.xml");
        byte[] content = Files.readAllBytes(example);
        MockMultipartFile file = new MockMultipartFile(
                "file", "gaeb-x80-example.xml", "application/xml", content);

        mockMvc.perform(multipart("/api/v1/convert/gaeb-boq")
                        .file(file)
                        .param("format", "plainjson")
                        .param("envelope", "true"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(content().string(containsString(
                        "\"id\": \"https://data.construct-x.org/gaeb/boq/BOQ-001\"")))
                .andExpect(content().string(containsString("\"identifier\": \"BOQ-001\"")))
                .andExpect(content().string(containsString("\"semanticModel\": {")))
                .andExpect(content().string(containsString("\"type\": \"BillOfQuantities\"")))
                .andExpect(content().string(containsString(
                        "\"https://www.construct-x.org/ontology/gaeb-boq/0.5.0\"")))
                .andExpect(content().string(containsString("\"content\": {")))
                .andExpect(content().string(containsString("\"entities\": [")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("\"nodes\""))));
    }

    @Test
    void convertWithUnsupportedFormatReturns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "invalid.xml", "text/xml", "<x/>".getBytes());

        mockMvc.perform(multipart("/api/v1/convert/gaeb-boq")
                        .file(file)
                        .param("format", "ntriples"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Unsupported format")));
    }

    @Test
    void convertInvalidGaebXmlReturns422() throws Exception {
        String invalidXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <GAEB xmlns="http://www.gaeb.de/GAEB_DA_XML/DA80/3.3" Version="3.3">
                    <BoQ ID="BOQ-INVALID">
                        <BoQInfo>
                            <LblBoQ>Missing required elements</LblBoQ>
                        </BoQInfo>
                    </BoQ>
                </GAEB>
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "invalid.xml", "application/xml", invalidXml.getBytes());

        mockMvc.perform(multipart("/api/v1/convert/gaeb-boq").file(file))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void sampleEndpointServesExampleXml() throws Exception {
        mockMvc.perform(get("/api/v1/sample/gaeb-boq"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/xml"))
                .andExpect(content().string(containsString("GAEB")));
    }

    @Test
    void validateEndpointReportsConformantDocument() throws Exception {
        Path example = Path.of("..", "core", "src", "test", "resources", "gaeb-x80-example.xml");
        byte[] content = Files.readAllBytes(example);
        MockMultipartFile file = new MockMultipartFile(
                "file", "gaeb-x80-example.xml", "application/xml", content);

        mockMvc.perform(multipart("/api/v1/validate/gaeb-boq").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private static byte[] forwardConvertExampleToTtl() throws Exception {
        Path example = Path.of("..", "core", "src", "test", "resources", "gaeb-x80-example.xml");
        var model = new org.constructx.converter.GaebBoqConverter()
                .convert(example.toFile());
        return org.constructx.converter.GaebBoqConverter
                .toTtlString(model).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test
    void reverseGaebBoqFromTtlReturnsGaebXml() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "gaeb-boq.ttl", "text/turtle", forwardConvertExampleToTtl());

        var result = mockMvc.perform(multipart("/api/v1/reverse/gaeb-boq").file(file))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.xml").isString())
                .andExpect(jsonPath("$.warnings").isArray())
                .andReturn();
        String xml = MAPPER.readTree(result.getResponse().getContentAsString())
                .get("xml").asText();
        org.junit.jupiter.api.Assertions.assertTrue(xml.contains("<GAEB"));
        org.junit.jupiter.api.Assertions.assertTrue(xml.contains("BoQ"));
    }

    @Test
    void reverseGaebBoqFromPlainJsonReturnsGaebXml() throws Exception {
        Path example = Path.of("..", "core", "src", "test", "resources", "gaeb-x80-example.xml");
        var model = new org.constructx.converter.GaebBoqConverter()
                .convert(example.toFile());
        String plainJson = org.constructx.sdk.PlainJsonModelWriter.write(model);
        MockMultipartFile file = new MockMultipartFile(
                "file", "gaeb-boq.json", "application/json",
                plainJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/reverse/gaeb-boq")
                        .file(file)
                        .param("format", "plainjson"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.xml").isString())
                .andExpect(jsonPath("$.warnings").isArray());
    }

    @Test
    void reverseGaebBoqWithoutBillOfQuantitiesReturns400() throws Exception {
        String noBoqTtl = """
                @prefix ex: <http://example.org/> .
                ex:s a ex:Thing ;
                     ex:p "v" .
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "no-boq.ttl", "text/turtle",
                noBoqTtl.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/reverse/gaeb-boq").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Conversion aborted")));
    }

    @Test
    void reverseGaebBoqWithUnsupportedFormatReturns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "invalid.ttl", "text/turtle", "@prefix ex: <http://example.org/> .".getBytes());

        mockMvc.perform(multipart("/api/v1/reverse/gaeb-boq")
                        .file(file)
                        .param("format", "ntriples"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Unsupported format")));
    }

    // --- Bauposten-Auswahl (selection) ---

    @Test
    void positionsEndpointReturnsSelectionTree() throws Exception {
        Path example = Path.of("..", "core", "src", "test", "resources", "gaeb-x80-example.xml");
        byte[] content = Files.readAllBytes(example);
        MockMultipartFile file = new MockMultipartFile(
                "file", "gaeb-x80-example.xml", "application/xml", content);

        mockMvc.perform(multipart("/api/v1/positions/gaeb-boq").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kind").value("SECTION"))
                .andExpect(jsonPath("$[0].id").value("SEC-01"))
                .andExpect(jsonPath("$[0].ordinalNumber").value("01"))
                .andExpect(jsonPath("$[0].itemCount").value(3))
                .andExpect(jsonPath("$[0].children[0].ordinalNumber").value("0101"))
                .andExpect(jsonPath("$[0].children[0].children[0].kind").value("ITEM"))
                .andExpect(jsonPath("$[0].children[0].children[0].ordinalNumber").value("0101001"))
                .andExpect(jsonPath("$[1].id").value("SEC-04"))
                .andExpect(jsonPath("$[1].ordinalNumber").value("02"));
    }

    @Test
    void positionsEndpointRejectsInvalidXmlWith422() throws Exception {
        String invalidXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <GAEB xmlns="http://www.gaeb.de/GAEB_DA_XML/DA80/3.3" Version="3.3">
                    <BoQ ID="BOQ-INVALID">
                        <BoQInfo><LblBoQ>Broken</LblBoQ></BoQInfo>
                    </BoQ>
                </GAEB>
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "invalid.xml", "application/xml", invalidXml.getBytes());

        mockMvc.perform(multipart("/api/v1/positions/gaeb-boq").file(file))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void convertWithSelectionReturnsOnlySelectedItems() throws Exception {
        Path example = Path.of("..", "core", "src", "test", "resources", "gaeb-x80-example.xml");
        byte[] content = Files.readAllBytes(example);
        MockMultipartFile file = new MockMultipartFile(
                "file", "gaeb-x80-example.xml", "application/xml", content);

        String ttl = mockMvc.perform(multipart("/api/v1/convert/gaeb-boq")
                        .file(file)
                        .param("selection", "0101001"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/turtle"))
                .andReturn().getResponse().getContentAsString();
        assertTrue(ttl.contains("ITM-001"), "selected item must be present");
        assertFalse(ttl.contains("ITM-002"), "unselected sibling item must be absent");
        assertFalse(ttl.contains("ITM-004"), "unselected section item must be absent");
    }

    @Test
    void convertWithSectionSelectionReturnsWholeSubtree() throws Exception {
        Path example = Path.of("..", "core", "src", "test", "resources", "gaeb-x80-example.xml");
        byte[] content = Files.readAllBytes(example);
        MockMultipartFile file = new MockMultipartFile(
                "file", "gaeb-x80-example.xml", "application/xml", content);

        String ttl = mockMvc.perform(multipart("/api/v1/convert/gaeb-boq")
                        .file(file)
                        .param("selection", "01"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(ttl.contains("ITM-001"));
        assertTrue(ttl.contains("ITM-002"));
        assertTrue(ttl.contains("ITM-003"));
        assertFalse(ttl.contains("ITM-004"), "item of unselected section 02 must be absent");
    }

    @Test
    void convertWithoutSelectionKeepsAllItems() throws Exception {
        Path example = Path.of("..", "core", "src", "test", "resources", "gaeb-x80-example.xml");
        byte[] content = Files.readAllBytes(example);
        MockMultipartFile file = new MockMultipartFile(
                "file", "gaeb-x80-example.xml", "application/xml", content);

        String ttl = mockMvc.perform(multipart("/api/v1/convert/gaeb-boq").file(file))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(ttl.contains("ITM-001"));
        assertTrue(ttl.contains("ITM-002"));
        assertTrue(ttl.contains("ITM-003"));
        assertTrue(ttl.contains("ITM-004"));
    }

    // --- Strict mode (reverse) ---

    @Test
    void reverseGaebBoqStrictToleratesLossyApproximations() throws Exception {
        // The example round-trips with only lossy approximations (percentage
        // splits, synthesized header) which stay tolerated in strict mode.
        MockMultipartFile file = new MockMultipartFile(
                "file", "gaeb-boq.ttl", "text/turtle", forwardConvertExampleToTtl());

        mockMvc.perform(multipart("/api/v1/reverse/gaeb-boq")
                        .file(file)
                        .param("strict", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.xml").isString());
    }

    @Test
    void reverseGaebBoqStrictAbortsOnDataLoss() throws Exception {
        // Drop the ordinal number of an item so the reverse converter must
        // skip it -> data loss -> strict mode aborts with 400.
        String noOrdinalTtl = """
                @prefix conx-gaeb: <https://www.construct-x.org/ontology/gaeb-boq#> .
                @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
                @prefix ex: <https://data.construct-x.org/gaeb/strict-test/> .
                ex:boq a conx-gaeb:BillOfQuantities ;
                    conx-gaeb:identifier "BOQ-001" ;
                    conx-gaeb:name "Test" ;
                    conx-gaeb:hasRootSection ex:sec .
                ex:sec a conx-gaeb:BoQSection ;
                    conx-gaeb:identifier "SEC-01" ;
                    conx-gaeb:ordinalNumber "01" ;
                    conx-gaeb:hasItem ex:itm .
                ex:itm a conx-gaeb:BoQItem ;
                    conx-gaeb:identifier "ITM-001" ;
                    conx-gaeb:quantity "10.000"^^xsd:decimal .
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "no-ordinal.ttl", "text/turtle", noOrdinalTtl.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/reverse/gaeb-boq")
                        .file(file)
                        .param("strict", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Conversion aborted")))
                .andExpect(jsonPath("$.detail").value(containsString("Strict mode")));
    }
}
