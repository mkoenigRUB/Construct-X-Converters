package org.constructx.converter;

import org.constructx.sdk.IriFactory;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts a GAEB DA XML 3.3 bill of quantities (Datenaustauschphasen X80-X87,
 * element {@code <BoQ>}) into the Construct-X GAEB BoQ ontology
 * ({@code conx-gaeb}, v0.5.0) as a Turtle model.
 *
 * <p>Mapping summary (GAEB XML -&gt; conx-gaeb):
 * <ul>
 *   <li>{@code BoQ@ID} / {@code BoQInfo/LblBoQ} -&gt; {@code BillOfQuantities} (identifier, name)</li>
 *   <li>{@code BoQCtgy} (nested via {@code BoQBody}) -&gt; {@code BoQSection} with level 1..n</li>
 *   <li>{@code Item} -&gt; {@code BoQItem} (RNoPart -&gt; ordinalNumber, Qty/QU, UP, IT, texts)</li>
 *   <li>{@code Item/QtySplit} -&gt; {@code QuantitySplit} (absolute or percentage quantity)</li>
 *   <li>{@code Item/UPComp1..6} -&gt; {@code UnitPriceComponent} with controlled type</li>
 *   <li>{@code CtlgAssign} -&gt; {@code CatalogAssignment} + {@code ExternalObjectReference}</li>
 * </ul>
 *
 * <p>The currency is read from {@code PrjInfo/Cur} when present, otherwise the
 * configured default is used.
 */
public class GaebBoqConverter {

    /** Default labels for the six GAEB unit-price components (UPComp1..6). */
    private static final String[] UPCOMP_LABELS = {
            "Lohn", "Material", "Gerät", "Allgemeiner Teil", "Gewinn", "Sonstiges"
    };

    /** Mapping of UPComp1..6 to the boq-price-component-type vocabulary. */
    private static final String[] UPCOMP_TYPES = {
            ConxNamespaces.PC_LABOR,
            ConxNamespaces.PC_MATERIALS,
            ConxNamespaces.PC_EQUIPMENT,
            ConxNamespaces.PC_OTHER,
            ConxNamespaces.PC_UNKNOWN,
            ConxNamespaces.PC_OTHER
    };

    /**
     * Versioned IRIs of the ontology and SHapes the converted model
     * conforms to. Used for the {@code semanticModel.conformsTo} field of
     * the exchange package envelope.
     */
    public static final List<String> CONFORMS_TO = List.of(
            "https://www.construct-x.org/ontology/gaeb-boq/0.5.0",
            "https://www.construct-x.org/shapes/gaeb-boq/core/0.5.0",
            "https://www.construct-x.org/vocab/boq-price-component-type/0.1.0",
            "https://www.construct-x.org/shapes/gaeb-boq/price-component-type/0.1.0"
    );

    private final IriFactory iriFactory;
    private final String defaultCurrency;
    private Model model;
    private Resource rootBoq;

    public GaebBoqConverter() {
        this(new IriFactory(), "EUR");
    }

    public GaebBoqConverter(IriFactory iriFactory, String defaultCurrency) {
        this.iriFactory = iriFactory;
        this.defaultCurrency = defaultCurrency;
    }

    /**
     * Converts the given GAEB XML file into a Jena model.
     *
     * @param xmlFile GAEB DA XML 3.3 file (any LV data-exchange phase X80-X87)
     * @return RDF model in the conx-gaeb namespace
     */
    public Model convert(File xmlFile) throws Exception {
        return convert(xmlFile, null);
    }

