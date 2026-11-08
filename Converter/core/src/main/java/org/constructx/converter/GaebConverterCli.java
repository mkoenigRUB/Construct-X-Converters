package org.constructx.converter;

import org.constructx.sdk.IriFactory;
import org.apache.jena.rdf.model.Model;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Command-line entry point for the GAEB BoQ converter.
 *
 * <pre>
 * mvn exec:java -Dexec.args="input.xml output.ttl [baseIri] [currency] [selection]"
 * </pre>
 *
 * <p>{@code selection} is an optional comma-separated list of full ordinal
 * numbers (Teil-OZ) of items and/or sections to convert (a selected section
 * includes its whole subtree). Omitted or empty = full conversion.
 */
public final class GaebConverterCli {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: GaebConverterCli <input.xml> <output.ttl> [baseIri] [currency] [selection]");
            System.exit(1);
        }

        File input = new File(args[0]);
        Path output = Paths.get(args[1]);
        String baseIri = args.length > 2 ? args[2] : IriFactory.DEFAULT_BASE_IRI;
        String currency = args.length > 3 ? args[3] : "EUR";
        List<String> selection = parseSelection(args.length > 4 ? args[4] : null);

        if (!input.isFile()) {
            System.err.println("Input file not found: " + input.getAbsolutePath());
            System.exit(1);
        }

        // Validate against the official GAEB DA XML 3.3 XSD before converting.
        new GaebXmlValidator().validateOrThrow(input);
        System.out.println("XSD validation passed (" + input.getName() + ")");

        GaebBoqConverter converter = new GaebBoqConverter(new IriFactory(baseIri), currency);
        Model model = converter.convert(input, selection);
        GaebBoqConverter.writeTtl(model, output);

        System.out.println("Converted " + input.getName() + " -> " + output);
        System.out.println("Statements: " + model.size());
    }

    /** Comma-separated ordinal numbers; blank input yields {@code null}. */
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

    private GaebConverterCli() {
    }
}
