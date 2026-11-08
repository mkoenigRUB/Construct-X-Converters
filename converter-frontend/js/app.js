// --- Service endpoints (independent services) ---
const port = location.port;
const GAEB_API = (port === '8081') ? '/api/v1' : 'http://localhost:8081/api/v1';
const MS_API = (port === '8085') ? '/api/v1' : 'http://localhost:8085/api/v1';

// --- Tabs ---
document.querySelectorAll('.tab').forEach(tab => {
    tab.addEventListener('click', () => {
        document.querySelectorAll('.tab').forEach(t => {
            t.classList.remove('active');
            t.setAttribute('aria-selected', 'false');
        });
        document.querySelectorAll('.tab-panel').forEach(p => p.classList.add('hidden'));
        tab.classList.add('active');
        tab.setAttribute('aria-selected', 'true');
        document.getElementById('panel-' + tab.dataset.tab).classList.remove('hidden');
    });
});

// --- Service health indicators ---
async function checkHealth(apiBase, indicatorId) {
    const el = document.getElementById(indicatorId);
    try {
        const res = await fetch(apiBase + '/health', { method: 'GET' });
        el.className = 'tab-status ' + (res.ok ? 'up' : 'down');
        el.title = res.ok ? 'Dienst erreichbar' : 'Dienst antwortet nicht korrekt';
    } catch (e) {
        el.className = 'tab-status down';
        el.title = 'Dienst nicht erreichbar';
    }
}
checkHealth(GAEB_API, 'status-gaeb');
checkHealth(MS_API, 'status-msproject');
setInterval(() => {
    checkHealth(GAEB_API, 'status-gaeb');
    checkHealth(MS_API, 'status-msproject');
}, 15000);

// --- Shared helpers ---
// Format descriptors: radio value -> API output format.
const FORMAT = {
    ttl: { label: 'TTL', ext: '.ttl', mediaType: 'text/turtle', hlLang: 'ttl' },
    json: { label: 'JSON-LD', ext: '.jsonld', mediaType: 'application/ld+json', hlLang: 'jsonld' },
    plainjson: { label: 'JSON (plain)', ext: '.json', mediaType: 'application/json', hlLang: 'json' }
};

function iriCount(text, formatValue) {
    const pattern = formatValue === 'plainjson'
        ? /"id":\s*"(?:https?:|_:)/g
        : formatValue === 'json'
            ? /"@id":\s*"https?:/g
            : /<\s*https?:/g;
    return (text.match(pattern) || []).length;
}

function envelopeChecked(prefix) {
    const el = document.getElementById(prefix + '-envelope');
    return el ? el.checked : false;
}

function envelopeFileName(name, envelope) {
    return envelope ? name.replace(/\.json$/i, '-package.json') : name;
}

