# AGENTS.md — Regeln für schnelles Vibe Coding

## Ton & Ablauf
- Deutsch, knapp, ohne Floskeln. Kein Brainstorming, keine Plan-/Design-Dokumente, keine Reviews von Haus aus.
- Zweifel → direkt an der Stelle verifizieren (Code/Tests/Daten ausführen), nicht fragen.
- Blocker → genau eine Frage, dann warten.
- Fertig = kurzer Report: Dateien, ausgeführte Befehle + Ergebnis, Risiken. Keine erfundenen Testergebnisse.

## Stack (tatsächlich — davon nicht abweichen)
- Java 25, Spring Boot 3.5, Apache Jena 4.10, Maven. Windows/PowerShell.
- Git-Repo: `https://github.com/mkoenigRUB/Construct-X-Converters` (Remote `origin`, Branch `main`).
  Windows-Pfad ist tief → `core.longpaths=true` gesetzt. Push via `gh`-Token (Windows Credential Manager).
- Frontends: Vanilla-JS, statisches HTML/CSS, nginx — kein Build-Schritt.
- Betrieb: Docker Compose (`converter-compose.yml`, `rdf-converter-compose.yml`).
- `docs/ARCHITECTURE.md` (Next.js/TS/Postgres) ist veraltet — diese Datei gilt.

## Service-Map
- `Converter` (GAEB BoQ): 8081 (API nur, keine eigene UI) · `converter-frontend`: 8084
- `rdf-converter-service`: 8082 · `rdf-converter-frontend`: 8080
- `msproject-converter-service`: 8085

## Fallen
- `PlainJsonModelWriter` + `ConxCurieRegister`: 3 identische Kopien (Converter/core, rdf-converter-service, msproject-converter-service) → Änderungen in allen drei spiegeln und diffen.
- Der GAEB-Dienst (8081) serviert **keine UI** mehr (eingebettete statische Seite 2026-11 entfernt) → 8081 nie als Frontend referenzieren; UI = 8084/8080.
- Static-Assets mit Cache-Buster `?v=N` in `index.html` → nach CSS/JS-Änderungen bumpen.
- Lange `javac -cp` brechen auf Windows → `mvn` nutzen oder CLASSPATH-Env-Var.
- Tests offline: `mvn -q -o test` im Modul-Verzeichnis.

## RDF-Konventionen
- IRIs nur über `IriFactory`, Namespace-Konstanten in `ConxNamespaces`.
- Versionierte Ontologie-IRIs (`…/0.5.0`) erst nach Absprache bumpen; Shapes in `<ont>-shapes-core.ttl`.
- JSON-Output nur über Jena RIOT + `PlainJsonModelWriter`, keine neuen JSON-Libs.

## Tests
- JUnit 5 / Spring Boot Test. Verhaltensänderung = Test mitliefern.
- Konverter-Logik: Negativfälle nicht vergessen (leere BOQ, fehlende Attribute, QtyPcnt-Splits, MLTexts).
- Vor Abschluss: Tests ausführen und echten Output zeigen.
- **Ontologien = Submodul `ontologies/`** (pinned Commit, Tag `v0.5.0` des
  `mkoenigRUB/Construct-X-Ontologies`-Repos). Clone:
  `git clone --recurse-submodules <url>` bzw. nachträglich
  `git submodule update --init ontologies`. `mvn test` läuft danach
  out-of-the-box (Defaults: SDK `../ontologies` aus `construct-x-sdk/`,
  `Converter/core` `../../ontologies`, rdf-service `../ontologies`).
  Override: `CONX_ONTOLOGY_ROOT` (Env) / `conx.ontology.root` (sysprop).
  Docker-Images backen die Ontologien aus dem getagten Tarball
  (`…/archive/refs/tags/v0.5.0.tar.gz`) — Tag-Bump = ARG in
  `rdf-converter-service/Dockerfile` + `msproject-converter-service/Dockerfile`
  ÄNDERN + ggf. Submodul-Pin (`git -C ontologies fetch --tags; git -C ontologies
  checkout <tag>`) mitbewegen.
- **Frische `.m2` / CI:** `Converter/core` hat `byte-buddy-agent` (test-scope) als
  explizite Dependency — ohne würde der Surefire-Fork (argLine prelaedt das Jar aus
  `${settings.localRepository}`) in einer leeren `.m2` mit „agent library failed"
  crashen. Dependency nicht entfernen.

## Erst nach Freigabe
- Deployments, Datenlöschung, Port-/Security-Änderungen, Software-Installationen.

## Skills
- Nur Skills aus der Allow-List in `opencode.json` laden. Standard: keiner.
