package org.constructx.converter.web;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.constructx.converter.GaebBoqConverter;
import org.constructx.converter.GaebXmlValidator;
import org.constructx.converter.GaebXmlValidationException;
import org.constructx.sdk.PlainJsonModelWriter;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

/**
 * Orchestrates the GAEB BoQ conversion pipeline: XSD validation followed by
 * conversion to Construct-X ontology TTL.
 */
@Service
public class GaebBoqConversionService {

    /** Result of a successful conversion. */
    public record ConversionResult(String ttl, String jsonLd, String plainJson, String plainJsonEnvelope,
                                   long tripleCount) {
    }

    /** XSD validation failure with all reported problems. */
    public record ValidationFailure(List<GaebXmlValidator.ValidationError> errors) {
    }

    private final GaebXmlValidator validator = new GaebXmlValidator();
    private final GaebBoqConverter converter = new GaebBoqConverter();

    /**
     * Validates and converts the given GAEB XML file.
     *
     * @param xmlFile uploaded GAEB DA XML 3.3 file
     * @return conversion result with TTL serialization
     * @throws GaebXmlValidationException if the document is not XSD-conformant
     * @throws Exception                  if the conversion itself fails
     */
    public ConversionResult convert(File xmlFile) throws Exception {
        return convert(xmlFile, null);
    }

    /**
     * Validates and converts the given GAEB XML file, optionally restricted
     * to a selection of positions (full ordinal numbers of items and/or
     * sections; sections include their whole subtree).
     *
     * @param xmlFile   uploaded GAEB DA XML 3.3 file
     * @param selection selected full ordinal numbers; {@code null} or empty = full conversion
     * @return conversion result with TTL serialization
     * @throws GaebXmlValidationException if the document is not XSD-conformant
     * @throws Exception                  if the conversion itself fails
     */
    public ConversionResult convert(File xmlFile, List<String> selection) throws Exception {
        validator.validateOrThrow(xmlFile);
        var model = converter.convert(xmlFile, selection);
        String ttl = GaebBoqConverter.toTtlString(model);
        String jsonLd = GaebBoqConverter.toJsonLdString(model);
        String plainJson = PlainJsonModelWriter.write(model);
        String plainJsonEnvelope = PlainJsonModelWriter.writeEnvelope(model, rootEnvelopeSpec(model));
        return new ConversionResult(ttl, jsonLd, plainJson, plainJsonEnvelope, model.size());
    }

    /**
     * Builds the position selection tree (all sections and bill items with
     * their full ordinal numbers) for the Bauposten-Auswahl UI.
     *
     * @throws GaebXmlValidationException if the document is not XSD-conformant
     * @throws Exception                  if the document cannot be parsed
     */
    public List<GaebBoqConverter.BoQSelectionEntry> selectionTree(File xmlFile) throws Exception {
        validator.validateOrThrow(xmlFile);
        return converter.selectionTree(xmlFile);
    }

    /** Envelope spec derived from the root BoQ entity, or {@code null}. */
    private PlainJsonModelWriter.EnvelopeSpec rootEnvelopeSpec(Model model) {
        Resource root = converter.getRootBoq();
        if (root == null) {
            return null;
        }
        return new PlainJsonModelWriter.EnvelopeSpec(
                root.getURI(),
                PlainJsonModelWriter.identifierOf(model, root),
                PlainJsonModelWriter.typeLocalName(root),
                GaebBoqConverter.CONFORMS_TO);
    }

    /**
     * Validates only, returning all XSD errors (empty list if conformant).
     */
    public List<GaebXmlValidator.ValidationError> validate(File xmlFile) {
        return validator.validate(xmlFile);
    }
}
