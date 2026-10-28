'use strict';

const $ = (id) => document.getElementById(id);
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

const state = {
  catalog: null,
  mode: 'page',
  id: 'login',
  variant: 'standard',
  locale: '',
  theme: { page: '', mail: '' },
  theme2: { page: 'keycloak.v2', mail: 'keycloak' },
  compare: false,
  width: 1280,
  zoom: 1,
  fit: true,
  shownZoom: 1,
  part: 'html',
  stamp: 0,
  report: null,
  resourceFindings: [],
};

const LEVEL_LABEL = { error: 'Fehler', warning: 'Warnungen', info: 'Hinweise' };

// ------------------------------------------------------------------ Start

async function init() {
  const cat = await (await fetch('/api/catalog')).json();
  state.catalog = cat;
  $('kcVersion').textContent = 'Version ' + cat.appVersion + ' · Keycloak ' + cat.keycloakVersion;
  $('themesDir').textContent = 'Theme-Ordner: ' + cat.themesDir;
  state.theme.page = cat.scenario.loginTheme;
  state.theme.mail = cat.scenario.emailTheme;
  restore();
  fillLocales();
  bind();
  bindThemes();
  showList();
  select(state.mode, state.id, state.variant);
  setInterval(pollStamp, 1500);
}

function persist() {
  try {
    localStorage.setItem('kcemu', JSON.stringify({ mode: state.mode, id: state.id, variant: state.variant, locale: state.locale, width: state.width, zoom: state.zoom, fit: state.fit }));
  } catch (e) { /* ohne Speicher weiter */ }
}

function restore() {
  try {
    const s = JSON.parse(localStorage.getItem('kcemu') || '{}');
    Object.assign(state, s);
  } catch (e) { /* ohne Speicher weiter */ }
  if (!isKnown(state.mode, state.id)) { state.mode = 'page'; state.id = 'login'; state.variant = 'standard'; }
  if (!(state.zoom > 0)) state.zoom = 1;
  if (typeof state.fit !== 'boolean') state.fit = true;
}

function isKnown(mode, id) {
  const c = state.catalog;
  return mode === 'page' ? c.pages.some((p) => p.id === id) : c.mails.some((m) => m.id === id);
}

function fillLocales() {
  const sc = state.catalog.scenario;
  const sel = $('locale');
  const realm = sc.realm;
  const opts = [`<option value="">Automatisch (${esc(sc.acceptLanguage || 'kein Accept-Language')})</option>`];
  if (realm.internationalizationEnabled) {
    for (const l of realm.supportedLocales) opts.push(`<option value="${esc(l)}">${esc(l)}</option>`);
  }
  sel.innerHTML = opts.join('');
  sel.value = realm.internationalizationEnabled && realm.supportedLocales.includes(state.locale) ? state.locale : '';
  state.locale = sel.value;
}

function fillThemes() {
  const names = state.mode === 'page' ? state.catalog.themes.login : state.catalog.themes.email;
  const key = state.mode;
  const html = names.map((n) => `<option>${esc(n)}</option>`).join('');
  $('theme').innerHTML = html;
  $('theme2').innerHTML = html;
  if (!names.includes(state.theme[key])) {
    $('theme').insertAdjacentHTML('afterbegin', `<option>${esc(state.theme[key])}</option>`);
  }
  $('theme').value = state.theme[key];
  $('theme2').value = names.includes(state.theme2[key]) ? state.theme2[key] : names[0];
}

// ------------------------------------------------------------------ Liste

function showList() {
  $('tabPages').setAttribute('aria-selected', String(state.mode === 'page'));
  $('tabMails').setAttribute('aria-selected', String(state.mode === 'mail'));
  const q = $('filter').value.trim().toLowerCase();
  const items = state.mode === 'page' ? state.catalog.pages : state.catalog.mails.map((m) => ({ ...m, group: 'E-Mails' }));
  let html = '';
  let group = null;
  for (const it of items) {
    const hay = (it.label + ' ' + it.template + ' ' + it.id).toLowerCase();
    if (q && !hay.includes(q)) continue;
    if (it.group !== group) { group = it.group; html += `<h3>${esc(group)}</h3>`; }
    html += `<button type="button" data-id="${esc(it.id)}" aria-current="${it.id === state.id}">${esc(it.label)}<small>${esc(it.template)}</small></button>`;
  }
  $('list').innerHTML = html || '<p class="muted small">Keine Treffer.</p>';
}

