const API_BASE = window.CONX_API_BASE
    || (window.location.port === '8084' ? 'http://localhost:8083/api/v1' : '/api/v1');
const fileInput = document.getElementById('fileInput');
const dropzone = document.getElementById('dropzone');
const filePill = document.getElementById('filePill');
const fileName = document.getElementById('fileName');
const fileSize = document.getElementById('fileSize');
const runButton = document.getElementById('runButton');
const statusEl = document.getElementById('status');
const resultSection = document.getElementById('resultSection');
const reportList = document.getElementById('reportList');
let currentFile = null;
let currentTask = 'convert';
let downloadContent = null;
let downloadName = '';
let downloadType = '';
let serviceAvailable = false;

document.getElementById('convertTab').addEventListener('click', () => setTask('convert'));
document.getElementById('validateTab').addEventListener('click', () => setTask('validate'));
document.getElementById('removeFile').addEventListener('click', clearFile);
runButton.addEventListener('click', processFile);
fileInput.addEventListener('change', () => fileInput.files[0] && selectFile(fileInput.files[0]));
document.getElementById('downloadButton').addEventListener('click', downloadResult);
document.getElementById('envelopeCheckbox').addEventListener('change', updateEnvelopeRowVisibility);
document.getElementById('targetFormat').addEventListener('change', updateEnvelopeRowVisibility);

for (const eventName of ['dragenter', 'dragover']) {
    dropzone.addEventListener(eventName, event => {
        event.preventDefault();
        dropzone.classList.add('dragover');
    });
}
for (const eventName of ['dragleave', 'drop']) {
    dropzone.addEventListener(eventName, event => {
        event.preventDefault();
        dropzone.classList.remove('dragover');
    });
}
dropzone.addEventListener('drop', event => {
    if (event.dataTransfer.files[0]) selectFile(event.dataTransfer.files[0]);
});

function setTask(task) {
    currentTask = task;
    const convert = task === 'convert';
    document.getElementById('convertTab').classList.toggle('active', convert);
    document.getElementById('validateTab').classList.toggle('active', !convert);
    document.getElementById('convertTab').setAttribute('aria-selected', String(convert));
    document.getElementById('validateTab').setAttribute('aria-selected', String(!convert));
    document.getElementById('convertOptions').classList.toggle('hidden', !convert);
    document.getElementById('validateOptions').classList.toggle('hidden', convert);
    updateEnvelopeRowVisibility();
    document.getElementById('optionsTitle').textContent = convert ? 'Zielformat festlegen' : 'Validierungsprofil wählen';
    document.getElementById('optionsDescription').textContent = convert
        ? 'Das Eingabeformat wird anhand der Dateiendung erkannt.'
        : 'Prüfung gegen ausgewählte Ontologie- und SHACL-Regeln.';
    document.getElementById('runIcon').textContent = convert ? '⇄' : '✓';
    document.getElementById('runLabel').textContent = convert ? 'Datei konvertieren' : 'Datei validieren';
    statusEl.classList.add('hidden');
    resultSection.classList.add('hidden');
}

function updateEnvelopeRowVisibility() {
    const row = document.getElementById('envelopeRow');
    if (!row) return;
    const show = currentTask === 'convert'
        && document.getElementById('targetFormat').value === 'plainjson';
    row.classList.toggle('hidden', !show);
}

function selectFile(file) {
    if (!/\.(ttl|json|jsonld)$/i.test(file.name)) {
        showStatus('Bitte eine Datei mit der Endung .ttl, .json oder .jsonld auswählen.', true);
        return;
    }
    if (file.size > 50 * 1024 * 1024) {
        showStatus('Die Datei überschreitet die maximale Größe von 50 MB.', true);
        return;
    }
    currentFile = file;
    fileName.textContent = file.name;
    fileSize.textContent = formatSize(file.size);
    filePill.classList.remove('hidden');
    document.getElementById('fileHint').textContent = 'Ausgewählt · ' + formatSize(file.size);
    runButton.disabled = !serviceAvailable;
    statusEl.classList.add('hidden');
    resultSection.classList.add('hidden');
}

