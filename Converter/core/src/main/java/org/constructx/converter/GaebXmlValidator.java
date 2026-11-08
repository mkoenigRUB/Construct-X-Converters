package org.constructx.converter;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Validates GAEB DA XML 3.3 files against the official GAEB XSD schemas
 * (release 2021-05, data-exchange phases X80-X87).
 *
 * <p>The applicable schema is selected automatically from the data-exchange
 * phase declared in the document ({@code <Award><DP>}) or, as a fallback,
 * from the root element's namespace. The XSDs are bundled in the classpath
 * under {@code /gaeb-xsd/}.</p>
 */
public class GaebXmlValidator {

    /** A single XSD validation problem. */
    public record ValidationError(int line, int column, String message) {
        @Override
        public String toString() {
            return String.format(Locale.ROOT, "line %d, column %d: %s", line, column, message);
        }
    }

    private static final String[] ALL_XSDS = {
            "GAEB_DA_XML_80_3.3_2021-05.xsd", "GAEB_DA_XML_81_3.3_2021-05.xsd",
            "GAEB_DA_XML_82_3.3_2021-05.xsd", "GAEB_DA_XML_83_3.3_2021-05.xsd",
            "GAEB_DA_XML_84_3.3_2021-05.xsd", "GAEB_DA_XML_85_3.3_2021-05.xsd",
            "GAEB_DA_XML_86_3.3_2021-05.xsd", "GAEB_DA_XML_87_3.3_2021-05.xsd",
            "GAEB_DA_XML_Lib_3.3_2021-05.xsd"
    };

    /**
     * The GAEB phase schemas reference the shared Lib schema via a relative
     * {@code schemaLocation}, which only resolves against a real file system
     * base URI. We therefore extract the bundled XSDs to a temporary directory
     * once and load schemas from there.
     */
    private static volatile File xsdDir;

    private static File xsdDirectory() throws Exception {
        File dir = xsdDir;
        if (dir != null) {
            return dir;
        }
        synchronized (GaebXmlValidator.class) {
            if (xsdDir != null) {
                return xsdDir;
            }
            File tmp = File.createTempFile("gaeb-xsd", "");
            if (!tmp.delete()) {
                throw new IllegalStateException("Could not remove temp file: " + tmp);
            }
            if (!tmp.mkdirs()) {
                throw new IllegalStateException("Could not create temp dir: " + tmp);
            }
            tmp.deleteOnExit();
            for (String name : ALL_XSDS) {
                try (InputStream in = GaebXmlValidator.class.getResourceAsStream("/gaeb-xsd/" + name)) {
                    if (in == null) {
                        throw new IllegalStateException("XSD not found on classpath: " + name);
                    }
                    java.nio.file.Files.copy(in, tmp.toPath().toAbsolutePath().resolve(name),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            xsdDir = tmp;
            return tmp;
        }
    }

    /**
     * Validates the given GAEB XML file.
     *
     * @param xmlFile GAEB DA XML 3.3 file (X80-X87)
     * @return list of validation errors; empty if the document is conformant
     */
    public List<ValidationError> validate(File xmlFile) {
        try {
            Document doc = parseXml(xmlFile);
            int phase = detectPhase(doc);
            Schema schema = loadSchema(phase);
            return validate(doc, schema);
        } catch (Exception e) {
            return List.of(new ValidationError(-1, -1,
                    "Could not validate document: " + e.getMessage()));
        }
    }

    /**
     * Validates the given GAEB XML file and throws on the first error.
     *
     * @param xmlFile GAEB DA XML 3.3 file (X80-X87)
     * @throws GaebXmlValidationException if the document is not conformant
     */
    public void validateOrThrow(File xmlFile) {
        List<ValidationError> errors = validate(xmlFile);
        if (!errors.isEmpty()) {
            throw new GaebXmlValidationException(errors);
        }
    }

    /**
     * Detects the data-exchange phase (80-87) from the document.
     */
    public static int detectPhase(Document doc) {
        Element root = doc.getDocumentElement();
        // Preferred: <Award><DP>80</DP>
        for (Element award : findAll(root, "Award")) {
            for (Element dp : findAll(award, "DP")) {
                String text = dp.getTextContent().trim();
                if (!text.isEmpty()) {
                    try {
                        int phase = Integer.parseInt(text);
                        if (phase >= 80 && phase <= 87) {
                            return phase;
                        }
                    } catch (NumberFormatException ignored) {
                        // fall through to namespace detection
                    }
                }
            }
        }
        // Fallback: root namespace, e.g. http://www.gaeb.de/GAEB_DA_XML/DA80/3.3
        String ns = root.getNamespaceURI();
        if (ns != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("DA(\\d{2})/").matcher(ns);
            if (m.find()) {
                int phase = Integer.parseInt(m.group(1));
                if (phase >= 80 && phase <= 87) {
                    return phase;
                }
            }
        }
        // Default: X80 (Leistungsverzeichnis)
        return 80;
    }

    private Schema loadSchema(int phase) throws Exception {
        File schemaFile = new File(xsdDirectory(),
                String.format("GAEB_DA_XML_%d_3.3_2021-05.xsd", phase));
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        return factory.newSchema(schemaFile);
    }

    private List<ValidationError> validate(Document doc, Schema schema) throws Exception {
        Validator validator = schema.newValidator();
        List<ValidationError> errors = new ArrayList<>();
        validator.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException e) {
                errors.add(new ValidationError(e.getLineNumber(), e.getColumnNumber(), e.getMessage()));
            }

            @Override
            public void error(SAXParseException e) {
                errors.add(new ValidationError(e.getLineNumber(), e.getColumnNumber(), e.getMessage()));
            }

            @Override
            public void fatalError(SAXParseException e) throws org.xml.sax.SAXException {
                errors.add(new ValidationError(e.getLineNumber(), e.getColumnNumber(), e.getMessage()));
                throw new org.xml.sax.SAXException(e);
            }
        });
        try {
            validator.validate(new javax.xml.transform.sax.SAXSource(toInputSource(doc)));
        } catch (org.xml.sax.SAXException e) {
            // fatal error already recorded in the handler
        }
        return errors;
    }

    private static InputSource toInputSource(Document doc) throws Exception {
        javax.xml.transform.Transformer transformer =
                javax.xml.transform.TransformerFactory.newInstance().newTransformer();
        java.io.StringWriter writer = new java.io.StringWriter();
        transformer.transform(new javax.xml.transform.dom.DOMSource(doc),
                new javax.xml.transform.stream.StreamResult(writer));
        return new InputSource(new java.io.StringReader(writer.toString()));
    }

    private static Document parseXml(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        try (InputStream in = new java.io.FileInputStream(file)) {
            return builder.parse(in);
        }
    }

    private static List<Element> findAll(Element parent, String localName) {
        List<Element> result = new ArrayList<>();
        org.w3c.dom.NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node node = children.item(i);
            if (node instanceof Element el) {
                if (localName.equals(el.getLocalName())) {
                    result.add(el);
                }
                result.addAll(findAll(el, localName));
            }
        }
        return result;
    }
}
