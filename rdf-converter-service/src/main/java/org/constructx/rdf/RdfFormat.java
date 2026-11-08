package org.constructx.rdf;

import org.apache.jena.riot.Lang;

import java.util.Locale;

/**
 * RDF formats supported by the conversion endpoint.
 *
 * <p>{@code ttl} and {@code jsonld} are full RDF serializations;
 * {@code plainjson} is a plain JSON node list without RDF/JSON-LD
 * semantics (see {@link PlainJsonModelWriter}). Plain JSON is an
 * output-only target: it cannot be used as a source format.
 */
public enum RdfFormat {
    TURTLE("ttl", "text/turtle", ".ttl", Lang.TURTLE),
    JSON_LD("jsonld", "application/ld+json", ".jsonld", Lang.JSONLD),
    PLAIN_JSON("plainjson", "application/json", ".json", null);

    private final String id;
    private final String mediaType;
    private final String extension;
    private final Lang lang;

    RdfFormat(String id, String mediaType, String extension, Lang lang) {
        this.id = id;
        this.mediaType = mediaType;
        this.extension = extension;
        this.lang = lang;
    }

    public String id() {
        return id;
    }

    public String mediaType() {
        return mediaType;
    }

    public String extension() {
        return extension;
    }

    public Lang lang() {
        return lang;
    }

    public static RdfFormat parse(String value) {
        if (value == null) {
            throw new RdfInputException("Format fehlt. Unterstützt werden Turtle, JSON-LD und plain JSON.");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[\\s/_+-]", "");
        return switch (normalized) {
            case "ttl", "turtle", "textturtle" -> TURTLE;
            case "json", "jsonld", "applicationldjson" -> JSON_LD;
            case "plainjson", "plain", "applicationjson" -> PLAIN_JSON;
            default -> throw new RdfInputException(
                    "Nicht unterstütztes RDF-Format: " + value
                            + ". Unterstützt werden Turtle, JSON-LD und plain JSON (Ausgabe).");
        };
    }
}
