package org.constructx.msproject;

import org.constructx.sdk.ConxCurieRegister;
import org.constructx.sdk.ConxStrict;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts a Construct-X construction-schedule ontology model back into
 * an MS Project XML document (2016/2019/2021/2024, schema
 * {@code http://schemas.microsoft.com/project}).
 *
 * <p>Pragmatic mode: the conversion always runs; lossy steps (for example
 * an {@code InProgress} status, which cannot be expressed as a single
 * {@code PercentComplete} value) are reported as warnings instead of
 * aborting the conversion.
 */
public class MsProjectReverseConverter {

    public static final String MS_PROJECT_NS = "http://schemas.microsoft.com/project";

    /** Result of a successful reverse conversion. */
    public record Result(String xml, List<String> warnings) {
    }

    private static final DateTimeFormatter DATETIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    /** Matches the numeric task UID inside identifiers and IRIs such as {@code T12}; the lookbehind keeps {@code PT40H} from matching. */
    private static final Pattern UID_PATTERN = Pattern.compile("(?<![A-Za-z0-9])T(\\d+)");
    /** Matches the forward converter's lag serialization {@code PT<minutes>M}. */
    private static final Pattern LAG_PATTERN = Pattern.compile("PT(\\d+)M");
    private static final String FALLBACK_PROJECT_NAME = "MS Project Plan";

    private MsProjectReverseConverter() {
    }

    /**
     * Converts the given model into an MS Project XML document.
     *
     * @param model the Construct-X construction-schedule model
     * @return the generated XML plus warnings for lossy conversion steps
     * @throws IllegalArgumentException if the model contains no {@code ConstructionSchedule}
     *                                  entity or the schedule has no activities
     */
    public static Result convert(Model model) {
        return convert(model, false);
    }

    /**
     * Converts the given model into an MS Project XML document.
     *
     * @param model  the Construct-X construction-schedule model
     * @param strict strict mode: warnings that lose data (skipped relationships,
     *               unknown statuses, placeholder names) abort the conversion
     *               with an {@link IllegalArgumentException} listing the
     *               offending warnings. Lossy approximations (an
     *               {@code InProgress} status mapped to 50% complete) remain
     *               tolerated.
     * @return the generated XML plus warnings for lossy conversion steps
     * @throws IllegalArgumentException if the model contains no {@code ConstructionSchedule}
     *                                  entity, the schedule has no activities, or —
     *                                  in strict mode — data-loss warnings occurred
     */
    public static Result convert(Model model, boolean strict) {
        List<String> warnings = new ArrayList<>();

        var scheduleIt = model.listResourcesWithProperty(RDF.type,
                model.createResource(MsProjectConverter.ONT + "ConstructionSchedule"));
        Resource schedule = scheduleIt.hasNext() ? scheduleIt.next() : null;
        if (schedule == null) {
            throw new IllegalArgumentException("No ConstructionSchedule entity found in the RDF model");
        }

        String projectName = firstLiteral(schedule, "name");
        if (projectName == null || projectName.isBlank()) {
            projectName = FALLBACK_PROJECT_NAME;
            warnings.add("Schedule without name – using placeholder '" + FALLBACK_PROJECT_NAME + "'.");
        }

        List<Activity> activities = collectActivities(model, schedule, warnings);
        if (activities.isEmpty()) {
            throw new IllegalArgumentException("No activities found in the schedule");
        }

        List<Relationship> relationships = collectRelationships(model, activities, warnings);

        Document doc = newDocument();
        Element project = doc.createElementNS(MS_PROJECT_NS, "Project");
        doc.appendChild(project);
        appendProjectHeader(doc, project, projectName, activities);
        appendTasks(doc, project, projectName, activities, relationships, warnings);

        if (strict) {
            List<String> strictWarnings = ConxStrict.strictWarnings(warnings);
            if (!strictWarnings.isEmpty()) {
                throw new IllegalArgumentException(ConxStrict.failureMessage(warnings));
            }
        }

        return new Result(serialize(doc), List.copyOf(warnings));
    }

    /** One schedule activity with all fields the reverse conversion can use. */
    private record Activity(String iri, int uid, String name, String description,
                            String hierarchyCode, boolean milestone, String status,
                            String start, String finish) {
    }

    /** One precedence relationship, already resolved to MS Project values. */
    private record Relationship(String identifier, String succIri, String predUid,
                                String msType, int lagMinutes) {
    }

    // ------------------------------------------------------------------
    // Model reading
    // ------------------------------------------------------------------

    private static List<Activity> collectActivities(Model model, Resource schedule,
                                                    List<String> warnings) {
        List<String> iris = new ArrayList<>();
        StmtIterator hasActivity = schedule.listProperties(prop("hasActivity"));
        while (hasActivity.hasNext()) {
            String iri = nodeIri(hasActivity.next().getObject());
            if (iri != null) {
                iris.add(iri);
            }
        }
        if (iris.isEmpty()) {
            collectByType(model, iris, "Activity");
            collectByType(model, iris, "Milestone");
        }

        List<Activity> activities = new ArrayList<>();
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (String iri : iris) {
            if (seen.putIfAbsent(iri, Boolean.TRUE) != null) {
                continue;
            }
            Resource resource = model.getResource(iri);
            String identifier = firstLiteral(resource, "identifier");
            Integer uid = extractUid(identifier != null ? identifier : iri);
            if (uid == null) {
                uid = activities.size() + 1;
                warnings.add("Activity '" + iri + "': no T<n> identifier – using ordinal " + uid + ".");
            }
            String name = firstLiteral(resource, "name");
            if (name == null || name.isBlank()) {
                name = identifier != null ? identifier : ConxCurieRegister.localName(iri);
                warnings.add("Activity '" + iri + "': no name – using '" + name + "'.");
            }
            activities.add(new Activity(
                    iri,
                    uid,
                    name,
                    firstLiteral(resource, "description"),
                    firstLiteral(resource, "hierarchyCode"),
                    isMilestoneType(resource),
                    firstLocalPart(resource, "hasStatus"),
                    firstLiteral(resource, "plannedStart"),
                    firstLiteral(resource, "plannedFinish")));
        }
        activities.sort((a, b) -> {
            int byUid = Integer.compare(a.uid(), b.uid());
            return byUid != 0 ? byUid : a.iri().compareTo(b.iri());
        });
        return activities;
    }

    private static void collectByType(Model model, List<String> iris, String typeLocal) {
        for (Resource resource : model.listResourcesWithProperty(RDF.type,
                model.createResource(MsProjectConverter.ONT + typeLocal)).toList()) {
            iris.add(resource.getURI());
        }
    }

    private static List<Relationship> collectRelationships(Model model, List<Activity> activities,
                                                           List<String> warnings) {
        List<Relationship> relationships = new ArrayList<>();
        for (Resource rel : model.listResourcesWithProperty(RDF.type,
                model.createResource(MsProjectConverter.ONT + "PrecedenceRelationship")).toList()) {
            String identifier = firstLiteral(rel, "identifier");
            String label = identifier != null ? identifier : rel.getURI();
            String predIri = firstNodeIri(rel, "hasPredecessor");
            String succIri = firstNodeIri(rel, "hasSuccessor");
            if (predIri == null || succIri == null) {
                warnings.add("Relationship '" + label + "': missing predecessor or successor – skipped.");
                continue;
            }
            Integer predUid = extractUid(predIri);
            if (predUid == null) {
                warnings.add("Relationship '" + label + "': no numeric UID in predecessor '"
                        + predIri + "' – skipped.");
                continue;
            }
            if (activities.stream().noneMatch(a -> a.iri().equals(succIri))) {
                warnings.add("Relationship '" + label + "': unknown successor '" + succIri + "' – skipped.");
                continue;
            }

            String msType;
            String relType = firstLocalPart(rel, "relationshipType");
            if (relType == null) {
                msType = "0";
                warnings.add("Relationship '" + label + "': missing relationship type – using FinishToStart (0).");
            } else {
                switch (relType) {
                    case "StartToStart" -> msType = "1";
                    case "FinishToFinish" -> msType = "2";
                    case "StartToFinish" -> msType = "3";
                    case "FinishToStart" -> msType = "0";
                    default -> {
                        msType = "0";
                        warnings.add("Relationship '" + label + "': unknown relationship type '"
                                + relType + "' – using FinishToStart (0).");
                    }
                }
            }

            int lagMinutes = 0;
            String lagValue = firstLiteral(rel, "lag");
            if (lagValue != null) {
                Matcher lagMatcher = LAG_PATTERN.matcher(lagValue.trim());
                if (lagMatcher.find()) {
                    lagMinutes = Integer.parseInt(lagMatcher.group(1));
                } else {
                    warnings.add("Relationship '" + label + "': unreadable lag '"
                            + lagValue + "' – using 0.");
                }
            }
            relationships.add(new Relationship(label, succIri, String.valueOf(predUid),
                    msType, lagMinutes));
        }
        relationships.sort((a, b) -> a.identifier().compareTo(b.identifier()));
        return relationships;
    }

    // ------------------------------------------------------------------
    // XML writing
    // ------------------------------------------------------------------

    private static void appendProjectHeader(Document doc, Element project, String projectName,
                                            List<Activity> activities) {
        LocalDateTime minStart = null;
        LocalDateTime maxFinish = null;
        for (Activity activity : activities) {
            LocalDateTime start = parseDateTime(activity.start());
            if (start != null && (minStart == null || start.isBefore(minStart))) {
                minStart = start;
            }
            LocalDateTime finish = parseDateTime(activity.finish());
            if (finish != null && (maxFinish == null || finish.isAfter(maxFinish))) {
                maxFinish = finish;
            }
        }
        LocalDateTime now = LocalDateTime.now();
        String projectStart = minStart != null ? minStart.format(DATETIME_FORMAT) : now.format(DATETIME_FORMAT);
        String projectFinish = maxFinish != null ? maxFinish.format(DATETIME_FORMAT) : projectStart;
        long projectHours = minStart != null && maxFinish != null
                ? Math.max(1, Duration.between(minStart, maxFinish).toHours())
                : 1;

        String nowString = now.format(DATETIME_FORMAT);
        text(doc, project, "Name", projectName);
        text(doc, project, "Title", projectName);
        text(doc, project, "Company", "Construct-X");
        text(doc, project, "Created", nowString);
        text(doc, project, "LastSaved", nowString);
        text(doc, project, "ScheduleFromStart", "1");
        text(doc, project, "StartDate", projectStart);
        text(doc, project, "FinishDate", projectFinish);
        text(doc, project, "Duration", String.valueOf(projectHours));
        text(doc, project, "DurationFormat", "7");

        Element workWeek = element(doc, project, "WorkWeek");
        text(doc, workWeek, "DaysPerWeek", "5");
        text(doc, workWeek, "Start", "1");
        for (int day = 1; day <= 5; day++) {
            Element dayElement = element(doc, workWeek, "Day" + day);
            text(doc, dayElement, "DayUnit", "0");
            text(doc, dayElement, "Start", "0");
            text(doc, dayElement, "Finish", "28800");
        }
    }

    private static void appendTasks(Document doc, Element project, String projectName,
                                    List<Activity> activities, List<Relationship> relationships,
                                    List<String> warnings) {
        Map<String, List<Relationship>> linksBySuccessor = new LinkedHashMap<>();
        for (Relationship rel : relationships) {
            linksBySuccessor.computeIfAbsent(rel.succIri(), key -> new ArrayList<>()).add(rel);
        }

        Element tasks = element(doc, project, "Tasks");

        // Project summary task (UID 0); the forward converter skips it, so it
        // does not affect the round trip, but MS Project expects a root row.
        Element summary = element(doc, tasks, "Task");
        text(doc, summary, "UID", "0");
        text(doc, summary, "ID", "1");
        text(doc, summary, "Name", projectName);
        text(doc, summary, "OutlineLevel", "0");
        text(doc, summary, "OutlineCode", "0");
        text(doc, summary, "Type", "1");
        text(doc, summary, "Duration", "PT" + projectDurationHours(activities) + "H");
        text(doc, summary, "Start", minDate(activities, true));
        text(doc, summary, "Finish", minDate(activities, false));
        text(doc, summary, "PercentComplete", "0");
        text(doc, summary, "IsSummary", "1");
        text(doc, summary, "Milestone", "0");

        int id = 2;
        for (Activity activity : activities) {
            Element task = element(doc, tasks, "Task");
            text(doc, task, "UID", String.valueOf(activity.uid()));
            text(doc, task, "ID", String.valueOf(id++));
            text(doc, task, "Name", activity.name());
            text(doc, task, "OutlineLevel", outlineLevel(activity.hierarchyCode()));
            if (activity.hierarchyCode() != null && !activity.hierarchyCode().isBlank()) {
                text(doc, task, "OutlineCode", activity.hierarchyCode());
            }
            text(doc, task, "Type", "1");
            text(doc, task, "Duration", taskDuration(activity.start(), activity.finish()));
            if (activity.start() != null && !activity.start().isBlank()) {
                text(doc, task, "Start", activity.start().trim());
            }
            if (activity.finish() != null && !activity.finish().isBlank()) {
                text(doc, task, "Finish", activity.finish().trim());
            }
            text(doc, task, "PercentComplete", String.valueOf(percentComplete(activity, warnings)));
            text(doc, task, "IsSummary", "0");
            text(doc, task, "Milestone", activity.milestone() ? "1" : "0");
            if (activity.description() != null && !activity.description().isBlank()) {
                text(doc, task, "Description", activity.description());
            }
            for (Relationship rel : linksBySuccessor.getOrDefault(activity.iri(), List.of())) {
                Element link = element(doc, task, "PredecessorLink");
                text(doc, link, "PredecessorId", rel.predUid());
                text(doc, link, "Type", rel.msType());
                text(doc, link, "Lag", String.valueOf(rel.lagMinutes()));
            }
        }
    }

    // ------------------------------------------------------------------
    // Field mappings
    // ------------------------------------------------------------------

    /**
     * Inverse of the forward converter's {@code statusFor}: Completed
     * round-trips exactly (100), Planned round-trips exactly (0), while
     * InProgress is approximated as 50 and reported as a warning.
     */
    private static int percentComplete(Activity activity, List<String> warnings) {
        String status = activity.status();
        return switch (status == null ? "" : status) {
            case "Completed" -> 100;
            case "InProgress" -> {
                warnings.add("Activity '" + activity.name() + "': status 'InProgress' cannot be "
                        + "represented exactly – assuming 50% complete.");
                yield 50;
            }
            case "Planned", "" -> 0;
            default -> {
                warnings.add("Activity '" + activity.name() + "': unknown status '"
                        + status + "' – assuming 0% complete.");
                yield 0;
            }
        };
    }

    /**
     * MS Project duration that never triggers the forward converter's
     * zero-duration milestone heuristic (it only fires for values like
     * {@code 0:00}), while keeping the document plausible.
     */
    private static String taskDuration(String start, String finish) {
        LocalDateTime s = parseDateTime(start);
        LocalDateTime f = parseDateTime(finish);
        if (s == null || f == null) {
            return "PT1H";
        }
        return "PT" + Math.max(0, Duration.between(s, f).toHours()) + "H";
    }

    /** Inverse of the forward converter's {@code relationshipTypeFor}. */
    private static String outlineLevel(String hierarchyCode) {
        if (hierarchyCode == null || hierarchyCode.isBlank()) {
            return "1";
        }
        int segments = 0;
        for (String part : hierarchyCode.split("[-.]")) {
            if (!part.isBlank()) {
                segments++;
            }
        }
        return String.valueOf(Math.max(1, segments));
    }

    private static long projectDurationHours(List<Activity> activities) {
        String start = minDate(activities, true);
        String finish = minDate(activities, false);
        LocalDateTime s = parseDateTime(start);
        LocalDateTime f = parseDateTime(finish);
        if (s == null || f == null) {
            return 1;
        }
        return Math.max(1, Duration.between(s, f).toHours());
    }

    /** Minimal start ({@code forStart = true}) or maximal finish. */
    private static String minDate(List<Activity> activities, boolean forStart) {
        LocalDateTime best = null;
        for (Activity activity : activities) {
            LocalDateTime value = parseDateTime(forStart ? activity.start() : activity.finish());
            if (value == null) {
                continue;
            }
            if (best == null || (forStart ? value.isBefore(best) : value.isAfter(best))) {
                best = value;
            }
        }
        return best != null ? best.format(DATETIME_FORMAT) : LocalDateTime.now().format(DATETIME_FORMAT);
    }

    // ------------------------------------------------------------------
    // Model helpers (tolerant for Resources *and* Literals, the latter
    // occur when the model was read from plain JSON)
    // ------------------------------------------------------------------

    private static Property prop(String localName) {
        return ResourceFactory.createProperty(MsProjectConverter.ONT + localName);
    }

    /** IRI of an object node; Literal references are decoded from CURIEs. */
    private static String nodeIri(RDFNode node) {
        if (node instanceof Resource resource) {
            return resource.isAnon() ? null : resource.getURI();
        }
        if (node instanceof Literal literal) {
            return ConxCurieRegister.decode(literal.getString());
        }
        return null;
    }

    /**
     * Local name of a node's IRI: after the last {@code #}, else after a
     * {@code :} that lies beyond the last {@code /} (so {@code https:} is
     * not mistaken for a namespace separator), else after the last {@code /}.
     */
    private static String localPart(RDFNode node) {
        String iri = nodeIri(node);
        if (iri == null) {
            return null;
        }
        int hash = iri.lastIndexOf('#');
        if (hash >= 0 && hash < iri.length() - 1) {
            return iri.substring(hash + 1);
        }
        int colon = iri.lastIndexOf(':');
        int slash = iri.lastIndexOf('/');
        if (colon > slash && colon < iri.length() - 1) {
            return iri.substring(colon + 1);
        }
        if (slash >= 0 && slash < iri.length() - 1) {
            return iri.substring(slash + 1);
        }
        return iri;
    }

    private static String firstLiteral(Resource resource, String localName) {
        StmtIterator it = resource.listProperties(prop(localName));
        if (it.hasNext()) {
            RDFNode object = it.next().getObject();
            if (object instanceof Literal literal) {
                return literal.getString();
            }
        }
        return null;
    }

    private static String firstNodeIri(Resource resource, String localName) {
        StmtIterator it = resource.listProperties(prop(localName));
        return it.hasNext() ? nodeIri(it.next().getObject()) : null;
    }

    private static String firstLocalPart(Resource resource, String localName) {
        StmtIterator it = resource.listProperties(prop(localName));
        return it.hasNext() ? localPart(it.next().getObject()) : null;
    }

    private static boolean isMilestoneType(Resource resource) {
        StmtIterator it = resource.listProperties(RDF.type);
        while (it.hasNext()) {
            RDFNode type = it.next().getObject();
            if ("Milestone".equals(localPart(type))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Numeric task UID from an identifier or IRI such as {@code T3} or
     * {@code …/activity/T3}; {@code null} when no {@code T<n>} token exists.
     */
    private static Integer extractUid(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = UID_PATTERN.matcher(text);
        Integer last = null;
        while (matcher.find()) {
            last = Integer.parseInt(matcher.group(1));
        }
        return last;
    }

    private static LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Document building
    // ------------------------------------------------------------------

    private static Document newDocument() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setAttribute(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            return factory.newDocumentBuilder().newDocument();
        } catch (Exception e) {
            throw new IllegalStateException("Could not create XML document", e);
        }
    }

    private static Element element(Document doc, Element parent, String name) {
        Element child = doc.createElementNS(MS_PROJECT_NS, name);
        parent.appendChild(child);
        return child;
    }

    private static Element text(Document doc, Element parent, String name, String value) {
        Element child = element(doc, parent, name);
        if (value != null) {
            child.setTextContent(value);
        }
        return child;
    }

    private static String serialize(Document doc) {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize MS Project XML", e);
        }
    }
}