function select(mode, id, variant) {
  state.mode = mode;
  state.id = id;
  fillThemes();
  const vw = $('variantWrap');
  if (mode === 'page') {
    const p = state.catalog.pages.find((x) => x.id === id);
    vw.hidden = false;
    $('variant').innerHTML = p.variants.map((v) => `<option value="${esc(v.id)}">${esc(v.label)}</option>`).join('');
    state.variant = p.variants.some((v) => v.id === variant) ? variant : p.variants[0].id;
    $('variant').value = state.variant;
    $('mailHead').hidden = true;
  } else {
    vw.hidden = true;
    $('mailHead').hidden = false;
  }
  showList();
  persist();
  render();
}

// ------------------------------------------------------------------ Darstellung

function url(kind, theme) {
  const p = new URLSearchParams();
  if (state.mode === 'page') p.set('variant', state.variant);
  if (state.locale) p.set('locale', state.locale);
  if (theme) p.set('theme', theme);
  if (kind === 'render' && state.mode === 'mail') p.set('part', state.part);
  const base = kind === 'render' ? '/render/' : '/api/';
  return base + (state.mode === 'page' ? 'page/' : 'mail/') + encodeURIComponent(state.id) + '?' + p;
}

/** Zoomstufen für +/−; „Breite“ berechnet die Stufe frei aus der Fensterbreite. */
const ZOOM_STEPS = [0.25, 0.33, 0.5, 0.67, 0.75, 0.8, 0.9, 1, 1.1, 1.25, 1.5, 1.75, 2, 2.5, 3];

/**
 * Breite und Zoom der Vorschau(en) anwenden. Die Seite wird mit ihrer logischen Breite
 * gerendert (Mobil 375, Tablet 768, Desktop 1280 bzw. „Voll“ = verfügbare Breite) und
 * dann skaliert – das Layout ändert sich durch den Zoom also nicht.
 */
function applyWidth() {
  let shown = state.zoom;
  for (const vp of [$('vp1'), $('vp2')]) {
    if (vp.closest('[hidden]')) continue;
    const f = vp.querySelector('iframe');
    const scaler = vp.querySelector('.scaler');
    const availW = Math.max(200, vp.clientWidth - 2);
    const availH = Math.max(300, vp.clientHeight - 2);
    let z = state.zoom;
    if (state.fit) z = state.width ? availW / state.width : 1;
    z = Math.min(4, Math.max(0.1, z));
    const w = state.width || Math.floor(availW / z);
    const h = Math.floor(availH / z);
    f.style.width = w + 'px';
    f.style.height = h + 'px';
    f.style.transform = z === 1 ? '' : `scale(${z})`;
    scaler.style.width = Math.round(w * z) + 'px';
    scaler.style.height = Math.round(h * z) + 'px';
    if (vp.id === 'vp1') shown = z;
  }
  state.shownZoom = shown;
  $('zoomLevel').textContent = Math.round(shown * 100) + ' %';
  $('zoomFit').classList.toggle('on', state.fit);
  for (const b of $('viewports').querySelectorAll('button')) b.classList.toggle('on', Number(b.dataset.w) === state.width);
}

function zoomStep(dir) {
  const cur = state.shownZoom || state.zoom;
  const next = dir > 0 ? ZOOM_STEPS.find((z) => z > cur + 0.001) : [...ZOOM_STEPS].reverse().find((z) => z < cur - 0.001);
  if (next === undefined) return;
  state.zoom = next;
  state.fit = false;
  persist();
  applyWidth();
}

function zoomReset() {
  state.zoom = 1;
  state.fit = false;
  persist();
  applyWidth();
}

function zoomFit() {
  state.fit = true;
  persist();
  applyWidth();
}

async function render() {
  const t1 = state.theme[state.mode];
  const t2 = state.theme2[state.mode];
  $('f1').src = url('render', t1);
  $('cap1').textContent = t1;
  $('openRaw').href = url('render', t1);
  $('frame2').hidden = !state.compare;
  $('theme2').disabled = !state.compare;
  if (state.compare) {
    $('f2').src = url('render', t2);
    $('cap2').textContent = t2;
  }
  applyWidth();
  $('a11y').innerHTML = '';
  state.resourceFindings = [];
  const rep = await (await fetch(url('api', t1))).json();
  state.report = rep;
  showReport();
}

