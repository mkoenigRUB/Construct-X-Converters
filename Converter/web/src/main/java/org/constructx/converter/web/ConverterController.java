package org.constructx.converter.web;

import org.constructx.converter.GaebXmlValidator;
import org.constructx.converter.GaebXmlValidationException;
import org.constructx.sdk.RdfInput;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * REST API of the Construct-X converter web service.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET  /api/v1/health} - liveness probe</li>
 *   <li>{@code GET  /api/v1/converters} - list of available converters</li>
 *   <li>{@code POST /api/v1/convert/gaeb-boq} - upload GAEB XML, receive TTL
 *       (optionally restricted to a selection of positions via {@code selection})</li>
 *   <li>{@code POST /api/v1/validate/gaeb-boq} - upload GAEB XML, receive XSD report</li>
 *   <li>{@code POST /api/v1/positions/gaeb-boq} - upload GAEB XML, receive the
 *       position selection tree (all sections and items with full ordinal numbers)</li>
 *   <li>{@code POST /api/v1/reverse/gaeb-boq} - upload RDF (TTL/JSON-LD/plain JSON),
 *       receive GAEB DA XML 3.3 + lossy-step warnings ({@code strict=true}
 *       aborts on data-loss warnings)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1")
public class ConverterController {

    private final GaebBoqConversionService conversionService;
    private final GaebBoqReverseConversionService reverseService;

