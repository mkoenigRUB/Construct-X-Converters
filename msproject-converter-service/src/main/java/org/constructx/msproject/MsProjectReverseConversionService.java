package org.constructx.msproject;

import org.constructx.sdk.RdfInput;
import org.apache.jena.rdf.model.Model;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates the reverse MS Project conversion pipeline: RDF input
 * (Turtle, JSON-LD or plain JSON) is converted back into an MS Project
 * XML document.
 *
 * <p>By default the conversion runs to completion (pragmatic mode) and every
 * lossy step is reported as a warning in the result instead of aborting. With
 * {@code strict=true}, warnings that lose data abort the conversion
 * (lossy approximations remain tolerated).
 */
@Service
public class MsProjectReverseConversionService {

    /** Result of a successful reverse conversion. */
    public record ReverseResult(String xml, List<String> warnings) {
    }

    /**
     * Converts the given RDF data to an MS Project XML document.
     *
     * @param data   RDF file content (Turtle, JSON-LD or plain JSON)
     * @param format the serialization of {@code data}
     * @return the generated XML plus warnings for lossy conversion steps
     * @throws IllegalArgumentException if the RDF contains no {@code ConstructionSchedule}
     *                                  entity or the schedule has no activities
     * @throws Exception                if the RDF input cannot be parsed
     */
    public ReverseResult convert(byte[] data, RdfInput.RdfFormat format) throws Exception {
        return convert(data, format, false);
    }

    /**
     * Converts the given RDF data to an MS Project XML document.
     *
     * @param data   RDF file content (TTL, JSON-LD or plain JSON)
     * @param format the serialization of {@code data}
     * @param strict strict mode: lossy steps that drop data abort the
     *               conversion (lossy approximations remain tolerated)
     * @return the generated XML plus warnings for lossy conversion steps
     * @throws IllegalArgumentException if the RDF contains no {@code ConstructionSchedule}
     *                                  entity, the schedule has no activities, or — in
     *                                  strict mode — data-loss warnings occurred
     * @throws Exception                if the RDF input cannot be parsed
     */
    public ReverseResult convert(byte[] data, RdfInput.RdfFormat format, boolean strict) throws Exception {
        Model model = RdfInput.read(data, format);
        MsProjectReverseConverter.Result result = MsProjectReverseConverter.convert(model, strict);
        return new ReverseResult(result.xml(), result.warnings());
    }
}