function clearFile() {
    currentFile = null;
    fileInput.value = '';
    filePill.classList.add('hidden');
    document.getElementById('fileHint').textContent = 'Maximal 50 MB · Verarbeitung ohne dauerhafte Speicherung';
    runButton.disabled = true;
    resultSection.classList.add('hidden');
    statusEl.classList.add('hidden');
}

async function processFile() {
    if (!currentFile) return;
    const format = selectedFormat(currentTask === 'convert' ? 'sourceFormat' : 'validationFormat');
    if (format === 'auto') {
        showStatus('Das Quellformat konnte nicht erkannt werden. Bitte Format manuell wählen.', true);
        return;
    }
    const form = new FormData();
    form.append('file', currentFile);
    runButton.disabled = true;
    resultSection.classList.add('hidden');
    showStatus(currentTask === 'convert' ? 'RDF-Datei wird konvertiert …' : 'SHACL-Prüfung läuft …');
    try {
        let response;
        if (currentTask === 'convert') {
            form.append('sourceFormat', format);
            const target = document.getElementById('targetFormat').value;
            form.append('targetFormat', target);
            const useEnvelope = target === 'plainjson'
                && document.getElementById('envelopeCheckbox').checked;
            if (useEnvelope) form.append('envelope', 'true');
            response = await fetch(API_BASE + '/convert', { method: 'POST', body: form });
            if (!response.ok) throw new Error(await errorMessage(response));
            const text = await response.text();
            downloadContent = text;
            const formatMeta = {
                ttl: { type: 'text/turtle;charset=utf-8', label: 'Turtle', ext: '.ttl' },
                jsonld: { type: 'application/ld+json;charset=utf-8', label: 'JSON-LD', ext: '.jsonld' },
                plainjson: { type: 'application/json;charset=utf-8', label: 'JSON (plain, ohne Ontologie)', ext: '.json' }
            }[target] || { type: 'text/plain;charset=utf-8', label: target, ext: '' };
            downloadType = formatMeta.type;
            downloadName = currentFile.name.replace(/\.(ttl|json|jsonld)$/i, '') + formatMeta.ext;
            if (useEnvelope) {
                downloadName = downloadName.replace(/\.json$/i, '-package.json');
            }
            showConversionResult(text, target, formatMeta.label, useEnvelope);
        } else {
            form.append('format', format);
            form.append('profile', document.getElementById('profileSelect').value);
            response = await fetch(API_BASE + '/validate', { method: 'POST', body: form });
            if (!response.ok) throw new Error(await errorMessage(response));
            showValidationResult(await response.json());
        }
    } catch (error) {
        showStatus(error.message || 'Die Anfrage ist fehlgeschlagen.', true);
    } finally {
        runButton.disabled = false;
    }
}