function formatSize(bytes) {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

function setStatus(prefix, msg, kind) {
    const el = document.getElementById(prefix + '-status');
    el.textContent = msg;
    el.className = 'status ' + kind;
    el.classList.remove('hidden');
}

function hideResults(prefix) {
    document.getElementById(prefix + '-resultSection').classList.add('hidden');
    document.getElementById(prefix + '-errorSection').classList.add('hidden');
    document.getElementById(prefix + '-status').classList.add('hidden');
}

function highlightInto(el, text, lang) {
    // Read-only syntax highlighting; falls back to plain text for
    // very large inputs or on any highlighting error.
    var html = null;
    try { html = window.ConXHighlight.render(text, lang); } catch (e) { html = null; }
    if (html !== null) { el.innerHTML = html; }
    else { el.textContent = text; }
}

function showResult(prefix, text, meta) {
    var out = document.getElementById(prefix + '-resultOutput');
    highlightInto(out, text, out.dataset.lang || 'ttl');
    document.getElementById(prefix + '-resultMeta').textContent = meta;
    document.getElementById(prefix + '-resultSection').classList.remove('hidden');
}

// --- Read-only file preview (section 1, both tabs) ---
function showPreview(prefix, text, fileName) {
    var panel = document.getElementById(prefix + '-preview');
    var out = document.getElementById(prefix + '-previewOutput');
    var meta = document.getElementById(prefix + '-previewMeta');
    meta.textContent = fileName + ' · ' + text.split('\n').length + ' Zeilen · XML';
    highlightInto(out, text, 'xml');
    panel.classList.remove('hidden');
}

function hidePreview(prefix) {
    var panel = document.getElementById(prefix + '-preview');
    panel.classList.add('hidden');
    panel.querySelector('pre').textContent = '';
}

function showViolations(prefix, violations) {
    const ul = document.createElement('ul');
    violations.forEach(v => {
        const li = document.createElement('li');
        li.textContent = (v.path ? v.path.split('#').pop() + ': ' : '') + (v.message || 'Unbekannter Verstoß');
        ul.appendChild(li);
    });
    const out = document.getElementById(prefix + '-errorOutput');
    out.innerHTML = '';
    out.appendChild(ul);
    document.getElementById(prefix + '-errorSection').classList.remove('hidden');
}

function showErrors(prefix, errors) {
    const ul = document.createElement('ul');
    errors.forEach(e => {
        const li = document.createElement('li');
        li.textContent = (e.line >= 0)
            ? `Zeile ${e.line}, Spalte ${e.column}: ${e.message}`
            : e.message;
        ul.appendChild(li);
    });
    const out = document.getElementById(prefix + '-errorOutput');
    out.innerHTML = '';
    out.appendChild(ul);
    document.getElementById(prefix + '-errorSection').classList.remove('hidden');
}

function setupDropzone(prefix, onFile) {
    const dropzone = document.getElementById(prefix + '-dropzone');
    const fileInput = document.getElementById(prefix + '-fileInput');
    dropzone.addEventListener('click', () => fileInput.click());
    dropzone.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); fileInput.click(); }
    });
    fileInput.addEventListener('change', () => {
        if (fileInput.files.length) onFile(fileInput.files[0]);
    });
    ['dragenter', 'dragover'].forEach(evt =>
        dropzone.addEventListener(evt, (e) => { e.preventDefault(); dropzone.classList.add('dragover'); }));
    ['dragleave', 'drop'].forEach(evt =>
        dropzone.addEventListener(evt, (e) => { e.preventDefault(); dropzone.classList.remove('dragover'); }));
    dropzone.addEventListener('drop', (e) => {
        if (e.dataTransfer.files.length) onFile(e.dataTransfer.files[0]);
    });
    return fileInput;
}

function setupDownload(prefix, state) {
    document.getElementById(prefix + '-downloadBtn').addEventListener('click', () => {
        if (!state.output) return;
        const blob = new Blob([state.output], { type: state.outputType });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = state.outputName;
        a.click();
        URL.revokeObjectURL(url);
    });
}

// ==================== GAEB TAB ====================
const gaebState = { file: null, output: null, outputName: 'gaeb-boq.ttl', outputType: 'text/turtle', selectionTree: null };

const gaebFileInput = setupDropzone('gaeb', (file) => {
    if (!/\.xml$/i.test(file.name) && file.type !== 'application/xml' && file.type !== 'text/xml') {
        alert('Bitte eine XML-Datei auswählen.');
        return;
    }
    gaebState.file = file;
    document.getElementById('gaeb-fileName').textContent = file.name;
    document.getElementById('gaeb-fileSize').textContent = formatSize(file.size);
    document.getElementById('gaeb-fileInfo').classList.remove('hidden');
    document.getElementById('gaeb-convertBtn').disabled = false;
    document.getElementById('gaeb-validateBtn').disabled = false;
    hideResults('gaeb');
    file.text().then((text) => showPreview('gaeb', text, file.name)).catch(() => {});
    loadSelectionTree('gaeb', file);
});

document.getElementById('gaeb-removeFile').addEventListener('click', () => {
    gaebState.file = null;
    gaebState.selectionTree = null;
    gaebFileInput.value = '';
    document.getElementById('gaeb-fileInfo').classList.add('hidden');
    document.getElementById('gaeb-convertBtn').disabled = true;
    document.getElementById('gaeb-validateBtn').disabled = true;
    hideResults('gaeb');
    hidePreview('gaeb');
    hideSelection('gaeb');
});

