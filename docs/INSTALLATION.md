# Installation & Betrieb — Construct-X Converters

Installationsanweisungen für die fünf Konverter-Container (GAEB, MS Project, RDF + 2 Frontends) per Docker Compose. Ziel: **nachvollziehbare, verifizierbare Schritte** — jeder Abschnitt endet mit einem Check, mit dem man sicherstellt, dass er funktioniert.

- Benutzerdokumentation (Bedienung der UIs + API): [USER_GUIDE.md](USER_GUIDE.md)
- Architektur & Design: [DESIGN.md](DESIGN.md)

---

## 1. Voraussetzungen

| Komponente | Version | Wo nötig |
|---|---|---|
| Docker Engine (Desktop oder Daemon) | aktuellt, mit Compose-Plugin v2 | immer |
| Git | 2.x | immer (Clone) |
| Java 25 + Maven 3.9 | — | **nur** für lokale Unit-Tests, nicht für den Docker-Betrieb |

**Keine** lokale Java-/Maven-Umgebung für den Betrieb nötig — alles läuft im Container.

### Docker-Check

```powershell
docker --version          # Docker version 2x.x.x, build …
docker compose version    # Docker Compose version v2.x.x
docker ps                 # läuft → Engine ok
```

---

## 2. Repository klonen

```powershell
git clone --recurse-submodules https://github.com/mkoenigRUB/Construct-X-Converters.git
cd Construct-X-Converters
```

Die Ontologien liegen als **gepinntes Git-Submodul** unter `ontologies/` (Tag `v0.5.0` des Repos `mkoenigRUB/Construct-X-Ontologies`).

**Bestehender Klon ohne Submodul** (z. B. ohne `--recurse-submodules` geklont):

```powershell
git submodule update --init ontologies
```

> **Hinweis:** Für den Docker-Betrieb ist das Submodul **nicht** nötig — die Images backen die Ontologien beim Build aus dem getagten Tarball (`v0.5.0`) ein. Es wird nur für `mvn test` lokal benötigt (Abschnitt 8).

**Check:**

```powershell
git submodule status     # →  <40-stellige SHA>  ontologies (v0.5.0)
ls ontologies            # → Verzeichnis mit .ttl-Dateien
```

---

## 3. Starten

```powershell
# GAEB (8081) + MS Project (8085) + Tab-UI (8084)
docker compose -f converter-compose.yml up -d --build

# RDF-Service (8082) + RDF-Studio-UI (8080)
docker compose -f rdf-converter-compose.yml up -d --build
```

`--build` baut die Images aus dem aktuellen Quellenstand (erster Start ~ ein paar Minuten; danach nur bei Code-Änderungen nötig).

**Check:**

```powershell
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

Erwartet — alle 5 Container `Up`, Ports gemappt:

| Container | Port | Zweck |
|---|---|---|
| `conx-converters-gaeb-converter-service-1` | 8081 | GAEB DA XML → Construct-X (API) |
| `conx-converters-msproject-converter-service-1` | 8085 | MS Project XML → Construct-X (API) |
| `conx-converters-converter-frontend-1` | 8084 | Tab-UI für GAEB + MS Project |
| `conx-rdf-rdf-converter-service-1` | 8082 | RDF-Konverter/Validator (API) |
| `conx-rdf-rdf-converter-frontend-1` | 8080 | RDF-Studio-UI (proxied auf 8082) |

> Compose-Projects: `conx-converters` (erste Datei) und `conx-rdf` (zweite). Container-Namen = `<project>-<service>-1`.

---

## 4. Verifikation (Health-Checks)

API-Health-Endpoints der drei Dienste (Antwort = JSON mit Status):

```powershell
curl.exe http://localhost:8081/api/v1/health   # GAEB      → 200
curl.exe http://localhost:8082/api/v1/health   # RDF       → 200
curl.exe http://localhost:8085/api/v1/health   # MS Project → 200
```

Frontends (statische UI):

```powershell
curl.exe http://localhost:8084/    # Tab-UI (GAEB + MS Project) → HTML
curl.exe http://localhost:8080/    # RDF-Studio                → HTML
```

> `curl.exe http://localhost:8084/api/v1/health` liefert **404 — das ist korrekt**: Port 8084 ist nur die statische UI, sie hat keinen Health-Endpoint.

**End-to-end-Smoke-Test** (ohne eigene Dateien, mit eingebauter Beispieldatei):

```powershell
# GAEB-Beispiel laden und zu TTL konvertieren
curl.exe -o "$env:TEMP\conx-boq.ttl" -X POST "http://localhost:8081/api/v1/convert/gaeb-boq?format=ttl" `
     -F "file=@(Invoke-WebRequest -Uri http://localhost:8081/examples/gaeb-boq.xml -OutFile $env:TEMP\conx-example.xml; $env:TEMP\conx-example.xml)"
