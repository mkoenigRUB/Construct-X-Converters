package org.constructx.converter;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.constructx.sdk.ConxStrict;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reconstructs a GAEB DA XML 3.3 bill of quantities (phase X80) from a
 * Construct-X GAEB BoQ ontology model — the inverse of
 * {@link GaebBoqConverter}.
 *
 * <p>Round-trip semantics:
 * <ul>
 *   <li>Structure: BoQ, sections (nesting, order numbers, levels), items
 *       (order number, quantity, unit, unit price, total amount, texts),
 *       quantity splits, unit-price components, catalog assignments.</li>
 *   <li>Lossy by design (each case produces a warning, not an error):
 *       percentage quantity splits are resolved to absolute values by the
 *       forward converter and are written back as absolute values;
 *       price components of type {@code Other} map to the free UPComp4
 *       slot, {@code Unknown} to UPComp5; the GAEB header
 *       (GAEBInfo/PrjInfo/Award) is not part of the RDF and is synthesized
 *       from {@link HeaderOverrides} or built-in defaults.</li>
 * </ul>
 *
 * <p>The generated document is validated against the official GAEB XSD in
 * the service layer / tests before it is returned to the caller.
 */
public final class GaebBoqReverseConverter {

    /** Header fields for the synthesized GAEB envelope; all optional. */
    public record HeaderOverrides(String projectLabel, String currency) {
        public static HeaderOverrides of(String projectLabel, String currency) {
            return new HeaderOverrides(projectLabel, currency);
        }
    }

    /** Result of a reverse conversion: XML document + lossy-step warnings. */
    public static final class Result {
        private final String xml;
        private final List<String> warnings;
        private final Set<String> usedIds;

        Result(String xml, List<String> warnings, Set<String> usedIds) {
            this.xml = xml;
            this.warnings = List.copyOf(warnings);
            this.usedIds = Set.copyOf(usedIds);
        }

        public String xml() {
            return xml;
        }

        public List<String> warnings() {
            return warnings;
        }

        /** GAEB ID attributes used in the document (for cross-checks). */
        public Set<String> usedIds() {
            return usedIds;
        }
    }

    private static final String NS = "http://www.gaeb.de/GAEB_DA_XML/DA80/3.3";
    /** Mapping of UPComp slots 1..6 to the boq-price-component-type vocabulary. */
    private static final String[] UPCOMP_TYPES = {
            ConxNamespaces.PC_LABOR, ConxNamespaces.PC_MATERIALS, ConxNamespaces.PC_EQUIPMENT,
            ConxNamespaces.PC_OTHER, ConxNamespaces.PC_UNKNOWN, ConxNamespaces.PC_OTHER
    };
    private static final String[] UPCOMP_LABELS = {
            "Lohn", "Material", "Gerät", "Allgemeiner Teil", "Unbekannt", "Sonstiges"
    };
    private static final Pattern UPC_INDEX = Pattern.compile("-UPC(\\d)$");

    private final List<String> warnings = new ArrayList<>();
    private final Set<String> usedIds = new HashSet<>();
    private final Set<String> seenIds = new HashSet<>();
    private final HeaderOverrides header;
    private final Document doc;
    /** Max RNoPart segment length per section level, for the BoQBkdn breakdowns. */
    private final java.util.TreeMap<Integer, Integer> sectionSegmentLengths = new java.util.TreeMap<>();
    private int maxItemSegmentLength = 0;