document.getElementById('gaeb-exampleBtn').addEventListener('click', async () => {
    const btn = document.getElementById('gaeb-exampleBtn');
    btn.disabled = true;
    setStatus('gaeb', 'Lade Beispieldatei …', 'info');
    try {
        const res = await fetch(GAEB_API + '/examples/gaeb-boq.xml');
        if (!res.ok) throw new Error('Beispieldatei nicht verfügbar (' + res.status + ')');
        const text = await res.text();
        const file = new File([text], 'gaeb-x80-example.xml', { type: 'application/xml' });
        gaebState.file = file;
        document.getElementById('gaeb-fileName').textContent = file.name;
        document.getElementById('gaeb-fileSize').textContent = formatSize(file.size);
        document.getElementById('gaeb-fileInfo').classList.remove('hidden');
        document.getElementById('gaeb-convertBtn').disabled = false;
        document.getElementById('gaeb-validateBtn').disabled = false;
        showPreview('gaeb', text, file.name);
        setStatus('gaeb', 'Beispieldatei geladen: gaeb-x80-example.xml', 'success');
        loadSelectionTree('gaeb', file);
    } catch (err) {
        setStatus('gaeb', `Beispieldatei konnte nicht geladen werden: ${err.message}`, 'error');
    } finally {
        btn.disabled = false;
    }
});

// --- Bauposten-Auswahl (GAEB, Checkbox-Baum) ---
async function loadSelectionTree(prefix, file) {
    const statusEl = document.getElementById(prefix + '-selectionStatus');
    statusEl.classList.remove('hidden');
    statusEl.textContent = 'Lade Bauposten-Auswahl …';
    const form = new FormData();
    form.append('file', file);
    try {
        const res = await fetch(GAEB_API + '/positions/gaeb-boq', { method: 'POST', body: form });
        const data = await res.json().catch(() => null);
        if (!res.ok || !Array.isArray(data)) {
            throw new Error((data && data.error) || ('HTTP ' + res.status));
        }
        gaebState.selectionTree = data;
        renderSelectionTree('gaeb', data);
        statusEl.textContent = 'Auswahl geladen. Haken = Position in die Konvertierung aufnehmen; '
            + 'keine Auswahl = komplette LV.';
    } catch (err) {
        gaebState.selectionTree = null;
        document.getElementById(prefix + '-selectionTree').innerHTML = '';
        statusEl.textContent = 'Bauposten-Auswahl nicht verfügbar: ' + err.message;
    }
}

function hideSelection(prefix) {
    document.getElementById(prefix + '-selection').classList.add('hidden');
    document.getElementById(prefix + '-selectionTree').innerHTML = '';
    document.getElementById(prefix + '-selectionStatus').classList.add('hidden');
}

function setTreeChecked(entries, checked) {
    (entries || []).forEach(entry => {
        if (entry.el) {
            entry.el.checked = checked;
            entry.el.indeterminate = false;
        }
        setTreeChecked(entry.children, checked);
    });
}

function renderSelectionTree(prefix, entries) {
    const container = document.getElementById(prefix + '-selectionTree');
    container.innerHTML = '';
    const tree = [];
    entries.forEach(entry => {
        const node = buildTreeNode(prefix, entry);
        tree.push(node);
        container.appendChild(node.root);
    });
    updateSelectionStatus(prefix, tree);
    // Save the tree for the all/none buttons.
    container._tree = tree;
}

function buildTreeNode(prefix, entry, parentEntry) {
    const node = document.createElement('div');
    node.className = 'sel-node';
    node.setAttribute('role', 'treeitem');

    const row = document.createElement('div');
    row.className = 'sel-row';

    const cb = document.createElement('input');
    cb.type = 'checkbox';
    cb.value = entry.ordinalNumber || '';
    cb.dataset.id = entry.id || '';
    cb.dataset.kind = entry.kind;

    const oz = document.createElement('span');
    oz.className = 'sel-oz tnum';
    oz.textContent = entry.ordinalNumber || entry.id || '?';

    const name = document.createElement('span');
    name.className = 'sel-name';
    name.textContent = entry.name || entry.id;

    const count = document.createElement('span');
    count.className = 'sel-count';
    if (entry.kind === 'SECTION' && entry.itemCount > 0) {
        count.textContent = entry.itemCount + (entry.itemCount === 1 ? ' Position' : ' Positionen');
    }

    row.appendChild(cb);
    row.appendChild(oz);
    row.appendChild(name);
    if (entry.kind === 'SECTION' && entry.children && entry.children.length > 0) {
        const kind = document.createElement('span');
        kind.className = 'sel-kind';
        kind.textContent = 'Posten';
        row.appendChild(kind);
    }
    row.appendChild(count);
    node.appendChild(row);

    const nodeEntry = { root: node, el: cb, entry: entry, children: [], parent: parentEntry || null };

    if (entry.kind === 'SECTION' && entry.children && entry.children.length > 0) {
        const kids = document.createElement('div');
        kids.className = 'sel-children';
        entry.children.forEach(child => {
            const childNode = buildTreeNode(prefix, child, nodeEntry);
            kids.appendChild(childNode.root);
            nodeEntry.children.push(childNode);
        });
        node.appendChild(kids);
    }

    // Row click toggles the checkbox (except clicks on the checkbox itself).
    row.addEventListener('click', (e) => {
        if (e.target === cb) return;
        cb.checked = !cb.checked;
        cb.dispatchEvent(new Event('change', { bubbles: false }));
    });
    cb.addEventListener('change', () => {
        // Section checkbox cascades to its whole subtree (sub-sections + items);
        // the API selection itself only carries item ordinal numbers.
        if (entry.kind === 'SECTION') {
            setTreeChecked(nodeEntry.children, cb.checked);
        }
        // Item/section change bubbles up: refresh ancestor section state.
        syncAncestors(nodeEntry);
        updateSelectionStatus(prefix,
            containerTree(document.getElementById(prefix + '-selectionTree')));
    });
    return nodeEntry;
}