function showReport() {
  const rep = state.report;
  if (!rep) return;
  $('trigger').innerHTML = `<strong>${esc(rep.template)}</strong> · ausgelöst durch: ${esc(rep.trigger)}`
    + (rep.status ? ` · HTTP ${esc(rep.status)}` : '') + (rep.locale ? ` · Sprache ${esc(rep.locale)}` : '');
  if (state.mode === 'mail') {
    $('mSubject').textContent = rep.subject ?? '—';
    $('mFrom').textContent = rep.from ?? '—';
    $('mTo').textContent = rep.to ?? '—';
    $('mSize').textContent = rep.htmlSize != null ? (rep.htmlSize / 1024).toFixed(1) + ' KB' : '—';
  }
  const all = [...(rep.findings || []), ...state.resourceFindings];
  renderFindings($('findings'), all);
  const c = { error: 0, warning: 0, info: 0 };
  for (const f of all) c[f.level]++;
  $('counts').innerHTML = (c.error + c.warning + c.info === 0)
    ? '<span class="pill ok">Keine Befunde</span>'
    : Object.entries(c).filter(([, n]) => n).map(([k, n]) => `<span class="pill ${k}">${n} ${LEVEL_LABEL[k]}</span>`).join('');
  $('chain').innerHTML = (rep.chain || []).map((e) => `<li><strong>${esc(e.name)}</strong> <span class="muted small">(${esc(e.relation)})</span>`
    + `<div class="files">${esc(e.source)}</div>`
    + (e.overriddenFiles && e.overriddenFiles.length ? `<div class="files">eigene Dateien: ${e.overriddenFiles.map(esc).join(', ')}</div>` : '')
    + '</li>').join('');
  const fm = (rep.freemarker && rep.freemarker.templates) || [];
  $('model').innerHTML = fm.map((t) => `<p><strong>${esc(t.template)}</strong> (Theme ${esc(t.theme)})</p>`
    + `<p class="muted">Variablen: ${t.dataModel.map((v) => `<code>${esc(v)}</code>`).join(' ')}</p>`
    + `<p class="muted">Abgefragte Textschlüssel (${t.usedKeys.length}): ${t.usedKeys.map((v) => `<code>${esc(v)}</code>`).join(' ')}</p>`).join('');
}

function renderFindings(ul, list) {
  const order = { error: 0, warning: 1, info: 2, ok: 3 };
  list = [...list].sort((a, b) => order[a.level] - order[b.level]);
  ul.innerHTML = list.map((f) => `<li class="${esc(f.level)}"><div class="area">${esc(f.area)}${f.heuristic ? '<span class="heur" title="Erfahrungsregel, kein Keycloak-Verhalten">Heuristik</span>' : ''}</div>`
    + `<div>${esc(f.message)}</div>${f.detail ? `<div class="detail">${esc(f.detail)}</div>` : ''}</li>`).join('');
}

// ------------------------------------------------------------------ Prüfungen im Browser

/** Prüft, ob alle eingebundenen Stylesheets, Skripte und Bilder ausgeliefert werden. */
async function checkResources(frame) {
  const doc = frame.contentDocument;
  if (!doc) return [];
  const urls = new Set();
  doc.querySelectorAll('link[rel~="stylesheet"][href], link[rel~="icon"][href], script[src], img[src]').forEach((el) => {
    const u = el.href || el.src;
    if (u && u.startsWith(location.origin)) urls.add(u);
  });
  const out = [];
  await Promise.all([...urls].map(async (u) => {
    try {
      const r = await fetch(u, { cache: 'no-store' });
      if (!r.ok) out.push({ level: 'error', area: 'Ressourcen', message: `HTTP ${r.status}: ${u.replace(location.origin, '')}`, detail: 'Die Datei fehlt im Theme (oder in parent/import).' });
    } catch (e) { /* Netzwerkfehler ignorieren */ }
  }));
  return out;
}

/** Seitenbezogene Regeln, die für E-Mail-HTML nicht gelten (kein Dokument im Browser-Sinn). */
const MAIL_AXE_OFF = ['document-title', 'landmark-one-main', 'page-has-heading-one', 'region', 'html-has-lang'];

