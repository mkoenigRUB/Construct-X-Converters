# Checkpoint: MS Project Converter + Tabbed Frontend + Ontologie-Refactor umgesetzt

## Status: ALLES UMGESETZT, GEPUSHT UND VERIFIZIERT (Stand: 2026-11-09)

## Änderungen am 2026-11-09 (Ontologie-Refactor)

### Docker-Images bauen Ontologien aus dem GitHub-Tarball
- Beide Service-Dockerfiles (`rdf-converter-service`, `msproject-converter-service`) holen bei jedem Build die Ontologien aus dem Ontologie-Repo (`https://github.com/mkoenigRUB/Construct-X-Ontologies`, Branch `main`, **kein Tag** → Builds nicht reproduzierbar; Ontologie-Update = Rebuild):
  - `rdf-converter-service/Dockerfile`: 8 Module (`construction-order`, `construction-schedule`, `construction-cost`, `construction-legal`, `construction-geoinfo`, `construction-status`, `construction-process`, `construction-type`) + `MANIFEST.json` → `/app/ontologies/`, `ENV CONX_ONTOLOGY_ROOT=/app/ontologies`
  - `msproject-converter-service/Dockerfile`: nur `construction-schedule` + `MANIFEST.json`
- Compose: `CONX_ONTOLOGY_ROOT: /app/ontologies` (MS-Project-Service in `converter-compose.yml`, rdf-Service in `rdf-converter-compose.yml`)
- `CONVERTER_ONTOLOGY_DIR`-Bind-Mounts entfallen → lokale Ontologie-Dirs nicht mehr für Docker nötig
- 120 lokale Ontologie-Dateien gelöscht (neben dem Repo, außerhalb von Git)
- Commit `ad7fb05` → gepusht auf `origin/main` (`https://github.com/mkoenigRUB/Construct-X-Converters`)

### Verifikation (echte, ausgeführte Befehle)
- Drift-Check `GET http://localhost:8082/api/v1/profiles`: 17 Profile, alle Versionen unverändert (boq 0.5.0 / cs 0.5.0 / cc 0.5.0 / cl 0.5.0 / cg 0.5.0)
- E2E MS Project: 4902 B XML → 6646 B TTL (3 Activities, 2 PrecedenceRelationships, 1 Schedule)
- E2E GAEB: 10711 B XML → 15108 B TTL (1 OrderBook, 13 Positionen, 538 Tripples)

## Was in dieser Session gebaut wurde

### 1. `msproject-converter-service/` (NEU, eigenständiger Dienst, Port 8085)
- Java 25 / Spring Boot 3.5.3 / Apache Jena 4.10 (jena-shacl + jena-arq)
- Konvertiert MS Project XML (2016/2019/2021/2024, Namespace `http://schemas.microsoft.com/project`) → TTL oder JSON-LD
- Mapping auf construction-schedule Ontologie:
  - Task → `conx-cs:Activity` (Milestone=1 oder Duration=0 → `conx-cs:Milestone`)
  - `OutlineCode` → `hierarchyCode`, `PercentComplete` → `conx-status` (≥100 Completed, >0 InProgress, sonst Planned)
  - `PredecessorLink` → `conx-cs:PrecedenceRelationship` (Type 0=FS, 1=SS, 2=FF, 3=SF → conx-prt), Lag in Minuten → `xsd:duration` (PT{n}M)
  - Projekt → `conx-cs:ConstructionSchedule` (scheduleType Default `conx-st:RoughNetwork`, IRI-Basis `https://data.construct-x.org/schedule`)
  - Summary-Task (UID 0) wird übersprungen
- REST (spiegelt GAEB-Interface):
  - `POST /api/v1/convert/msproject` (multipart: file, format=ttl|json, scheduleType, iriBase optional)
  - `POST /api/v1/validate/msproject` (SHACL gegen construction-schedule Shapes)
  - `GET /api/v1/converters`, `GET /api/v1/health`, `GET /api/v1/examples/msproject.xml`
