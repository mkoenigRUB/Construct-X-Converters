/*
 * ConXHighlight — lightweight read-only syntax highlighting
 * for XML, TTL (Turtle) and JSON (JSON-LD).
 *
 * - No external dependencies, no editing: pure HTML output for a <pre> element.
 * - Escape-safe: all source text is HTML-escaped before rendering.
 * - Line numbers via .hl-ln spans (aria-hidden, not selectable).
 * - render() returns null for inputs larger than MAX_LINES lines;
 *   callers should then fall back to plain text.
 */
(function (global) {
    'use strict';

    var MAX_LINES = 1500;

    function esc(s) {
        return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }

    function span(cls, text) {
        return '<span class="' + cls + '">' + esc(text) + '</span>';
    }

    function tokenize(code, re, classify) {
        re.lastIndex = 0;
        var html = '', m, last = 0;
        while ((m = re.exec(code)) !== null) {
            if (m.index > last) html += esc(code.slice(last, m.index));
            html += span(classify(m[0]), m[0]);
            last = m.index + m[0].length;
        }
        html += esc(code.slice(last));
        return html;
    }

    /* ---------------- XML ---------------- */

    function findTagEnd(line, start) {
        var inQ = null;
        for (var k = start; k < line.length; k++) {
            var c = line[k];
            if (inQ) { if (c === inQ) inQ = null; continue; }
            if (c === '"' || c === "'") { inQ = c; continue; }
            if (c === '>') return k;
        }
        return -1;
    }

    function tagHtml(tag) {
        var re = /(?<tag><\/?[\w:.-]*|\/?>)|(?<attr>[\w:.-]+(?=\s*=))|(?<str>"[^"]*"|'[^']*')|(?<ent>&[A-Za-z0-9#]+;)|(?<eq>=)|(?<txt>[ \t]+)|(?<other>.)/g;
        re.lastIndex = 0;
        var html = '', m, last = 0;
        while ((m = re.exec(tag)) !== null) {
            if (m.index > last) html += esc(tag.slice(last, m.index));
            var t = m[0];
            if (m.groups.tag) html += span('hl-tag', t);
            else if (m.groups.attr) html += span('hl-attr', t);
            else if (m.groups.str) html += span('hl-string', t);
            else if (m.groups.ent) html += span('hl-entity', t);
            else if (m.groups.eq) html += span('hl-punct', t);
            else html += esc(t);
            last = m.index + t.length;
        }
        html += esc(tag.slice(last));
        return html;
    }

    function xmlLine(line, state) {
        var html = '', i = 0, end, em;
        if (state.mode === 'comment') {
            end = line.indexOf('-->');
            if (end === -1) return span('hl-comment', line);
            html += span('hl-comment', line.slice(0, end + 3));
            state.mode = null; i = end + 3;
        } else if (state.mode === 'cdata') {
            end = line.indexOf(']]>');
            if (end === -1) return span('hl-cdata', line);
            html += span('hl-cdata', line.slice(0, end + 3));
            state.mode = null; i = end + 3;
        } else if (state.mode === 'pi') {
            end = line.indexOf('?>');
            if (end === -1) return span('hl-pi', line);
            html += span('hl-pi', line.slice(0, end + 2));
            state.mode = null; i = end + 2;
        }
        while (i < line.length) {
            if (line.startsWith('<!--', i)) {
                end = line.indexOf('-->', i + 4);
                if (end === -1) { html += span('hl-comment', line.slice(i)); state.mode = 'comment'; break; }
                html += span('hl-comment', line.slice(i, end + 3)); i = end + 3; continue;
            }
            if (line.startsWith('<![CDATA[', i)) {
                end = line.indexOf(']]>', i + 9);
                if (end === -1) { html += span('hl-cdata', line.slice(i)); state.mode = 'cdata'; break; }
                html += span('hl-cdata', line.slice(i, end + 3)); i = end + 3; continue;
            }
            if (line.startsWith('<?', i)) {
                end = line.indexOf('?>', i + 2);
                if (end === -1) { html += span('hl-pi', line.slice(i)); state.mode = 'pi'; break; }
                html += span('hl-pi', line.slice(i, end + 2)); i = end + 2; continue;
            }
            if (line.startsWith('<!', i)) {
                end = line.indexOf('>', i);
                if (end === -1) { html += esc(line.slice(i)); break; }
                html += span('hl-decl', line.slice(i, end + 1)); i = end + 1; continue;
            }
            if (line[i] === '<' && line[i + 1] !== undefined && /[_A-Za-z/]/.test(line[i + 1])) {
                end = findTagEnd(line, i);
                if (end === -1) { html += esc(line.slice(i)); break; }
                html += tagHtml(line.slice(i, end + 1)); i = end + 1; continue;
            }
            em = /^&[A-Za-z0-9#]+;/.exec(line.slice(i));
            if (em) { html += span('hl-entity', em[0]); i += em[0].length; continue; }
            html += esc(line[i]); i++;
        }
        return html;
    }

    /* ---------------- TTL (Turtle) ---------------- */

    var TTL_RE = /@(?:prefix|base)|<[^<>\n]*>|"(?:\\.|[^"\\\n])*"(?:@[A-Za-z0-9_+.-]+|\^\^<[^<>\n]*>|\^\^[\w:.-]+)?|'[^'\n]*'(?:@[\w-]+)?|_:([\w-]+)|\b\d+(?:\.\d+)?(?:[eE][+-]?\d+)?\b|\b[a-zA-Z][\w-]*:[\w-]*\b|\b[a-zA-Z][\w-]*\b|[;.,]/g;

    function ttlClass(t) {
        if (t === '@prefix' || t === '@base') return 'hl-directive';
        if (t.charAt(0) === '<') return 'hl-uri';
        if (t.charAt(0) === '"' || t.charAt(0) === "'") return 'hl-string';
        if (t.charAt(0) === '_') return 'hl-blank';
        if (t.charAt(0) === ';' || t.charAt(0) === ',' || t.charAt(0) === '.') return 'hl-punct';
        if (/\d/.test(t.charAt(0))) return 'hl-number';
        if (t.indexOf(':') !== -1) return 'hl-prefix';
        if (t === 'a') return 'hl-keyword';
        return 'hl-name';
    }

    function ttlLine(line) {
        // Locate '#' outside of strings and IRIs.
        var inStr = null, j, k, c;
        for (j = 0; j < line.length; j++) {
            c = line[j];
            if (inStr) {
                if (c === '\\') { j++; continue; }
                if (c === inStr) inStr = null;
                continue;
            }
            if (c === '"' || c === "'") { inStr = c; continue; }
            if (c === '<') {
                k = line.indexOf('>', j);
                if (k === -1) { j = line.length; break; }
                j = k; continue;
            }
            if (c === '#') break;
        }
        if (j >= line.length || line.charAt(j) !== '#') {
            return tokenize(line, TTL_RE, ttlClass);
        }
        return tokenize(line.slice(0, j), TTL_RE, ttlClass) + span('hl-comment', line.slice(j));
    }

    /* ---------------- JSON (JSON-LD) ---------------- */

    var JSON_RE = /"(?:\\.|[^"\\])*"(?=\s*:)|"(?:\\.|[^"\\])*"|\b(?:true|false|null)\b|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?\b|[{}[\],:]/g;

    function jsonLine(line) {
        JSON_RE.lastIndex = 0;
        var html = '', m, last = 0;
        while ((m = JSON_RE.exec(line)) !== null) {
            if (m.index > last) html += esc(line.slice(last, m.index));
            var t = m[0];
            var cls;
            if (t.charAt(0) === '"') {
                cls = /^\s*:/.test(line.slice(m.index + t.length)) ? 'hl-key' : 'hl-string';
            } else if (/^(?:true|false|null)$/.test(t)) cls = 'hl-keyword';
            else if (/^[-\d]/.test(t)) cls = 'hl-number';
            else cls = 'hl-punct';
            html += span(cls, t);
            last = m.index + t.length;
        }
        html += esc(line.slice(last));
        return html;
    }

    /* ---------------- Renderer ---------------- */

    function render(code, lang) {
        var text = String(code == null ? '' : code).replace(/^\uFEFF/, '');
        if (text === '') {
            return '<span class="hl-line"><span class="hl-ln" aria-hidden="true">1</span><span class="hl-code">&nbsp;</span></span>';
        }
        var langKey = String(lang || '').toLowerCase();
        var kind = (langKey === 'ttl' || langKey === 'turtle') ? 'ttl'
            : (langKey === 'json' || langKey === 'jsonld' || langKey === 'json-ld') ? 'json'
            : 'xml';
        var lines = text.split('\n');
        if (lines.length > MAX_LINES) return null;
        var state = { mode: null };
        var html = '';
        for (var i = 0; i < lines.length; i++) {
            var line = lines[i];
            var body = (kind === 'ttl') ? ttlLine(line)
                : (kind === 'json') ? jsonLine(line)
                : xmlLine(line, state);
            if (!body) body = '&nbsp;';
            html += '<span class="hl-line"><span class="hl-ln" aria-hidden="true">' + (i + 1) +
                '</span><span class="hl-code">' + body + '</span></span>';
        }
        return html;
    }

    global.ConXHighlight = {
        render: render,
        MAX_LINES: MAX_LINES
    };
})(typeof window !== 'undefined' ? window : globalThis);