async function runAxe(frame, isMail) {
  const doc = frame.contentDocument;
  const win = frame.contentWindow;
  if (!doc || !win) return [];
  if (!win.axe) {
    await new Promise((res, rej) => {
      const s = doc.createElement('script');
      s.src = '/vendor/axe.min.js';
      s.onload = res;
      s.onerror = rej;
      doc.head.appendChild(s);
    });
  }
  const opts = { resultTypes: ['violations'] };
  if (isMail) opts.rules = Object.fromEntries(MAIL_AXE_OFF.map((r) => [r, { enabled: false }]));
  const r = await win.axe.run(doc, opts);
  return r.violations.map((v) => ({
    level: v.impact === 'critical' || v.impact === 'serious' ? 'error' : 'warning',
    area: 'Barrierefreiheit · ' + (v.impact || ''),
    message: v.help + ` (${v.nodes.length}×)`,
    detail: v.nodes.slice(0, 3).map((n) => n.target.join(' ') + (n.failureSummary ? '\n' + n.failureSummary : '')).join('\n\n') + '\n' + v.helpUrl,
    heuristic: false,
  }));
}

// ------------------------------------------------------------------ Szenario

const FLAG_LABELS = {
  registrationAllowed: 'Registrierung erlaubt',
  registrationEmailAsUsername: 'E-Mail als Benutzername',
  resetPasswordAllowed: '„Passwort vergessen“',
  rememberMe: '„Angemeldet bleiben“',
  loginWithEmailAllowed: 'Anmeldung mit E-Mail',
  duplicateEmailsAllowed: 'Doppelte E-Mails erlaubt',
  editUsernameAllowed: 'Benutzername änderbar',
  verifyEmail: 'E-Mail bestätigen',
  internationalizationEnabled: 'Mehrsprachigkeit',
  darkMode: 'Dark Mode (Realm-Attribut)',
  organizationsEnabled: 'Organisationen',
};

function openScenario() {
  const sc = state.catalog.scenario;
  let html = '';
  for (const [k, label] of Object.entries(FLAG_LABELS)) {
    html += `<label><input type="checkbox" data-flag="${k}" ${sc.realm[k] ? 'checked' : ''}> ${esc(label)}</label>`;
  }
  html += `<label class="text">Anzeigename des Realms<input type="text" data-text="displayName" value="${esc(sc.realm.displayName || '')}"></label>`;
  html += `<label class="text">Unterstützte Sprachen (Komma)<input type="text" data-text="supportedLocales" value="${esc((sc.realm.supportedLocales || []).join(','))}"></label>`;
  html += `<label class="text">Standardsprache<input type="text" data-text="defaultLocale" value="${esc(sc.realm.defaultLocale || '')}"></label>`;
  html += `<label class="text">Passwort-Richtlinie<input type="text" data-text="passwordPolicy" value="${esc(sc.realm.passwordPolicy || '')}"></label>`;
  $('flags').innerHTML = html;
  $('scenarioJson').value = JSON.stringify(sc, null, 2);
  $('scenarioErr').textContent = '';
  $('dlgScenario').showModal();
}

function syncFlagsToJson() {
  let sc;
  try { sc = JSON.parse($('scenarioJson').value); } catch (e) { return; }
  for (const cb of $('flags').querySelectorAll('[data-flag]')) sc.realm[cb.dataset.flag] = cb.checked;
  for (const t of $('flags').querySelectorAll('[data-text]')) {
    const k = t.dataset.text;
    sc.realm[k] = k === 'supportedLocales' ? t.value.split(',').map((s) => s.trim()).filter(Boolean) : t.value;
  }
  $('scenarioJson').value = JSON.stringify(sc, null, 2);
}

async function saveScenario(persistFile) {
  let sc;
  try { sc = JSON.parse($('scenarioJson').value); } catch (e) {
    $('scenarioErr').textContent = 'JSON ungültig: ' + e.message;
    return;
  }
  const r = await fetch('/api/scenario' + (persistFile ? '?save=1' : ''), {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(sc),
  });
  const res = await r.json();
  if (!r.ok) { $('scenarioErr').textContent = res.error; return; }
  state.catalog = await (await fetch('/api/catalog')).json();
  state.theme.page = state.catalog.scenario.loginTheme;
  state.theme.mail = state.catalog.scenario.emailTheme;
  fillLocales();
  $('dlgScenario').close();
  select(state.mode, state.id, state.variant);
}

// ------------------------------------------------------------------ Gesamtprüfung

let auditRows = [];