    /**
     * Converts the given GAEB XML file into a Jena model, optionally
     * restricted to a selection of bill items.
     *
     * <p>The selection is applied by the <em>ordinal number</em> (Teil-OZ):
     * a selected <b>item</b> converts its own data, while the containing
     * sections are kept as empty structure nodes (levels, names, order
     * numbers), so the hierarchical context survives. A selected
     * <b>section</b> converts the whole subtree beneath it (all items and
     * sub-sections). When no selection is given, the document is converted
     * in full (legacy behavior).
     *
     * @param xmlFile GAEB DA XML 3.3 file (any LV data-exchange phase X80-X87)
     * @param selection optional full ordinal numbers of items and/or sections
     *                  to include; {@code null} or empty = convert everything
     * @return RDF model in the conx-gaeb namespace
     */
    public Model convert(File xmlFile, List<String> selection) throws Exception {
        Document doc = parseXml(xmlFile);
        this.model = ModelFactory.createDefaultModel();
        model.setNsPrefix("conx-gaeb", ConxNamespaces.GAEB_BOQ);
        model.setNsPrefix("conx-pct", ConxNamespaces.BOQ_PRICE_COMPONENT_TYPE);
        List<Element> boqElements = findAll(doc.getDocumentElement(), "BoQ");
        if (boqElements.isEmpty()) {
            throw new IllegalArgumentException(
                    "No <BoQ> element found in " + xmlFile.getName()
                            + " (expected GAEB DA XML 3.3 LV phase X80-X87)");
        }
        String currency = resolveCurrency(doc.getDocumentElement());
        for (Element boq : boqElements) {
            if (rootBoq == null) {
                rootBoq = res(iriFactory.boq(boq.getAttribute("ID")));
            }
            convertBoQ(boq, currency, selection);
        }
        return model;
    }

    /**
     * The root {@code BillOfQuantities} entity of the last conversion (the
     * first {@code <BoQ>} element of the input), or {@code null} when no
     * conversion was performed. Used to derive the exchange package
     * envelope ({@code id}, {@code identifier}, {@code semanticModel.type}).
     */
    public Resource getRootBoq() {
        return rootBoq;
    }

    // ------------------------------------------------------------------
    // Position selection tree (for the frontend Bauposten-Auswahl)
    // ------------------------------------------------------------------

    /**
     * Entry of the position selection tree. Sections carry their sub-sections
     * and bill items as children; items are leaves. {@code ordinalNumber} is
     * the full order number (Teil-OZ along the hierarchy), which is what the
     * {@code convert} selection matches on.
     *
     * @param kind           {@code SECTION} or {@code ITEM}
     * @param id             GAEB {@code ID} attribute of the element
     * @param ordinalNumber  full order number (empty for degenerated inputs)
     * @param name           display name (section label or item outline text)
     * @param itemCount      number of bill items in the subtree (items: 1)
     * @param children       child entries (sections only; items: empty)
     */
    public record BoQSelectionEntry(String kind, String id, String ordinalNumber, String name,
                                    long itemCount, List<BoQSelectionEntry> children) {
        public static final String SECTION = "SECTION";
        public static final String ITEM = "ITEM";
    }

    /**
     * Builds the selection tree of the given GAEB document: all sections
     * (all levels) and all bill items, in document order. Used by the
     * frontend to offer a checkbox tree for the Bauposten-Auswahl.
     *
     * @param xmlFile GAEB DA XML 3.3 file (any LV data-exchange phase X80-X87)
     * @return tree entries for the root level (root sections and direct items)
     */
    public List<BoQSelectionEntry> selectionTree(File xmlFile) throws Exception {
        Document doc = parseXml(xmlFile);
        List<Element> boqElements = findAll(doc.getDocumentElement(), "BoQ");
        if (boqElements.isEmpty()) {
            throw new IllegalArgumentException(
                    "No <BoQ> element found in " + xmlFile.getName()
                            + " (expected GAEB DA XML 3.3 LV phase X80-X87)");
        }
        List<BoQSelectionEntry> roots = new ArrayList<>();
        for (Element boq : boqElements) {
            Element body = firstChild(boq, "BoQBody");
            if (body == null) {
                continue;
            }
            for (Element section : childElements(body, "BoQCtgy")) {
                roots.add(selectionSectionEntry(section, ""));
            }
            for (Element item : directItems(body)) {
                roots.add(selectionItemEntry(item, ""));
            }
        }
        return roots;
    }

