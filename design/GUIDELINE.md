# Construct-X Design-Guideline

Stand: 2026-11-06 · Gültig für alle Construct-X-Frontends (Vanilla-JS, statisches HTML, kein Build-Step).

## 1. Quellen & Verbindlichkeit

| Tag | Quelle | Verbindlichkeit |
|---|---|---|
| `[RUB]` | RUB-Markenportal (PDFs in `design/`: Farben, Logo, Icons) | **verbindliche Markenregeln** |
| `[App]` | „Frontend-Designvorgaben: RUB-inspirierte Anwendungen“ (Bauablaufplan-Doku) | Produktentscheidungen, übertragbar, **keine** offiziellen RUB-Vorgaben |
| `[CONX]` | Construct-X-Projectwerte (Domänenfarben, Code-Palette) | Projekt-intern verbindlich |

Jedes Token in `design/tokens.css` trägt sein Quellen-Tag. `[App]`-Werte dürfen ohne
RUB-Freigabe nicht als „RUB-Vorgabe“ nach außen kommuniziert werden.

## 2. Architektur (übertragbar, kein Build-Step)

```
design/
├── tokens.css      ← Single Source of Truth: ALLE Werte (Farben, Fonts, Radien, Icons)
├── conx.css        ← @import tokens.css + alle gemeinsamen Komponenten
├── logo.svg        ← Standard-Logo Topbars (Lockup mit Würfel, rein #003560)
├── logo-rub.svg    ← kompakte Wortmarke ohne Würfel (nur Text-Kontexte)
└── fonts/          ← Self-hosted WOFF2 (DM Sans, Manrope, IBM Plex Mono)
```

Einsatz in einem Frontend — **genau zwei Stylesheets**:

```html
<link rel="stylesheet" href="/design/conx.css?v=1">   <!-- gemeinsam -->
<link rel="stylesheet" href="css/style.css?v=N">      <!-- nur Frontend-Layout -->
```

Regeln:

1. `conx.css` importiert `tokens.css`. Frontends binden `tokens.css` **nicht** einzeln ein.
2. Lokale Stylesheets tragen **nur** Frontend-spezifisches Layout (Spalten, Abstände,
   Seitentitel). Keine neuen Farben, Fonts, Radien, keine Komponenten-Duplikate.
3. Neue Komponenten erst in `conx.css` definieren, dann in allen Frontends nutzen.
4. JS-States bleiben stabil: `.active .hidden .dragover .up .down .online .offline
   .info .success .error .warning`.

### 2.1 Neue App aufsetzen

- **nginx-Volumen** (z. B. converter-frontend): Compose-Volumen
  `./design:/usr/share/nginx/html/design:ro` ergänzen → fertig.
- **Dockerfile-Build** (z. B. rdf-converter-frontend):
  `COPY design/conx.css`, `COPY design/tokens.css`, `COPY design/logo.svg`,
  `COPY design/logo-rub.svg`, `COPY design/fonts/` nach `/usr/share/nginx/html/design/`.
- `?v=N`-Buster wie in §9 führen.

> Hinweis: Der GAEB-Service (`Converter`, 8081) hat **keine eigene UI** mehr —
> die früher eingebettete statische Seite wurde 2026-11 entfernt (UI = 8084/8080).

## 3. Farbe

### 3.1 Markentöne `[RUB]`

- **Primärfarbe = `#003560` (RUB Blau, Pantone 540)** = `--primary`.
  Text auf RUB Blau: Weiß. Text neben RUB Blau: RUB Blau.
- Ergänzungsfarben: 6 Familien (Hellblau, Lila, Rosa, Orange, Gelb, Grün),
  je `100` (Vollton) / `50` (hell). Pro Seite/Medium **maximal eine** Familie als
  gestalterische Farbgruppe; nicht kombinieren; keine exklusiven Zuordnungen.
- **100/50-Regel (digitale Kontraststufen):**
  - Volltöne (`100`) digital: **nur großer Text** (≥ 24 px bzw. ≥ 19 px fett) — erfüllt AA.
  - `50`-Abstufungen: auch kleiner Text — erfüllen AAA.
  - Kleiner Text (< 24 px) auf Volltönen = nicht erlaubt.