async function runAudit() {
  const withA11y = $('auditA11y').checked;
  const jobs = [];
  for (const p of state.catalog.pages) for (const v of p.variants) jobs.push({ mode: 'page', id: p.id, variant: v.id, label: `${p.label} – ${v.label}`, template: p.template });
  for (const m of state.catalog.mails) jobs.push({ mode: 'mail', id: m.id, label: m.label, template: m.template });
  const prog = $('auditProgress');
  prog.hidden = false; prog.max = jobs.length; prog.value = 0;
  $('auditRun').disabled = true;
  auditRows = [];
  const tbl = $('auditTable');
  tbl.innerHTML = '<tr><th>Seite / Mail</th><th>Template</th><th class="n">Fehler</th><th class="n">Warn.</th><th class="n">Hinw.</th><th class="n">A11y</th><th>Wichtigste Befunde</th></tr>';
  const locale = state.locale;
  for (const j of jobs) {
    const theme = j.mode === 'page' ? state.theme.page : state.theme.mail;
    const p = new URLSearchParams();
    if (j.variant) p.set('variant', j.variant);
    if (locale) p.set('locale', locale);
    p.set('theme', theme);
    const base = j.mode === 'page' ? 'page/' : 'mail/';
    let findings = [];
    try {
      const rep = await (await fetch('/api/' + base + j.id + '?' + p)).json();
      findings = rep.findings || [];
    } catch (e) {
      findings = [{ level: 'error', area: 'Emulator', message: String(e) }];
    }
    let a11y = [];
    if (withA11y) {
      const f = $('auditFrame');
      await new Promise((res) => { f.onload = res; f.src = '/render/' + base + j.id + '?' + p; });
      try {
        findings = findings.concat(await checkResources(f));
        a11y = await runAxe(f, j.mode === 'mail');
      } catch (e) { a11y = [{ level: 'warning', area: 'Barrierefreiheit', message: 'axe-core konnte nicht laufen: ' + e }]; }
    }
    const all = findings.concat(a11y);
    const n = (lvl) => findings.filter((x) => x.level === lvl).length;
    const row = { ...j, theme, errors: n('error'), warnings: n('warning'), infos: n('info'), a11y: a11y.length, findings: all };
    auditRows.push(row);
    const top = all.filter((x) => x.level !== 'info').slice(0, 3).map((x) => esc(x.message)).join('<br>');
    tbl.insertAdjacentHTML('beforeend', `<tr><td>${esc(j.label)}</td><td>${esc(j.template)}</td><td class="n">${row.errors}</td><td class="n">${row.warnings}</td><td class="n">${row.infos}</td><td class="n">${withA11y ? row.a11y : '–'}</td><td class="small">${top}</td></tr>`);
    prog.value++;
  }
  $('auditRun').disabled = false;
  $('auditDownload').disabled = false;
}

function downloadAudit() {
  const sc = state.catalog.scenario;
  let md = `# Theme-Prüfbericht\n\n- Login-Theme: ${state.theme.page}\n- E-Mail-Theme: ${state.theme.mail}\n- Keycloak: ${state.catalog.keycloakVersion}\n`
    + `- Sprache: ${state.locale || 'automatisch (' + sc.acceptLanguage + ')'}\n- Erstellt: ${new Date().toLocaleString('de-DE')}\n\n`;
  md += '| Seite / Mail | Template | Fehler | Warnungen | Hinweise | A11y |\n|---|---|---:|---:|---:|---:|\n';
  for (const r of auditRows) md += `| ${r.label} | ${r.template} | ${r.errors} | ${r.warnings} | ${r.infos} | ${r.a11y} |\n`;
  md += '\n## Befunde im Einzelnen\n';
  for (const r of auditRows) {
    if (!r.findings.length) continue;
    md += `\n### ${r.label} (${r.template})\n\n`;
    for (const f of r.findings) md += `- **${LEVEL_LABEL[f.level] || f.level}** · ${f.area}${f.heuristic ? ' (Heuristik)' : ''}: ${f.message}\n`;
  }
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob([md], { type: 'text/markdown' }));
  a.download = `theme-pruefung-${state.theme.page}-${new Date().toISOString().slice(0, 10)}.md`;
  a.click();
  URL.revokeObjectURL(a.href);
}

// ------------------------------------------------------------------ Themes importieren und auswählen

const MAX_IMPORT = 100 * 1024 * 1024;

function b64(buffer) {
  const bytes = new Uint8Array(buffer);
  let bin = '';
  for (let i = 0; i < bytes.length; i += 0x8000) bin += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000));
  return btoa(bin);
}

function importMsg(text, kind) {
  const m = $('importMsg');
  m.textContent = text;
  m.className = 'small ' + (kind || '');
}

async function refreshCatalog() {
  state.catalog = await (await fetch('/api/catalog')).json();
  fillThemes();
}

