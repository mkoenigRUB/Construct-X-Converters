package org.constructx.converter;

import java.util.List;

/**
 * Thrown when a GAEB XML document does not conform to the official
 * GAEB DA XML 3.3 XSD schema.
 */
public class GaebXmlValidationException extends RuntimeException {

    private final List<GaebXmlValidator.ValidationError> errors;

    public GaebXmlValidationException(List<GaebXmlValidator.ValidationError> errors) {
        super("GAEB XML validation failed with " + errors.size() + " error(s): "
                + String.join("; ", errors.stream().map(Object::toString).toList()));
        this.errors = List.copyOf(errors);
    }

    public List<GaebXmlValidator.ValidationError> getErrors() {
        return errors;
    }
}