    private BoQSelectionEntry selectionSectionEntry(Element section, String ozPrefix) {
        String oz = ozPrefix + section.getAttribute("RNoPart");
        List<BoQSelectionEntry> children = new ArrayList<>();
        long itemCount = 0;
        Element body = firstChild(section, "BoQBody");
        if (body != null) {
            for (Element sub : childElements(body, "BoQCtgy")) {
                BoQSelectionEntry subEntry = selectionSectionEntry(sub, oz);
                children.add(subEntry);
                itemCount += subEntry.itemCount();
            }
            for (Element item : directItems(body)) {
                children.add(selectionItemEntry(item, oz));
                itemCount++;
            }
        }
        String name = textOf(firstChild(section, "LblTx"));
        return new BoQSelectionEntry(BoQSelectionEntry.SECTION, section.getAttribute("ID"),
                oz, name != null ? name : section.getAttribute("ID"), itemCount, children);
    }

    private BoQSelectionEntry selectionItemEntry(Element item, String ozPrefix) {
        String name = itemName(item);
        return new BoQSelectionEntry(BoQSelectionEntry.ITEM, item.getAttribute("ID"),
                ozPrefix + item.getAttribute("RNoPart"),
                name != null ? name : item.getAttribute("ID"), 1, List.of());
    }

    /** Display name of an item: outline (short) text, else detail (long) text. */
    private static String itemName(Element item) {
        Element description = firstChild(item, "Description");
        if (description == null) {
            return null;
        }
        Element completeText = firstChild(description, "CompleteText");
        Element outline = completeText != null ? firstChild(completeText, "OutlineText")
                : firstChild(description, "OutlineText");
        if (outline != null) {
            Element outlTxt = firstChild(outline, "OutlTxt");
            String shortText = mlText(outlTxt != null ? outlTxt : outline);
            if (shortText != null) {
                return shortText;
            }
        }
        Element detailTxt = completeText != null ? firstChild(completeText, "DetailTxt") : null;
        return detailTxt != null ? mlText(detailTxt) : null;
    }

    /**
     * Converts the model to a Turtle file.
     *
     * @param model      converted model
     * @param outputFile target .ttl file
     */
    public static void writeTtl(Model model, Path outputFile) throws Exception {
        Files.createDirectories(outputFile.toAbsolutePath().getParent());
        try (OutputStream out = Files.newOutputStream(outputFile)) {
            model.write(out, "TTL");
        }
    }

    /**
     * Serializes the model to a Turtle string.
     *
     * @param model converted model
     * @return Turtle serialization
     */
    public static String toTtlString(Model model) {
        StringWriter sw = new StringWriter();
        model.write(sw, "TTL");
        return sw.toString();
    }

    /**
     * Serializes the model to a JSON-LD string.
     *
     * @param model converted model
     * @return JSON-LD serialization
     */
    public static String toJsonLdString(Model model) {
        StringWriter sw = new StringWriter();
        model.write(sw, "JSON-LD");
        return sw.toString();
    }

    // ------------------------------------------------------------------
    // Jena helpers (Jena 4.x has no ResFactory)
    // ------------------------------------------------------------------

    private Resource res(String iri) {
        return model.createResource(iri);
    }

    private Property prop(String iri) {
        return model.createProperty(iri);
    }

    /**
     * Creates a literal with an explicit {@code xsd:decimal} datatype so the
     * Turtle output always carries the datatype (Jena may otherwise emit
     * plain numeric literals for BigDecimal values).
     */
    private Literal decimalLiteral(String value) {
        return model.createTypedLiteral(value.trim(), XSDDatatype.XSDdecimal);
    }

    // ------------------------------------------------------------------
    // BoQ
    // ------------------------------------------------------------------

    private void convertBoQ(Element boq, String currency) {
        convertBoQ(boq, currency, null);
    }

