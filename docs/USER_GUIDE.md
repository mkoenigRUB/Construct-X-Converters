# Benutzerdokumentation — Construct-X Converters

Wie man die drei Konverter bedient: die **Tab-UI** (GAEB BoQ + MS Project) auf **Port 8084**, das **RDF-Studio** auf **Port 8080** und direkt die **REST-APIs** auf 8081/8082/8085.

- Installation & Betrieb: [INSTALLATION.md](INSTALLATION.md)
- Vor der ersten Nutzung: `docker compose -f converter-compose.yml up -d` und `docker compose -f rdf-converter-compose.yml up -d` (siehe [INSTALLATION.md](INSTALLATION.md), Abschnitt 3).

---

## 1. Überblick

| Was? | UI | Port | Ergebnis |
|---|---|---|---|
| GAEB Leistungsverzeichnis (DA XML 3.3, X80–X87) → Construct-X | Tab „GAEB BoQ" | 8084 → 8081 | TTL / JSON-LD / plain JSON, optional Reverse zurück nach GAEB XML |
| MS Project XML (2016–2024) → Construct-X | Tab „MS Project" | 8084 → 8085 | TTL / JSON-LD / plain JSON, optional Reverse zurück nach MS Project XML |
| Beliebiges RDF (Turtle/JSON-LD) ↔ RDF, SHACL-Validierung | RDF-Studio | 8080 → 8082 | TTL / JSON-LD / plain JSON, SHACL-Prüfung gegen lokale Construct-X-Profile |

Alle drei APIs teilen das Schema: Basis-Pfad `/api/v1`, Health-Check `GET /api/v1/health`.

---

## 2. Tab-UI (Port 8084) — GAEB BoQ & MS Project

Browser öffnen: <http://localhost:8084>

### 2.1 Health-Indikatoren

An jedem Tab („GAEB BoQ", „MS Project") sitzt ein Status-Dot. Die UI prüft alle 15 Sekunden die Health-Endpoints des jeweiligen Backends (8081 bzw. 8085):

