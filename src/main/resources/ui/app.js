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
  $('kcVersion').textContent = 'Keycloak ' + cat.keycloakVersion;
  $('themesDir').textContent = 'Theme-Ordner: ' + cat.themesDir;
  state.theme.page = cat.scenario.loginTheme;
  state.theme.mail = cat.scenario.emailTheme;
  restore();
  fillLocales();
  bind();
  showList();
  select(state.mode, state.id, state.variant);
  setInterval(pollStamp, 1500);
}

function persist() {
  try {
    localStorage.setItem('kcemu', JSON.stringify({ mode: state.mode, id: state.id, variant: state.variant, locale: state.locale, width: state.width }));
  } catch (e) { /* ohne Speicher weiter */ }
}

function restore() {
  try {
    const s = JSON.parse(localStorage.getItem('kcemu') || '{}');
    Object.assign(state, s);
  } catch (e) { /* ohne Speicher weiter */ }
  if (!isKnown(state.mode, state.id)) { state.mode = 'page'; state.id = 'login'; state.variant = 'standard'; }
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

function applyWidth() {
  for (const vp of [$('vp1'), $('vp2')]) {
    const f = vp.querySelector('iframe');
    f.style.width = state.width ? state.width + 'px' : '100%';
    f.style.maxWidth = '100%';
  }
  for (const b of $('viewports').querySelectorAll('button')) b.classList.toggle('on', Number(b.dataset.w) === state.width);
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