- CORS konfigurierbar via `CONX_CORS_ALLOWED_ORIGINS` (Default http://localhost:8084)
- Ontology-Root-Resolution (SDK `OntologyRegistry`): expliziter Pfad → Env `CONX_ONTOLOGY_ROOT` → sysprop `conx.ontology.root` → Default `../..` (SDK) bzw. `..` (MS-Service `application.yml`). Root = das Verzeichnis mit `MANIFEST.json` + Modul-Unterverzeichnissen; in Docker: `/app/ontologies`. Lokal (ohne Docker): Sibling-Clone `Construct-X-Ontologies` (siehe Start-Anleitung)
- **16/16 Tests erfolgreich** (9 Converter-Unit-Tests + 7 API-Integrationstests)
- Beispieldatei: `src/main/resources/examples/msproject-example.xml` (Doppelboden-Plan, 5 Tasks, 1 Meilenstein, 5 Beziehungen inkl. SS mit 1440-Min-Lag)
- Dockerfile vorhanden (Build-Context = Repo-Root)

### 2. `converter-frontend/` (NEU, Repo-Root, Port 8084)
- Statische Vanilla-JS-UI mit **zwei Tabs**:
  - 📋 **GAEB BoQ** → ruft GAEB-Dienst auf `http://localhost:8081/api/v1` (XSD-Validierung, Beispieldatei)
  - 📅 **MS Project** → ruft MS Project-Dienst auf `http://localhost:8085/api/v1` (TTL/JSON-LD, Planart-Dropdown, SHACL-Validierung)
- Pro Tab Health-Indikator (grün/rot), alle 15s gepollt
- Dropzone, Beispieldatei-Button, Ergebnis-Anzeige, Download
- `js/app.js` bestimmt API-Basis automatisch: wenn Frontend auf 8081/8085 selbst gehostet wird → relativer Pfad, sonst absolute localhost-URLs
- **WICHTIG:** Nach Änderungen an app.js den Cache-Buster in index.html erhöhen (`js/app.js?v=N`)

### 3. `converter-compose.yml` (NEU, Repo-Root)
- `msproject-converter-service` (Build aus msproject-converter-service/Dockerfile, Port 8085)
- `converter-frontend` (nginx:1.27-alpine, Volume-Mount, Port 8084)

### 4. README.md aktualisiert
- Neue Sektion "MS Project Converter (as of 2026-10-29)"
- Modul-Liste um msproject-converter-service + converter-frontend ergänzt

## Laufende Dienste (Stand 2026-10-30)
| Dienst | Port | Status |
|---|---|---|
| GAEB Converter | 8081 | läuft (JDK 25, Spring Boot 3.5.3) |
| converter-frontend (python http.server) | 8084 | läuft |
| msproject-converter-service | 8085 | läuft |
| rdf-converter-service | 8083 | optional, nicht vom neuen Frontend benötigt |

Browser: http://localhost:8084/index.html — beide Tabs grün.

## Start-Anleitung für einen anderen Rechner

### Voraussetzungen
- Java 25 (hier: `C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot`), Maven 3.9+ (hier: `C:\Program Files\apache-maven-3.9.4\bin\mvn.cmd`, in User-PATH)
- Python 3 (nur für statisches Frontend ohne Docker)
- Nur für lokalen Start ohne Docker: Sibling-Clone `Construct-X-Ontologies` auf gleicher Ebene wie das Converters-Repo (hier: `...\AP 3.4\Construct-X-Ontologies`) — die Docker-Images holen den Ontologie-Tarball selbst beim Build

### Start ohne Docker (empfohlen, wie in dieser Session)
```powershell
# 1. MS Project Service (Port 8085, in application.yml hartkodiert)
Push-Location "msproject-converter-service"
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot'
$env:CONX_CORS_ALLOWED_ORIGINS = 'http://localhost:8084'
& 'C:\Program Files\apache-maven-3.9.4\bin\mvn.cmd' spring-boot:run

# 2. Frontend (Port 8084) — in neuem Terminal
Push-Location "converter-frontend"
python -m http.server 8084

# 3. GAEB Service (Port 8081) — für den GAEB-Tab (benötigt JDK 25!)
Push-Location "Converter"
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot'
mvn -q install -DskipTests          # einmalig nach pom-Änderungen
Push-Location "web"
mvn spring-boot:run

# 4. (optional) RDF Service (Port 8083; CORS: 8084 = neues Frontend, 8080 = altes rdf-converter-frontend)
Push-Location "rdf-converter-service"
$env:SERVER_PORT = '8083'
$env:CONX_CORS_ALLOWED_ORIGINS = 'http://localhost:8084,http://localhost:8080'
& 'C:\Program Files\apache-maven-3.9.4\bin\mvn.cmd' spring-boot:run
```
Dann http://localhost:8084/index.html öffnen.

### Start mit Docker
```bash
docker compose -f converter-compose.yml up --build   # GAEB 8081 + MS Project 8085 + Frontend 8084
docker compose -f rdf-converter-compose.yml up --build  # RDF 8082 + Frontend 8080
```
Beide brauchen `--build` + Internetzugriff: die Dockerfiles laden den Ontologie-Tarball vom Ontologie-Repo (Branch `main`, kein Tag → nicht reproduzierbar).

### Tests
```powershell
Push-Location "msproject-converter-service"
& 'C:\Program Files\apache-maven-3.9.4\bin\mvn.cmd' -o test   # 38 Tests
Push-Location "..\rdf-converter-service"
& 'C:\Program Files\apache-maven-3.9.4\bin\mvn.cmd' -o test   # 17 Tests
```
GAEB: `mvn -o test` in `Converter/core` (34) und `Converter/web` (15).

## Wichtige technische Details / Stolpersteine
- **`debugJavaApplication`-Tool ist in dieser Umgebung kaputt** (immer "Maven build failed exit code 1" ohne Details) → Dienste per `mvn spring-boot:run` in async-PowerShell starten
- **Jena 4.10 API:** `RDFDataMgr.write(writer, model, Lang.TURTLE)` / `Lang.JSONLD` (nicht RDFFormat.JSON_LD); `model.createTypedLiteral(str, XSDDatatype.XSDdateTime)` für `^^`-Serialisierung (nicht `createLiteral(str, uri)` → erzeugt `@`-Form, die SHACL als ungültig wertet); `RDF.type` statt `ResourceFactory.createProperty("rdf","type")` (sonst `<rdftype>`); `RDFDataMgr.read(model, inputStream, baseUri, lang)` braucht Base-URI
- **Jena SHACL:** mehrere `sh:message` pro Node werden abgelehnt → `removeDuplicateMessages()` (nur erste pro Subject behalten)
- **XSD dateTime** braucht Sekunden: `yyyy-MM-dd'T'HH:mm:ss` (MS Project XML hat nur HH:mm)
- **MS Project Lag** ist in Minuten (Integer) → `PT{n}M`
- **Browser-Cache:** app.js wird gecacht → `?v=N` in index.html erhöhen
- **Frontend auf 8084** muss cross-origin auf 8081/8085 zugreifen → CORS auf beiden Diensten für `http://localhost:8084` setzen
- **SHACL-Validierung:** Data-Graph = konvertiertes Modell + Ontologie-Graph (alle TTL außer -shapes und -example aus construction-schedule/)

## Bestätigte Entscheidungen (User)
- XML-Versionen: nur aktuelle (2016/2019/2021/2024)
- Beispieldatei: selbst erstellt (keine vorhanden)
- Frontend-Tabs: nur GAEB + MS Project (kein RDF-Studio-Tab)
- Default scheduleType: RoughNetwork (Grobterminplan), in UI änderbar
- IRI-Basis: `https://data.construct-x.org/schedule`
- Frontend-Ort: `converter-frontend/` am Repo-Root
- Dienste vollständig unabhängig voneinander

## Offene Punkte / mögliche nächste Schritte
- `rdf-converter-frontend/` (altes RDF-Studio-Frontend, Port 8080 via compose) existiert weiterhin parallel zum neuen `converter-frontend/` — ggf. später konsolidieren
- Lokale Start-Anleitung (ohne Docker) müsste `CONX_ONTOLOGY_ROOT` explizit setzen (die Defaults `..`/`../..` zeigen nicht eindeutig auf den Sibling-Root)
- Browser-Checks nach dem Refactor mit `Ctrl+F5` nachziehen (Cache-Buster `?v=8` in `converter-frontend/index.html`): Envelope-Checkboxen v2, Bauposten-Auswahl-Baum + Strikt-Checkbox

## Änderungen am 2026-11-01 (Plain-JSON-Ausgabeformat v1 in allen drei Convertern — durch v2 abgelöst)

> **Hinweis:** v1 wurde nie produktiv genutzt und wurde noch am 2026-11-01 durch die an das Doppelboden-Beispiel ausgerichtete **v2** abgelöst (siehe Abschnitt unten). Historie der ersten Implementierung:

Neues, zusätzliches Ausgabeformat **`plainjson`** — verlustfreie Node-Liste als reines JSON **ohne** RDF/JSON-LD-Semantik (keine `@id`, keine `@context`). TTL und JSON-LD bleiben unverändert erhalten.

### Format (Version 1, `meta.format = construct-x-plain-json`)
```json
{
  "meta": {"format": "construct-x-plain-json", "version": 1, "tripleCount": 42},
  "nodes": [
    {"id": "<IRI>", "type": ["<Klasse>"], "name": "plain String",
     "label": {"value": "…", "lang": "de"},
     "start": {"value": "…", "datatype": "http://www.w3.org/2001/XMLSchema#dateTime"},
     "hasActivity": ["<IRI>", "<IRI>"]}
  ]
}
```
- Plain-Literal (kein Lang, kein Datatype bzw. `xsd:string`) → JSON-String; sonst Objekt mit `value` + `lang`/`datatype`
- Mehrwerte → Arrays; Blank-Nodes → `_:label`; Keys = lokale Namen, bei Kollision (gleicher lokaler Name, verschiedene IRI) volle IRI
- **Einschränkung:** kein Namespace-Kontext → IRIs müssen für Consumer extern aufgelöst werden (z. B. via `@context`-Map). Für die interne Nutzung in Construct-X ausreichend; JSON-LD bleibt der Austausch-Standard.

### Geänderte Dateien
- NEU `PlainJsonModelWriter.java` in allen drei Diensten (je eigenständige Kopie):
  - `msproject-converter-service/src/main/java/org/constructx/msproject/`
  - `Converter/core/src/main/java/org/constructx/converter/`
  - `rdf-converter-service/src/main/java/org/constructx/rdf/`
- **MS Project (8085):** `OutputFormat.java` (Enum neu: TURTLE/JSON_LD/PLAIN_JSON, Parse-Aliase `plainjson|plain`), `MsProjectService.java` (Switch auf Format), `MsProjectController.java` (Format-Validierung via Enum, `/converters` listet `plainjson`)
- **GAEB (8081):** `ConverterController.java` (Format `plainjson|plain|plain-json`, `application/json`, Suffix `.json`), `GaebBoqConversionService.java` (Record `ConversionResult` um `plainJson` erweitert)
- **RDF (8083):** `RdfFormat.java` (PLAIN_JSON, `lang()` = null, **nur als Ziel** — als Quelle wird mit 400 abgelehnt), `RdfService.java` (Writer-Pfad)
- NEU Tests: `PlainJsonModelWriterTest` (MS 3, GAEB 2) + API-Tests (`MsProjectApiTest`, `ConverterApiTest`, `RdfApiTest` je +plainjson-Fälle)
- **Frontends:** `converter-frontend/index.html` + `js/app.js` (dritter Radiobutton „JSON (plain, ohne Ontologie)" in beiden Tabs, `FORMAT`-Deskriptor, `iriCount` pro Format, Cache-Buster `app.js?v=3`); `rdf-converter-frontend/index.html` + `app.js` (Ziel-Option `plainjson`, Download/Summary, Cache-Buster `app.js?v=3`)

### ⚠️ Stolperstein: Jena 4.10 `Literal.getLanguage()`
Liefert für Literals **ohne** Sprache keinen `null`, sondern die **leere Zeichenkette** `""` (und `getDatatypeURI()` ist nie null, sondern mindestens `xsd:string`). Plain-Literal-Erkennung daher:
```java
(language == null || language.isEmpty()) && (datatype == null || XSD_STRING.equals(datatype))
```
Außerdem (Jena 4.10, nicht 3.x): `Resource.getBlankNodeLabel()` existiert nicht mehr → `resource.getId().getLabelString()`; `isPlainLiteral()` existiert nicht.

### Test-/Laufstatus (2026-11-01)
- `mvn test`: **MS Project 21/21**, **GAEB 25/25** (core 15 + web 10), **RDF 11/11** — alle grün
- Dienste neu gestartet mit neuem Code: GAEB (8081, `Converter/web` via `mvn spring-boot:run`, JAVA_HOME = JDK 25), MS Project (8085), RDF (8083, CORS um `http://localhost:8080` erweitert für rdf-converter-frontend), Frontend 8084 (python http.server, dient neu von Platte)
- Verifikation (E2E über HTTP, 2026-11-01): GAEB 200 (`application/json`, 27 Knoten, ohne `@id`, 35 typisierte Literals → `{"value","datatype"}`, 4 lang-Literals → `{"value","lang"}`), MS Project 200 (11 Knoten, ohne `@id`), RDF 200 (plain literal → JSON-String, lang-tagged → nur `lang`, typisiert → nur `datatype`), RDF-Quelle `plainjson` → 400 ✅
- Alle drei Dienste mit final korrigiertem Writer neu gestartet (GAEB 8081, MS 8085, RDF 8083)
- Offener Browser-Check: End-to-End-Konvertierung mit `format=plainjson` im UI prüfen (Hard-Refresh `Ctrl+Shift+R` wegen Cache-Buster)

## Änderungen am 2026-11-01 (Fortsetzung): Plain-JSON an Exchange-Konvention ausgerichtet (v2) + optionale Envelope

Nach Vergleich mit `examples/construct-x-doppelboden-beispieldaten` (das Referenzformat für Construct-X JSON-Austausch) wurde das `plainjson`-Format auf **Version 2** umgestellt. Das v1-Format oben wurde **nicht** produktiv genutzt und ist damit abgelöst (API-Name `plainjson` unverändert; `json`/`jsonld` bleibt JSON-LD).

### Format v2 (`meta.version = 2`) — Konvention des Doppelboden-Beispiels
- **CURIE-Keys & -Werte:** Property-Keys, `type`-Werte und `datatype`-Werte als CURIEs via neuem `ConxCurieRegister` (spiegelt die `vann:preferredNamespacePrefix`-Deklarationen aller Construct-X-Ontologien + Standards `xsd`, `rdf`, `rdfs`, `owl`, `dcterms`, `dcat`, `prov`, `sh`, `vann` + QUDT 2.1 `qudt`/`unit`/`qk`/`sys`). Beispiel: `conx-gaeb:identifier`, `conx-cs:scheduleType`, `xsd:decimal`, `conx-st:FrameNetwork`. Nicht registrierte Namespaces (z. B. Projekt-IRIs) bleiben volle IRIs → verlustfrei, beseitigt die v1-Mehrdeutigkeit bei gemischten Vokabeln.
- **Lang-Literals:** `{"value", "language"}` (wie im Beispiel) statt `{"value", "lang"}`.
- **Booleans:** `xsd:boolean` → natives JSON `true`/`false`.
- **Ressourcen-Referenzen:** CURIE wenn registriert (z. B. `conx-st:RoughNetwork`), sonst volle IRI (Projekt-IRIs).
- **`type`:** String bei 1 Typ, Array bei mehreren (v1: immer Array).
- **Envelope (optional, API-Parameter `envelope=true`, nur für `plainjson`):**
  ```json
  {"id": "<Root-IRI>", "identifier": "<Root-identifier>",
   "semanticModel": {"type": "BillOfQuantities", "conformsTo": ["<versionierte Ontologie-/Shape-IRIs>"]},
   "content": {"entities": [ ...Knotenliste wie oben... ]}}
  ```
  - GAEB/MS Project: Header aus der Root-Entität abgeleitet (`getRootBoq()` bzw. `getRootSchedule()`: IRI, `identifier`-Eigenschaft, `rdf:type`), `conformsTo` aus `GaebBoqConverter.CONFORMS_TO` / `MsProjectConverter.CONFORMS_TO`.
  - RDF-Dienst (beliebige Dokumente): erster (sortierter) Knoten = Paket-Root; `conformsTo` = versionierte `construct-x.org`-IRIs, die das Dokument selbst referenziert (Best-Effort).
  - Datei-Download-Namen: Suffix `-package` (`…-package.json`), sichtbar in beiden Frontends.

### Geänderte/Neue Dateien (v2)
- NEU `ConxCurieRegister.java` in allen drei Projekten (Kopien, nur Paketname unterschiedlich)
- UMGESCHRIEBEN `PlainJsonModelWriter.java` (v2, +`writeEnvelope`, `writeEnvelopeOrNodes`, `writeEntities`, `identifierOf`, `typeLocalName`, `EnvelopeSpec`-Record)
- **MS Project (8085):** `MsProjectConverter.java` (`CONFORMS_TO`, `rootSchedule`, `getRootSchedule()`), `MsProjectService.java` (Envelope-Spec), `MsProjectController.java` (Parameter `envelope`)
- **GAEB (8081):** `GaebBoqConverter.java` (`CONFORMS_TO`, `rootBoq`, `getRootBoq()`), `GaebBoqConversionService.java` (`plainJsonEnvelope`, Jena-Imports), `ConverterController.java` (Parameter `envelope`, Dateiname `-package`)
- **RDF (8083):** `RdfService.java` (`genericEnvelopeSpec`), `RdfController.java` (Parameter `envelope`)
- Tests: `PlainJsonModelWriterTest` (core 7 Tests, MS 4) neu auf v2; API-Tests +Envelope-Fälle (GAEB `convertWithPlainJsonAndEnvelopeReturnsExchangePackage`, MS `convertsExampleToPlainJsonWithEnvelope`, RDF `convertsTurtleToPlainJsonWithEnvelope` + `plainJsonEnvelopeCuriesConstructXVocabularies`)
- **Frontends:** `converter-frontend` (Checkbox „Exchange-Paket-Envelope" in beiden Tabs, `envelope`-Formfeld nur bei plainjson, Dateiname `-package`, Knoten-Zählung erfasst jetzt auch `_:label`, `app.js?v=4`); `rdf-converter-frontend` (Checkbox erscheint bei Zielformat plainjson, `envelope`-Formfeld, Summary mit Knoten-/Envelope-Hinweis, `app.js?v=4`, `styles.css?v=2` + `.envelope-row`/`.envelope-check`)

### ⚠️ Stolperstein: Jena 4.10 `StmtIterator`
`org.apache.jena.rdf.model.StmtIterator` implementiert in Jena 4.10 **kein** `AutoCloseable` → **kein try-with-resources**; stattdessen `try { … } finally { iterator.close(); }` (wie im Rest des Codes).

### ⚠️ Stolperstein: VS-Code Java-Sprachserver kompiliert ins gleiche `target/classes`
Der `redhat.java`-Server (VS Code) legt bei offenen Projekten eigene Klassen in `target/classes` ab. Bei inkonsistentem Sprachserver-Zustand entstehen **kaputte Class-Files mit „Unresolved compilation problem"** im Bytecode (erkennbar via `javap -v -p`), die Maven bei inkrementellem Build überspringt, weil das Class-File neuer als die Quelle ist. Symptom: im Testlauf `ClassNotFoundException` **ohne Paketname**. Heilung: `mvn clean test`.

### Bugfixes nach E2E-Erstdurchlauf (2026-11-02)
- `MsProjectService.java`: Download-Dateiname fehlte der `-package`-Suffix bei `envelope=true` (nur „schedule.json") → jetzt `schedule-package.json`.
- `RdfService.java`: `outputName(...)` kannte den Envelope-Parameter nicht → jetzt `-package`-Suffix bei plainjson+envelope (z. B. `sample-package.json`).
- E2E-Skript `C:\Users\Markus König\AppData\Local\Temp\opencode\e2e-plainjson.ps1` vollständig auf v2 umgestellt (CURIE-Keys, `language`, `xsd:decimal`/`xsd:dateTime`, native Booleans, `version: 2`, Envelope-Fälle für alle drei Dienste, `-package.json`-Dateinamen-Checks, negativer plainjson-Quellen-Test).

### Test-/Laufstatus (v2)
- `mvn test` (final, 2026-11-02): **GAEB core 20/20, GAEB web 11/11, MS Project 23/23, RDF 13/13** (67 gesamt) — alle grün
- **E2E-HTTP verifiziert (2026-11-02):** alle Checks OK — v2-Format (CURIE-Keys, `language`, CURIE-Datatypes, native Booleans, `version: 2`), Envelope (`id`/`identifier`/`semanticModel`/`conformsTo`/`content.entities`, kein `nodes`/`meta`), Download-Dateinamen `-package.json` (alle drei Dienste), negative Fälle (plainjson-Quelle → 400, kein `@id`/`@context`).
- Dienste laufen: GAEB (8081), MS (8085), RDF (8083), Frontend (8084)
- Open: Browser-Check der Envelope-Checkboxen durch Nutzer (http://localhost:8084, `Ctrl+Shift+R`; rdf-converter-frontend auf http://localhost:8080 bzw. über die eigene Startseite)

## Änderungen am 2026-11-03 (Content-Disposition-Header-Fix + Cache-Buster + Frontend-8084)

### Bug: ungültige `Content-Disposition`-Header (MS Project 8085, RDF 8083)
- **Symptom:** Download-Header sah aus wie `attachment; filename="=?UTF-8?Q?schedule.ttl?="; filename*=UTF-8''schedule.ttl`. Spring `ContentDisposition.attachment(name, UTF_8)` kodiert den quoted `filename` mit **RFC 2047** (MIME-Mail-Kodierung) — in HTTP-Headern **ungültig**.
- **Fix:** eigenes package-privates `attachmentFilename(String)` + `rfc5987(String)` in `MsProjectController.java` (2 Aufrufstellen) und `RdfController.java` (1 Aufrufstelle):
  - reine ASCII-Namen (in der Praxis alle Fälle: `schedule.ttl`, `sample.jsonld`, `…-package.json`) → `attachment; filename="name"`
  - nicht-ASCII → ASCII-Fallback (`[^A-Za-z0-9._-]` → `_`, reiner `./_`-Name → `download`) + `filename*=UTF-8''<percent-encodiert>` (RFC 5987, nur `A-Za-z0-9-_.~` bleiben)
  - GAEB (8081) **unverändert** — dessen plain-String-Header waren schon korrekt (dienste als Referenz genutzt).
- **Fallen (per Test erwischt):** `String.indexOf(c)` auf `"A-Za-z0-9-_.~'"` interpretiert **keine** Bereiche — nur Literal-Zeichen. Korrekte Umsetzung = explizite Bereichs-Checks in `rfc5987`.
- **Live verifiziert (laufende Dienste):** `attachment; filename="schedule.ttl"` (8085), `attachment; filename="sample.jsonld"` (8083), `attachment; filename="msproject-example.xml"` (8085 GET), GAEB `attachment; filename="gaeb-x80-example.xml"` (8081) — nirgends mehr `=?UTF-8?Q?`.
- **Neue Tests:** 3 in `MsProjectApiTest` (TestRestTemplate) + 2 in `RdfApiTest` (MockMvc + Unit) — inkl. Nicht-ASCII-Fall mit Erwartung `attachment; filename="Gr__e__.ttl"; filename*=UTF-8''Gr%C3%BC%C3%9Fe%20%C3%BC.ttl`.

### Formatierungs-Bug im Frontend: Browser-Cache, nicht der Code
- On-Disk-UI-Code war von Anfang an korrekt (nachgewiesen per Headless-Chrome-Screenshot der Live-Seite 8084). Ursache: **heuristisches Browser-Caching** — `python -m http.server` und der nginx-Container senden keine `Cache-Control`-Header, der Browser liefert veraltete `app.js`/CSS aus dem Cache.
- **Cache-Buster erhöht (alle drei Frontends):** `converter-frontend` (8084): `style.css?v=5`, `js/highlight.js?v=2`, `js/app.js?v=6` · `rdf-converter-frontend` (8080): `styles.css?v=3`, `js/app.js?v=5` · GAEB-Static `Converter/web/src/main/resources/static/index.html`: `style.css?v=5`, `app.js?v=5` (`target/` wird vom GAEB-Dienst automatisch synchronisiert).
- **Nutzer-Aktion: Hard-Reload `Ctrl+F5`** im Browser (sonst bleibt die alte Darstellung aus dem Cache erhalten).

### Frontend 8084 wird jetzt vom Docker-Container bedient
- `construct-x-ontologies-converter-frontend-1` (nginx, Volume-Mount auf `converter-frontend/`, Port 8084→80) läuft und serviert die neuen Cache-Buster (verifiziert: `v=5, v=2, v=6`). Der python-`http.server` auf 8084 ist damit **entbehrlich** (bei Container-Stopp wäre Bind-Konflikt) — Start-Anleitung oben entsprechend.

### Test-/Laufstatus (2026-11-03)
- `mvn -q -o test`: **MS 38/38, RDF 17/17, GAEB core 34/34, GAEB web 15/15** — alle grün
- **E2E-HTTP (`e2e-plainjson.ps1`): 34/34 OK** (plainjson v2 + Envelope in allen drei Diensten, negative plainjson-Quelle → 400, `-package.json`-Dateinamen)
- Dienste laufen mit neuem Code: GAEB (8081), MS (8085), RDF (8083, CORS `8084,8080`), Frontend 8084 (nginx-Container), rdf-converter-frontend (8080, python http.server)
- **⚠️ Opencode-Server-Neustart (2026-11-03) hat alle lokal gestarteten Prozesse beendet** (Maven-Wrappers, App-JVMs, python-Server). Docker-Daemon/-Container sind automatisch wieder hochgekommen. Die Java-Dienste müssen nach einem solchen Neustart manuell neu gestartet werden (Start-Anleitung oben).

## Änderungen am 2026-10-30 (GAEB-Dienst gestartet)
- **Eclipse Temurin JDK 25 installiert** (`C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot`) — der GAEB-Converter (`Converter/`) und `rdf-converter-service` zielen auf Java 25; die bisherige JDK 20 reichte nicht. Beim Starten des GAEB-Dienstes `JAVA_HOME` auf die JDK 25 setzen:
  ```powershell
  $env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot'
  ```
- **Spring Boot im GAEB-Converter von 3.2.5 auf 3.5.3 angehoben** (`Converter/pom.xml`, Property `spring-boot.version`) — Spring Boot 3.2.5 kann Java-25-Klassendateien (major version 69) beim `repackage` nicht verarbeiten.
- **`Converter/web/src/main/resources/application.properties` korrigiert** — die Datei enthielt YAML-Format in einer `.properties`-Datei; Spring Boot 3.5.3 hat das nicht mehr erkannt und den Default-Port 8080 genutzt. Jetzt echtes Properties-Format (`server.port=8081`).
- GAEB-Dienst läuft wieder auf **Port 8081**, beide Frontend-Tabs zeigen grün.
- **Vorschau mit Syntax-Highlighting (read-only)** in beiden Tabs:
  - NEU `converter-frontend/js/highlight.js`: eigenständiger, dependency-freier Highlighter für XML, TTL und JSON-LD (Zeilennummern, HTML-Escaping, `MAX_LINES=1500` — darüber Fallback auf Klartext). Keine externen Bibliotheken/CDN (Offline-/Docker-Betrieb).
  - NEU Vorschau-Panel in Sektion 1 beider Tabs (`#gaeb-preview`, `#ms-preview`): erscheint nach Datei-Upload (Dropzone) oder Laden der Beispieldatei; wird beim Entfernen der Datei ausgeblendet. Meta-Zeile: Dateiname · Zeilenanzahl · XML.
  - Ergebnis-Sektion (Sektion 3) wird ebenfalls per `highlightInto()` highlighted (`data-lang`-Attribut, bei Formatwechsel von TTL auf JSON-LD auf `jsonld` umgestellt).
  - CSS: neue Tokens `--code-bg/--code-fg/--code-line`, Dark-Code-Palette (`.hl-*`-Klassen), `:focus-visible` auf den `<pre>`-Panes (tabindex=0), `prefers-reduced-motion`, mobile Breakpoint 640px. Zeilennummern `aria-hidden` + `user-select:none` (keine doppelte Auswahl beim Kopieren).
  - Cache-Buster erhöht: `style.css?v=2`, `js/app.js?v=2`, `js/highlight.js?v=1`.
  - Verifiziert: Node-Smoke-Test (9 Checks: Roundtrips XML/TTL/JSON-LD, Injection-Escaping, Größenlimit, Sprach-Aliase) + HTTP-Check aller 4 Assets (HTTP 200, Verweise korrekt).

## Änderungen am 2026-11-08 (Parker B: Bauposten-Auswahl + Strikt-Modus-Toggle)

Die beiden offenen Punkte des GAEB/MS-Reverse-Bereichs sind umgesetzt und verifiziert.

### 1. Parker B — selektive LV-Positions-Auswahl (Entscheidung: **Baum mit Checkboxen**)
- `GaebBoqConverter.convert(file, List<String> selection)`: Auswahl per **voller OZ (Teil-OZ)**. SECTION-Auswahl = ganzer Subtree; ITEM-Auswahl = einzelne Position. Enthalten `BoQCtgy` bleiben als leere Strukturknoten (OZ/Name/Level), Posten ohne gewählten Nachfahren = weggelassen. Kein/leere `selection` = Legacy (komplette LV).
- `GaebBoqConverter.selectionTree(file)` + Record `BoQSelectionEntry(kind, id, ordinalNumber, name, itemCount, children)` — Auswahlbaum für die UI.
- **API:** neuer Endpoint `POST /api/v1/positions/gaeb-boq` (XSD-Validierung, Baum als JSON); `POST /api/v1/convert/gaeb-boq` + Parameter `selection` (komma-getrennt). CLI: optionales 5. Argument.
- **Frontend (8084, GAEB-Tab):** Checkbox-Baum „Bauposten-Auswahl (optional)" nach Upload/Beispiel-Load; Zeile = Haken · OZ · Name · Positionszähler; Buttons „Alle" / „Alle abwählen (komplette LV)"; Live-Zähler („n von m Position(en) ausgewählt"). Gesetzte ITEM-Haken werden als `selection` an den Convert-Endpoint übergeben (nur bei Konvertierung, nicht bei Validate/Reverse). CSS: `.selection*`/`.sel-*` in `converter-frontend/css/style.css` (Tokens aus `design/tokens.css`).
- Cache-Buster: `style.css?v=8`, `js/app.js?v=8`.

### 2. Strikt-Modus-Toggle (Reverse-Konverter: GAEB + MS Project)
- **Semantik:** pragmatisch = Default (läuft immer, Verluste → Warnliste). `strict=true` → **Datenverlust-Warnungen werden zum Abbruch** (HTTP 400, `error: "Conversion aborted"`, `detail` = Liste). **Getolert** bleiben verlustige Approximationen (Prozentsplits→absolut, `InProgress`→50 %, synthetisierter GAEB-Header, Level-Mismatch→Nesting) — dort geht nichts verloren.
- Gemeinsame Klassifikation: **NEU `org.constructx.sdk.ConxStrict`** (SDK, `isStrict()`/`strictWarnings()`/`failureMessage()`, Keyword-basiert, bei Zweifel strikt). GAEB: `GaebBoqReverseConverter.convert(model, header, strict)`; MS: `MsProjectReverseConverter.convert(model, strict)` + Services + Controller-Parameter `strict` (Default `false`).
- `IllegalArgumentException`-Handler beider Reverse-Controller: 400-Meldung jetzt `Conversion aborted` (deckt Input-Fehler + Strikt-Abbruch ab).
- **Frontend (8084):** Checkbox „Strikt-Modus …" in beiden Reverse-Bereichen (`gaeb-rev-strict`/`ms-rev-strict`); Abbruch wird als Fehlerliste gerendert; Erfolgs-Meta zeigt „Strikt-Modus".

### Geänderte/Neue Dateien
- NEU `construct-x-sdk/src/main/java/org/constructx/sdk/ConxStrict.java` + `ConxStrictTest` (4 Tests).
- `Converter/core`: `GaebBoqConverter` (selection + tree), `GaebBoqReverseConverter` (strict), `GaebConverterCli` (5. Argument); NEU Test `GaebBoqConverterSelectionTest` (7), `GaebBoqReverseConverterTest` +2 (strict).
- `Converter/web`: `GaebBoqConversionService` (convert+selection, selectionTree), `ConverterController` (positions-Endpoint, selection- + strict-Parameter, 400-Text); `ConverterApiTest` +7, 1 angepasst (400-Text).
- `msproject-converter-service`: `MsProjectReverseConverter` (strict), `MsProjectReverseConversionService`, `MsProjectController` (strict-Parameter, 400-Text); `MsProjectApiTest` +2, 1 angepasst (400-Text).
- `converter-frontend`: `index.html` (Baum-Section + Strict-Checkboxen, Cache-Buster v=8), `css/style.css` (`.selection*`/`.sel-*`), `js/app.js` (loadSelectionTree/render/selection-Handling/strict-Parameter/Abbruch-Rendering).

### Verifikation (2026-11-08, echte Läufe — JDK 25.0.5 systemweit, Maven 3.9.4)
- **SDK:** `mvn clean install` → **85/85** (81 + 4 ConxStrict).
- **GAEB:** `mvn clean test` (mit `-Dbyte-buddy-agent.jar` Kurzpfad, s. u.) → **core 26/26, web 22/22 — BUILD SUCCESS**.
- **MS Project:** `mvn clean test` (gleicher Kurzpfad-Trick) → **35/35 — BUILD SUCCESS**.
- **Live-E2E (GAEB-Web-Jar, Port 8081, per HTTP):**
  - `POST /positions/gaeb-boq` → 200, Baum korrekt (2 Root-Posten, SEC-01/01 mit 3 Positionen, Sub 0101, Item 0101001/Aushub Flachgründung, SEC-04/02).
  - `convert` + `selection=0101001` → nur ITM-001, SEC-02 bleibt (Struktur), SEC-04/ITM-002/ITM-004 weg.
  - `convert` + `selection=01` → ITM-001/002/003, ITM-004 weg.
  - `convert` ohne selection → alle 4 (Legacy).
  - `reverse` strict + Datenverlust (Item ohne OZ) → **400** `{"error":"Conversion aborted","detail":"Strict mode: … no ordinal number …"}`.
  - `reverse` pragmatisch, gleiches Modell → 200 + Warnung „no ordinal number".
  - `reverse` strict + sauberes Forward-Beispiel (nur Approximationen) → **200** (getolert).
- Frontend: `node --check app.js` → Syntax OK. (Browser-Check des Baums + Strict-Checkbox durch Nutzer, Hard-Reload `Ctrl+F5` wegen Cache-Buster.)

### Neue Falle (dieser Rechner)
- **byte-buddy-agent im Surefire-argLine:** der Property-Wert `${settings.localRepository}/…/byte-buddy-agent-1.17.6.jar` enthält das Leerzeichen im Benutzernamen „Markus **König**" → Surefire-Fork bricht („The forked VM terminated …"). Workaround: `-Dbyte-buddy-agent.jar=<8.3-Kurzpfad>` (z. B. `C:\Users\MARKUS~1\M2E4AB~1\…\BYTE-B~1.JAR`) dem Maven-Call anhängen (Pom nicht ändern). Test-Skript: `C:\Users\Markus König\AppData\Local\Temp\opencode\conx-test-module.ps1`.
- Auf diesem Rechner laufen JDK 25.0.5 (`C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot`) + Maven 3.9.4 (`C:\Program Files\apache-maven-3.9.4`) systemweit — die Temp-Toolchains aus dem Ontologie-Repo-HANDOVER sind hier nicht mehr nötig.
