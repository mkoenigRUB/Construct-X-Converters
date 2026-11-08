package org.constructx.converter.web;

import org.apache.jena.rdf.model.Model;
import org.constructx.converter.GaebBoqReverseConverter;
import org.constructx.converter.GaebXmlValidator;
import org.constructx.sdk.RdfInput;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Orchestrates the reverse GAEB BoQ conversion pipeline: RDF input
 * (Turtle, JSON-LD or plain JSON) is converted to a GAEB DA XML 3.3
 * document which is then validated against the official GAEB XSD schemas.
 *
 * <p>Conversion always runs (pragmatic mode); every lossy step is reported
 * as a warning in the result instead of aborting.
 */
@Service
public class GaebBoqReverseConversionService {

    /** Result of a successful reverse conversion. */
    public record ReverseResult(String xml, List<String> warnings) {
    }

    /** Generated XML failed the GAEB XSD validation. */
    public static final class ValidationFailure extends RuntimeException {

        private final List<GaebXmlValidator.ValidationError> errors;

        public ValidationFailure(List<GaebXmlValidator.ValidationError> errors) {
            super("Generated GAEB XML is not conformant to the official GAEB XSD schemas");
            this.errors = List.copyOf(errors);
        }

        public List<GaebXmlValidator.ValidationError> getErrors() {
            return errors;
        }
    }

    private final GaebXmlValidator validator = new GaebXmlValidator();

    /**
     * Converts the given RDF data to a GAEB DA XML 3.3 document.
     *
     * @param data   RDF file content (TTL, JSON-LD or plain JSON)
     * @param format the serialization of {@code data}
     * @return the generated XML plus warnings for lossy conversion steps
     * @throws IllegalArgumentException if the RDF contains no {@code BillOfQuantities}
     * @throws ValidationFailure        if the generated XML is not XSD-conformant
     * @throws Exception                if the RDF input cannot be parsed
     */
    public ReverseResult convert(byte[] data, RdfInput.RdfFormat format) throws Exception {
        return convert(data, format, false);
    }

    /**
     * Converts the given RDF data to a GAEB DA XML 3.3 document.
     *
     * @param data   RDF file content (TTL, JSON-LD or plain JSON)
     * @param format the serialization of {@code data}
     * @param strict strict mode: lossy steps that drop data abort the
     *               conversion (lossy approximations remain tolerated)
     * @return the generated XML plus warnings for lossy conversion steps
     * @throws IllegalArgumentException if the RDF contains no {@code BillOfQuantities},
     *                                  or — in strict mode — on data-loss warnings
     * @throws ValidationFailure        if the generated XML is not XSD-conformant
     * @throws Exception                if the RDF input cannot be parsed
     */
    public ReverseResult convert(byte[] data, RdfInput.RdfFormat format, boolean strict) throws Exception {
        Model model = RdfInput.read(data, format);
        GaebBoqReverseConverter.Result result =
                GaebBoqReverseConverter.convert(model, null, strict);
        Path tmp = Files.createTempFile("gaeb-reverse-", ".xml");
        try {
            Files.write(tmp, result.xml().getBytes(StandardCharsets.UTF_8));
            List<GaebXmlValidator.ValidationError> errors = validator.validate(tmp.toFile());
            if (!errors.isEmpty()) {
                throw new ValidationFailure(errors);
            }
            return new ReverseResult(result.xml(), result.warnings());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