### 3.2 UI-Neutraltöne `[App]`

Sehr hell, kühl getönt — **keine grauen Flächen**.

| Token | Wert | Rolle |
|---|---|---|
| `--bg` | `#f8fafc` | Seitenhintergrund |
| `--surface` | `#ffffff` | Karten, Panels |
| `--surface-muted` | `#f2f5f8` | gedämpfte Fläche (Dropzone, Gruppierung) |
| `--text` | `#162636` | Primärtext |
| `--muted` | `#506071` | Sekundärtext, Labels, Meta |
| `--border` | `#dae0e7` | feine blau-graue Grenzen |
| `--selection` | `#e9f4fb` | aktive Fläche: Hover, Auswahl, Chips |

### 3.3 UI-States

`--success / --warning / --error / --info / --pending` je mit `-soft`-Flächenversion.
Dunkle Textwerte auf hellen Soft-Flächen (Kontrast ≥ 4.5:1), z. B. `--warning #9a6700`
auf Gelb-50 statt Vollton-Gelb.

**Status nie nur über Farbe:** Status-Meldungen tragen zusätzlich ein Symbol
(`::before`: `i` / `✓` / `!`), Service-Dots immer mit Text-Label daneben.

### 3.4 Diagramm-/Infografik-Palette `[RUB]`

`#4296E9 / #E560C8 / #E8613D / #899597 / #18A748` — nur für **Datenreihen** auf hellem
Hintergrund (Mindestkontrast 3:1), zusätzlich beschriften bzw. mit Formen/Mustern
unterscheiden. Keine UI-Farben ableiten.

### 3.5 Domänenfarben `[CONX]` — dokumentierte Ausnahme

Die 9 Ontologie-Domänen haben je eine Kennfarbe (`--dom-*`, nur für Chips/Labels/
Legenden/Icons). Dies ist eine **bewusste, dokumentierte Ausnahme** zur RUB-Regel
„eine Farbgruppe pro Medium“: Die Domänenfarben erscheinen in Construct-X ausschließlich
als kleine Kennzeichnungselemente (keine Flächen, keine Textfarben) und dienen der
domänenübergreifenden Orientierung. `--dom-schedule` = RUB Blau.

## 4. Typografie

| Rolle | Font | Token |
|---|---|---|
| Fließtext, UI | **DM Sans** (400–700, variable) | `--font-body` |
| Überschriften | **Manrope** (400–800, variable) | `--font-display` |
| Daten: IDs, Zahlen, Code, Ordnungsnummern | **IBM Plex Mono** (400/500) | `--font-mono` |

- Self-hosted WOFF2 in `design/fonts/` (Subsets latin + latin-ext), `font-display: swap`.
  Produktentscheidung `[App]` — keine offizielle RUB-Schriftvorgabe.
- Größen: `--text-xs 12 px` (Minimum) → `--text-2xl 32 px` (Hero). Ab 24 px zählt
  „großer Text“ für die 100/50-Regel (§3.1).
- Zahlen/IDs, die verglichen werden: `font-variant-numeric: tabular-nums`
  (Klassen: `.tnum`, `.mono`, `.file-meta`, `.preview-meta`, `.result-summary`,
  `.tab-status-count`).
- Eyebrow (kicker): Mono, 12 px, Uppercase, Letter-Spacing 0.14em, `--muted`.

## 5. Layout & Komponenten

- Spacing: 4-px-Grundraster (`--space-1 … --space-12`). Mehr Abstand **zwischen**
  Hauptbereichen als **innerhalb** von Gruppen.
- Radien: `--radius-xs 4 px` (Chips) · `--radius-sm 5 px` (Buttons/Inputs) ·
  `--radius 8 px` (Karten, generisch) · `--radius-lg 12 px` (große Panels).
- Weißraum statt grauer Flächen; Hierarchie über Typografie + Abstand, nicht über Flächen.
- Standardbreite Content-Container: 1080 px (`.container`); rdf-frontend nutzt 1180 px
  im eigenen 2-Spalten-Layout.

### 6. Komponenten-Katalog (`conx.css`)

