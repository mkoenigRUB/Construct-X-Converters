package org.constructx.rdf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class RdfController {
    private static final Logger LOGGER = LoggerFactory.getLogger(RdfController.class);
    private final RdfService rdfService;

    public RdfController(RdfService rdfService) {
        this.rdfService = rdfService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "construct-x-rdf-service");
    }

    @GetMapping("/profiles")
    public List<RdfService.ProfileSummary> profiles() {
        return rdfService.listProfiles();
    }

    @PostMapping(value = "/convert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> convert(
            @RequestParam("file") MultipartFile file,
            @RequestParam("sourceFormat") String sourceFormat,
            @RequestParam("targetFormat") String targetFormat,
            @RequestParam(value = "envelope", defaultValue = "false") boolean envelope) throws Exception {
        ensureFile(file);
        RdfService.Conversion conversion = rdfService.convert(
                file.getBytes(), RdfFormat.parse(sourceFormat), RdfFormat.parse(targetFormat),
                file.getOriginalFilename(), envelope);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(conversion.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, attachmentFilename(conversion.fileName()))
                .body(conversion.content());
    }

    @PostMapping(value = "/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RdfService.ValidationResult validate(
            @RequestParam("file") MultipartFile file,
            @RequestParam("format") String format,
            @RequestParam("profile") String profile) throws Exception {
        ensureFile(file);
        return rdfService.validate(file.getBytes(), RdfFormat.parse(format), profile);
    }

    private static void ensureFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new RdfInputException("Bitte eine Turtle- oder JSON-LD-Datei hochladen.");
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

    @ExceptionHandler(RdfInputException.class)
    public ResponseEntity<Map<String, String>> handleInputError(RdfInputException exception) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Ungültige Eingabe",
                "message", exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpectedError(Exception exception) {
        LOGGER.error("Unexpected RDF API failure", exception);
        return ResponseEntity.internalServerError().body(Map.of(
                "error", "Interner Dienstfehler",
                "message", "Die Anfrage konnte nicht verarbeitet werden."));
    }
}
