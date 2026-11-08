package org.constructx.msproject;

import org.constructx.sdk.RdfInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class MsProjectController {
    private static final Logger LOGGER = LoggerFactory.getLogger(MsProjectController.class);
    private final MsProjectService service;
    private final MsProjectReverseConversionService reverseService;

    public MsProjectController(MsProjectService service,
                               MsProjectReverseConversionService reverseService) {
        this.service = service;
        this.reverseService = reverseService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "construct-x-msproject-service");
    }

    @GetMapping("/converters")
    public List<Map<String, Object>> converters() {
        return List.of(Map.of(
                "id", "msproject",
                "name", "MS Project XML → TTL / JSON-LD / JSON",
                "description", "Konvertiert MS Project XML-Dateien (2016/2019/2021/2024) in die Construct-X construction-schedule Ontologie",
                "inputFormats", List.of("xml"),
                "outputFormats", List.of("ttl", "jsonld", "plainjson"),
                "ontology", "construction-schedule"));
    }

    @GetMapping(value = "/examples/msproject.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<Resource> example() {
        Resource resource = new ClassPathResource("examples/msproject-example.xml");
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .header(HttpHeaders.CONTENT_DISPOSITION, attachmentFilename("msproject-example.xml"))
                .body(resource);
    }

    @PostMapping(value = "/convert/msproject", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> convert(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "format", defaultValue = "ttl") String format,
            @RequestParam(value = "scheduleType", defaultValue = "RoughNetwork") String scheduleType,
            @RequestParam(value = "iriBase", required = false) String iriBase,
            @RequestParam(value = "envelope", defaultValue = "false") boolean envelope) throws Exception {
        ensureFile(file);
        OutputFormat.parse(format); // throws MsProjectInputException for unsupported formats
        MsProjectService.ConversionResult result = service.convert(file.getBytes(), format, scheduleType, iriBase, envelope);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(result.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, attachmentFilename(result.fileName()))
                .body(result.content());
    }

    @PostMapping(value = "/validate/msproject", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MsProjectService.ValidationResult validate(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "scheduleType", defaultValue = "RoughNetwork") String scheduleType,
            @RequestParam(value = "iriBase", required = false) String iriBase) throws Exception {
        ensureFile(file);
        return service.validate(file.getBytes(), scheduleType, iriBase);
    }

    /**
     * Reverse conversion: RDF (Turtle, JSON-LD or plain JSON) back into an
     * MS Project XML document.
     *
     * <p>The response body is a JSON object {@code {xml, warnings[]}};
     * lossy conversion steps (e.g. an {@code InProgress} status) are
     * reported in {@code warnings} instead of aborting. With
     * {@code strict=true}, lossy steps that drop data abort the conversion
     * with a 400 response (lossy approximations remain tolerated).
     */
    @PostMapping(value = "/reverse/msproject", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> reverseMsProject(@RequestParam("file") MultipartFile file,
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
        MsProjectReverseConversionService.ReverseResult result =
                reverseService.convert(file.getBytes(), parsed, strict);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(result);
    }

    private static void ensureFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new MsProjectInputException("Bitte eine MS Project XML-Datei hochladen.");
        }
    }

    /**
     * Builds a valid RFC 6266 {@code Content-Disposition} header value.
     *
     * <p>Spring's {@code ContentDisposition.filename(name, charset)} emits an
     * RFC 2047 encoded-word ({@code =?UTF-8?Q?...?=?}), which is invalid in
     * HTTP headers; browsers then reject or mangle the download name. For
     * ASCII names a plain quoted {@code filename} is used; for non-ASCII
     * names an ASCII {@code filename} fallback plus an RFC 5987
     * {@code filename*=UTF-8''...} parameter is emitted.</p>
     */
    static String attachmentFilename(String fileName) {
        String name = fileName == null ? "download" : fileName.replace('"', '_').replace('\\', '_');
        if (name.isEmpty()) {
            name = "download";
        }
        if (name.chars().allMatch(c -> c < 128)) {
            return "attachment; filename=\"" + name + "\"";
        }
        String ascii = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (ascii.matches("^[._]+$")) {
            ascii = "download";
        }
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + rfc5987(name);
    }

    /** Percent-encodes a string for an RFC 5987 {@code ext-value}. */
    private static String rfc5987(String value) {
        StringBuilder sb = new StringBuilder(value.length() * 2);
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~') {
                sb.append((char) c);
            } else {
                sb.append('%').append(String.format("%02X", c));
            }
        }
        return sb.toString();
    }

    @ExceptionHandler(MsProjectInputException.class)
    public ResponseEntity<Map<String, String>> handleInputError(MsProjectInputException exception) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Ungültige Eingabe",
                "message", exception.getMessage()));
    }

    /**
     * Maps reverse-conversion input errors (e.g. RDF without a
     * {@code ConstructionSchedule} entity) and strict-mode aborts to a
     * 400 response.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Conversion aborted",
                "detail", e.getMessage() == null ? "" : e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpectedError(Exception exception) {
        LOGGER.error("Unexpected MS Project API failure", exception);
        return ResponseEntity.internalServerError().body(Map.of(
                "error", "Interner Dienstfehler",
                "message", "Die Anfrage konnte nicht verarbeitet werden."));
    }
}