    private GaebBoqReverseConverter(HeaderOverrides header) throws Exception {
        this.header = header == null ? new HeaderOverrides(null, null) : header;
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setAttribute(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        this.doc = builder.newDocument();
    }

    /**
     * Converts the given model to a GAEB DA XML 3.3 (X80) document.
     *
     * @param model  Jena model containing a conx-gaeb BillOfQuantities
     * @param header optional header overrides (project label, currency)
     * @return XML document plus the warnings of all lossy reconstruction steps
     */
    public static Result convert(org.apache.jena.rdf.model.Model model, HeaderOverrides header)
            throws Exception {
        return convert(model, header, false);
    }

    /**
     * Converts the given model to a GAEB DA XML 3.3 (X80) document.
     *
     * @param model  Jena model containing a conx-gaeb BillOfQuantities
     * @param header optional header overrides (project label, currency)
     * @param strict strict mode: warnings that lose data (skipped entries,
     *               unmappable values) abort the conversion with an
     *               {@link IllegalArgumentException} listing the offending
     *               warnings. Lossy approximations (e.g. percentage splits
     *               written back as absolute values, the synthesized header)
     *               remain tolerated.
     * @return XML document plus the warnings of all lossy reconstruction steps
     */
    public static Result convert(org.apache.jena.rdf.model.Model model, HeaderOverrides header,
                                 boolean strict)
            throws Exception {
        GaebBoqReverseConverter c = new GaebBoqReverseConverter(header);
        c.convertModel(model);
        if (strict) {
            List<String> strictWarnings = ConxStrict.strictWarnings(c.warnings);
            if (!strictWarnings.isEmpty()) {
                throw new IllegalArgumentException(ConxStrict.failureMessage(c.warnings));
            }
        }
        return c.finish();
    }

    private Result finish() throws Exception {
        StringWriter out = new StringWriter();
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        transformer.transform(new DOMSource(doc), new StreamResult(out));
        return new Result(out.toString(), warnings, usedIds);
    }

    // ------------------------------------------------------------------
    // Model walking
    // ------------------------------------------------------------------

    private void convertModel(org.apache.jena.rdf.model.Model model) {
        Resource rootBoq = findRootBoq(model);
        Element gaeb = child(doc, NS, "GAEB");
        addGaebInfo(gaeb, rootBoq);
        Element award = child(gaeb, NS, "Award");
        childText(award, NS, "DP", "80");
        Element awardInfo = child(award, NS, "AwardInfo");
        // BoQID is xs:GUID32 — a plain BoQ identifier is only an approximation.
        childText(awardInfo, NS, "BoQID",
                firstLiteral(rootBoq, ConxNamespaces.P_IDENTIFIER, "00000000-0000-0000-0000-000000000000"));
        childText(awardInfo, NS, "Cat", "OpenProc");
        addBoQ(award, model, rootBoq);
    }

    private Resource findRootBoq(org.apache.jena.rdf.model.Model model) {
        Resource root = null;
        var iterator = model.listSubjectsWithProperty(
                RDF.type, model.createResource(ConxNamespaces.BILL_OF_QUANTITIES));
        while (iterator.hasNext()) {
            root = iterator.next();
        }
        if (root == null) {
            throw new IllegalArgumentException(
                    "No BillOfQuantities entity found in the RDF model");
        }
        return root;
    }

    // ------------------------------------------------------------------
    // GAEB header (synthesized; not part of the RDF)
    // ------------------------------------------------------------------

    private void addGaebInfo(Element gaeb, Resource rootBoq) {
        warnings.add("GAEBInfo/PrjInfo/Award are not part of the RDF model and were "
                + "synthesized with default values.");
        Element info = child(gaeb, NS, "GAEBInfo");
        childText(info, NS, "Version", "3.3");
        childText(info, NS, "VersDate", "2021-05");
        childText(info, NS, "Date", LocalDate.now().toString());
        childText(info, NS, "Time", LocalTime.now().withNano(0)
                .format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        childText(info, NS, "ProgSystem", "Construct-X");
        childText(info, NS, "ProgName", "Construct-X Reverse Converter");

        Element prj = child(gaeb, NS, "PrjInfo");
        String label = header.projectLabel() != null
                ? header.projectLabel()
                : firstLiteral(rootBoq, ConxNamespaces.P_NAME, "Construct-X Project");
        childText(prj, NS, "NamePrj", label);
        childText(prj, NS, "PrjID", "00000000-0000-0000-0000-000000000000");
        childText(prj, NS, "LblPrj", label);
        String currency = header.currency() != null
                ? header.currency()
                : firstLiteral(rootBoq, ConxNamespaces.P_CURRENCY_CODE, "EUR");
        childText(prj, NS, "Cur", currency);
    }

    // ------------------------------------------------------------------
    // BoQ
    // ------------------------------------------------------------------

    private void addBoQ(Element award, org.apache.jena.rdf.model.Model model, Resource boq) {
        String id = uniqueId(firstLiteral(boq, ConxNamespaces.P_IDENTIFIER, "BOQ-001"));
        Element boqEl = child(award, NS, "BoQ");
        boqEl.setAttributeNS(null, "ID", id);

        // Pre-scan the model to derive the order-number breakdown lengths
        // (needed in BoQInfo before the sections/items are written).
        preScanBreakdowns(boq, model);

        Element boqInfo = child(boqEl, NS, "BoQInfo");
        String name = firstLiteral(boq, ConxNamespaces.P_NAME, null);
        if (name != null) {
            childText(boqInfo, NS, "Name", name);
        }
        childText(boqInfo, NS, "LblBoQ", name != null ? name : id);
        childText(boqInfo, NS, "Date", LocalDate.now().toString());
        childText(boqInfo, NS, "OutlCompl", "AllTxt");

        // Order-number breakdowns: mandatory per XSD (1..7), before NoUPComps.
        int maxSectionLevel = sectionSegmentLengths.isEmpty() ? 0 : sectionSegmentLengths.lastKey();
        for (int lvl = 1; lvl <= Math.max(maxSectionLevel, 1); lvl++) {
            int len = sectionSegmentLengths.getOrDefault(lvl, 2);
            addBkdn(boqInfo, "BoQLevel", lvl == 1 ? "Bereich" : "Bereich " + lvl, len, "Yes");
        }
        if (maxItemSegmentLength > 0) {
            addBkdn(boqInfo, "Item", "Position", maxItemSegmentLength, "Yes");
        }

        int noUpComps = Math.max(maxUnitPriceComponents(model), 2);
        // XSD enum tgNoUPComps: 0, 2..6 — 1 is not allowed.
        childText(boqInfo, NS, "NoUPComps", String.valueOf(noUpComps));
        for (int i = 1; i <= noUpComps; i++) {
            Element lbl = child(boqInfo, NS, "LblUPComp" + i);
            lbl.setAttributeNS(null, "Type", upCompTypeXml(i));
            lbl.setTextContent(UPCOMP_LABELS[i - 1]);
        }

        Element body = child(boqEl, NS, "BoQBody");
        int sectionCounter = 0;
        for (Resource section : orderedChildren(boq, ConxNamespaces.P_HAS_ROOT_SECTION, model)) {
            sectionCounter++;
            String rNoPart = ordinalSegment(section, "", String.format("%02d", sectionCounter));
            addSection(body, section, model, 1, "", rNoPart);
        }
        List<Resource> directItems = orderedChildren(boq, ConxNamespaces.P_HAS_DIRECT_ITEM, model);
        if (!directItems.isEmpty()) {
            Element itemlist = child(body, NS, "Itemlist");
            for (Resource item : directItems) {
                addItem(itemlist, item, model, "");
            }
        }
    }

    /**
     * Pre-scan of the BoQ to derive the order-number segment lengths per
     * section level and for items — needed for the mandatory {@code BoQBkdn}
     * entries that must precede {@code NoUPComps} in {@code BoQInfo}.
     * Mirrors the segment logic used while writing.
     */
    private void preScanBreakdowns(Resource boq, org.apache.jena.rdf.model.Model model) {
        for (Resource section : orderedChildren(boq, ConxNamespaces.P_HAS_ROOT_SECTION, model)) {
            preScanSection(section, model, 1, "");
        }
        // Direct items under the BoQ.
        for (Resource item : orderedChildren(boq, ConxNamespaces.P_HAS_DIRECT_ITEM, model)) {
            trackItemSegment(item, "");
        }
    }

    private void preScanSection(Resource section, org.apache.jena.rdf.model.Model model,
                                int level, String parentOz) {
        String segment = ordinalSegment(section, parentOz, "00");
        sectionSegmentLengths.merge(level, segment.length(), Math::max);
        String fullOz = (parentOz == null ? "" : parentOz) + segment;
        for (Resource sub : orderedChildren(section, ConxNamespaces.P_HAS_SUB_SECTION, model)) {
            preScanSection(sub, model, level + 1, fullOz);
        }
        for (Resource item : orderedChildren(section, ConxNamespaces.P_HAS_ITEM, model)) {
            trackItemSegment(item, fullOz);
        }
    }

    private void trackItemSegment(Resource item, String ozPrefix) {
        String fullOz = firstLiteral(item, ConxNamespaces.P_ORDINAL_NUMBER, null);
        int len = (fullOz == null || fullOz.isBlank())
                ? 3
                : (ozPrefix != null && !ozPrefix.isBlank() && fullOz.startsWith(ozPrefix))
                        ? Math.max(1, fullOz.length() - ozPrefix.length())
                        : fullOz.length();
        maxItemSegmentLength = Math.max(maxItemSegmentLength, len);
    }

    /** Adds a mandatory {@code BoQBkdn} order-number breakdown entry. */
    private void addBkdn(Element boqInfo, String type, String label, int length, String num) {
        Element bkdn = child(boqInfo, NS, "BoQBkdn");
        childText(bkdn, NS, "Type", type);
        childText(bkdn, NS, "LblBoQBkdn", label);
        childText(bkdn, NS, "Length", String.valueOf(length));
        childText(bkdn, NS, "Num", num);
    }

    /** Highest UPComp index (1..6) present among all UnitPriceComponent entities. */
    private int maxUnitPriceComponents(org.apache.jena.rdf.model.Model model) {
        int max = 0;
        var iterator = model.listSubjectsWithProperty(
                RDF.type, model.createResource(ConxNamespaces.UNIT_PRICE_COMPONENT));
        while (iterator.hasNext()) {
            Resource upc = iterator.next();
            String identifier = firstLiteral(upc, ConxNamespaces.P_IDENTIFIER, null);
            if (identifier == null) {
                continue;
            }
            Matcher m = UPC_INDEX.matcher(identifier);
            if (m.find()) {
                max = Math.max(max, Integer.parseInt(m.group(1)));
            }
        }
        return max;
    }

    private String upCompTypeXml(int index) {
        // XSD enum: Wages, Materials, Plant, Miscellaneous, Unknown
        return switch (index) {
            case 1 -> "Wages";
            case 2 -> "Materials";
            case 3 -> "Plant";
            case 4 -> "Miscellaneous";
            case 5 -> "Unknown";
            default -> "Miscellaneous";
        };
    }

    // ------------------------------------------------------------------
    // Sections
    // ------------------------------------------------------------------

    private void addSection(Element parent, Resource section,
                            org.apache.jena.rdf.model.Model model, int level,
                            String ozPrefix, String rNoPart) {
        String id = uniqueId(firstLiteral(section, ConxNamespaces.P_IDENTIFIER,
                "SEC-" + (rNoPart == null || rNoPart.isBlank() ? "00" : rNoPart)));
        Element el = child(parent, NS, "BoQCtgy");
        el.setAttributeNS(null, "ID", id);
        el.setAttributeNS(null, "RNoPart", rNoPart);

        String name = firstLiteral(section, ConxNamespaces.P_NAME, null);
        // MLText content: <span> belongs to the GAEB namespace.
        Element span = child(child(el, NS, "LblTx"), NS, "span");
        span.setTextContent(name != null ? name : id);

        // Section-level catalog assignments (between LblTx and BoQBody per XSD)
        addCatalogAssignments(el, section);

        // Cross-check the stored level against the nesting depth
        String storedLevel = firstLiteral(section, ConxNamespaces.P_LEVEL_NUMBER, null);
        if (storedLevel != null && !storedLevel.equals(String.valueOf(level))) {
            warnings.add("Section " + id + ": stored level " + storedLevel
                    + " does not match nesting depth " + level + "; using nesting depth.");
        }

        String fullOz = ozPrefix + rNoPart;
        Element body = child(el, NS, "BoQBody");
        boolean hasContent = false;

        int subCounter = 0;
        for (Resource sub : orderedChildren(section, ConxNamespaces.P_HAS_SUB_SECTION, model)) {
            subCounter++;
            String subRNoPart = ordinalSegment(sub, fullOz, String.format("%02d", subCounter));
            addSection(body, sub, model, level + 1, fullOz, subRNoPart);
            hasContent = true;
        }

        List<Resource> items = orderedChildren(section, ConxNamespaces.P_HAS_ITEM, model);
        if (!items.isEmpty()) {
            Element itemlist = child(body, NS, "Itemlist");
            for (Resource item : items) {
                addItem(itemlist, item, model, fullOz);
            }
            hasContent = true;
        }
        if (!hasContent) {
            // A BoQCtgy without a BoQBody is valid; drop the empty one.
            body.getParentNode().removeChild(body);
        }
    }

    /**
     * The RNoPart segment for a child: its ordinal number with the parent
     * order number stripped; the fallback when that is not possible.
     */
    private String ordinalSegment(Resource child, String parentOz, String fallback) {
        String ordinal = firstLiteral(child, ConxNamespaces.P_ORDINAL_NUMBER, null);
        if (ordinal != null && !ordinal.isBlank()) {
            if (parentOz != null && !parentOz.isBlank() && ordinal.startsWith(parentOz)) {
                String segment = ordinal.substring(parentOz.length());
                if (!segment.isBlank()) {
                    return segment;
                }
            }
            // Root level: the stored ordinal number IS the segment.
            if (parentOz == null || parentOz.isBlank()) {
                return ordinal;
            }
        }
        return fallback;
    }

    // ------------------------------------------------------------------
    // Items
    // ------------------------------------------------------------------

    private void addItem(Element itemlist, Resource item,
                         org.apache.jena.rdf.model.Model model, String ozPrefix) {
        String id = uniqueId(firstLiteral(item, ConxNamespaces.P_IDENTIFIER, "ITM"));
        Element el = child(itemlist, NS, "Item");
        el.setAttributeNS(null, "ID", id);

        String fullOz = firstLiteral(item, ConxNamespaces.P_ORDINAL_NUMBER, null);
        String rNoPart;
        if (fullOz != null && !fullOz.isBlank()
                && ozPrefix != null && !ozPrefix.isBlank()
                && fullOz.startsWith(ozPrefix)) {
            // Item inside a section: strip the section's order number.
            rNoPart = fullOz.substring(ozPrefix.length());
        } else if (fullOz != null && !fullOz.isBlank()
                && (ozPrefix == null || ozPrefix.isBlank())) {
            // Direct item under the BoQ: the stored ordinal number is already
            // the item's own segment (the forward converter prefixes nothing).
            rNoPart = fullOz;
        } else if (fullOz != null && !fullOz.isBlank()) {
            rNoPart = fullOz;
            warnings.add("Item " + id + ": ordinal number " + fullOz
                    + " does not extend the parent order number "
                    + (ozPrefix == null || ozPrefix.isBlank() ? "(root)" : ozPrefix)
                    + "; writing it verbatim as RNoPart.");
        } else {
            rNoPart = "000";
            warnings.add("Item " + id + ": no ordinal number in the model; "
                    + "using RNoPart \"000\".");
        }
        el.setAttributeNS(null, "RNoPart", rNoPart);
        String ordinalIndex = firstLiteral(item, ConxNamespaces.P_ORDINAL_INDEX, null);
        if (ordinalIndex != null && !ordinalIndex.isBlank()) {
            el.setAttributeNS(null, "RNoIndex", ordinalIndex);
        }

        String quantity = firstLiteral(item, ConxNamespaces.P_QUANTITY, null);
        if (quantity != null) {
            childText(el, NS, "Qty", normalizeDecimal(quantity, 3));
        }
        for (Resource split : orderedChildren(item, ConxNamespaces.P_HAS_QUANTITY_SPLIT, model)) {
            // GAEB requires the item Qty element before any QtySplit.
            if (quantity == null) {
                warnings.add("Item " + id + " has quantity splits but no quantity; "
                        + "splits were skipped (GAEB requires Qty before QtySplit).");
                break;
            }
            addQuantitySplit(el, split);
        }
        String unitCode = firstLiteral(item, ConxNamespaces.P_UNIT_CODE, null);
        if (unitCode != null) {
            childText(el, NS, "QU", unitCode);
        }

        addCatalogAssignments(el, item);

        String unitPrice = firstLiteral(item, ConxNamespaces.P_UNIT_PRICE, null);
        if (unitPrice != null) {
            childText(el, NS, "UP", normalizeDecimal(unitPrice, 3));
        }
        addUnitPriceComponents(el, item);
        String total = firstLiteral(item, ConxNamespaces.P_TOTAL_AMOUNT, null);
        if (total != null) {
            childText(el, NS, "IT", normalizeDecimal(total, 2));
        }
        addDescription(el, item);
    }

    private void addQuantitySplit(Element itemEl, Resource split) {
        String quantity = firstLiteral(split, ConxNamespaces.P_QUANTITY, null);
        if (quantity == null) {
            warnings.add("QuantitySplit " + firstLiteral(split, ConxNamespaces.P_IDENTIFIER, "?")
                    + " has no quantity in the model; skipped.");
            return;
        }
        // The forward converter resolves percentage splits to absolute
        // values, so the RDF only carries absolute values.
        Element el = child(itemEl, NS, "QtySplit");
        childText(el, NS, "Qty", normalizeDecimal(quantity, 3));
        addCatalogAssignments(el, split);
    }

    private void addUnitPriceComponents(Element itemEl, Resource item) {
        List<Resource> upcs = new ArrayList<>();
        item.listProperties(property(item, ConxNamespaces.P_HAS_UNIT_PRICE_COMPONENT))
                .forEachRemaining(o -> {
                    if (o.getObject().isResource()) {
                        upcs.add(o.getObject().asResource());
                    }
                });
        upcs.sort((a, b) -> Integer.compare(upcIndex(a), upcIndex(b)));

        Set<String> usedSlots = new HashSet<>();
        for (Resource upc : upcs) {
            int idx = upcIndex(upc);
            String amount = firstLiteral(upc, ConxNamespaces.P_AMOUNT, null);
            if (amount == null) {
                continue;
            }
            String type = priceComponentTypeUri(upc);
            int slot = -1;
            if (idx >= 1 && idx <= 6
                    && (type == null || UPCOMP_TYPES[idx - 1].equals(type))) {
                slot = idx;
            }
            if (slot == -1 && type != null) {
                // Lossy fallback: Other -> free UPComp4, Unknown -> UPComp5
                if (ConxNamespaces.PC_OTHER.equals(type) && !usedSlots.contains("4")) {
                    slot = 4;
                } else if (ConxNamespaces.PC_UNKNOWN.equals(type) && !usedSlots.contains("5")) {
                    slot = 5;
                }
            }
            if (slot == -1 || slot > 6) {
                warnings.add("Item " + firstLiteral(item, ConxNamespaces.P_IDENTIFIER, "?")
                        + ": unit price component "
                        + firstLiteral(upc, ConxNamespaces.P_IDENTIFIER, "?")
                        + " could not be mapped to a UPComp slot; skipped.");
                continue;
            }
            usedSlots.add(String.valueOf(slot));
            childText(itemEl, NS, "UPComp" + slot, normalizeDecimal(amount, 3));
        }
    }

    private void addDescription(Element itemEl, Resource item) {
        String shortText = firstLiteral(item, ConxNamespaces.P_SHORT_TEXT, null);
        String longText = firstLiteral(item, ConxNamespaces.P_LONG_TEXT, null);
        if (shortText == null && longText == null) {
            return;
        }
        Element completeText = child(child(itemEl, NS, "Description"), NS, "CompleteText");
        // DetailTxt is mandatory (tgBoQText requires at least one Text);
        // an empty <Text> is valid when the model carries no long text.
        Element text = child(child(completeText, NS, "DetailTxt"), NS, "Text");
        if (longText != null) {
            childSpan(text, longText);
        }
        if (shortText != null) {
            // OutlineText -> OutlTxt -> TextOutlTxt -> span (all GAEB-qualified)
            Element textOutlTxt = child(
                    child(child(completeText, NS, "OutlineText"), NS, "OutlTxt"),
                    NS, "TextOutlTxt");
            childSpan(textOutlTxt, shortText);
        }
    }

    // ------------------------------------------------------------------
    // Catalog assignments
    // ------------------------------------------------------------------

    private void addCatalogAssignments(Element ownerEl, Resource owner) {
        List<Resource> assigns = new ArrayList<>();
        owner.listProperties(property(owner, ConxNamespaces.P_HAS_CATALOG_ASSIGNMENT))
                .forEachRemaining(o -> {
                    if (o.getObject().isResource()) {
                        assigns.add(o.getObject().asResource());
                    }
                });
        for (Resource assign : assigns) {
            String catalogId = firstLiteral(assign, ConxNamespaces.P_CATALOG_IDENTIFIER, null);
            Resource extRef = firstResource(assign, ConxNamespaces.P_REFERENCES_EXTERNAL_OBJECT);
            String catalogCode = extRef != null
                    ? firstLiteral(extRef, ConxNamespaces.P_EXTERNAL_IDENTIFIER, null)
                    : null;
            if (catalogId == null || catalogCode == null) {
                warnings.add("CatalogAssignment "
                        + firstLiteral(assign, ConxNamespaces.P_IDENTIFIER, "?")
                        + " is incomplete in the model; skipped.");
                continue;
            }
            Element el = child(ownerEl, NS, "CtlgAssign");
            childText(el, NS, "CtlgID", catalogId);
            childText(el, NS, "CtlgCode", catalogCode);
        }
    }

    // ------------------------------------------------------------------
    // RDF helpers
    // ------------------------------------------------------------------

    private static Property property(Resource resource, String propertyIri) {
        return resource.getModel() != null
                ? resource.getModel().createProperty(propertyIri)
                : org.apache.jena.rdf.model.ResourceFactory.createProperty(propertyIri);
    }

    private List<Resource> orderedChildren(Resource parent, String propertyIri,
                                           org.apache.jena.rdf.model.Model model) {
        List<Resource> children = new ArrayList<>();
        parent.listProperties(model.createProperty(propertyIri)).forEachRemaining(o -> {
            if (o.getObject().isResource()) {
                children.add(o.getObject().asResource());
            }
        });
        // Stable sort by ordinal number; entries without one come last.
        children.sort((a, b) -> {
            String oa = firstLiteral(a, ConxNamespaces.P_ORDINAL_NUMBER, null);
            String ob = firstLiteral(b, ConxNamespaces.P_ORDINAL_NUMBER, null);
            if (oa == null && ob == null) {
                return 0;
            }
            if (oa == null) {
                return 1;
            }
            if (ob == null) {
                return -1;
            }
            return oa.compareTo(ob);
        });
        return children;
    }

    /** The first literal value of the given property; the fallback otherwise. */
    private static String firstLiteral(Resource resource, String propertyIri, String fallback) {
        Statement s = resource.getProperty(property(resource, propertyIri));
        if (s != null && s.getObject().isLiteral()) {
            String v = s.getObject().asLiteral().getString();
            return v.isBlank() ? fallback : v;
        }
        return fallback;
    }

    private static Resource firstResource(Resource resource, String propertyIri) {
        Statement s = resource.getProperty(property(resource, propertyIri));
        if (s != null && s.getObject().isResource()) {
            return s.getObject().asResource();
        }
        return null;
    }

    /** The boq-price-component-type vocabulary IRI of the component. */
    private static String priceComponentTypeUri(Resource upc) {
        Resource ref = firstResource(upc, ConxNamespaces.P_HAS_PRICE_COMPONENT_TYPE);
        if (ref != null) {
            return ref.getURI();
        }
        // Fallback for hand-crafted models: the rdf:type of the component
        return firstTypeUri(upc);
    }

    private static String firstTypeUri(Resource resource) {
        if (!resource.hasProperty(RDF.type)) {
            return null;
        }
        var type = resource.getProperty(RDF.type).getObject();
        if (type.isResource()) {
            return type.asResource().getURI();
        }
        return null;
    }

    private int upcIndex(Resource upc) {
        String identifier = firstLiteral(upc, ConxNamespaces.P_IDENTIFIER, null);
        if (identifier != null) {
            Matcher m = UPC_INDEX.matcher(identifier);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        }
        return 0;
    }

    /**
     * Formats a decimal with exactly the given number of fraction digits —
     * the GAEB XSD decimal patterns require a fixed precision. Values
     * already at that scale are returned unchanged; unparseable values
     * pass through (the XSD validation will report them).
     */
    private String normalizeDecimal(String value, int scale) {
        try {
            java.math.BigDecimal d = new java.math.BigDecimal(value.trim());
            return d.scale() == scale
                    ? d.toPlainString()
                    : d.setScale(scale, java.math.RoundingMode.UNNECESSARY).toPlainString();
        } catch (Exception e) {
            warnings.add("Decimal value \"" + value + "\" could not be normalized "
                    + "to " + scale + " fraction digits; written as-is.");
            return value;
        }
    }

    /**
     * Guarantees a unique, XSD-{@code ID}-compatible GAEB identifier:
     * invalid characters are replaced, duplicates get a numeric suffix.
     */
    private String uniqueId(String candidate) {
        String cleaned = candidate.replaceAll("[^A-Za-z0-9._-]", "_");
        if (cleaned.isEmpty() || !Character.isLetter(cleaned.charAt(0))) {
            cleaned = "ID" + cleaned;
        }
        String unique = cleaned;
        int suffix = 2;
        while (!seenIds.add(unique)) {
            unique = cleaned + "-" + suffix++;
        }
        if (!unique.equals(candidate)) {
            warnings.add("Identifier \"" + candidate
                    + "\" is not a valid/unique GAEB ID; using \"" + unique + "\".");
        }
        usedIds.add(unique);
        return unique;
    }

    // ------------------------------------------------------------------
    // DOM helpers
    // ------------------------------------------------------------------

    private Element child(Node parent, String namespace, String name) {
        Element el = namespace == null
                ? doc.createElement(name)
                : doc.createElementNS(namespace, name);
        parent.appendChild(el);
        return el;
    }

    private Element childText(Node parent, String namespace, String name, String text) {
        Element el = child(parent, namespace, name);
        el.setTextContent(text);
        return el;
    }

    private void childSpan(Element parent, String text) {
        Element span = child(parent, NS, "span");
        span.setTextContent(text);
    }
}