/**
 * Re-derives the checked/indeterminate state of every ancestor section
 * checkbox from its items, so that checking a single item marks its parent
 * section as "partially selected".
 */
function syncAncestors(nodeEntry) {
    let ancestor = nodeEntry.parent;
    while (ancestor) {
        const items = descendantItems(ancestor);
        const checkedItems = items.filter(i => i.el.checked).length;
        ancestor.el.indeterminate = checkedItems > 0 && checkedItems < items.length;
        ancestor.el.checked = items.length > 0 && checkedItems === items.length;
        ancestor = ancestor.parent;
    }
}

/** All item nodes within the subtree of the given section node entry. */
function descendantItems(nodeEntry) {
    const result = [];
    const walk = (nodes) => {
        nodes.forEach(t => {
            if (t.entry.kind === 'ITEM') result.push(t);
            walk(t.children);
        });
    };
    walk(nodeEntry.children);
    return result;
}

function containerTree(container) {
    return container && container._tree ? container._tree : [];
}

function updateSelectionStatus(prefix, tree) {
    const statusEl = document.getElementById(prefix + '-selectionStatus');
    const checked = countChecked(tree);
    const totalItems = countItems(tree);
    statusEl.classList.remove('hidden');
    statusEl.textContent = checked > 0
        ? checked + ' von ' + totalItems + ' Position(en) ausgewählt.'
        : 'Keine Auswahl — es wird die komplette LV konvertiert.';
}

function countChecked(tree) {
    let n = 0;
    tree.forEach(t => {
        if (t.el && t.el.checked && t.entry.kind === 'ITEM') n++;
        n += countChecked(t.children);
    });
    return n;
}

function countItems(tree) {
    let n = 0;
    tree.forEach(t => {
        n += (t.entry && t.entry.kind === 'ITEM') ? 1 : 0;
        n += countItems(t.children);
    });
    return n;
}

function selectedOrdinalNumbers() {
    const tree = containerTree(document.getElementById('gaeb-selectionTree'));
    const selected = [];
    const walk = (nodes) => {
        nodes.forEach(t => {
            if (t.el && t.el.checked && t.entry.kind === 'ITEM') {
                selected.push(t.entry.ordinalNumber);
            }
            walk(t.children);
        });
    };
    walk(tree);
    return selected;
}

document.getElementById('gaeb-selectionAllBtn').addEventListener('click', () => {
    const tree = containerTree(document.getElementById('gaeb-selectionTree'));
    setTreeChecked(tree, true);
    tree.forEach(t => { t.el.indeterminate = false; });
    updateSelectionStatus('gaeb', tree);
});

document.getElementById('gaeb-selectionNoneBtn').addEventListener('click', () => {
    const tree = containerTree(document.getElementById('gaeb-selectionTree'));
    setTreeChecked(tree, false);
    updateSelectionStatus('gaeb', tree);
});

function gaebFormat() {
    const el = document.querySelector('input[name="gaeb-format"]:checked');
    return el ? el.value : 'ttl';
}

