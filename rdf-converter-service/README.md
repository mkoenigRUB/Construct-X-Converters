# Construct-X RDF Converter Service

Unabhängiger Spring-Boot-Dienst für Turtle/JSON-LD-Konvertierung und SHACL-Validierung. Er ist nicht Teil des GAEB-Converters. Die Ontologie- und SHACL-Dateien werden lokal aus dem Ontologien-Repository geladen; externe JSON-LD-Kontexte werden abgelehnt.

## Lokal starten

Voraussetzung: Java 25 und Maven 3.9+.

```powershell
cd rdf-converter-service
mvn spring-boot:run
```

Der Dienst erwartet standardmäßig die Ontologieverzeichnisse im übergeordneten Repository-Verzeichnis. Für einen anderen Speicherort kann `CONX_ONTOLOGY_ROOT` gesetzt werden. Standardport: `8082`.
Für ein getrennt gehostetes Frontend kann `CONX_CORS_ALLOWED_ORIGINS` auf eine kommaseparierte Liste erlaubter Origins gesetzt werden; standardmäßig ist `http://localhost:8080` freigegeben.

## API

- `GET /api/v1/health` — Health-Status
- `GET /api/v1/profiles` — verfügbare Domain- und Link-SHACL-Profile
- `POST /api/v1/convert` — Multipart-Felder `file`, `sourceFormat` (`ttl`/`jsonld`) und `targetFormat`
- `POST /api/v1/validate` — Multipart-Felder `file`, `format` und `profile`

Die Validierung kombiniert das hochgeladene RDF mit den lokalen Ontologie- und Vokabulargraphen, führt die SHACL-Dateien des gewählten Profils aus und liefert Verletzungen strukturiert zurück. Ein syntaktisch ungültiges Dokument oder ein unbekanntes Profil wird als HTTP 400 gemeldet; SHACL-Verletzungen sind ein erfolgreicher Prüfaufruf mit `valid: false`.

## Tests

```powershell
mvn test
```
