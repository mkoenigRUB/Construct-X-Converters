# GAEB BoQ Ontologie (Draft)

**Namespace:** `https://www.construct-x.org/ontology/gaeb-boq#`  
**Version:** `0.5.0 (Draft)`  
**Link:** `gaeb-boq.ttl`

Die Ontologie beschreibt ein konkretes **Leistungsverzeichnis (Bill of Quantities)** mit LV-Bereichen, Leistungspositionen, Mengen, Mengensplits und Preisangaben.

---

## Klassen

| Klasse | Deutsch | Fachliche Beschreibung |
|---|---|---|
| `BillOfQuantities` | Leistungsverzeichnis | Konkretes Leistungsverzeichnis eines Projekts. |
| `BoQSection` | LV-Bereich | Hierarchischer Gliederungsbereich eines Leistungsverzeichnisses. |
| `BoQItem` | Leistungsposition | Konkrete Teilleistung mit Menge, Einheit und optionalen Preisangaben. |
| `QuantitySplit` | Mengensplit | Abgegrenzte Teilmenge einer Leistungsposition. |
| `UnitPriceComponent` | Einheitspreisanteil | Bestandteil des Einheitspreises einer Leistungsposition. |
| `CatalogAssignment` | Katalogzuordnung | Zuordnung einer Position oder eines Mengensplits zu einer externen Referenz. |
| `ExternalObjectReference` | Externe Objektreferenz | Generische Referenz auf ein Objekt in einem externen System oder Modell. |

---

## Eigenschaften

### BillOfQuantities

| Property | Wertebereich | Beschreibung |
|---|---|---|
| `identifier` | `xsd:string` | Fachliche Kennung des Leistungsverzeichnisses. |
| `name` | `xsd:string` | Bezeichnung des Leistungsverzeichnisses. |
| `currencyCode` | `xsd:string` | Währung des Leistungsverzeichnisses, z. B. `EUR`. |
| `hasRootSection` | `BoQSection` | Ordnet dem Leistungsverzeichnis einen obersten LV-Bereich zu. |
| `hasDirectItem` | `BoQItem` | Ordnet dem Leistungsverzeichnis eine direkte Position zu. |

### BoQSection

| Property | Wertebereich | Beschreibung |
|---|---|---|
| `identifier` | `xsd:string` | Fachliche Kennung des LV-Bereichs. |
| `name` | `xsd:string` | Bezeichnung des LV-Bereichs. |
| `ordinalNumber` | `xsd:string` | Ordnungszahl des LV-Bereichs. |
| `hierarchyLevelName` | `xsd:string` | Bezeichnung der Hierarchieebene. |
| `levelNumber` | `xsd:positiveInteger` | Nummer der Hierarchieebene. |
| `isRootSectionOf` | `BillOfQuantities` | Leistungsverzeichnis, zu dem der oberste LV-Bereich gehört. |
| `isSubSectionOf` | `BoQSection` | Direkter übergeordneter LV-Bereich. |
| `hasSubSection` | `BoQSection` | Direkter untergeordneter LV-Bereich. |
| `hasItem` | `BoQItem` | Enthaltene Leistungsposition. |

### BoQItem

| Property | Wertebereich | Beschreibung |
|---|---|---|
| `identifier` | `xsd:string` | Fachliche Kennung der Leistungsposition. |
| `name` | `xsd:string` | Bezeichnung der Leistungsposition. |
| `ordinalNumber` | `xsd:string` | Ordnungszahl der Position. |
| `positionCounter` | `xsd:nonNegativeInteger` | Positionszähler. |
| `ordinalIndex` | `xsd:string` | OZ-Index. |
| `shortText` | `xsd:string` | Kurztext der Position. |
| `longText` | `rdf:langString` | Langtext der Position. |
| `quantity` | `xsd:decimal` | Positionsmenge. |
| `unitCode` | `xsd:string` | Mengeneinheit. |
| `unitPrice` | `xsd:decimal` | Einheitspreis der Position. |
| `totalAmount` | `xsd:decimal` | Gesamtbetrag der Position. |
| `splitsAreExhaustive` | `xsd:boolean` | Kennzeichnet, dass die Mengensplits die gesamte Positionsmenge abdecken. |
| `belongsToSection` | `BoQSection` | Direkter LV-Bereich der Position. |
| `isDirectItemOf` | `BillOfQuantities` | Leistungsverzeichnis bei direkter Einordnung ohne LV-Bereich. |
| `hasQuantitySplit` | `QuantitySplit` | Ordnet der Position einen Mengensplit zu. |
| `hasUnitPriceComponent` | `UnitPriceComponent` | Ordnet der Position einen Einheitspreisanteil zu. |
| `hasCatalogAssignment` | `CatalogAssignment` | Ordnet der Position eine externe Referenzzuordnung zu. |