async function openThemes() {
  if (!$('dlgThemes').open) $('dlgThemes').showModal();
  const data = await (await fetch('/api/themes')).json();
  $('themesInfo').textContent = 'Theme-Ordner: ' + data.themesDir + ' – Änderungen an den Dateien erscheinen sofort in der Vorschau.';
  const rows = data.themes.map((t) => {
    const has = (k) => Object.prototype.hasOwnProperty.call(t.types, k);
    const parent = has('login') ? t.types.login.parent : has('email') ? t.types.email.parent : '';
    const active = [state.theme.page === t.name && has('login') ? 'Login' : '', state.theme.mail === t.name && has('email') ? 'E-Mail' : '']
      .filter(Boolean).join(', ');
    return `<tr><td><strong>${esc(t.name)}</strong>${active ? `<div class="small muted">aktiv: ${esc(active)}</div>` : ''}</td>`
      + `<td>${Object.keys(t.types).map((k) => `<span class="tag ${k === 'login' || k === 'email' ? 'on' : ''}">${esc(k)}</span>`).join('')}</td>`
      + `<td class="small">${parent ? 'parent: ' + esc(parent) : ''}</td>`
      + `<td class="small">${esc(new Date(t.modified).toLocaleString('de-DE'))}</td>`
      + `<td>${has('login') ? `<button type="button" data-use="login" data-name="${esc(t.name)}">Login testen</button>` : ''}`
      + `${has('email') ? `<button type="button" data-use="email" data-name="${esc(t.name)}">E-Mails testen</button>` : ''}`
      + `<button type="button" data-open="${esc(t.name)}">Ordner</button>`
      + `<button type="button" data-del="${esc(t.name)}">Entfernen</button></td></tr>`;
  });
  $('themeTable').innerHTML = '<tr><th>Theme</th><th>Typen</th><th>Basis</th><th>Geändert</th><th></th></tr>'
    + (rows.join('') || '<tr><td colspan="5" class="muted">Noch keine eigenen Themes – oben einen Ordner, ein ZIP oder JAR hineinziehen.</td></tr>');
}

/** Theme für Login-Seiten bzw. E-Mails auswählen und sofort anzeigen. */
async function useTheme(name, kind) {
  const body = { save: $('saveDefault').checked };
  body[kind] = name;
  await fetch('/api/themes/select', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  if (kind === 'login') state.theme.page = name; else state.theme.mail = name;
  await refreshCatalog();
  const mode = kind === 'login' ? 'page' : 'mail';
  if (state.mode !== mode) {
    select(mode, mode === 'page' ? 'login' : state.catalog.mails[0].id, 'standard');
  } else {
    fillThemes();
    render();
  }
}

async function importPayload(payload, label, overwrite) {
  importMsg(`Importiere ${label} …`);
  const r = await fetch('/api/themes/import' + (overwrite ? '?overwrite=1' : ''), {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload),
  });
  const res = await r.json().catch(() => ({ error: 'Unerwartete Antwort (HTTP ' + r.status + ')' }));
  if (r.status === 409) {
    if (confirm(`Theme „${res.existing.join('“, „')}“ ist schon vorhanden. Ersetzen?\n(Die bisherigen Dateien dieses Themes werden gelöscht.)`)) {
      return importPayload(payload, label, true);
    }
    importMsg('Import abgebrochen – vorhandenes Theme bleibt unverändert.');
    return;
  }
  if (!r.ok) {
    importMsg(res.error || 'Import fehlgeschlagen.', 'error');
    return;
  }
  const names = res.imported.map((t) => `„${t.name}“ (${t.types.join(', ') || 'ohne Typ'}, ${t.files} Dateien)`);
  importMsg('Importiert: ' + names.join(', ') + (res.warnings.length ? ' – ' + res.warnings.join(' ') : ''), 'ok');
  await refreshCatalog();
  await openThemes();
  // das erste importierte Theme direkt anzeigen
  const first = res.imported[0];
  if (first && first.types.includes('login')) await useTheme(first.name, 'login');
  else if (first && first.types.includes('email')) await useTheme(first.name, 'email');
}

async function importArchive(file) {
  if (file.size > MAX_IMPORT) { importMsg('Datei ist größer als 100 MB.', 'error'); return; }
  await importPayload({ archiveName: file.name, archive: b64(await file.arrayBuffer()) }, file.name);
}

/** files: [{path, file}] mit Pfaden relativ zum gewählten Ordner (inkl. Ordnername). */
async function importFolder(folderName, files) {
  const total = files.reduce((n, f) => n + f.file.size, 0);
  if (total > MAX_IMPORT) { importMsg('Ordner ist größer als 100 MB.', 'error'); return; }
  const out = [];
  for (const f of files) out.push({ path: f.path, data: b64(await f.file.arrayBuffer()) });
  await importPayload({ folderName, files: out }, `Ordner „${folderName}“`);
}

