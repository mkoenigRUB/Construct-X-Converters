package org.constructx.msproject;

import java.util.Locale;

/**
 * Output formats of the MS Project conversion endpoint.
 *
 * <p>{@code ttl} and {@code json} (JSON-LD) carry full RDF semantics;
 * {@code plainjson} is a plain JSON node list without any RDF/JSON-LD
 * semantics (see {@link PlainJsonModelWriter}).
 */
public enum OutputFormat {
    TURTLE("ttl"),
    JSON_LD("jsonld"),
    PLAIN_JSON("json");

    private final String extension;

    OutputFormat(String extension) {
        this.extension = extension;
    }

    public String extension() {
        return extension;
    }

    public String mediaType() {
        return switch (this) {
            case TURTLE -> "text/turtle";
            case JSON_LD -> "application/ld+json";
            case PLAIN_JSON -> "application/json";
        };
    }

    public static OutputFormat parse(String value) {
        String normalized = value == null ? "" : value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s/_+-]", "");
        return switch (normalized) {
            case "ttl", "turtle", "textturtle" -> TURTLE;
            case "json", "jsonld", "applicationldjson" -> JSON_LD;
            case "plainjson", "plain", "applicationjson" -> PLAIN_JSON;
            default -> throw new MsProjectInputException(
                    "Format muss 'ttl', 'json' (JSON-LD) oder 'plainjson' sein.");
        };
    }
}