Für den Gesamtbetrag gilt fachlich:

```text
totalAmount ≈ quantity × unitPrice
```

### QuantitySplit

| Property | Wertebereich | Beschreibung |
|---|---|---|
| `identifier` | `xsd:string` | Fachliche Kennung des Mengensplits. |
| `name` | `xsd:string` | Bezeichnung des Mengensplits. |
| `quantity` | `xsd:decimal` | Teilmenge der Leistungsposition. |
| `unitCode` | `xsd:string` | Mengeneinheit. |
| `isQuantitySplitOf` | `BoQItem` | Leistungsposition, zu der der Mengensplit gehört. |
| `hasCatalogAssignment` | `CatalogAssignment` | Externe Referenzzuordnung des Mengensplits. |

### UnitPriceComponent

| Property | Wertebereich | Beschreibung |
|---|---|---|
| `identifier` | `xsd:string` | Fachliche Kennung des Einheitspreisanteils. |
| `name` | `xsd:string` | Bezeichnung des Einheitspreisanteils. |
| `amount` | `xsd:decimal` | Betrag des Einheitspreisanteils. |
| `hasPriceComponentType` | `skos:Concept` | Typ des Einheitspreisanteils. |
| `isUnitPriceComponentOf` | `BoQItem` | Leistungsposition, zu der der Einheitspreisanteil gehört. |

Für Einheitspreisanteile gilt:

```text
Summe(UnitPriceComponent.amount) = BoQItem.unitPrice
```

### CatalogAssignment

| Property | Wertebereich | Beschreibung |
|---|---|---|
| `identifier` | `xsd:string` | Fachliche Kennung der Zuordnung. |
| `name` | `xsd:string` | Bezeichnung der Zuordnung. |
| `catalogIdentifier` | `xsd:string` | Kennung des verwendeten Katalogs bzw. Referenzschemas. |
| `isAssignmentOf` | `CatalogAssignableEntity` | Position oder Mengensplit, zu dem die Zuordnung gehört. |
| `referencesExternalObject` | `ExternalObjectReference` | Referenz auf das externe Objekt. |

### ExternalObjectReference

| Property | Wertebereich | Beschreibung |
|---|---|---|
| `identifier` | `xsd:string` | Fachliche Kennung der Referenzressource. |
| `name` | `xsd:string` | Bezeichnung der Referenzressource. |
| `externalIdentifier` | `xsd:string` | Objektkennung im externen System. |
| `sourceSystem` | `xsd:string` | Quellsystem oder Modell. |
| `objectType` | `xsd:string` | Typ des extern referenzierten Objekts. |

---

## Beziehungen innerhalb der Ontologie

| Quelle | Beziehung | Ziel | Bedeutung |
|---|---|---|---|
| `BillOfQuantities` | `hasRootSection` | `BoQSection` | Leistungsverzeichnis enthält obersten LV-Bereich |
| `BoQSection` | `hasSubSection` | `BoQSection` | LV-Bereich enthält Unterbereich |
| `BoQSection` | `hasItem` | `BoQItem` | LV-Bereich enthält Leistungsposition |
| `BillOfQuantities` | `hasDirectItem` | `BoQItem` | Leistungsverzeichnis enthält direkte Position |
| `BoQItem` | `hasQuantitySplit` | `QuantitySplit` | Position besitzt Mengensplit |
| `BoQItem` | `hasUnitPriceComponent` | `UnitPriceComponent` | Position besitzt Einheitspreisanteil |
| `UnitPriceComponent` | `hasPriceComponentType` | `skos:Concept` | Typ des Einheitspreisanteils |
| `CatalogAssignableEntity` | `hasCatalogAssignment` | `CatalogAssignment` | Position oder Mengensplit besitzt externe Zuordnung |
| `CatalogAssignment` | `referencesExternalObject` | `ExternalObjectReference` | Zuordnung referenziert externes Objekt |

---

## Externe Verknüpfungen

Verbindungen zwischen verschiedenen Construct-X-Fachontologien werden immer in einer separaten Link-Ontologie modelliert.