/** Ordner aus Drag & Drop rekursiv einlesen (FileSystemEntry-API). */
function readEntry(entry, prefix, out) {
  return new Promise((resolve) => {
    if (entry.isFile) {
      entry.file((file) => { out.push({ path: prefix + entry.name, file }); resolve(); }, () => resolve());
    } else if (entry.isDirectory) {
      const reader = entry.createReader();
      const all = [];
      const next = () => reader.readEntries(async (batch) => {
        if (!batch.length) {
          for (const e of all) await readEntry(e, prefix + entry.name + '/', out);
          resolve();
        } else {
          all.push(...batch);
          next();
        }
      }, () => resolve());
      next();
    } else {
      resolve();
    }
  });
}

/** entries/files müssen synchron im drop-Ereignis gelesen werden – danach ist das DataTransfer leer. */
async function handleDrop(entries, fallbackFile) {
  if (!entries.length) {
    if (fallbackFile) await importArchive(fallbackFile);
    return;
  }
  for (const entry of entries) {
    if (entry.isFile) {
      if (/\.(zip|jar)$/i.test(entry.name)) {
        await new Promise((res) => entry.file(async (f) => { await importArchive(f); res(); }, res));
      } else {
        importMsg(`„${entry.name}“ ist weder Ordner noch ZIP/JAR.`, 'error');
      }
    } else {
      const files = [];
      await readEntry(entry, '', files);
      await importFolder(entry.name, files);
    }
  }
}

function bindThemes() {
  $('btnThemes').onclick = openThemes;
  $('btnThemes2').onclick = openThemes;
  $('themesClose').onclick = () => $('dlgThemes').close();
  $('pickZip').onclick = () => $('fileZip').click();
  $('pickFolder').onclick = () => $('fileFolder').click();
  $('fileZip').onchange = async (e) => { if (e.target.files[0]) await importArchive(e.target.files[0]); e.target.value = ''; };
  $('fileFolder').onchange = async (e) => {
    const files = [...e.target.files].map((f) => ({ path: f.webkitRelativePath || f.name, file: f }));
    if (files.length) await importFolder(files[0].path.split('/')[0], files);
    e.target.value = '';
  };
  $('themeTable').onclick = async (e) => {
    const b = e.target.closest('button');
    if (!b) return;
    if (b.dataset.use) {
      await useTheme(b.dataset.name, b.dataset.use);
      $('dlgThemes').close();
    } else if (b.dataset.open) {
      const r = await fetch(`/api/themes/${encodeURIComponent(b.dataset.open)}/open`, { method: 'POST' });
      const res = await r.json();
      if (!res.ok) importMsg('Ordner: ' + res.path, '');
    } else if (b.dataset.del) {
      if (!confirm(`Theme „${b.dataset.del}“ mit allen Dateien aus dem Theme-Ordner löschen?`)) return;
      await fetch(`/api/themes/${encodeURIComponent(b.dataset.del)}`, { method: 'DELETE' });
      importMsg(`„${b.dataset.del}“ entfernt.`);
      await refreshCatalog();
      await openThemes();
    }
  };
  // Drag & Drop: im Dialog auf die Fläche oder irgendwo auf das Fenster
  const dz = $('dropzone');
  let depth = 0;
  const hasFiles = (e) => [...(e.dataTransfer?.types || [])].includes('Files');
  window.addEventListener('dragenter', (e) => { if (hasFiles(e)) { depth++; $('dropveil').hidden = false; } });
  window.addEventListener('dragleave', () => { depth = Math.max(0, depth - 1); if (!depth) $('dropveil').hidden = true; });
  window.addEventListener('dragover', (e) => { if (hasFiles(e)) e.preventDefault(); });
  window.addEventListener('drop', async (e) => {
    if (!hasFiles(e)) return;
    e.preventDefault();
    const dt = e.dataTransfer;
    const entries = [...(dt.items || [])].map((i) => i.webkitGetAsEntry && i.webkitGetAsEntry()).filter(Boolean);
    const fallbackFile = dt.files && dt.files[0];
    depth = 0;
    $('dropveil').hidden = true;
    dz.classList.remove('over');
    await openThemes();
    await handleDrop(entries, fallbackFile);
  });
  dz.addEventListener('dragover', () => dz.classList.add('over'));
  dz.addEventListener('dragleave', () => dz.classList.remove('over'));
}