- **grün** = Dienst erreichbar
- **rot** = Dienst nicht erreichbar (Backend läuft nicht oder ist überlastet — siehe [Troubleshooting](#6-troubleshooting))

### 2.2 Schritt 1 — Datei hochladen

- **Drag & Drop** auf die Dropzone („Datei hierher ziehen") oder Dateiauswahl.
- Oder Button **„Beispieldatei laden (GAEB X80)"** bzw. **„Beispieldatei laden (MS Project)"** — lädt eine integrierte Beispiel-Datei des Servers (`/examples/gaeb-boq.xml` bzw. `/examples/msproject.xml`). Gut zum Ausprobieren.
- Erwartete Dateitypen: GAEB-Tab `.xml` (GAEB DA XML 3.3), MS-Tab `.xml` (MS Project XML).

### 2.3 Schritt 2 — Konvertieren (vorwärts: Fachformat → Construct-X)

**Zielformat** (Radioknöpfe, beide Tabs):

| UI-Label | API-Parameter `format` | Inhalt |
|---|---|---|
| **TTL (Turtle)** | `ttl` | RDF/Turtle mit Construct-X-IRIs, der Standard für Weiterverarbeitung |
| **JSON (JSON-LD)** | `json` | JSON-LD mit `@context` + Construct-X-IRIs |
| **JSON (plain, ohne Ontologie)** | `plainjson` | Ebenes JSON **ohne** Ontologie-IRIs — zum einfachen Einlesen |

- Bei `plainjson` erscheint zusätzlich die Checkbox **„Exchange-Paket-Envelope (id, semanticModel, content.entities)"** — wickelt das Ergebnis in das Exchange-Paket-Format ein (Datei wird dann `-package.json` heißen).
- **GAEB-Tab** zusätzlich: Checkbox **„Nur XSD-Validierung"** — prüft die GAEB-Datei nur gegen die offiziellen GAEB-XSD-Schemas und konvertiert nicht.
- **MS-Tab** zusätzlich:
  - Checkbox **„SHACL-Validierung"** — prüft das Konvertierungsergebnis gegen die lokalen Construct-X SHACL-Profile.
  - Dropdown **„Planart (scheduleType)"** — steuert, wie der Terminplan semantisch gedeutet wird:

| UI-Label | Wert (API `scheduleType`) |
|---|---|
| Grobterminplan (RoughNetwork) — **Default** | `RoughNetwork` |
| Feinterminplan (DetailedNetwork) | `DetailedNetwork` |
| Gesamtplan (TotalNetwork) | `TotalNetwork` |
| Rahmenplan (FrameNetwork) | `FrameNetwork` |
| Teilplan (PartialNetwork) | `PartialNetwork` |
| Meilensteinplan (MilestoneNetwork) | `MilestoneNetwork` |
| Standardplan (StandardNetwork) | `StandardNetwork` |

- **GAEB-Tab, optional — „Bauposten-Auswahl":** Auswahlbaum aller Bauposten (ITEM-Positionen) der Datei; Buttons **„Alle"** und **„Alle abwählen (komplette LV)"**. Nur ausgewählte Positionen werden konvertiert; ohne Auswahl wird das komplette LV übernommen.

Danach Button **„Konvertieren"**.

### 2.4 Schritt 3 — Ergebnis

- Vorschau des Konvertierungsergebnisses (XML/TTL-Preview im Ergebnisbereich).
- **„Datei herunterladen"** — Name des Downloads:
  - GAEB vorwärts: `gaeb-boq-<Dateiname>.<ext>` (z. B. `gaeb-boq-mein-lv.ttl`)
  - MS vorwärts: `schedule-<Dateiname>.<ext>`
  - Envelope-Option: Suffix `-package.json`
- Bei Validierungsoptionen wird das Prüfprotokoll (Fehler/Warnungen) im Ergebnisbereich angezeigt.

### 2.5 Rückwärtskonvertierung (RDF → Fachformat)

Beide Tabs haben einen Bereich **„Rückwärtskonvertierung (RDF → …)"** für den Weg zurück:

1. Eine Construct-X-Datei hochladen (vorwärts erzeugtes Ergebnis oder eigenes RDF).
2. **Eingangsformat** wählen: **TTL** / **JSON (JSON-LD)** / **JSON (plain)**.
3. Optional: **„Strikt-Modus: Datenverluste (übersprungene/unabbildbare Einträge) brechen die Konvertierung ab"** — ohne Strict-Modus werden nicht abbildbare Einträge übersprungen und im Warnbereich gelistet; mit Strict-Modus schlägt die Konvertierung fehl, sobald etwas verloren gehen würde.
4. Button **„Zu GAEB DA XML konvertieren"** (GAEB-Tab) bzw. **„Zu MS Project XML konvertieren"** (MS-Tab).
5. Ergebnis inkl. Warndisplay (übersprungene/unabbildbare Einträge) + Download: `<Dateiname>.xml` (Basisname der Eingangsdatei, Endung `.xml`).

---

## 3. RDF-Studio (Port 8080) — RDF ↔ RDF & SHACL

Browser öffnen: <http://localhost:8080>

Oben in der Topbar zeigt der Status **`serviceStatus`** an, ob der Backend-Dienst (8082) erreichbar ist („Dienst wird geprüft" = Verbindungsaufbau läuft, 8× 1 s Retry).

### 3.1 Tab „Konvertieren"

1. **Schritt 1 — „RDF-Datei auswählen":** Drag & Drop oder Dateiauswahl, **max. 50 MB**.
2. **Schritt 2 — „Zielformat festlegen":**
   - **Quelldatei** (Format-Erkennung): **Automatisch erkennen** (Default; Client prüft Endung `.ttl`/`.json`/`.jsonld`), **Turtle (.ttl)**, **JSON-LD (.jsonld)**
   - **Zielformat:** **JSON-LD** / **TTL** / **Plain JSON**
   - Bei Zielformat *Plain JSON* erscheint die Envelope-Zeile — wie in der Tab-UI wählbar.
3. **„Datei konvertieren"** → Ergebnis:
   - **„Herunterladen"** (Datei)
   - **„Inhalt anzeigen"** (Vorschau im Browser)

> Hinweis für API-Nutzer: Der Server-Endpoint `/api/v1/convert` verlangt den Parameter **`sourceFormat` zwingend** (Default „Automatisch erkennen" der UI passiert clientseitig; `auto` wird nie an den Server geschickt). Ohne `sourceFormat` → HTTP 500.

### 3.2 Tab „Validieren"

1. **RDF-Datei** auswählen.
2. **RDF-Format** wählen (Turtle / JSON-LD).
3. **SHACL-Profil** aus dem Dropdown — die lokal verfügbaren Construct-X-Profile, z. B. `gaeb-boq` (v0.5.0), `construction-schedule` (v0.5.0) und weitere (Vollstand: `GET /api/v1/profiles`).
4. **Validieren** → SHACL-Bericht mit conforms/violations im Ergebnisbereich.

---

## 4. REST-API (für Skripte & Integrationen)

Alle Endpunkte liegen unter `/api/v1`. Basis-URLs: GAEB `http://localhost:8081`, RDF `http://localhost:8082` (oder via Studio-Proxy `http://localhost:8080/api/v1`), MS Project `http://localhost:8085`.

### 4.1 Gemeinsame Endpunkte (alle drei Dienste)

| Methode | Pfad | Bedeutung |
|---|---|---|
| GET | `/api/v1/health` | Health-Check |
| GET | `/api/v1/converters` | Verfügbare Konverter des Dienstes (GAEB, MS) |

### 4.2 GAEB (Port 8081)

| Methode | Pfad | Parameter (Form-Data) |
|---|---|---|
| GET | `/examples/gaeb-boq.xml` | — |
| POST | `/convert/gaeb-boq` | `file` (GAEB XML) · `format` = `ttl` \| `json` \| `jsonld` \| `plainjson` (Aliase `plain`, `plain-json`) · `envelope` (bool, nur bei `plainjson`) · `selection` (optional, komma-getrennte `ordinalNumber` der ITEM-Positionen) |
| POST | `/reverse/gaeb-boq` | `file` (Construct-X RDF) · `format` = `ttl` \| `jsonld` \| `plainjson` · `strict` (bool) |
| POST | `/positions/gaeb-boq` | `file` — Auswahlbaum (Bauposten-Struktur) |
| POST | `/validate/gaeb-boq` | `file` — XSD-Validierung gegen offizielle GAEB-Schemas |

**Beispiel (PowerShell):**

```powershell
Invoke-WebRequest http://localhost:8081/examples/gaeb-boq.xml -OutFile "$env:TEMP\lv.xml"
curl.exe -X POST "http://localhost:8081/api/v1/convert/gaeb-boq?format=ttl" -F "file=@$env:TEMP\lv.xml"
# → Turtle. ?format=plainjson&envelope=true → Exchange-Paket-JSON
```

### 4.3 MS Project (Port 8085)

| Methode | Pfad | Parameter (Form-Data) |
|---|---|---|
| GET | `/examples/msproject.xml` | — |
| POST | `/convert/msproject` | `file` (MS Project XML) · `format` = `ttl` \| `jsonld` \| `plainjson` · `scheduleType` (Default `RoughNetwork`, Werte wie in [2.3](#23-schritt-2---konvertieren-vorwärts-fachformat---construct-x)) · `iriBase` (optional) · `envelope` (bool) |
| POST | `/validate/msproject` | `file` — SHACL-Validierung |
| POST | `/reverse/msproject` | `file` (Construct-X RDF) · `format` = `ttl` \| `jsonld` \| `plainjson` · `strict` (bool) |

```powershell
Invoke-WebRequest http://localhost:8085/examples/msproject.xml -OutFile "$env:TEMP\plan.xml"
curl.exe -X POST "http://localhost:8085/api/v1/convert/msproject?format=jsonld&scheduleType=TotalNetwork" -F "file=@$env:TEMP\plan.xml"
```

### 4.4 RDF (Port 8082)

| Methode | Pfad | Parameter (Form-Data) |
|---|---|---|
| GET | `/profiles` | — (Liste der verfügbaren SHACL-Profile) |
| POST | `/convert` | `file` · **`sourceFormat` — Pflicht** (`ttl` \| `jsonld`) · `targetFormat` (`ttl` \| `jsonld` \| `plainjson`) · `envelope` (bool, Default false) |
| POST | `/validate` | `file` · `format` (`ttl` \| `jsonld`) · `profile` (Name aus `/profiles`) |

```powershell
curl.exe -X POST "http://localhost:8082/api/v1/convert?sourceFormat=ttl&targetFormat=jsonld" -F "file=@$env:TEMP\model.ttl"
# via Studio-Proxy (CORS-freundlich):
curl.exe -X POST "http://localhost:8080/api/v1/convert?sourceFormat=ttl&targetFormat=jsonld" -F "file=@$env:TEMP\model.ttl"
```

> **Achtung:** `sourceFormat` ohne → HTTP 500 („Interner Dienstfehler" über den generischen Exception-Handler, nicht 400). Das Studio-UI löst den Format-Parameter clientseitig aus der Dateiendung auf und schickt ihn immer konkret.

---

## 5. Ausgabeformate im Detail

| Format | Charakter | Typische Anwendung |
|---|---|---|
| **TTL (Turtle)** | Menschlesbares RDF, `@prefix`-Deklarationen, Construct-X-IRIs | Standardaustausch, Weiterverarbeitung in Jena/Protégé/Sparql-Endpoints |
| **JSON-LD** | `@context` + Construct-X-IRIs, maschinenlesbar | Web-/API-Integration, Linking auf Ontologie-IRIs |
| **Plain JSON** | Ebenes JSON, **keine** Ontologie-IRIs | Einfaches Einlesen in DB-/Code-Pipelines ohne RDF-Tooling |
| **Exchange-Paket-Envelope** | `plainjson` + Hülle `{ id, semanticModel, content.entities }` | Austausch nach Construct-X-Exchange-Format |
| **Reverse-GAEB-XML** | GAEB DA XML 3.3 | Rückweg ins GAEB-Umfeld |
| **Reverse-MS-Project-XML** | MS Project XML | Rückweg in MS Project |

Download-Namenskonvention der UIs: vorwärts `gaeb-boq-<Base>.<ext>` / `schedule-<Base>.<ext>` (Envelope: `-package.json`), rückwärts `<Base>.xml`.

---

## 6. Troubleshooting

| Symptom | Ursache / Fix |
|---|---|
| Tab-UI lädt, Status-Dot rot | Backend (8081/8085) läuft nicht — `docker ps`, `docker logs conx-converters-gaeb-converter-service-1`; Start: `docker compose -f converter-compose.yml up -d` |
| RDF-Studio zeigt dauerhaft „Dienst wird geprüft" | 8082 nicht erreichbar — `docker logs conx-rdf-rdf-converter-service-1`; Start: `docker compose -f rdf-converter-compose.yml up -d` |
| `curl.exe http://localhost:8084/api/v1/health` → 404 | **Erwartet.** 8084 ist nur die statische UI, ohne Health-Endpoint. |
| Konvertierung schlägt fehl mit XSD-/SHACL-Fehler | Eingangsdatei ungültig — erst `POST /validate/…` nutzen, um die konkreten Fehler zu sehen |
| Reverse-Konvertierung listet viele „übersprungene Einträge" | Eingabe enthält Konstruktionen, die ins Zielformat nicht abbildbar sind — Strict-Modus nutzen, um solche Fälle sofort zu erkennen, oder Eingangs-RDF bereinigen |
| CORS-Fehler im Browser | UIs sind auf `http://localhost:8084`/`8080` ausgelegt; von fremden Origin aus auf 8081/8085: GAEB erlaubt `*`, MS-Project default `http://localhost:8084` (Override: Env `CONX_CORS_ALLOWED_ORIGINS`) |
| Datei > 50 MB im RDF-Studio | Hard-Limit (nginx `client_max_body_size 50m` + Client-Check) — Datei aufteilen |
| PowerShell: `curl`-Alias-Fehler | `curl.exe` statt `curl` verwenden |