function showConversionResult(content, target, label, useEnvelope) {
    document.getElementById('resultTitle').textContent = 'Konvertierung erfolgreich';
    let summary = `${currentFile.name} wurde in ${label} konvertiert.`;
    if (target === 'plainjson') {
        const entityCount = (content.match(/"id":\s*"(?:https?:|_:)/g) || []).length;
        summary += ` ${entityCount} Knoten`;
        if (useEnvelope) summary += ' im Exchange-Paket (id, semanticModel, content.entities)';
    }
    document.getElementById('resultSummary').textContent = summary;
    document.getElementById('downloadButton').classList.remove('hidden');
    document.getElementById('previewDetails').classList.remove('hidden');
    document.getElementById('preview').textContent = content;
    reportList.replaceChildren();
    resultSection.classList.remove('hidden');
    showStatus('Konvertierung abgeschlossen. Die Datei kann heruntergeladen werden.');
}

function showValidationResult(result) {
    document.getElementById('resultTitle').textContent = result.valid ? 'Datei ist gültig' : 'Verstöße gefunden';
    document.getElementById('resultSummary').textContent = result.valid
        ? `Keine SHACL-Verstöße im Profil „${result.profileName}“.`
        : `${result.violationCount} SHACL-Verstoß/Verstöße im Profil „${result.profileName}“.`;
    document.getElementById('downloadButton').classList.add('hidden');
    document.getElementById('previewDetails').classList.add('hidden');
    reportList.replaceChildren();
    for (const violation of result.violations || []) {
        const item = document.createElement('article');
        item.className = 'report-item';
        const heading = document.createElement('strong');
        heading.textContent = [violation.severity, violation.focusNode].filter(Boolean).join(' · ');
        const message = document.createElement('p');
        message.textContent = [violation.message, violation.path ? `Pfad: ${violation.path}` : '',
            violation.sourceShape ? `Shape: ${violation.sourceShape}` : ''].filter(Boolean).join(' ');
        item.append(heading, message);
        reportList.append(item);
    }
    resultSection.classList.remove('hidden');
    showStatus(result.valid ? 'SHACL-Validierung erfolgreich.' : 'SHACL-Validierung abgeschlossen; Verstöße bitte prüfen.',
        !result.valid);
}

function selectedFormat(selectId) {
    const selected = document.getElementById(selectId).value;
    if (selected !== 'auto') return selected;
    return /\.ttl$/i.test(currentFile.name) ? 'ttl' : /\.json(ld)?$/i.test(currentFile.name) ? 'jsonld' : 'auto';
}

async function errorMessage(response) {
    try {
        const body = await response.json();
        return body.message || body.error || `Anfrage fehlgeschlagen (${response.status}).`;
    } catch {
        return `Anfrage fehlgeschlagen (${response.status}).`;
    }
}

function showStatus(message, isError = false) {
    statusEl.textContent = message;
    statusEl.classList.toggle('error', isError);
    statusEl.classList.remove('hidden');
}

function downloadResult() {
    if (downloadContent === null) return;
    const url = URL.createObjectURL(new Blob([downloadContent], { type: downloadType }));
    const link = document.createElement('a');
    link.href = url;
    link.download = downloadName;
    link.click();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function formatSize(bytes) {
    return bytes < 1024 ? `${bytes} B` : bytes < 1024 * 1024
        ? `${(bytes / 1024).toFixed(1)} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

async function loadProfiles() {
    let failure;
    for (let attempt = 0; attempt < 8; attempt++) {
        try {
            const response = await fetch(API_BASE + '/profiles');
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            const profiles = await response.json();
            const select = document.getElementById('profileSelect');
            select.replaceChildren();
            for (const profile of profiles) {
                const option = document.createElement('option');
                option.value = profile.id;
                option.textContent = `${profile.name} · v${profile.version} · ${profile.shapeFiles} SHACL`;
                select.append(option);
            }
            if (profiles.length === 0) throw new Error('Keine SHACL-Profile verfügbar');
            document.querySelector('.state-dot').classList.add('online');
            document.getElementById('serviceStatus').textContent = 'RDF-Dienst verbunden';
            serviceAvailable = true;
            runButton.disabled = !currentFile;
            return;
        } catch (error) {
            failure = error;
            if (attempt < 7) await new Promise(resolve => window.setTimeout(resolve, 1000));
        }
    }
    document.querySelector('.state-dot').classList.add('offline');
    document.getElementById('serviceStatus').textContent = 'RDF-Dienst nicht erreichbar';
    document.getElementById('profileSelect').replaceChildren(new Option('Profile nicht verfügbar', ''));
    serviceAvailable = false;
    runButton.disabled = true;
    showStatus(`Der RDF-Dienst ist nicht erreichbar: ${failure?.message || 'Verbindung fehlgeschlagen'}`, true);
}

loadProfiles();
