package org.constructx.rdf;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RdfApiTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsOntologyProfiles() throws Exception {
        mockMvc.perform(get("/api/v1/profiles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'construction-schedule')]").exists())
                .andExpect(jsonPath("$[?(@.id == 'schedule-breakdown-link')]").exists());
    }

    @Test
    void convertsTurtleToJsonLdAndKeepsTriples() throws Exception {
        String ttl = "@prefix ex: <https://example.org/> . ex:item ex:label \"Test\" .";
        MockMultipartFile file = new MockMultipartFile("file", "sample.ttl", "text/turtle", ttl.getBytes());

        MvcResult converted = mockMvc.perform(multipart("/api/v1/convert").file(file)
                        .param("sourceFormat", "ttl")
                        .param("targetFormat", "jsonld"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/ld+json"))
                .andExpect(content().string(containsString("ex:label")))
                .andExpect(content().string(containsString("https://example.org/")))
                .andExpect(content().string(containsString("Test")))
                .andReturn();

        MockMultipartFile jsonLd = new MockMultipartFile("file", "converted.jsonld",
                "application/ld+json", converted.getResponse().getContentAsByteArray());
        mockMvc.perform(multipart("/api/v1/convert").file(jsonLd)
                        .param("sourceFormat", "jsonld")
                        .param("targetFormat", "ttl"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Test")));
    }

    @Test
    void convertsJsonLdToTurtle() throws Exception {
        String jsonLd = """
                {"@id":"https://example.org/item",
                 "https://example.org/label":"Test"}
                """;
        MockMultipartFile file = new MockMultipartFile("file", "sample.jsonld",
                "application/ld+json", jsonLd.getBytes());

        mockMvc.perform(multipart("/api/v1/convert").file(file)
                        .param("sourceFormat", "jsonld")
                        .param("targetFormat", "ttl"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/turtle"))
                .andExpect(content().string(containsString("https://example.org/label")))
                .andExpect(content().string(containsString("Test")));
    }

    @Test
    void rejectsRemoteJsonLdContexts() throws Exception {
        String jsonLd = """
                {"@context":"https://example.org/context.jsonld",
                 "@id":"https://example.org/item",
                 "https://example.org/label":"Test"}
                """;
        MockMultipartFile file = new MockMultipartFile("file", "remote.jsonld",
                "application/ld+json", jsonLd.getBytes());

        mockMvc.perform(multipart("/api/v1/convert").file(file)
                        .param("sourceFormat", "jsonld")
                        .param("targetFormat", "ttl"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        containsString("Externe JSON-LD-Kontexte")));
    }

    @Test
    void rejectsMalformedRdfInput() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "broken.ttl", "text/turtle", "not a valid RDF statement".getBytes());

        mockMvc.perform(multipart("/api/v1/convert").file(file)
                        .param("sourceFormat", "ttl")
                        .param("targetFormat", "jsonld"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("kein gültiges ttl-RDF-Dokument")));
    }

    @Test
    void validatesConformantGaebExampleAgainstOntologyProfile() throws Exception {
        // Ontology root: CONX_ONTOLOGY_ROOT env var / conx.ontology.root system
        // property (matching RdfService's constructx.ontology-root), defaulting
        // to the pinned 'ontologies' git submodule at the repository root.
        String env = System.getenv("CONX_ONTOLOGY_ROOT");
        String prop = System.getProperty("conx.ontology.root");
        Path root = (env != null && !env.isBlank()) ? Path.of(env)
                : (prop != null && !prop.isBlank()) ? Path.of(prop)
                : Path.of("..", "ontologies");
        byte[] ttl = Files.readAllBytes(root.resolve("gaeb-boq").resolve("gaeb-boq-example.ttl"));
        MockMultipartFile file = new MockMultipartFile("file", "gaeb-boq-example.ttl",
                "text/turtle", ttl);

        mockMvc.perform(multipart("/api/v1/validate").file(file)
                        .param("format", "ttl")
                        .param("profile", "gaeb-boq"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.violationCount").value(0));
    }

    @Test
    void reportsShaclViolations() throws Exception {
        String invalidSchedule = """
                @prefix conx: <https://www.construct-x.org/ontology/construction-schedule#> .
                <https://example.org/schedule> a conx:ConstructionSchedule .
                """;
        MockMultipartFile file = new MockMultipartFile("file", "invalid.ttl",
                "text/turtle", invalidSchedule.getBytes());

        mockMvc.perform(multipart("/api/v1/validate").file(file)
                        .param("format", "ttl")
                        .param("profile", "construction-schedule"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.violationCount").isNumber())
                .andExpect(jsonPath("$.violations[0].severity").isNotEmpty());
    }

    @Test
    void everyPublishedProfileCanBeValidated() throws Exception {
        String ttl = "<https://example.org/resource> <https://example.org/property> \"value\" .";
        for (String profile : new String[]{
                "built-asset", "breakdown-structure", "construction-delivery",
                "construction-progress-report", "construction-schedule", "construction-site",
                "gaeb-boq", "built-asset-breakdown-link", "delivery-boq-link",
                "delivery-site-link", "schedule-boq-link", "schedule-breakdown-link",
                "schedule-delivery-link", "schedule-progress-report-link",
                "schedule-site-link", "site-breakdown-link", "site-built-asset-link"}) {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "empty.ttl", "text/turtle", ttl.getBytes());
            mockMvc.perform(multipart("/api/v1/validate").file(file)
                            .param("format", "ttl")
                            .param("profile", profile))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.profile").value(profile));
        }
    }

    @Test
    void convertsTurtleToPlainJson() throws Exception {
        String ttl = "@prefix ex: <https://example.org/> . ex:item ex:label \"Test\" .";
        MockMultipartFile file = new MockMultipartFile("file", "sample.ttl", "text/turtle", ttl.getBytes());

        mockMvc.perform(multipart("/api/v1/convert").file(file)
                        .param("sourceFormat", "ttl")
                        .param("targetFormat", "plainjson"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(content().string(containsString("\"version\": 2")))
                .andExpect(content().string(containsString("\"nodes\"")))
                .andExpect(content().string(containsString("\"tripleCount\": 1")))
                .andExpect(content().string(containsString("Test")))
                .andExpect(content().string(containsString("\"https://example.org/label\": \"Test\"")));
    }

    @Test
    void convertsTurtleToPlainJsonWithEnvelope() throws Exception {
        String ttl = "@prefix ex: <https://example.org/> . ex:item ex:label \"Test\" .";
        MockMultipartFile file = new MockMultipartFile("file", "sample.ttl", "text/turtle", ttl.getBytes());

        mockMvc.perform(multipart("/api/v1/convert").file(file)
                        .param("sourceFormat", "ttl")
                        .param("targetFormat", "plainjson")
                        .param("envelope", "true"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(content().string(containsString("\"id\": \"https://example.org/item\"")))
                .andExpect(content().string(containsString("\"identifier\": \"item\"")))
                .andExpect(content().string(containsString("\"semanticModel\": {")))
                .andExpect(content().string(containsString("\"content\": {")))
                .andExpect(content().string(containsString("\"entities\": [")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("\"nodes\""))));
    }

    @Test
    void plainJsonEnvelopeCuriesConstructXVocabularies() throws Exception {
        String ttl = """
                @prefix conx: <https://www.construct-x.org/ontology/construction-schedule#> .
                @prefix conx-st: <https://www.construct-x.org/vocab/construction-schedule/schedule-type#> .
                <https://example.org/schedule> a conx:ConstructionSchedule ;
                    conx:scheduleType conx-st:FrameNetwork .
                """;
        MockMultipartFile file = new MockMultipartFile("file", "sample.ttl", "text/turtle", ttl.getBytes());

        mockMvc.perform(multipart("/api/v1/convert").file(file)
                        .param("sourceFormat", "ttl")
                        .param("targetFormat", "plainjson"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"type\": \"conx-cs:ConstructionSchedule\"")))
                .andExpect(content().string(containsString("\"conx-cs:scheduleType\": \"conx-st:FrameNetwork\"")));
    }

    @Test
    void rejectsPlainJsonAsSourceFormat() throws Exception {
        String plain = """
                {"meta":{"format":"construct-x-plain-json","version":1},
                 "nodes":[{"id":"https://example.org/item"}]}
                """;
        MockMultipartFile file = new MockMultipartFile("file", "sample.json",
                "application/json", plain.getBytes());

        mockMvc.perform(multipart("/api/v1/convert").file(file)
                        .param("sourceFormat", "plainjson")
                        .param("targetFormat", "ttl"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("nur erzeugt")));
    }

    @Test
    void rejectsUnsupportedTargetFormat() throws Exception {
        String ttl = "@prefix ex: <https://example.org/> . ex:item ex:label \"Test\" .";
        MockMultipartFile file = new MockMultipartFile("file", "sample.ttl", "text/turtle", ttl.getBytes());

        mockMvc.perform(multipart("/api/v1/convert").file(file)
                        .param("sourceFormat", "ttl")
                        .param("targetFormat", "ntriples"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Nicht unterstütztes")));
    }

    @Test
    void convertResponseHasRfc6266ContentDisposition() throws Exception {
        String ttl = "@prefix ex: <https://example.org/> . ex:item ex:label \"Test\" .";
        MockMultipartFile file = new MockMultipartFile("file", "sample.ttl", "text/turtle", ttl.getBytes());

        mockMvc.perform(multipart("/api/v1/convert").file(file)
                        .param("sourceFormat", "ttl")
                        .param("targetFormat", "jsonld"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"sample.jsonld\""));
    }

    @Test
    void attachmentFilenameUsesRfc5987ForNonAsciiNames() {
        assertEquals("attachment; filename=\"Gr__e__.ttl\"; filename*=UTF-8''Gr%C3%BC%C3%9Fe%20%C3%BC.ttl",
                RdfController.attachmentFilename("Grüße ü.ttl"));
    }
}
