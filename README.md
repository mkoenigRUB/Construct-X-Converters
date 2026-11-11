# Construct-X Converters

Konverter-Dienste von Domänen-Dateiformaten in **Construct-X** Ontologie-Daten (TTL, JSON-LD, Plain JSON) — Java 25 / Spring Boot 3.5 / Apache Jena 4.10, Betrieb per Docker Compose. Die Ontologien selbst liegen im Repository **[`mkoenigRUB/Construct-X-Ontologies`](https://github.com/mkoenigRUB/Construct-X-Ontologies)** — lokal als gepinntes Git-Submodul unter `ontologies/` (Tests), in den Docker-Images aus dem getagten Tarball (`v0.5.0`).

## Services & Ports

| Service | Port | Aufgabe |
|---|---|---|
| `gaeb-converter-service` (`Converter/`) | 8081 | GAEB DA XML 3.3 (X80–X87) → `gaeb-boq` TTL/JSON, inkl. XSD-Validierung gegen offizielle GAEB-Schemas und Reverse-Converter (TTL → GAEB XML) |
| `rdf-converter-service/` | 8082 | Turtle ↔ JSON-LD Konversion, SHACL-Validierung gegen lokale Construct-X SHACL-Profile |
| `msproject-converter-service/` | 8085 | MS Project XML (2016–2024) → `construction-schedule` TTL/JSON |
| `converter-frontend/` (nginx) | 8084 | Tab-UI für GAEB (8081) + MS Project (8085), Health-Indikatoren, XML-/TTL-Preview |
| `rdf-converter-frontend/` (nginx) | 8080 | UI für RDF-Konverter/Validator; proxyt `/api/` auf den rdf-converter-service |

Alle fünf laufen als Container; die Frontends sind nginx-Container (konverter-frontend: Bind-Mount, rdf-converter-frontend: eigenes Image).

## Betrieb

```powershell
# Alles starten (Bilder bauen + Container up)
docker compose -f converter-compose.yml up -d --build
docker compose -f rdf-converter-compose.yml up -d --build

# Status / Logs
docker compose -f converter-compose.yml ps
docker logs construct-x-converters-gaeb-converter-service-1
```

- `converter-compose.yml` — GAEB (8081), MS Project (8085), converter-frontend (8084)
- `rdf-converter-compose.yml` — rdf-converter-service (8082, auch per Host-Port exponiert), rdf-converter-frontend (8080)
- Die Ontologien werden beim Build aus dem getagten Ontologie-Tarball in das Image gebacken (`CONX_ONTOLOGY_ROOT=/app/ontologies`); der GAEB-Service benötigt keine Ontologie-Dateien zur Laufzeit (nur eingebettete GAEB-XSDs).
- Repo klonen: `git clone --recurse-submodules https://github.com/mkoenigRUB/Construct-X-Converters.git` (bzw. bei bestehendem Klon: `git submodule update --init ontologies`) — sonst fehlen `mvn test` die Ontologie-Dateien.

## REST-Interface (alle drei Konverter)

- `POST /api/v1/convert/gaeb-boq` · `POST /api/v1/convert/msproject` · `POST /api/v1/convert` (RDF)
- `POST /api/v1/validate/gaeb-boq` · `POST /api/v1/validate/msproject` (SHACL) · `POST /api/v1/validate` (RDF)
- `POST /api/v1/reverse/gaeb-boq` · `POST /api/v1/reverse/msproject` (RDF → Fachformat, + `strict`)
- `POST /api/v1/positions/gaeb-boq` (GAEB: Positionsauswahl-Baum, s. u.)
- `GET /api/v1/converters` · `GET /api/v1/health`
- MS Project zusätzlich: `GET /api/v1/examples/msproject.xml`

Ausgabeformate: `ttl` (Default), `json` (JSON-LD), `plainjson` (siehe unten).

### Bauposten-Auswahl (GAEB, as of 2026-11-08)

Selektive Konvertierung einzelner LV-Positionen statt der kompletten LV:

- `POST /api/v1/positions/gaeb-boq` — liefert den **Auswahlbaum** (alle `BoQCtgy`-Posten aller Ebenen + alle `Item`-Positionen, in Dokumentreihenfolge) als JSON-Array `{kind: SECTION|ITEM, id, ordinalNumber, name, itemCount, children: [...]}`. `ordinalNumber` ist die volle OZ (Teil-OZ entlang der Hierarchie).
- `POST /api/v1/convert/gaeb-boq` + Parameter `selection` (komma-getrennte `ordinalNumber`s): konvertiert nur die gewählten Einträge. **Posten** (SECTION) fangen ihren ganzen Subtree; **Positionen** (ITEM) werden einzeln aufgenommen. Enthalten `BoQCtgy` bleiben als leere Strukturknoten (Hierarchie/OZ/Name), Posten ohne gewählten Nachfahren werden weggelassen. Ohne/leere `selection` = komplette LV (Legacy-Verhalten).
- Frontend `converter-frontend` (8084, GAEB-Tab): Checkbox-Baum „Bauposten-Auswahl" nach Upload/Beispiel-Load, mit „Alle" / „Alle abwählen"-Buttons und Auswahl-Zähler; die gesetzten Haken werden als `selection` an den Convert-Endpoint übergeben.
- CLI (`GaebConverterCli`): optionales 5. Argument = `selection` (gleiche Semantik).

### Strikt-Modus (Reverse-Konverter, as of 2026-11-08)

Beide Reverse-Konverter (RDF → GAEB / RDF → MS Project) laufen standardmäßig **pragmatisch** (Konvertierung läuft immer, verlustige Schritte → Warnliste). Mit `strict=true` (API-Parameter `strict` / `Convert(file, format, strict)`) werden **Warnungen mit Datenverlust** zu einem Abbruch:

- **Strikt** (Abbruch mit HTTP 400, `error: "Conversion aborted"`, `detail` = Liste): übersprungene/unabbildbare Einträge (fehlende OZ, fehlende Qty, unmappbare UPComp-Slots, fehlende Beziehungsenden, unbekannte Status, Platzhalter-Namen …).
- **Getolert** (bleibt Warnung): verlustige **Approximationen**, bei denen nichts verloren geht (Prozentsplits → absolute Werte, `InProgress` → 50 %, synthetisierter GAEB-Header, Level-Mismatch → Nesting).
- Gemeinsame Klassifikation: `org.constructx.sdk.ConxStrict` (Keyword-basiert, konservative Default). Frontend: Checkbox „Strikt-Modus" in beiden Reverse-Bereichen (8084); Abbruch wird als Fehlerliste gerendert.

## Plain-JSON-Format v2 (alle Converter, Stand 2026-11-01)

Alle drei Converter unterstützen das `plainjson`-Format, **abgestimmt auf die Construct-X Exchange-Package-Konvention** (Formatversion 2, `meta.format = construct-x-plain-json`, `meta.version = 2`); Referenzbeispiel: `examples/construct-x-doppelboden-beispieldaten` im Ontologie-Repository.

- **Node list**: `{"meta": {"format", "version", "tripleCount"}, "nodes": [...]}` — verlustfreie 1:1-Serrialisierung des RDF-Modells; keine RDF/JSON-LD-Semantik (kein `@id`, `@context` o. ä.). Plain JSON ist **nur Ausgabetarget** (RDF-Service lehnt es als Eingabe ab).
- **CURIE-Keys/-Werte**: Präfixregister `ConxCurieRegister` (spiegelt die `vann:preferredNamespacePrefix`-Deklarationen aller Construct-X Ontologien plus `xsd`, `rdf`, `rdfs`, `owl`, `dcterms`, `dcat`, `prov`, `sh`, `vann`, QUDT 2.1 `qudt`/`unit`/`qk`/`sys`). Nicht registrierte Namespaces bleiben als volle IRI erhalten.
- **Value-Encoding** (verlustfrei): Literal → String; language-tagged → `{"value", "language"}`; typed → `{"value", "datatype": "xsd:decimal"}`; `xsd:boolean` → natives `true`/`false`; Resource → CURIE oder volle IRI; Blank Node → `_:label`.
- **`type`**: String bei einem Typ, Array bei mehreren.

**Exchange-Package-Envelope** (optional, `envelope=true`): wraps the node list as `{"id", "identifier", "semanticModel": {"type", "conformsTo"}, "content": {"entities": [...]}}`. Für GAEB und MS Project stammt der Envelope-Header aus der Wurzel-Entität (BoQ / ConstructionSchedule: IRI, `identifier`, `rdf:type`); `conformsTo` trägt die versionierten Ontologie-/Shape-IRIs, die der Converter emittiert (`GaebBoqConverter.CONFORMS_TO` / `MsProjectConverter.CONFORMS_TO`). Beim RDF-Service wird der erste (sortierte) Node zur Paketwurzel und `conformsTo` sammelt die referenzierten versionierten `construct-x.org`-IRIs (best effort). Die Envelope macht das Converter-Output direkt kompatibel mit der Exchange-Struktur des doppelboden-Beispiels; der Notification-Anteil bleibt außerhalb des Converter-Scope.

API: `format=plainjson` (Aliase `plain`, `plain-json`, `application/json`) an allen drei Convert-Endpoints; `envelope` ist nur für `plainjson` relevant (Default `false`). `json`/`jsonld` bleibt überall JSON-LD.

## Module

### `Converter/` — GAEB (Multi-Module: `core` + `web`)

- `core` — Bibliothek: XSD-Validierung, XML→TTL (inkl. **Bauposten-Auswahl** `convert(file, selection)` + `selectionTree(file)`), IRI-Konventionen, Reverse-Converter (TTL → validiertes GAEB XML, inkl. **Strikt-Modus**), CLI (5. Argument = selection).
- `web` — Spring-Boot-REST-Service (API nur; die Upload-UI liegt in `converter-frontend/` auf 8084 — die früher eingebettete statische Seite wurde 2026-11 entfernt).
- Tests: `mvn -q -o test` (offline) im Modul-Verzeichnis; Verifikation zuletzt 2026-11-08 → **core 26/26, web 22/22** (JDK 25.0.5, Maven 3.9.4; byte-buddy-agent-Pfad per `-Dbyte-buddy-agent.jar` mit 8.3-Kurzpfad übergeben, da der Benutzerpfad ein Leerzeichen enthält).

### `rdf-converter-service/`

Eigenständiger Konverter/Validator (Turtle ↔ JSON-LD, SHACL gegen lokale Profile; Plain-JSON-Output). `rdf-converter-frontend/` ist die separat deploybare Web-UI (nginx + Custom-Image, proxyt `/api/` auf `rdf-converter-service:8082`).

### `msproject-converter-service/`

- Tasks → `conx-cs:Activity` (Zero-Duration/Milestone → `conx-cs:Milestone`), `OutlineCode` → `hierarchyCode`, `PercentComplete` → `conx-status`.
- `PredecessorLink` → `conx-cs:PrecedenceRelationship` (`conx-prt`, FS/SS/FF/SF, Lag als `xsd:duration`).
- Projekt → `conx-cs:ConstructionSchedule` (konfigurierbares `scheduleType`, Default `conx-st:RoughNetwork`, IRI-Basis `https://data.construct-x.org/schedule`).
- Benötigt `CONX_ONTOLOGY_ROOT` (im Image `/app/ontologies`, im Compose gesetzt).

### `converter-frontend/`

Tab-UI (GAEB BoQ + MS Project) ohne Build-Schritt (Vanilla-JS + nginx, Bind-Mount). Spricht beide Services direkt an (absolute localhost-URLs 8081/8085) und zeigt pro Service einen Health-Indikator. Read-only, syntax-highlightte XML-Preview nach Upload/Beispiel-Load sowie hervorgehobene TTL/JSON-LD-Ausgabe; `js/highlight.js` ist dependency-free (offline/Docker-fähig, keine CDN).

## Konventionen & Fallen

- IRIs nur über `IriFactory`, Namespace-Konstanten in `ConxNamespaces`. Versionierte Ontologie-IRIs (`…/0.5.0`) erst nach Absprache bumpen.
- JSON-Output nur über Jena RIOT + `PlainJsonModelWriter` — **drei identische Kopien** (`Converter/core`, `rdf-converter-service`, `msproject-converter-service`): Änderungen in allen drei spiegeln und diffen.
- Static-Assets mit Cache-Buster `?v=N` in `index.html` → nach CSS/JS-Änderungen bumpen; `rdf-converter-frontend` ist ein Custom-Image → nach UI-Änderungen `up -d --build rdf-converter-frontend`.
- Architektur-/Stack-Fragen: `HANDOFF.md` und `AGENTS.md` gelten.
- Tests offline: `mvn -q -o test` im Modul-Verzeichnis. Konverter-Logik: Negativfälle nicht vergessen (leere BOQ, fehlende Attribute, QtyPcnt-Splits, MLTexts).