    private void convertBoQ(Element boq, String currency, List<String> selection) {
        String id = boq.getAttribute("ID");
        Resource boqRes = res(iriFactory.boq(id));
        boqRes.addProperty(prop(ConxNamespaces.RDF_TYPE), res(ConxNamespaces.BILL_OF_QUANTITIES));
        boqRes.addProperty(prop(ConxNamespaces.P_IDENTIFIER), id);
        boqRes.addProperty(prop(ConxNamespaces.P_CURRENCY_CODE), currency);

        Element boqInfo = firstChild(boq, "BoQInfo");
        if (boqInfo != null) {
            String name = textOf(firstChild(boqInfo, "LblBoQ"));
            if (name == null) {
                name = textOf(firstChild(boqInfo, "Name"));
            }
            if (name != null) {
                boqRes.addProperty(prop(ConxNamespaces.P_NAME), name);
            }
        }

        Element boqBody = firstChild(boq, "BoQBody");
        if (boqBody == null) {
            return;
        }

        // Root sections
        for (Element section : childElements(boqBody, "BoQCtgy")) {
            if (!subtreeHasSelection(section, "", selection)) {
                continue;
            }
            Resource sectionRes = convertSection(section, 1, boqRes, true, "", selection);
            boqRes.addProperty(prop(ConxNamespaces.P_HAS_ROOT_SECTION), sectionRes);
        }

        // Direct items (Itemlist directly under BoQBody)
        for (Element item : directItems(boqBody)) {
            if (!isSelected(item.getAttribute("RNoPart"), selection)) {
                continue;
            }
            Resource itemRes = convertItem(item, boqRes, null, "");
            boqRes.addProperty(prop(ConxNamespaces.P_HAS_DIRECT_ITEM), itemRes);
        }
    }

    private String resolveCurrency(Element root) {
        Element prjInfo = firstChild(root, "PrjInfo");
        if (prjInfo != null) {
            Element cur = firstChild(prjInfo, "Cur");
            if (cur != null && !cur.getTextContent().trim().isEmpty()) {
                return cur.getTextContent().trim();
            }
        }
        return defaultCurrency;
    }

    // ------------------------------------------------------------------
    // Sections (BoQCtgy), recursive
    // ------------------------------------------------------------------

    private Resource convertSection(Element section, int level, Resource parent, boolean isRoot, String ozPrefix) {
        return convertSection(section, level, parent, isRoot, ozPrefix, null);
    }

    private Resource convertSection(Element section, int level, Resource parent, boolean isRoot,
                                    String ozPrefix, List<String> selection) {
        String id = section.getAttribute("ID");
        Resource sectionRes = res(iriFactory.section(id));
        sectionRes.addProperty(prop(ConxNamespaces.RDF_TYPE), res(ConxNamespaces.BOQ_SECTION));
        sectionRes.addProperty(prop(ConxNamespaces.P_IDENTIFIER), id);
        sectionRes.addProperty(prop(ConxNamespaces.P_LEVEL_NUMBER),
                model.createTypedLiteral(level, XSDDatatype.XSDpositiveInteger));

        // In GAEB DA XML the RNoPart attribute holds only the *segment* of the
        // order number (Teil-OZ) for this level; the full OZ is the
        // concatenation of the segments along the section hierarchy.
        String rNoPart = section.getAttribute("RNoPart");
        String fullOz = ozPrefix + rNoPart;
        if (!fullOz.isEmpty()) {
            sectionRes.addProperty(prop(ConxNamespaces.P_ORDINAL_NUMBER), fullOz);
        }

        Element lblTx = firstChild(section, "LblTx");
        if (lblTx != null) {
            String name = textOf(lblTx);
            if (name != null) {
                sectionRes.addProperty(prop(ConxNamespaces.P_NAME), name);
            }
        }

        // Containment
        if (isRoot && parent != null) {
            sectionRes.addProperty(prop(ConxNamespaces.P_IS_ROOT_SECTION_OF), parent);
        } else if (parent != null) {
            sectionRes.addProperty(prop(ConxNamespaces.P_IS_SUB_SECTION_OF), parent);
            parent.addProperty(prop(ConxNamespaces.P_HAS_SUB_SECTION), sectionRes);
        }

        // Catalog assignments at section level
        convertCatalogAssignments(section, sectionRes, id);

        // Nested content
        Element body = firstChild(section, "BoQBody");
        if (body == null) {
            return sectionRes;
        }
        for (Element sub : childElements(body, "BoQCtgy")) {
            if (!subtreeHasSelection(sub, fullOz, selection)) {
                continue;
            }
            convertSection(sub, level + 1, sectionRes, false, fullOz, selection);
        }
        for (Element item : directItems(body)) {
            if (!isSelected(fullOz + item.getAttribute("RNoPart"), selection)) {
                continue;
            }
            Resource itemRes = convertItem(item, null, sectionRes, fullOz);
            sectionRes.addProperty(prop(ConxNamespaces.P_HAS_ITEM), itemRes);
        }
        return sectionRes;
    }