```

Einfacher mit zwei Schritten:

```powershell
Invoke-WebRequest http://localhost:8081/examples/gaeb-boq.xml -OutFile "$env:TEMP\conx-example.xml"
curl.exe -X POST "http://localhost:8081/api/v1/convert/gaeb-boq?format=ttl" `
     -F "file=@$env:TEMP\conx-example.xml"
# → Turtle-Ausgabe mit @prefix constructx: …, <http://construct-x.ontology/…> u. a.
```

> Details zu allen Endpunkten, Parametern und Formaten: [USER_GUIDE.md](USER_GUIDE.md).

---

## 5. Stoppen & Neustarten

```powershell
# Alles stoppen + Container entfernen (Volumen bleiben)
docker compose -f converter-compose.yml down
docker compose -f rdf-converter-compose.yml down

# Neustarten (ohne Rebuild)
docker compose -f converter-compose.yml up -d
docker compose -f rdf-converter-compose.yml up -d
```

**Logs** (verifizierte Container-Namen):

```powershell
docker logs conx-converters-gaeb-converter-service-1
docker logs conx-converters-msproject-converter-service-1
docker logs conx-converters-converter-frontend-1
docker logs conx-rdf-rdf-converter-service-1
docker logs conx-rdf-rdf-converter-frontend-1
```

---

## 6. Update nach `git pull`

```powershell
git pull
git submodule update --init ontologies     # Submodul-Pin ggf. mitbewegt
docker compose -f converter-compose.yml up -d --build
docker compose -f rdf-converter-compose.yml up -d --build
```

`--build` ist nötig, weil sich Java-Code im Image befindet. Danach Abschnitt 4 (Health-Checks) wiederholen.

> **Ontologie-Tags:** Die Docker-Images backen die Ontologien aus dem getagten Tarball `v0.5.0` ein. Ein Tag-Bump erfordert eine Änderung im `Dockerfile` (ARG), nicht nur `git pull`.

---

## 7. Troubleshooting

| Symptom | Ursache / Fix |
|---|---|
| Port am Host nicht erreichbar, `docker ps` zeigt Mapping aber Container `Up` | Bekannter Docker-Desktop-Proxy-Fehler (Host-Socket wird nie gebunden, `netstat` zeigt nur `WARTEND`). Fix: `docker compose -f <file> up -d --force-recreate <service>` |
| UI auf 8084/8080 lädt, aber roter Status-Dot / „Dienst wird geprüft" | Backend (8081/8085 bzw. 8082) nicht erreichbar — Health-Check oben nachprüfen, Container-Logs lesen |
| `mvn test` findet keine Ontologie-Dateien | Submodul fehlt: `git submodule update --init ontologies` |
| Build schlägt fehl, `ontologies/`-Referenz im Log | Submodul nicht ausgecheckt (betrifft nur lokale `mvn`-Builds, nicht Docker) |
| PowerShell: `curl` liefert seltsame Fehler | Windows-CURL-Alias — immer `curl.exe` schreiben |
| Nicht-ASCII in Pfaden (z. B. `König` → `K?nig`) | PowerShell-Konsole-Codierung; Befehle im Repo-Verzeichnis mit relativen Pfaden ausführen |

---

## 8. Optional: lokale Unit-Tests (ohne Docker-Betrieb)

Nur für Entwickler, die `mvn test` lokal laufen lassen wollen. Es gibt **kein** Aggregator-Pom im Repo-Root — zuerst das SDK in die lokale Maven-Repository installieren, dann pro Modul testen.

```powershell
git submodule update --init ontologies     # Ontologien für die Tests

# SDK installieren (wird von allen Modulen als 0.1.0-SNAPSHOT referenziert)
cd construct-x-sdk
mvn -q install -DskipTests
cd ..

# Pro Modul (Default-Pfad für Ontologien ist relativ zur Modul-Position, z. B. Converter/core: ../../ontologies)
cd Converter/core
mvn -q -o test
```

Java 25 + Maven 3.9 erforderlich. Alternative ohne lokale Toolchain (einmalig):

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace/construct-x-sdk maven:3.9-eclipse-temurin-25 mvn -q install -DskipTests
docker run --rm -v "${PWD}:/workspace" -w /workspace/Converter/core maven:3.9-eclipse-temurin-25 mvn -q -o test
```

> `Converter/core` hat `byte-buddy-agent` (test-scope) als explizite Dependency — in einer leeren `.m2` muss das Jar per `mvn install`/Download verfügbar sein, sonst crasht der Surefire-Fork. Nicht entfernen.