| Klasse | Zweck |
|---|---|
| `.topbar`, `.brand`, `.brand-logo`, `.brand-name` | Sticky-Topbar (72 px) mit Logo-Lockup (60 px) |
| `.topbar-status`, `.state-dot[.online/.offline]` | Service-Status (Dot + Text) |
| `.tabs`, `.tab[.active]`, `.tab-status[.up/.down]` | Tab-Navigation mit Status-Punkten |
| `.card` | Standardkarte; `.step`/`.step-num` = Schritt-Kopf mit Nummernkreis |
| `.dropzone[.dragover]`, `.dz-icon`, `.dz-title`, `.dz-hint` | Datei-Dropzone |
| `.file-pill`, `.file-icon`, `.file-details`, `.file-remove` | ausgewählte Datei |
| `.field`, `.field-label`, `.select`, `.radio` | Formulare |
| `.btn`, `.btn-primary`, `.btn-secondary`, `.btn-small`, `.btn-block`, `.actions` | Buttons |
| `.run-button` | große Lauf-Aktion (RDF-Studio) |
| `.status[.info/.success/.error/.warning]` | Statusmeldungen (Basis = Info; Symbol via `::before`) |
| `.warnings` | Rückwärtskonvertierungs-Warnungen |
| `.result-meta`, `.result-summary` | Ergebnis-Kopf |
| `.code`, `.result-output`, `.hl-*` | dunkles Code-Panel + Syntax-Highlighting |
| `.error-output` | Fehlerliste |
| `.report-list`, `.report-item` | SHACL-Bericht (RDF-Studio) |
| `.preview-details` | expandierbare Vorschau (`<details>`) |
| `.preview`, `.preview-head`, `.preview-title`, `.preview-meta` | Datei-Vorschau (Converter) |
| `.icon.icon-boq/-schedule/-progress/-site/-delivery` | Domänen-Icons (CSS-Data-URIs) |
| `.ic`, `.ic-16`, `.ic-24` | Inline-SVG (Lucide), erbt Textfarbe |
| `.footer` | Footer (2-spaltig via `.container`) |
| `.hidden`, `.muted`, `.eyebrow`, `.tnum` | Utilities |

**HTML-Muster — Topbar:**

```html
<header class="topbar">
  <div class="container">
    <a class="brand" href="/" aria-label="…Startseite">
      <img class="brand-logo" src="/design/logo.svg" alt="Lehrstuhl Computing in Engineering, Ruhr-Universität Bochum">
      <span class="brand-name">Construct-X <span>Converter</span></span>
    </a>
    <!-- optional rechts: -->
    <div class="topbar-status"><span class="state-dot"></span><span id="serviceStatus">…</span></div>
  </div>
</header>
```

**HTML-Muster — Status-Meldung:**

```html
<div id="status" class="status hidden" role="status"></div>
<!-- JS: el.className = 'status ' + kind; (info|success|error|warning) -->
```

**HTML-Muster — Dropzone:**

```html
<div id="dropzone" class="dropzone" tabindex="0">
  <span class="dz-icon" aria-hidden="true">
    <svg class="ic ic-24" viewBox="0 0 24 24">…Lucide-Pfade…</svg>
  </span>
  <p class="dz-title"><strong>Datei hierher ziehen</strong> oder klicken zum Auswählen</p>
  <p class="dz-hint">…</p>
  <input type="file" id="fileInput" hidden>
</div>
```

### 6.1 Neue App aufsetzen (Checkliste)

1. `design/` binden (vgl. §2.1) + eigene schmale Layout-Datei anlegen.
2. Topbar-Muster aus §6 verwenden.
3. IDs/Klassen der bestehenden JS-Logik 1:1 erhalten (`.active .hidden .dragover …`).
4. Cache-Buster `?v=1` setzen.

## 7. Logo

- **Standard für Topbars** = `logo.svg` (Lehrstuhl-Lockup „Computing in
  Engineering“ + RUB-Würfel + Wortmarke, ein horizontales Logo, rein `#003560`):
  **60 px hoch** (`--logo-lockup-h`) in der **72-px-Topbar** — so erfüllt der
  Würfel (≈ 60 % der Bildhöhe) die **Mindestgröße 36 px**. Schutzraum = 1/5 der
  Würfelhöhe (≈ 7 px), durch die Topbar-Höhe gedeckt.