async function gaebRun(endpoint, isConvert) {
    if (!gaebState.file) return;
    hideResults('gaeb');
    setStatus('gaeb', isConvert ? 'Konvertiere …' : 'Validiere …', 'info');
    document.getElementById('gaeb-convertBtn').disabled = true;
    document.getElementById('gaeb-validateBtn').disabled = true;

    const form = new FormData();
    form.append('file', gaebState.file);
    const isPlainJson = gaebFormat() === 'plainjson';
    if (isConvert) {
        form.append('format', gaebFormat());
        if (isPlainJson) form.append('envelope', String(envelopeChecked('gaeb')));
        const selection = selectedOrdinalNumbers();
        if (selection.length > 0) form.append('selection', selection.join(','));
    }

    try {
        const res = await fetch(GAEB_API + endpoint, { method: 'POST', body: form });
        if (res.ok && isConvert) {
            const text = await res.text();
            const formatValue = gaebFormat();
            const fmt = FORMAT[formatValue] || FORMAT.ttl;
            gaebState.output = text;
            document.getElementById('gaeb-resultOutput').dataset.lang = fmt.hlLang;
            gaebState.outputName = envelopeFileName('gaeb-boq-' + gaebState.file.name.replace(/\.xml$/i, '') + fmt.ext,
                isPlainJson && envelopeChecked('gaeb'));
            gaebState.outputType = fmt.mediaType;
            const count = iriCount(text, formatValue);
            const countLabel = formatValue === 'plainjson' ? 'Knoten' : 'IRI-Referenzen';
            showResult('gaeb', text, `${gaebState.file.name} → ${gaebState.outputName} · ${fmt.label} · ${formatSize(text.length)} · ${count} ${countLabel}`);
            setStatus('gaeb', 'Konvertierung erfolgreich.', 'success');
        } else if (res.ok) {
            const data = await res.json();
            if (data.valid) {
                setStatus('gaeb', 'XSD-Validierung erfolgreich: Dokument ist konform.', 'success');
            } else {
                showErrors('gaeb', data.errors || []);
                setStatus('gaeb', `XSD-Validierung fehlgeschlagen (${data.errorCount} Fehler).`, 'error');
            }
        } else {
            const data = await res.json().catch(() => ({}));
            if (data.errors) {
                showErrors('gaeb', data.errors);
                setStatus('gaeb', `XSD-Validierung fehlgeschlagen (${data.errorCount} Fehler).`, 'error');
            } else {
                setStatus('gaeb', `Fehler: ${data.error || res.status}`, 'error');
            }
        }
    } catch (err) {
        setStatus('gaeb', `Netzwerkfehler: ${err.message}`, 'error');
    } finally {
        document.getElementById('gaeb-convertBtn').disabled = false;
        document.getElementById('gaeb-validateBtn').disabled = false;
    }
}

document.getElementById('gaeb-convertBtn').addEventListener('click', () => gaebRun('/convert/gaeb-boq', true));
document.getElementById('gaeb-validateBtn').addEventListener('click', () => gaebRun('/validate/gaeb-boq', false));
setupDownload('gaeb', gaebState);

// ==================== MS PROJECT TAB ====================
const msState = { file: null, output: null, outputName: 'schedule.ttl', outputType: 'text/turtle' };

const msFileInput = setupDropzone('ms', (file) => {
    if (!/\.xml$/i.test(file.name) && file.type !== 'application/xml' && file.type !== 'text/xml') {
        alert('Bitte eine XML-Datei auswählen.');
        return;
    }
    msState.file = file;
    document.getElementById('ms-fileName').textContent = file.name;
    document.getElementById('ms-fileSize').textContent = formatSize(file.size);
    document.getElementById('ms-fileInfo').classList.remove('hidden');
    document.getElementById('ms-convertBtn').disabled = false;
    document.getElementById('ms-validateBtn').disabled = false;
    hideResults('ms');
    file.text().then((text) => showPreview('ms', text, file.name)).catch(() => {});
});

document.getElementById('ms-removeFile').addEventListener('click', () => {
    msState.file = null;
    msFileInput.value = '';
    document.getElementById('ms-fileInfo').classList.add('hidden');
    document.getElementById('ms-convertBtn').disabled = true;
    document.getElementById('ms-validateBtn').disabled = true;
    hideResults('ms');
    hidePreview('ms');
});