Für die Verbindung zwischen Schedule und BoQ wird verwendet:

`https://www.construct-x.org/ontology/schedule-boq-link#`

| Quelle | Property | Ziel | Fachliche Bedeutung |
|---|---|---|---|
| `ConstructionSchedule` | `usesBillOfQuantities` | `BillOfQuantities` | Leistungsverzeichnis, auf das sich der Terminplan bezieht |
| `QuantitySplit` | `isAssignedToActivity` | `Activity` | Vorgang, dem die Teilmenge zugeordnet ist |

Damit gilt:

```text
ConstructionSchedule ── usesBillOfQuantities ──► BillOfQuantities
QuantitySplit ── isAssignedToActivity ─────────► Activity
```

Diese Properties sind **nicht Bestandteil der GAEB-BoQ-Ontologie**.

---

## Vokabulare

Die Core-Ontologie legt nur fest, dass `hasPriceComponentType` auf `skos:Concept` verweist.

Die konkret zulässigen Werte werden über ein separates Vokabular definiert.

### BoQ Price Component Type

**Typ:** `skos:ConceptScheme`

**Namespace:**

`https://www.construct-x.org/vocab/boq-price-component-type#`

Das **BoQ Price Component Type** ist ein kontrolliertes SKOS-Vokabular. Das Vokabular selbst ist ein `skos:ConceptScheme`. Die einzelnen zulässigen Werte sind `skos:Concept`-Instanzen.

| Vocabulary Term | RDF-Typ | Deutsch |
|---|---|---|
| `Labor` | `skos:Concept` | Löhne |
| `Materials` | `skos:Concept` | Stoffe |
| `Equipment` | `skos:Concept` | Geräte |
| `Other` | `skos:Concept` | Sonstiges |
| `Unknown` | `skos:Concept` | Unbekannt |

Die Property

```text
UnitPriceComponent --hasPriceComponentType--> skos:Concept
```

verweist auf eines dieser Konzepte. Die konkreten zulässigen Werte werden über das separate Vokabular und das zugehörige optionale SHACL-Profil eingeschränkt.

Damit gilt:

```text
BoQ Price Component Type
        a skos:ConceptScheme
        │
        ├── Labor       a skos:Concept
        ├── Materials   a skos:Concept
        ├── Equipment   a skos:Concept
        ├── Other       a skos:Concept
        └── Unknown     a skos:Concept
```

Die Begriffe sind bewusst **SKOS-Konzepte und keine OWL-Klassen**.

---

## Versionierung

Ein `BillOfQuantities` wird als Hauptartefakt versioniert.

Dabei werden die dauerhafte fachliche Identität des Leistungsverzeichnisses und seine einzelnen unveränderlichen Stände getrennt.

```text
stabile Identität
.../boq/BOQ-01
        │
        ├── version/001
        ├── version/002
        └── version/003
```

Die stabile IRI bezeichnet das Leistungsverzeichnis unabhängig von einem bestimmten Bearbeitungsstand.

Die Versions-IRI bezeichnet einen konkreten Snapshot:

```text
https://data.example.com/project/alpha/boq/BOQ-01/version/002
```

### Verwendete Standardmetadaten

| Property | Bedeutung |
|---|---|
| `dcterms:hasVersion` | stabile Identität → Version |
| `dcterms:isVersionOf` | Version → stabile Identität |
| `prov:wasRevisionOf` | unmittelbar vorhergehende Version |
| `prov:generatedAtTime` | Zeitpunkt der Erzeugung |
| `dcterms:issued` | fachliches Ausgabedatum |

---

## Grundprinzip

```text
GAEB BoQ Ontologie
        │
        ├─ Was gehört zum Leistungsverzeichnis?
        │      BillOfQuantities
        │      BoQSection
        │      BoQItem
        │      QuantitySplit
        │      UnitPriceComponent
        │
        ├─ Welche kontrollierten Werte?
        │      BoQ Price Component Type
        │
        └─ Welche externen Bezüge?
               ausschließlich über Link-Ontologien
               ├─ ConstructionSchedule → BillOfQuantities
               └─ QuantitySplit → Activity
```

Damit bleiben Leistungsverzeichnis, Vokabulare, externe Beziehungen und Versionierung sauber voneinander getrennt.

---

## Ontologievisualisierung

![GAEB BoQ Ontologie – T-Box und A-Box](boq-ontology-visualization-skos.png)