    public ConverterController(GaebBoqConversionService conversionService,
                               GaebBoqReverseConversionService reverseService) {
        this.conversionService = conversionService;
        this.reverseService = reverseService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "service", "construct-x-converters");
    }

    @GetMapping("/converters")
    public List<Map<String, Object>> converters() {
        return List.of(Map.of(
                "id", "gaeb-boq",
                "name", "GAEB DA XML 3.3 → Construct-X GAEB BoQ Ontology",
                "inputFormats", List.of("application/xml", "text/xml"),
                "inputDescription", "GAEB DA XML 3.3 (Datenaustauschphasen X80–X87)",
                "outputFormat", "text/turtle",
                "outputFormats", List.of(
                        Map.of("format", "ttl", "mediaType", "text/turtle", "extension", ".ttl"),
                        Map.of("format", "json", "mediaType", "application/ld+json", "extension", ".jsonld"),
                        Map.of("format", "plainjson", "mediaType", "application/json", "extension", ".json")),
                "ontology", "https://www.construct-x.org/ontology/gaeb-boq",
                "endpoint", "/api/v1/convert/gaeb-boq"
        ));
    }

    /**
     * Serves the bundled example GAEB XML for testing via the frontend.
     */
    @GetMapping(value = "/examples/gaeb-boq.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<org.springframework.core.io.Resource> exampleGaebBoq() {
        org.springframework.core.io.Resource resource =
                new org.springframework.core.io.ClassPathResource("examples/gaeb-x80-example.xml");
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"gaeb-x80-example.xml\"")
                .body(resource);
    }

    /**
     * Converts an uploaded GAEB DA XML 3.3 file to Construct-X ontology data.
     * The file is first validated against the official GAEB XSD schemas.
     *
     * @param format   output format: {@code ttl} (default), {@code json} (JSON-LD)
     *                 or {@code plainjson} (plain JSON, aligned with the exchange
     *                 package convention)
     * @param envelope only for {@code plainjson}: when {@code true} the node list
     *                 is wrapped in the exchange package envelope
     *                 ({@code id}, {@code identifier}, {@code semanticModel},
     *                 {@code content.entities})
     * @param selection optional comma-separated full ordinal numbers (Teil-OZ) of
     *                 items and/or sections to convert. A selected section
     *                 includes its whole subtree; unselected sections are kept
     *                 as empty structure nodes (only when they still contain a
     *                 selected descendant) or dropped. Empty = full conversion.
     */
    @PostMapping(value = "/convert/gaeb-boq", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> convertGaebBoq(@RequestParam("file") MultipartFile file,
                                            @RequestParam(value = "format", defaultValue = "ttl") String format,
                                            @RequestParam(value = "envelope", defaultValue = "false") boolean envelope,
                                            @RequestParam(value = "selection", required = false) String selection) throws Exception {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "No file provided",
                    "hint", "Upload a GAEB DA XML 3.3 file as multipart field 'file'"));
        }
        String normalized = format == null ? "ttl" : format.trim().toLowerCase(java.util.Locale.ROOT);
        boolean asJsonLd = "json".equals(normalized) || "jsonld".equals(normalized);
        boolean asPlainJson = "plainjson".equals(normalized) || "plain".equals(normalized)
                || "plain-json".equals(normalized);
        if (!"ttl".equals(normalized) && !"turtle".equals(normalized) && !asJsonLd && !asPlainJson) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Unsupported format: " + format,
                    "hint", "Use 'ttl', 'json' (JSON-LD) or 'plainjson'"));
        }
        Path tmp = Files.createTempFile("gaeb-upload-", ".xml");
        try {
            file.transferTo(tmp);
            GaebBoqConversionService.ConversionResult result =
                    conversionService.convert(tmp.toFile(), parseSelection(selection));
            String baseName = "gaeb-boq-" + stripExtension(file.getOriginalFilename());
            if (asJsonLd) {
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + baseName + ".jsonld\"")
                        .contentType(MediaType.parseMediaType("application/ld+json"))
                        .body(result.jsonLd());
            }
            if (asPlainJson) {
                String body = envelope ? result.plainJsonEnvelope() : result.plainJson();
                String fileName = baseName + (envelope ? "-package" : "") + ".json";
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body);
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + baseName + ".ttl\"")
                    .contentType(MediaType.parseMediaType("text/turtle"))
                    .body(result.ttl());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * Reverse conversion: uploads Construct-X RDF data and returns a GAEB
     * DA XML 3.3 document.
     *
     * <p>The response is JSON with the generated XML ({@code xml}) and the
     * warnings collected for lossy conversion steps ({@code warnings}).
     * If the generated document is not XSD-conformant, the endpoint answers
     * with 400 and the full validation report.
     *
     * @param format input format: {@code ttl} (default), {@code jsonld}
     *               or {@code plainjson}
     * @param strict when {@code true}, lossy steps that drop data (skipped
     *               entries, unmappable values) abort the conversion with a
     *               400 response listing the offending warnings; lossy
     *               approximations (e.g. the synthesized GAEB header) remain
     *               tolerated
     */
    @PostMapping(value = "/reverse/gaeb-boq", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> reverseGaebBoq(@RequestParam("file") MultipartFile file,
                                            @RequestParam(value = "format", defaultValue = "ttl") String format,
                                            @RequestParam(value = "strict", defaultValue = "false") boolean strict)
            throws Exception {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "No file provided",
                    "hint", "Upload an RDF file (Turtle, JSON-LD or plain JSON) as multipart field 'file'"));
        }
        RdfInput.RdfFormat parsed;
        try {
            parsed = RdfInput.RdfFormat.parse(format);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Unsupported format: " + format,
                    "hint", "Use 'ttl', 'jsonld' or 'plainjson'"));
        }
        GaebBoqReverseConversionService.ReverseResult result =
                reverseService.convert(file.getBytes(), parsed, strict);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(result);
    }

    /**
     * Builds the position selection tree (Bauposten-Auswahl) of an uploaded
     * GAEB document: all sections (all levels) and all bill items in document
     * order, each with its GAEB ID, name and full ordinal number (Teil-OZ).
     * The frontend renders this as a checkbox tree; the checked ordinal
     * numbers are passed to {@code /convert/gaeb-boq} as the {@code selection}
     * parameter.
     *
     * @return JSON array of tree entries: {@code {kind: SECTION|ITEM, id,
     *         ordinalNumber, name, itemCount, children: [...]}}
     */
    @PostMapping(value = "/positions/gaeb-boq", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> positionsGaebBoq(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "No file provided",
                    "hint", "Upload a GAEB DA XML 3.3 file as multipart field 'file'"));
        }
        Path tmp = Files.createTempFile("gaeb-positions-", ".xml");
        try {
            file.transferTo(tmp);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(conversionService.selectionTree(tmp.toFile()));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * Parses the comma-separated {@code selection} parameter into a list of
     * full ordinal numbers; blank or empty input yields {@code null}
     * (full conversion).
     */
    private static List<String> parseSelection(String selection) {
        if (selection == null || selection.isBlank()) {
            return null;
        }
        List<String> parts = new java.util.ArrayList<>();
        for (String part : selection.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                parts.add(trimmed);
            }
        }
        return parts.isEmpty() ? null : parts;
    }

    /**
     * Serves the bundled GAEB example file so users can test the converter
     * without having their own file.
     */
    @GetMapping("/sample/gaeb-boq")
    public ResponseEntity<byte[]> sampleGaebBoq() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/examples/gaeb-x80-example.xml")) {
            if (in == null) {
                return ResponseEntity.notFound().build();
            }
            byte[] content = in.readAllBytes();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"gaeb-x80-example.xml\"")
                    .contentType(MediaType.parseMediaType("application/xml"))
                    .body(content);
        }
    }

    /**
     * Validates an uploaded GAEB DA XML 3.3 file against the official XSD
     * schemas without converting.
     */
    @PostMapping(value = "/validate/gaeb-boq", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> validateGaebBoq(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "No file provided",
                    "hint", "Upload a GAEB DA XML 3.3 file as multipart field 'file'"));
        }
        Path tmp = Files.createTempFile("gaeb-validate-", ".xml");
        try {
            file.transferTo(tmp);
            List<GaebXmlValidator.ValidationError> errors =
                    conversionService.validate(tmp.toFile());
            if (errors.isEmpty()) {
                return ResponseEntity.ok(Map.of("valid", true, "errors", List.of()));
            }
            return ResponseEntity.badRequest().body(Map.of(
                    "valid", false,
                    "errorCount", errors.size(),
                    "errors", errors));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static String stripExtension(String name) {
        if (name == null) {
            return "upload";
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /**
     * Maps XSD validation failures to a structured 422 response.
     */
    @ExceptionHandler(GaebXmlValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(GaebXmlValidationException e) {
        List<GaebXmlValidator.ValidationError> errors = e.getErrors();
        return ResponseEntity.unprocessableEntity().body(Map.of(
                "valid", false,
                "error", "GAEB XML is not conformant to the official GAEB XSD schemas",
                "errorCount", errors.size(),
                "errors", errors));
    }

    /**
     * Maps reverse-conversion input errors (e.g. RDF without a
     * {@code BillOfQuantities} entity) and strict-mode aborts to a
     * 400 response.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Conversion aborted",
                "detail", String.valueOf(e.getMessage())));
    }

    /**
     * Maps XSD validation failures of a *generated* GAEB document to a
     * structured 400 response.
     */
    @ExceptionHandler(GaebBoqReverseConversionService.ValidationFailure.class)
    public ResponseEntity<Map<String, Object>> handleReverseValidationException(
            GaebBoqReverseConversionService.ValidationFailure e) {
        return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "error", "Generated GAEB XML is not conformant to the official GAEB XSD schemas",
                "errorCount", e.getErrors().size(),
                "errors", e.getErrors()));
    }

    /**
     * Maps unexpected conversion errors to a 500 response.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception e) {
        return ResponseEntity.internalServerError().body(Map.of(
                "error", "Conversion failed",
                "detail", String.valueOf(e.getMessage())));
    }
}