document.getElementById('ms-exampleBtn').addEventListener('click', async () => {
    const btn = document.getElementById('ms-exampleBtn');
    btn.disabled = true;
    setStatus('ms', 'Lade Beispieldatei …', 'info');
    try {
        const res = await fetch(MS_API + '/examples/msproject.xml');
        if (!res.ok) throw new Error('Beispieldatei nicht verfügbar (' + res.status + ')');
        const text = await res.text();
        const file = new File([text], 'msproject-example.xml', { type: 'application/xml' });
        msState.file = file;
        document.getElementById('ms-fileName').textContent = file.name;
        document.getElementById('ms-fileSize').textContent = formatSize(file.size);
        document.getElementById('ms-fileInfo').classList.remove('hidden');
        document.getElementById('ms-convertBtn').disabled = false;
        document.getElementById('ms-validateBtn').disabled = false;
        showPreview('ms', text, file.name);
        setStatus('ms', 'Beispieldatei geladen: msproject-example.xml', 'success');
    } catch (err) {
        setStatus('ms', `Beispieldatei konnte nicht geladen werden: ${err.message}`, 'error');
    } finally {
        btn.disabled = false;
    }
});

function msFormat() {
    const el = document.querySelector('input[name="ms-format"]:checked');
    return el ? el.value : 'ttl';
}

async function msRun(endpoint, isConvert) {
    if (!msState.file) return;
    hideResults('ms');
    setStatus('ms', isConvert ? 'Konvertiere …' : 'Validiere …', 'info');
    document.getElementById('ms-convertBtn').disabled = true;
    document.getElementById('ms-validateBtn').disabled = true;

    const form = new FormData();
    form.append('file', msState.file);
    form.append('scheduleType', document.getElementById('ms-scheduleType').value);
    const isPlainJson = msFormat() === 'plainjson';
    if (isConvert) {
        form.append('format', msFormat());
        if (isPlainJson) form.append('envelope', String(envelopeChecked('ms')));
    }

    try {
        const res = await fetch(MS_API + endpoint, { method: 'POST', body: form });
        if (res.ok && isConvert) {
            const text = await res.text();
            const formatValue = msFormat();
            const fmt = FORMAT[formatValue] || FORMAT.ttl;
            msState.output = text;
            document.getElementById('ms-resultOutput').dataset.lang = fmt.hlLang;
            msState.outputName = envelopeFileName('schedule-' + msState.file.name.replace(/\.xml$/i, '') + fmt.ext,
                isPlainJson && envelopeChecked('ms'));
            msState.outputType = fmt.mediaType;
            const count = iriCount(text, formatValue);
            const countLabel = formatValue === 'plainjson' ? 'Knoten' : 'IRI-Referenzen';
            showResult('ms', text, `${msState.file.name} → ${msState.outputName} · ${fmt.label} · ${formatSize(text.length)} · ${count} ${countLabel}`);
            setStatus('ms', 'Konvertierung erfolgreich.', 'success');
        } else if (res.ok) {
            const data = await res.json();
            if (data.valid) {
                setStatus('ms', 'SHACL-Validierung erfolgreich: Modell ist konform zur construction-schedule Ontologie.', 'success');
            } else {
                showViolations('ms', data.violations || []);
                setStatus('ms', `SHACL-Validierung: ${data.violationCount} Verletzungen gefunden.`, 'error');
            }
        } else {
            const data = await res.json().catch(() => ({}));
            setStatus('ms', `Fehler: ${data.message || data.error || res.status}`, 'error');
        }
    } catch (err) {
        setStatus('ms', `Netzwerkfehler: ${err.message}`, 'error');
    } finally {
        document.getElementById('ms-convertBtn').disabled = false;
        document.getElementById('ms-validateBtn').disabled = false;
    }
}

document.getElementById('ms-convertBtn').addEventListener('click', () => msRun('/convert/msproject', true));
document.getElementById('ms-validateBtn').addEventListener('click', () => msRun('/validate/msproject', false));
setupDownload('ms', msState);

// ==================== Reverse Konvertierung (RDF → XML) ====================
function revInputFormat(prefix) {
    const el = document.querySelector('input[name="' + prefix + '-format"]:checked');
    return el ? el.value : 'ttl';
}

