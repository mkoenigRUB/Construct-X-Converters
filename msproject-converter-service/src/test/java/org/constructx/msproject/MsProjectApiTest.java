package org.constructx.msproject;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MsProjectApiTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private byte[] exampleXml() throws Exception {
        return Files.readAllBytes(Path.of("src", "main", "resources", "examples", "msproject-example.xml"));
    }

    private HttpEntity<MultiValueMap<String, Object>> multipart(byte[] content, String... params) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return "plan.xml";
            }
        });
        for (int i = 0; i < params.length; i += 2) {
            body.add(params[i], params[i + 1]);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void healthIsUp() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/health", String.class);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("\"UP\""));
    }

    @Test
    void listsConverter() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/converters", String.class);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("msproject"));
    }

    @Test
    void convertsExampleToTtl() throws Exception {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/convert/msproject", multipart(exampleXml(), "format", "ttl"), String.class);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("conx-cs:ConstructionSchedule"));
        assertTrue(response.getBody().contains("conx-cs:Milestone"));
    }

    @Test
    void convertsExampleToJsonLd() throws Exception {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/convert/msproject", multipart(exampleXml(), "format", "json"), String.class);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("ConstructionSchedule"),
                "Expected ConstructionSchedule in JSON-LD but got: " + response.getBody());
    }

    @Test
    void convertsExampleToPlainJson() throws Exception {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/convert/msproject", multipart(exampleXml(), "format", "plainjson"), String.class);
        assertEquals(200, response.getStatusCode().value());
        String body = response.getBody();
        assertTrue(body.contains("\"meta\""), "Expected meta block in plain JSON but got: " + body);
        assertTrue(body.contains("\"version\": 2"), "Expected format version 2 but got: " + body);
        assertTrue(body.contains("\"nodes\""), "Expected nodes array in plain JSON but got: " + body);
        assertTrue(body.contains("conx-cs:ConstructionSchedule"),
                "Expected CURIEd type in plain JSON but got: " + body);
        assertFalse(body.contains("@id"), "plain JSON must not contain JSON-LD keywords");
        assertFalse(body.contains("@context"), "plain JSON must not contain JSON-LD context");
    }

    @Test
    void convertsExampleToPlainJsonWithEnvelope() throws Exception {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/convert/msproject",
                multipart(exampleXml(), "format", "plainjson", "envelope", "true"), String.class);
        assertEquals(200, response.getStatusCode().value());
        String body = response.getBody();
        assertTrue(body.contains("\"id\": \"https://data.construct-x.org/schedule/doppelboden-feinterminplanung/schedule/doppelboden-feinterminplanung\""),
                "Expected root schedule IRI as package id but got: " + body);
        assertTrue(body.contains("\"identifier\": \"doppelboden-feinterminplanung\""),
                "Expected root identifier but got: " + body);
        assertTrue(body.contains("\"semanticModel\": {"));
        assertTrue(body.contains("\"type\": \"ConstructionSchedule\""));
        assertTrue(body.contains("\"conformsTo\": ["));
        assertTrue(body.contains("\"https://www.construct-x.org/ontology/construction-schedule/0.5.0\""));
        assertTrue(body.contains("\"content\": {"));
        assertTrue(body.contains("\"entities\": ["));
        assertFalse(body.contains("\"nodes\""), "envelope must not use the plain nodes wrapper");
    }

    @Test
    void rejectsUnsupportedFormat() throws Exception {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/convert/msproject", multipart(exampleXml(), "format", "xml"), String.class);
        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void validatesExampleAgainstScheduleShapes() throws Exception {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/validate/msproject", multipart(exampleXml()), String.class);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("\"valid\":true"),
                "Expected valid result but got: " + response.getBody());
    }

    @Test
    void rejectsEmptyFile() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/convert/msproject", multipart(new byte[0]), String.class);
        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void rejectsNonMsProjectXml() {
        byte[] xml = "<foo xmlns=\"http://example.com\"><Tasks/></foo>".getBytes(StandardCharsets.UTF_8);
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/convert/msproject", multipart(xml), String.class);
        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void reverseMsProjectFromTtlReturnsXml() throws Exception {
        ResponseEntity<String> ttl = restTemplate.postForEntity(
                "/api/v1/convert/msproject", multipart(exampleXml(), "format", "ttl"), String.class);
        assertEquals(200, ttl.getStatusCode().value());

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/reverse/msproject",
                multipart(ttl.getBody().getBytes(StandardCharsets.UTF_8), "format", "ttl"), String.class);
        assertEquals(200, response.getStatusCode().value());
        String body = response.getBody();
        assertTrue(body.contains("\"xml\""), "Expected xml field but got: " + body);
        assertTrue(body.contains("\"warnings\""), "Expected warnings field but got: " + body);
        assertTrue(body.contains("http://schemas.microsoft.com/project"),
                "Expected MS Project namespace but got: " + body);
        assertTrue(body.contains("PredecessorLink"), "Expected predecessor links but got: " + body);
    }

    @Test
    void reverseMsProjectFromPlainJsonReturnsXml() throws Exception {
        ResponseEntity<String> plain = restTemplate.postForEntity(
                "/api/v1/convert/msproject", multipart(exampleXml(), "format", "plainjson"), String.class);
        assertEquals(200, plain.getStatusCode().value());

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/reverse/msproject",
                multipart(plain.getBody().getBytes(StandardCharsets.UTF_8), "format", "plainjson"), String.class);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("\"xml\""),
                "Expected xml field but got: " + response.getBody());
    }

    @Test
    void reverseMsProjectWithoutScheduleReturns400() {
        byte[] ttl = ("@prefix ex: <https://example.org/> .\n"
                + "ex:foo a ex:Bar ;\n"
                + "    ex:name \"no schedule\" .\n").getBytes(StandardCharsets.UTF_8);
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/reverse/msproject", multipart(ttl, "format", "ttl"), String.class);
        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody().contains("Conversion aborted"),
                "Expected Conversion aborted but got: " + response.getBody());
    }

    @Test
    void reverseMsProjectStrictAbortsOnInProgressApproximationWarnings() throws Exception {
        // The example contains an InProgress activity, which is a lossy
        // approximation (tolerated, not data loss) -> strict still succeeds.
        ResponseEntity<String> ttl = restTemplate.postForEntity(
                "/api/v1/convert/msproject", multipart(exampleXml(), "format", "ttl"), String.class);
        assertEquals(200, ttl.getStatusCode().value());

        ResponseEntity<String> strict = restTemplate.postForEntity(
                "/api/v1/reverse/msproject",
                multipart(ttl.getBody().getBytes(StandardCharsets.UTF_8), "format", "ttl", "strict", "true"),
                String.class);
        assertEquals(200, strict.getStatusCode().value(),
                "Lossy approximations must stay tolerated in strict mode: " + strict.getBody());
        assertTrue(strict.getBody().contains("\"xml\""));
    }

    @Test
    void reverseMsProjectStrictAbortsOnUnknownStatus() {
        // An activity with an unknown status is classified as data loss
        // (the status cannot be represented at all) -> strict aborts with 400.
        String ttl = "@prefix conx-s: <https://www.construct-x.org/ontology/construction-schedule#> .\n"
                + "@prefix conx-as: <https://www.construct-x.org/vocab/construction-schedule/activity-status#> .\n"
                + "@prefix ex: <https://data.construct-x.org/schedule/strict-test/> .\n"
                + "ex:schedule a conx-s:ConstructionSchedule ;\n"
                + "    conx-s:name \"Strict\" ;\n"
                + "    conx-s:hasActivity ex:a1 .\n"
                + "ex:a1 a conx-s:Activity ;\n"
                + "    conx-s:identifier \"T1\" ;\n"
                + "    conx-s:name \"Test\" ;\n"
                + "    conx-s:plannedStart \"2026-11-01T08:00:00\"^^<xsd:dateTime> ;\n"
                + "    conx-s:plannedFinish \"2026-11-02T08:00:00\"^^<xsd:dateTime> ;\n"
                + "    conx-s:hasStatus conx-as:OnHold .\n";
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/reverse/msproject",
                multipart(ttl.getBytes(StandardCharsets.UTF_8), "format", "ttl", "strict", "true"),
                String.class);
        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody().contains("Strict mode"),
                "Expected a strict-mode abort message but got: " + response.getBody());
    }

    @Test
    void reverseMsProjectWithEmptyFileReturns400() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/reverse/msproject", multipart(new byte[0]), String.class);
        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody().contains("No file provided"),
                "Expected No file provided but got: " + response.getBody());
    }

    @Test
    void convertResponseHasRfc6266ContentDisposition() throws Exception {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/convert/msproject", multipart(exampleXml(), "format", "ttl"), String.class);
        assertEquals(200, response.getStatusCode().value());
        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertEquals("attachment; filename=\"schedule.ttl\"", disposition);
    }

    @Test
    void exampleResponseHasRfc6266ContentDisposition() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/examples/msproject.xml", String.class);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("attachment; filename=\"msproject-example.xml\"",
                response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
    }

    @Test
    void attachmentFilenameUsesRfc5987ForNonAsciiNames() {
        assertEquals("attachment; filename=\"Gr__e__.ttl\"; filename*=UTF-8''Gr%C3%BC%C3%9Fe%20%C3%BC.ttl",
                MsProjectController.attachmentFilename("Grüße ü.ttl"));
    }
}