    // ------------------------------------------------------------------
    // Position selection (Bauposten-Auswahl)
    // ------------------------------------------------------------------

    private static boolean hasSelection(List<String> selection) {
        return selection != null && !selection.isEmpty();
    }

    /**
     * Whether the entry with the given full ordinal number is included in the
     * selection: exact match or a selected ordinal number that is a prefix of
     * it (a selected section includes everything beneath it). Without a
     * selection everything is included.
     */
    private static boolean isSelected(String fullOz, List<String> selection) {
        if (!hasSelection(selection) || fullOz == null || fullOz.isEmpty()) {
            return true;
        }
        for (String selected : selection) {
            if (fullOz.equals(selected) || fullOz.startsWith(selected)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the subtree of the given section (the section itself included)
     * contains a selected entry. {@code ozPrefix} is the order-number prefix
     * of the section's parent (empty at the BoQ level).
     */
    private static boolean subtreeHasSelection(Element section, String ozPrefix, List<String> selection) {
        if (!hasSelection(selection)) {
            return true;
        }
        String oz = ozPrefix + section.getAttribute("RNoPart");
        Element body = firstChild(section, "BoQBody");
        if (body == null) {
            return false;
        }
        for (Element sub : childElements(body, "BoQCtgy")) {
            if (subtreeHasSelection(sub, oz, selection)) {
                return true;
            }
        }
        for (Element item : directItems(body)) {
            if (isSelected(oz + item.getAttribute("RNoPart"), selection)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Items
    // ------------------------------------------------------------------

    private Resource convertItem(Element item, Resource boq, Resource section, String ozPrefix) {
        String id = item.getAttribute("ID");
        Resource itemRes = res(iriFactory.item(id));
        itemRes.addProperty(prop(ConxNamespaces.RDF_TYPE), res(ConxNamespaces.BOQ_ITEM));
        itemRes.addProperty(prop(ConxNamespaces.P_IDENTIFIER), id);

        // RNoPart is the item's own OZ segment; the full OZ includes the
        // segments of all containing sections.
        String rNoPart = item.getAttribute("RNoPart");
        String fullOz = ozPrefix + rNoPart;
        if (!fullOz.isEmpty()) {
            itemRes.addProperty(prop(ConxNamespaces.P_ORDINAL_NUMBER), fullOz);
        }
        String rNoIndex = item.getAttribute("RNoIndex");
        if (!rNoIndex.isEmpty()) {
            itemRes.addProperty(prop(ConxNamespaces.P_ORDINAL_INDEX), rNoIndex);
        }
        Integer counter = positionCounter(rNoPart);
        if (counter != null) {
            itemRes.addProperty(prop(ConxNamespaces.P_POSITION_COUNTER),
                    model.createTypedLiteral(counter, XSDDatatype.XSDnonNegativeInteger));
        }

        // Containment
        if (section != null) {
            itemRes.addProperty(prop(ConxNamespaces.P_BELONGS_TO_SECTION), section);
        } else if (boq != null) {
            itemRes.addProperty(prop(ConxNamespaces.P_IS_DIRECT_ITEM_OF), boq);
        }

        // Quantity and unit
        Element qty = firstChild(item, "Qty");
        BigDecimal quantity = qty != null ? decimal(qty.getTextContent()) : null;
        if (quantity != null) {
            itemRes.addProperty(prop(ConxNamespaces.P_QUANTITY), decimalLiteral(qty.getTextContent()));
        }
        Element qu = firstChild(item, "QU");
        if (qu != null) {
            itemRes.addProperty(prop(ConxNamespaces.P_UNIT_CODE), qu.getTextContent().trim());
        }

        // Prices
        Element up = firstChild(item, "UP");
        if (up != null) {
            itemRes.addProperty(prop(ConxNamespaces.P_UNIT_PRICE), decimalLiteral(up.getTextContent()));
        }
        Element it = firstChild(item, "IT");
        if (it != null) {
            itemRes.addProperty(prop(ConxNamespaces.P_TOTAL_AMOUNT), decimalLiteral(it.getTextContent()));
        }

        // Texts. In GAEB DA XML the long text lives in
        // Description/CompleteText/DetailTxt and the short text in
        // Description/CompleteText/OutlineText/OutlTxt (both are MLText,
        // i.e. their content is in p/div/span children, not xml:lang).
        Element description = firstChild(item, "Description");
        if (description != null) {
            Element completeText = firstChild(description, "CompleteText");
            Element outline = completeText != null ? firstChild(completeText, "OutlineText")
                    : firstChild(description, "OutlineText");
            if (outline != null) {
                Element outlTxt = firstChild(outline, "OutlTxt");
                String shortText = mlText(outlTxt != null ? outlTxt : outline);
                if (shortText != null) {
                    itemRes.addProperty(prop(ConxNamespaces.P_SHORT_TEXT), shortText);
                }
            }
            Element detailTxt = completeText != null ? firstChild(completeText, "DetailTxt") : null;
            if (detailTxt != null) {
                String longText = mlText(detailTxt);
                if (longText != null) {
                    itemRes.addProperty(prop(ConxNamespaces.P_LONG_TEXT),
                            model.createLiteral(longText, "de"));
                }
            }
        }

        // Quantity splits
        List<Element> splits = childElements(item, "QtySplit");
        if (!splits.isEmpty() && quantity != null) {
            BigDecimal sum = BigDecimal.ZERO;
            for (int i = 0; i < splits.size(); i++) {
                Element split = splits.get(i);
                Resource splitRes = convertQuantitySplit(split, itemRes, i, quantity);
                itemRes.addProperty(prop(ConxNamespaces.P_HAS_QUANTITY_SPLIT), splitRes);
                sum = sum.add(splitQuantity(split, quantity));
            }
            itemRes.addProperty(prop(ConxNamespaces.P_SPLITS_ARE_EXHAUSTIVE),
                    model.createTypedLiteral(quantity.compareTo(sum) == 0));
        }

        // Unit price components (UPComp1..6)
        for (int i = 1; i <= 6; i++) {
            Element upc = firstChild(item, "UPComp" + i);
            if (upc != null) {
                Resource upcRes = res(iriFactory.unitPriceComponent(id, i));
                upcRes.addProperty(prop(ConxNamespaces.RDF_TYPE), res(ConxNamespaces.UNIT_PRICE_COMPONENT));
                upcRes.addProperty(prop(ConxNamespaces.P_IDENTIFIER), id + "-UPC" + i);
                upcRes.addProperty(prop(ConxNamespaces.P_NAME), UPCOMP_LABELS[i - 1]);
                upcRes.addProperty(prop(ConxNamespaces.P_AMOUNT), decimalLiteral(upc.getTextContent()));
                upcRes.addProperty(prop(ConxNamespaces.P_HAS_PRICE_COMPONENT_TYPE), res(UPCOMP_TYPES[i - 1]));
                upcRes.addProperty(prop(ConxNamespaces.P_IS_UNIT_PRICE_COMPONENT_OF), itemRes);
                itemRes.addProperty(prop(ConxNamespaces.P_HAS_UNIT_PRICE_COMPONENT), upcRes);
            }
        }

        // Catalog assignments
        convertCatalogAssignments(item, itemRes, id);

        return itemRes;
    }

    // ------------------------------------------------------------------
    // Quantity splits
    // ------------------------------------------------------------------

    private Resource convertQuantitySplit(Element split, Resource itemRes, int index, BigDecimal itemQuantity) {
        String itemId = itemRes.getProperty(prop(ConxNamespaces.P_IDENTIFIER)).getString();
        Resource splitRes = res(iriFactory.quantitySplit(itemId, index + 1));
        splitRes.addProperty(prop(ConxNamespaces.RDF_TYPE), res(ConxNamespaces.QUANTITY_SPLIT));
        splitRes.addProperty(prop(ConxNamespaces.P_IDENTIFIER), itemId + "-SPLIT-" + (index + 1));
        splitRes.addProperty(prop(ConxNamespaces.P_IS_QUANTITY_SPLIT_OF), itemRes);

        BigDecimal q = splitQuantity(split, itemQuantity);
        splitRes.addProperty(prop(ConxNamespaces.P_QUANTITY),
                decimalLiteral(q.toPlainString()));

        convertCatalogAssignments(split, splitRes, itemId + "-SPLIT-" + (index + 1));
        return splitRes;
    }

    private BigDecimal splitQuantity(Element split, BigDecimal itemQuantity) {
        Element qty = firstChild(split, "Qty");
        if (qty != null) {
            return decimal(qty.getTextContent());
        }
        Element pct = firstChild(split, "QtyPcnt");
        if (pct != null) {
            return itemQuantity.multiply(decimal(pct.getTextContent()))
                    .divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    // ------------------------------------------------------------------
    // Catalog assignments
    // ------------------------------------------------------------------

    private void convertCatalogAssignments(Element owner, Resource ownerRes, String ownerId) {
        List<Element> assigns = childElements(owner, "CtlgAssign");
        for (int i = 0; i < assigns.size(); i++) {
            Element assign = assigns.get(i);
            Element ctlgId = firstChild(assign, "CtlgID");
            Element ctlgCode = firstChild(assign, "CtlgCode");
            if (ctlgId == null || ctlgCode == null) {
                continue;
            }
            String catalogId = ctlgId.getTextContent().trim();
            String catalogCode = ctlgCode.getTextContent().trim();

            Resource assignRes = res(iriFactory.catalogAssignment(ownerId, i + 1));
            assignRes.addProperty(prop(ConxNamespaces.RDF_TYPE), res(ConxNamespaces.CATALOG_ASSIGNMENT));
            assignRes.addProperty(prop(ConxNamespaces.P_IDENTIFIER), ownerId + "-CA-" + (i + 1));
            assignRes.addProperty(prop(ConxNamespaces.P_CATALOG_IDENTIFIER), catalogId);
            assignRes.addProperty(prop(ConxNamespaces.P_IS_ASSIGNMENT_OF), ownerRes);
            ownerRes.addProperty(prop(ConxNamespaces.P_HAS_CATALOG_ASSIGNMENT), assignRes);

            Resource extRef = res(iriFactory.externalObjectReference(catalogId, catalogCode));
            extRef.addProperty(prop(ConxNamespaces.RDF_TYPE), res(ConxNamespaces.EXTERNAL_OBJECT_REFERENCE));
            extRef.addProperty(prop(ConxNamespaces.P_IDENTIFIER), catalogId + ":" + catalogCode);
            extRef.addProperty(prop(ConxNamespaces.P_CATALOG_IDENTIFIER), catalogId);
            extRef.addProperty(prop(ConxNamespaces.P_EXTERNAL_IDENTIFIER), catalogCode);
            extRef.addProperty(prop(ConxNamespaces.P_SOURCE_SYSTEM), "GAEB");
            extRef.addProperty(prop(ConxNamespaces.P_OBJECT_TYPE), "catalog-item");

            assignRes.addProperty(prop(ConxNamespaces.P_REFERENCES_EXTERNAL_OBJECT), extRef);
        }
    }

    // ------------------------------------------------------------------
    // XML helpers
    // ------------------------------------------------------------------

    private static Document parseXml(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setAttribute(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        try {
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (Exception ignored) {
            // Not supported by all parsers; secure processing is already on.
        }
        DocumentBuilder builder = factory.newDocumentBuilder();
        try (InputStream in = new FileInputStream(file)) {
            return builder.parse(new InputSource(in));
        }
    }

    /** All direct children with the given local name (namespace-agnostic). */
    private static List<Element> childElements(Element parent, String localName) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element && localName.equals(node.getLocalName())) {
                result.add((Element) node);
            }
        }
        return result;
    }

    /** All descendants (any depth) with the given local name (namespace-agnostic). */
    private static List<Element> findAll(Element root, String localName) {
        List<Element> result = new ArrayList<>();
        collectAll(root, localName, result);
        return result;
    }

    private static void collectAll(Element element, String localName, List<Element> result) {
        for (Element child : childElements(element, localName)) {
            result.add(child);
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element) {
                collectAll((Element) node, localName, result);
            }
        }
    }

    private static Element firstChild(Element parent, String localName) {
        List<Element> children = childElements(parent, localName);
        return children.isEmpty() ? null : children.get(0);
    }

    /**
     * Items directly contained in a BoQBody: either directly as children or
     * wrapped in an {@code Itemlist} element.
     */
    private static List<Element> directItems(Element boqBody) {
        List<Element> items = childElements(boqBody, "Item");
        if (!items.isEmpty()) {
            return items;
        }
        Element itemlist = firstChild(boqBody, "Itemlist");
        return itemlist != null ? childElements(itemlist, "Item") : items;
    }

    private static String textOf(Element element) {
        if (element == null) {
            return null;
        }
        String text = element.getTextContent();
        return text == null || text.trim().isEmpty() ? null : text.trim();
    }

    /**
     * Extracts the plain text of a GAEB MLText element. The content is
     * carried by {@code p}/{@code div}/{@code span} children; paragraphs are
     * joined with a line break. Falls back to the raw text content.
     */
    private static String mlText(Element element) {
        if (element == null) {
            return null;
        }
        List<Element> blocks = new ArrayList<>();
        blocks.addAll(childElements(element, "p"));
        blocks.addAll(childElements(element, "div"));
        if (blocks.isEmpty()) {
            blocks.addAll(childElements(element, "span"));
        }
        if (blocks.isEmpty()) {
            return textOf(element);
        }
        StringBuilder sb = new StringBuilder();
        for (Element block : blocks) {
            String t = block.getTextContent();
            if (t == null) {
                continue;
            }
            t = t.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(t);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value.trim());
    }

    /**
     * Extracts the numeric position counter from a GAEB Teil-OZ such as
     * {@code "01.001"} -&gt; {@code 1}. Returns null if not parseable.
     */
    private static Integer positionCounter(String rNoPart) {
        if (rNoPart == null || rNoPart.isEmpty()) {
            return null;
        }
        String[] parts = rNoPart.split("[.\\-]");
        for (int i = parts.length - 1; i >= 0; i--) {
            String part = parts[i].trim();
            if (!part.isEmpty() && part.chars().allMatch(Character::isDigit)) {
                try {
                    return Integer.parseInt(part);
                } catch (NumberFormatException ignored) {
                    // Too large for int; try next part.
                }
            }
        }
        return null;
    }
}