function setupReverse(prefix, apiBase, endpoint, state) {
    const convertBtn = document.getElementById(prefix + '-convertBtn');
    const removeBtn = document.getElementById(prefix + '-removeFile');
    const fileInput = document.getElementById(prefix + '-fileInput');

    setupDropzone(prefix, (file) => {
        state.file = file;
        state.outputName = file.name.replace(/\.[^.]+$/, '') + '.xml';
        document.getElementById(prefix + '-fileName').textContent = file.name;
        document.getElementById(prefix + '-fileSize').textContent = formatSize(file.size);
        document.getElementById(prefix + '-fileInfo').classList.remove('hidden');
        convertBtn.disabled = false;
        hideResults(prefix);
    });

    removeBtn.addEventListener('click', () => {
        state.file = null;
        fileInput.value = '';
        document.getElementById(prefix + '-fileInfo').classList.add('hidden');
        convertBtn.disabled = true;
        hideResults(prefix);
    });

    convertBtn.addEventListener('click', () => revRun(prefix, apiBase + endpoint, state));
    setupDownload(prefix, state);
}

async function revRun(prefix, url, state) {
    if (!state.file) return;
    hideResults(prefix);
    setStatus(prefix, 'Konvertiere …', 'info');
    const convertBtn = document.getElementById(prefix + '-convertBtn');
    convertBtn.disabled = true;

    const form = new FormData();
    form.append('file', state.file);
    form.append('format', revInputFormat(prefix));
    const strictEl = document.getElementById(prefix + '-strict');
    if (strictEl && strictEl.checked) form.append('strict', 'true');

    try {
        const res = await fetch(url, { method: 'POST', body: form });
        const data = await res.json().catch(() => ({}));
        if (res.ok) {
            const warnings = Array.isArray(data.warnings) ? data.warnings : [];
            state.output = data.xml;
            state.outputType = 'application/xml';
            document.getElementById(prefix + '-resultOutput').dataset.lang = 'xml';
            showResult(prefix, data.xml,
                `${state.file.name} → ${state.outputName} · XML · ${formatSize(data.xml.length)}${warnings.length ? ' · ' + warnings.length + ' Hinweis(e)' : ''}${strictEl && strictEl.checked ? ' · Strikt-Modus' : ''}`);
            const wEl = document.getElementById(prefix + '-warnings');
            wEl.innerHTML = '';
            if (warnings.length) {
                const ul = document.createElement('ul');
                warnings.forEach(w => {
                    const li = document.createElement('li');
                    li.textContent = w;
                    ul.appendChild(li);
                });
                wEl.appendChild(ul);
                wEl.classList.remove('hidden');
            } else {
                wEl.classList.add('hidden');
            }
            setStatus(prefix, 'Konvertierung erfolgreich.', 'success');
        } else if (Array.isArray(data.errors)) {
            // XSD-Validierungsreport des generierten GAEB XML
            showErrors(prefix, data.errors);
            setStatus(prefix, `${data.error || 'Validierung fehlgeschlagen'} (${data.errorCount} Fehler).`, 'error');
        } else if (data.error === 'Conversion aborted' && data.detail) {
            // Strikt-Modus: Abbruch wegen Datenverlust-Warnungen
            const ul = document.createElement('ul');
            String(data.detail).split(' | ').forEach(d => {
                const li = document.createElement('li');
                li.textContent = d;
                ul.appendChild(li);
            });
            const out = document.getElementById(prefix + '-errorOutput');
            out.innerHTML = '';
            out.appendChild(ul);
            document.getElementById(prefix + '-errorSection').classList.remove('hidden');
            setStatus(prefix, 'Strikt-Modus: Konvertierung abgebrochen (Datenverluste).', 'error');
        } else {
            setStatus(prefix, `Fehler: ${data.error || res.status}${data.detail ? ' – ' + data.detail : ''}`, 'error');
        }
    } catch (err) {
        setStatus(prefix, `Netzwerkfehler: ${err.message}`, 'error');
    } finally {
        convertBtn.disabled = false;
    }
}

const gaebRevState = { file: null, output: null, outputName: 'gaeb-boq.xml', outputType: 'application/xml' };
setupReverse('gaeb-rev', GAEB_API, '/reverse/gaeb-boq', gaebRevState);

const msRevState = { file: null, output: null, outputName: 'schedule.xml', outputType: 'application/xml' };
setupReverse('ms-rev', MS_API, '/reverse/msproject', msRevState);
