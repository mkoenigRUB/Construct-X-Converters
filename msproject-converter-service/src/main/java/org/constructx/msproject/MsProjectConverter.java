package org.constructx.msproject;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Converts MS Project XML files (2016/2019/2021/2024, schema
 * http://schemas.microsoft.com/project) into a Construct-X
 * construction-schedule ontology model.
 */
public class MsProjectConverter {

    public static final String ONT = "https://www.construct-x.org/ontology/construction-schedule#";
    public static final String SCHEDULE_TYPE_VOCAB = "https://www.construct-x.org/vocab/construction-schedule/schedule-type#";
    public static final String STATUS_VOCAB = "https://www.construct-x.org/vocab/construction-schedule/activity-status#";
    public static final String REL_TYPE_VOCAB = "https://www.construct-x.org/vocab/construction-schedule/precedence-relationship-type#";
    public static final String DEFAULT_IRI_BASE = "https://data.construct-x.org/schedule";
    public static final String DEFAULT_SCHEDULE_TYPE = "RoughNetwork";

    /**
     * Versioned IRIs of the ontology and SHapes the converted model
     * conforms to. Used for the {@code semanticModel.conformsTo} field of
     * the exchange package envelope.
     */
    public static final List<String> CONFORMS_TO = List.of(
            "https://www.construct-x.org/ontology/construction-schedule/0.5.0",
            "https://www.construct-x.org/shapes/construction-schedule/core/0.5.0",
            "https://www.construct-x.org/vocab/construction-schedule/schedule-type/0.1.0",
            "https://www.construct-x.org/shapes/construction-schedule/schedule-type/0.1.0",
            "https://www.construct-x.org/vocab/construction-schedule/activity-status/0.1.2",
            "https://www.construct-x.org/shapes/construction-schedule/activity-status/0.1.0",
            "https://www.construct-x.org/vocab/construction-schedule/precedence-relationship-type/0.1.0",
            "https://www.construct-x.org/shapes/construction-schedule/precedence-relationship-type/0.1.0"
    );

    private static final String MS_PROJECT_NS = "http://schemas.microsoft.com/project";
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final Pattern SAFE_SLUG = Pattern.compile("[^a-z0-9-]+");

    private final String iriBase;
    private final String scheduleType;
    private Resource rootSchedule;

    public MsProjectConverter() {
        this(DEFAULT_IRI_BASE, DEFAULT_SCHEDULE_TYPE);
    }

    public MsProjectConverter(String iriBase, String scheduleType) {
        this.iriBase = (iriBase == null || iriBase.isBlank()) ? DEFAULT_IRI_BASE : iriBase.replaceAll("/+$", "");
        this.scheduleType = (scheduleType == null || scheduleType.isBlank()) ? DEFAULT_SCHEDULE_TYPE : scheduleType;
    }

    public Model convert(byte[] xml) throws Exception {
        Document doc = parseXml(xml);
        Element root = doc.getDocumentElement();
        if (!MS_PROJECT_NS.equals(root.getNamespaceURI())) {
            throw new MsProjectInputException(
                    "Ungültiges MS Project XML: erwartet Wurzelelement im Namespace " + MS_PROJECT_NS);
        }

        String projectName = textOf(root, "Name");
        if (projectName == null || projectName.isBlank()) {
            projectName = "MS Project Plan";
        }
        String slug = slugify(projectName);
        String base = iriBase + "/" + slug;

        Model model = ModelFactory.createDefaultModel();
        model.setNsPrefix("conx-cs", ONT);
        model.setNsPrefix("conx-st", SCHEDULE_TYPE_VOCAB);
        model.setNsPrefix("conx-status", STATUS_VOCAB);
        model.setNsPrefix("conx-prt", REL_TYPE_VOCAB);
        model.setNsPrefix("xsd", "http://www.w3.org/2001/XMLSchema#");

        Resource schedule = model.createResource(base + "/schedule/" + slug);
        rootSchedule = schedule;
        schedule.addProperty(RDF.type,
                model.createResource(ONT + "ConstructionSchedule"));
        schedule.addProperty(prop("identifier"), slug);
        schedule.addProperty(prop("name"), projectName);
        schedule.addProperty(prop("scheduleType"), model.createResource(SCHEDULE_TYPE_VOCAB + scheduleType));

        Element tasksElement = childElement(root, "Tasks");
        if (tasksElement == null) {
            throw new MsProjectInputException("Kein <Tasks>-Element im MS Project XML gefunden.");
        }

        List<Resource> activities = new ArrayList<>();
        List<String[]> relationships = new ArrayList<>(); // [predUid, succUid, type, lagMinutes]

        for (Element task : childElements(tasksElement, "Task")) {
            String uid = textOf(task, "UID");
            if (uid == null || uid.isBlank() || "0".equals(uid)) {
                continue; // skip invalid and project summary task
            }
            boolean isMilestone = "1".equals(textOf(task, "Milestone"));
            boolean isSummary = "1".equals(textOf(task, "IsSummary"));
            String duration = textOf(task, "Duration");
            if (!isMilestone && !isSummary && duration != null && duration.startsWith("0") && duration.contains(":")) {
                isMilestone = true; // zero-duration task -> milestone
            }

            Resource activity = model.createResource(base + "/activity/T" + uid);
            activity.addProperty(RDF.type,
                    model.createResource(ONT + (isMilestone ? "Milestone" : "Activity")));
            activity.addProperty(prop("identifier"), "T" + uid);
            String outlineCode = textOf(task, "OutlineCode");
            if (outlineCode != null && !outlineCode.isBlank()) {
                activity.addProperty(prop("hierarchyCode"), outlineCode);
            }
            String name = textOf(task, "Name");
            if (name != null && !name.isBlank()) {
                activity.addProperty(prop("name"), name);
            }
            String description = textOf(task, "Description");
            if (description != null && !description.isBlank()) {
                activity.addProperty(prop("description"), description);
            }
            activity.addProperty(prop("hasStatus"), model.createResource(STATUS_VOCAB + statusFor(task)));

            String start = textOf(task, "Start");
            String finish = textOf(task, "Finish");
            if (start != null && !start.isBlank()) {
                activity.addProperty(prop("plannedStart"), dateTimeLiteral(model, start));
            }
            if (finish != null && !finish.isBlank()) {
                activity.addProperty(prop("plannedFinish"), dateTimeLiteral(model, finish));
            }

            activities.add(activity);

            for (Element link : childElements(task, "PredecessorLink")) {
                String predUid = textOf(link, "PredecessorId");
                if (predUid == null || predUid.isBlank()) {
                    continue;
                }
                String type = textOf(link, "Type");
                String lag = textOf(link, "Lag");
                relationships.add(new String[]{predUid, uid,
                        type == null ? "0" : type,
                        lag == null ? "0" : lag});
            }
        }

        if (activities.isEmpty()) {
            throw new MsProjectInputException("Keine Aufgaben (Tasks) im MS Project XML gefunden.");
        }

        for (Resource activity : activities) {
            schedule.addProperty(prop("hasActivity"), activity);
        }

        for (String[] rel : relationships) {
            Resource predecessor = model.getResource(base + "/activity/T" + rel[0]);
            Resource successor = model.getResource(base + "/activity/T" + rel[1]);
            Resource relationship = model.createResource(base + "/relationship/REL-" + rel[0] + "-" + rel[1]);
            relationship.addProperty(RDF.type,
                    model.createResource(ONT + "PrecedenceRelationship"));
            relationship.addProperty(prop("identifier"), "REL-" + rel[0] + "-" + rel[1]);
            relationship.addProperty(prop("hasPredecessor"), predecessor);
            relationship.addProperty(prop("hasSuccessor"), successor);
            relationship.addProperty(prop("relationshipType"),
                    model.createResource(REL_TYPE_VOCAB + relationshipTypeFor(rel[2])));
            relationship.addProperty(prop("lag"),
                    model.createTypedLiteral("PT" + rel[3] + "M", XSDDatatype.XSDduration));
        }

        return model;
    }

    /**
     * The root {@code ConstructionSchedule} entity of the last conversion,
     * or {@code null} when no conversion was performed. Used to derive the
     * exchange package envelope ({@code id}, {@code identifier},
     * {@code semanticModel.type}).
     */
    public Resource getRootSchedule() {
        return rootSchedule;
    }

    public String toTtl(Model model) {
        StringWriter writer = new StringWriter();
        RDFDataMgr.write(writer, model, Lang.TURTLE);
        return writer.toString();
    }

    public String toJsonLd(Model model) {
        StringWriter writer = new StringWriter();
        RDFDataMgr.write(writer, model, Lang.JSONLD);
        return writer.toString();
    }

    private static Document parseXml(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setAttribute(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new ByteArrayInputStream(xml)));
    }

    private static Property prop(String localName) {
        return ResourceFactory.createProperty(ONT + localName);
    }

    private static String statusFor(Element task) {
        String percent = textOf(task, "PercentComplete");
        if (percent == null || percent.isBlank()) {
            return "Planned";
        }
        try {
            double value = Double.parseDouble(percent.replace(',', '.'));
            if (value >= 100) {
                return "Completed";
            }
            if (value > 0) {
                return "InProgress";
            }
        } catch (NumberFormatException ignored) {
            // fall through to Planned
        }
        return "Planned";
    }

    private static String relationshipTypeFor(String msType) {
        return switch (msType) {
            case "1" -> "StartToStart";
            case "2" -> "FinishToFinish";
            case "3" -> "StartToFinish";
            default -> "FinishToStart";
        };
    }

    private static Literal dateTimeLiteral(Model model, String value) {
        String trimmed = value.trim();
        try {
            if (trimmed.length() <= 10) {
                LocalDate date = LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE);
                return model.createTypedLiteral(date.atStartOfDay().format(DATETIME_FORMAT), XSDDatatype.XSDdateTime);
            }
            LocalDateTime dateTime = LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return model.createTypedLiteral(dateTime.format(DATETIME_FORMAT), XSDDatatype.XSDdateTime);
        } catch (DateTimeParseException e) {
            throw new MsProjectInputException("Ungültiges Datumsformat in MS Project XML: " + value);
        }
    }

    private static String textOf(Element parent, String childName) {
        Element child = childElement(parent, childName);
        return child == null ? null : child.getTextContent();
    }

    private static Element childElement(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element && name.equals(element.getLocalName())) {
                return element;
            }
        }
        return null;
    }

    private static List<Element> childElements(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element && name.equals(element.getLocalName())) {
                result.add(element);
            }
        }
        return result;
    }

    private static String slugify(String name) {
        String slug = SAFE_SLUG.matcher(name.toLowerCase(Locale.ROOT)).replaceAll("-").replaceAll("^-+|-+$", "");
        return slug.isEmpty() ? "project" : slug;
    }
}
