package org.constructx.converter;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GaebXmlValidatorTest {

    private final File example = Paths.get("src", "test", "resources", "gaeb-x80-example.xml").toFile();

    @Test
    void detectsPhaseFromDocument() {
        // X80 example declares <DP>80</DP>
        assertEquals(80, GaebXmlValidator.detectPhase(
                parse(example)));
    }

    @Test
    void exampleIsValidAgainstXsd() {
        GaebXmlValidator validator = new GaebXmlValidator();
        List<GaebXmlValidator.ValidationError> errors = validator.validate(example);
        assertTrue(errors.isEmpty(), "Expected no XSD errors but got: " + errors);
    }

    @Test
    void invalidDocumentIsRejected() {
        // A document with a wrong root element must fail.
        File bad = new File("target", "gaeb-invalid.xml");
        bad.getParentFile().mkdirs();
        try (var w = new java.io.FileWriter(bad, java.nio.charset.StandardCharsets.UTF_8)) {
            w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<GAEB xmlns=\"http://www.gaeb.de/GAEB_DA_XML/DA80/3.3\">\n"
                    + "  <NotARealElement>oops</NotARealElement>\n"
                    + "</GAEB>\n");
        } catch (Exception e) {
            fail(e);
        }
        GaebXmlValidator validator = new GaebXmlValidator();
        List<GaebXmlValidator.ValidationError> errors = validator.validate(bad);
        assertFalse(errors.isEmpty(), "Expected XSD errors for invalid document");
    }

    private org.w3c.dom.Document parse(File f) {
        try {
            javax.xml.parsers.DocumentBuilderFactory b = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            b.setNamespaceAware(true);
            return b.newDocumentBuilder().parse(f);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