// ------------------------------------------------------------------ Live-Neuladen

async function pollStamp() {
  if (!$('liveReload').checked || document.hidden) return;
  try {
    const { stamp } = await (await fetch('/api/stamp')).json();
    if (state.stamp && stamp !== state.stamp) render();
    state.stamp = stamp;
  } catch (e) { /* Server neu gestartet */ }
}

// ------------------------------------------------------------------ Ereignisse

function bind() {
  $('tabPages').onclick = () => { if (state.mode !== 'page') select('page', state.catalog.pages[0].id, 'standard'); };
  $('tabMails').onclick = () => { if (state.mode !== 'mail') select('mail', state.catalog.mails[0].id); };
  $('filter').oninput = showList;
  $('list').onclick = (e) => {
    const b = e.target.closest('button[data-id]');
    if (b) select(state.mode, b.dataset.id, 'standard');
  };
  $('variant').onchange = (e) => { state.variant = e.target.value; persist(); render(); };
  $('locale').onchange = (e) => { state.locale = e.target.value; persist(); render(); };
  $('theme').onchange = (e) => { state.theme[state.mode] = e.target.value; render(); };
  $('theme2').onchange = (e) => { state.theme2[state.mode] = e.target.value; render(); };
  $('compare').onchange = (e) => { state.compare = e.target.checked; render(); };
  $('reload').onclick = render;
  $('zoomIn').onclick = () => zoomStep(1);
  $('zoomOut').onclick = () => zoomStep(-1);
  $('zoomLevel').onclick = zoomReset;
  $('zoomFit').onclick = zoomFit;
  // Tastatur: + / − / 0 / F (nicht in Eingabefeldern und nicht bei gedrückter Strg-/Cmd-Taste)
  document.addEventListener('keydown', (e) => {
    if (e.ctrlKey || e.metaKey || e.altKey || e.target.closest('input, textarea, select, dialog[open]')) return;
    if (e.key === '+' || e.key === '=') zoomStep(1);
    else if (e.key === '-' || e.key === '−') zoomStep(-1);
    else if (e.key === '0') zoomReset();
    else if (e.key === 'f' || e.key === 'F') zoomFit();
    else return;
    e.preventDefault();
  });
  // Fensterbreite, Vergleichsansicht und Seitenleisten ändern den verfügbaren Platz
  new ResizeObserver(() => applyWidth()).observe($('stage'));
  $('viewports').onclick = (e) => {
    const b = e.target.closest('button');
    if (!b) return;
    state.width = Number(b.dataset.w);
    persist();
    applyWidth();
  };
  $('mailParts').onclick = (e) => {
    const b = e.target.closest('button');
    if (!b) return;
    state.part = b.dataset.part;
    for (const x of $('mailParts').querySelectorAll('button')) x.classList.toggle('on', x === b);
    render();
  };
  $('f1').addEventListener('load', async () => {
    state.resourceFindings = await checkResources($('f1'));
    showReport();
  });
  $('btnA11y').onclick = async () => {
    $('btnA11y').disabled = true;
    try {
      const v = await runAxe($('f1'), state.mode === 'mail');
      renderFindings($('a11y'), v.length ? v : [{ level: 'ok', area: 'Barrierefreiheit', message: 'axe-core: keine Verstöße gefunden' }]);
    } catch (e) {
      renderFindings($('a11y'), [{ level: 'warning', area: 'Barrierefreiheit', message: 'axe-core konnte nicht laufen: ' + e }]);
    }
    $('btnA11y').disabled = false;
  };
  $('btnScenario').onclick = openScenario;
  $('flags').onchange = syncFlagsToJson;
  $('scApply').onclick = () => saveScenario(false);
  $('scSave').onclick = () => saveScenario(true);
  $('btnAudit').onclick = () => {
    $('auditInfo').textContent = `Login-Theme „${state.theme.page}“, E-Mail-Theme „${state.theme.mail}“, Sprache ${state.locale || 'automatisch'} – alle Seitenvarianten und Mails.`;
    $('dlgAudit').showModal();
  };
  $('auditRun').onclick = runAudit;
  $('auditDownload').onclick = downloadAudit;
  $('auditClose').onclick = () => $('dlgAudit').close();
}

init().catch((e) => {
  document.body.insertAdjacentHTML('afterbegin', `<p class="error" style="padding:8px 16px">Start fehlgeschlagen: ${esc(e)}</p>`);
});