- **Mobile-Ausnahme** (≤ 640 px): Lockup 48 px (Würfel ≈ 29 px), damit Brand-Logo
  und Produktname nebeneinander passen.
- **Kleine Smartphones** (≤ 374 px): Lockup 44 px und nur „Construct-X" als
  Brand-Name (App-Variante „Converter" / „RDF Studio" weggelassen), damit die
  Topbar auch bei 320 px ohne Horizontal-Scroll bleibt (volle Topbar braucht
  ~365 px; bei 375/390 px bleibt der volle Name).
- `logo-rub.svg` ist die **kompakte Wortmarke ohne Würfel** (3 Zeilen) — nur für
  enge, textlastige Kontexte, **nie als alleiniges Markenzeichen** in Topbars
  (verformt bei kleinen Größen, keine Ikonomie).
- Negativ-Variante (weiß) nur auf RUB-Blaue; B/W nur wenn alle Logos daneben B/W sind.
- Favicon/App-Icon: gesonderte Würfel-im-Kreis-Version (bei cd@rub.de anfragen).

## 8. Barrierefreiheit & Motion

- Fokus immer sichtbar: `:focus-visible` mit `--focus-ring` (2 px Primär).
- Status/Interaktion **nie nur über Farbe** (Symbol/Text/Form zusätzlich).
- `prefers-reduced-motion: reduce` → Animations/Transitions deaktiviert.
- Formulare: Labels sichtbar (`field-label`), Radio/Checkbox in Primärfarbe
  (`accent-color`), Dropzone per Tastatur bedienbar (`tabindex="0"`, Enter/Space).
- `role="tablist"/tab"`, `aria-selected`, `aria-live="polite"` bei Ergebnis-Panels.
- Text-Größengrenzen und Kontraststufen nach §3.1/§3.3 (WCAG 2.2).

## 9. Wartung & Cache-Busting

1. **Token ändern → nur in `design/tokens.css`** (nie lokal in Frontends).
2. **Komponente ändern → nur in `design/conx.css`.**
3. Danach **Cache-Buster `?v=N` in allen `index.html` erhöhen** (conx.css und/oder
   style.css):
   - `converter-frontend/index.html`
   - `rdf-converter-frontend/index.html`
4. Docker: `converter-frontend` bekommt `design/` per Volume (live);
   `rdf-converter-frontend` per `COPY` im Dockerfile → **Image neu bauen**
   (`docker compose -f rdf-converter-compose.yml up --build -d`).

## 10. Icons

- **UI-Icons: Lucide, inline im HTML** mit Klasse `.ic` (`stroke="currentColor"` wird
  über CSS gesteuert) → erben die Textfarbe. Keine Emoji, keine Icon-Fonts.
- **Domänen-Icons:** CSS-Data-URIs (`--icon-dom-*` in `tokens.css`) mit **fester
  Primärfarbe `#003560`** — `currentColor` wird in `background-image`-URIs *nicht*
  geerbt, daher bewusst in den URI kodiert. Einsatz: `.icon.icon-boq` u. a.
- `design/Icons/` (5 Lucide-SVGs) und `design/Elements/` (Entypo-Bibliothek, 419 Dateien)
  sind Referenz-/Quellmaterial; neue Icons aus Lucide ergänzen, Stil: 2 px Stroke,
  runde Kappen.

## 11. Code-Panels

- Dunkles, read-only Panel (`.code`): `--code-bg #0f172a` auf hellem UI-Kontrast.
- Syntax-Palette in `tokens.css` (`--hl-*`), Kontrast zu `--code-bg` ≥ 4.5:1.
- Highlighting: `js/highlight.js` (Converter-Frontends), Zeilennummern-Gutter mit
  `user-select: none`, Content mit `user-select: text`.
- JSON-Output serverseitig ausschließlich über Jena RIOT + `PlainJsonModelWriter`
  (AGENTS.md) — die UI ändert daran nichts.
