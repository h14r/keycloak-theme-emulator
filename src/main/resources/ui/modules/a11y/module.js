/*
 * Modul „Barrierefreiheit (WCAG 2.2 AA)“ für den Keycloak Theme Emulator.
 *
 * Prüft die Vorschau mit axe-core (Regeln mit den Tags wcag2a, wcag2aa, wcag21a, wcag21aa,
 * wcag22aa sowie best-practice) und stellt das Ergebnis ähnlich wie WAVE dar: Symbole direkt
 * auf der Seite in sechs Kategorien, Übersicht, Details mit Referenz, Tab-Reihenfolge, Gliederung,
 * Kontrastrechner und eine Liste aller WCAG-2.2-Kriterien der Stufen A und AA.
 *
 * Das Modul ist in sich geschlossen: Löschen des Ordners ui/modules/a11y/ und des Eintrags in
 * ui/modules/modules.json entfernt es vollständig.
 */
(() => {
  'use strict';
  const K = window.KCEMU;
  if (!K) return;
  const { esc } = K.api;

  // ---------------------------------------------------------------- Grundlagen

  /** WCAG 2.2 (W3C-Empfehlung 12.12.2024), Erfolgskriterien Stufe A und AA – 4.1.1 ist entfallen. */
  const WCAG = [
    ['1.1.1', 'A', 'Non-text Content', 'non-text-content'],
    ['1.2.1', 'A', 'Audio-only and Video-only (Prerecorded)', 'audio-only-and-video-only-prerecorded'],
    ['1.2.2', 'A', 'Captions (Prerecorded)', 'captions-prerecorded'],
    ['1.2.3', 'A', 'Audio Description or Media Alternative (Prerecorded)', 'audio-description-or-media-alternative-prerecorded'],
    ['1.2.4', 'AA', 'Captions (Live)', 'captions-live'],
    ['1.2.5', 'AA', 'Audio Description (Prerecorded)', 'audio-description-prerecorded'],
    ['1.3.1', 'A', 'Info and Relationships', 'info-and-relationships'],
    ['1.3.2', 'A', 'Meaningful Sequence', 'meaningful-sequence'],
    ['1.3.3', 'A', 'Sensory Characteristics', 'sensory-characteristics'],
    ['1.3.4', 'AA', 'Orientation', 'orientation'],
    ['1.3.5', 'AA', 'Identify Input Purpose', 'identify-input-purpose'],
    ['1.4.1', 'A', 'Use of Color', 'use-of-color'],
    ['1.4.2', 'A', 'Audio Control', 'audio-control'],
    ['1.4.3', 'AA', 'Contrast (Minimum)', 'contrast-minimum'],
    ['1.4.4', 'AA', 'Resize Text', 'resize-text'],
    ['1.4.5', 'AA', 'Images of Text', 'images-of-text'],
    ['1.4.10', 'AA', 'Reflow', 'reflow'],
    ['1.4.11', 'AA', 'Non-text Contrast', 'non-text-contrast'],
    ['1.4.12', 'AA', 'Text Spacing', 'text-spacing'],
    ['1.4.13', 'AA', 'Content on Hover or Focus', 'content-on-hover-or-focus'],
    ['2.1.1', 'A', 'Keyboard', 'keyboard'],
    ['2.1.2', 'A', 'No Keyboard Trap', 'no-keyboard-trap'],
    ['2.1.4', 'A', 'Character Key Shortcuts', 'character-key-shortcuts'],
    ['2.2.1', 'A', 'Timing Adjustable', 'timing-adjustable'],
    ['2.2.2', 'A', 'Pause, Stop, Hide', 'pause-stop-hide'],
    ['2.3.1', 'A', 'Three Flashes or Below Threshold', 'three-flashes-or-below-threshold'],
    ['2.4.1', 'A', 'Bypass Blocks', 'bypass-blocks'],
    ['2.4.2', 'A', 'Page Titled', 'page-titled'],
    ['2.4.3', 'A', 'Focus Order', 'focus-order'],
    ['2.4.4', 'A', 'Link Purpose (In Context)', 'link-purpose-in-context'],
    ['2.4.5', 'AA', 'Multiple Ways', 'multiple-ways'],
    ['2.4.6', 'AA', 'Headings and Labels', 'headings-and-labels'],
    ['2.4.7', 'AA', 'Focus Visible', 'focus-visible'],
    ['2.4.11', 'AA', 'Focus Not Obscured (Minimum)', 'focus-not-obscured-minimum'],
    ['2.5.1', 'A', 'Pointer Gestures', 'pointer-gestures'],
    ['2.5.2', 'A', 'Pointer Cancellation', 'pointer-cancellation'],
    ['2.5.3', 'A', 'Label in Name', 'label-in-name'],
    ['2.5.4', 'A', 'Motion Actuation', 'motion-actuation'],
    ['2.5.7', 'AA', 'Dragging Movements', 'dragging-movements'],
    ['2.5.8', 'AA', 'Target Size (Minimum)', 'target-size-minimum'],
    ['3.1.1', 'A', 'Language of Page', 'language-of-page'],
    ['3.1.2', 'AA', 'Language of Parts', 'language-of-parts'],
    ['3.2.1', 'A', 'On Focus', 'on-focus'],
    ['3.2.2', 'A', 'On Input', 'on-input'],
    ['3.2.3', 'AA', 'Consistent Navigation', 'consistent-navigation'],
    ['3.2.4', 'AA', 'Consistent Identification', 'consistent-identification'],
    ['3.2.6', 'A', 'Consistent Help', 'consistent-help'],
    ['3.3.1', 'A', 'Error Identification', 'error-identification'],
    ['3.3.2', 'A', 'Labels or Instructions', 'labels-or-instructions'],
    ['3.3.3', 'AA', 'Error Suggestion', 'error-suggestion'],
    ['3.3.4', 'AA', 'Error Prevention (Legal, Financial, Data)', 'error-prevention-legal-financial-data'],
    ['3.3.7', 'A', 'Redundant Entry', 'redundant-entry'],
    ['3.3.8', 'AA', 'Accessible Authentication (Minimum)', 'accessible-authentication-minimum'],
    ['4.1.2', 'A', 'Name, Role, Value', 'name-role-value'],
    ['4.1.3', 'AA', 'Status Messages', 'status-messages'],
  ].map(([sc, level, title, slug]) => ({ sc, level, title, url: `https://www.w3.org/WAI/WCAG22/Understanding/${slug}.html` }));
  const SC = Object.fromEntries(WCAG.map((c) => [c.sc, c]));

  const WCAG_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'];
  /** Seitenbezogene Regeln, die für E-Mail-HTML nicht gelten (kein Dokument im Browser-Sinn). */
  const MAIL_OFF = ['document-title', 'landmark-one-main', 'page-has-heading-one', 'region', 'html-has-lang'];

  /** Kategorien wie in WAVE. */
  const CATS = {
    error: { label: 'Fehler', icon: '✕', tip: 'Verstöße gegen WCAG 2.2 A/AA, die axe-core sicher erkennt' },
    contrast: { label: 'Kontrastfehler', icon: '◐', tip: 'Text mit zu geringem Kontrast (1.4.3)' },
    alert: { label: 'Hinweise', icon: '!', tip: 'Manuell zu prüfen oder Best Practice – nicht automatisch ein Verstoß' },
    feature: { label: 'Merkmale', icon: '✓', tip: 'Vorhandene Barrierefreiheits-Merkmale (Alternativtexte, Beschriftungen, Sprache …)' },
    structure: { label: 'Struktur', icon: '≡', tip: 'Überschriften, Landmarks, Listen und Tabellen' },
    aria: { label: 'ARIA', icon: 'A', tip: 'Elemente mit ARIA-Rollen oder -Attributen' },
  };
  const CAT_KEYS = Object.keys(CATS);

  const IMPACT = { critical: 'kritisch', serious: 'schwer', moderate: 'mittel', minor: 'gering' };

  const STATUS = {
    fail: { label: 'Verstoß', cls: 'error' },
    review: { label: 'Prüfen', cls: 'warning' },
    pass: { label: 'Autom. ohne Befund', cls: 'ok' },
    na: { label: 'Nicht anwendbar', cls: 'info' },
    manual: { label: 'Nur manuell', cls: 'manual' },
  };

  const STORE = 'kcemu-a11y';
  const prefs = Object.assign({ auto: true, icons: true, tab: 'summary', cats: Object.fromEntries(CAT_KEYS.map((k) => [k, true])), order: false }, load());
  function load() {
    try { return JSON.parse(localStorage.getItem(STORE) || '{}'); } catch (e) { return {}; }
  }
  function save() {
    try { localStorage.setItem(STORE, JSON.stringify(prefs)); } catch (e) { /* ohne Speicher weiter */ }
  }

  // ---------------------------------------------------------------- Prüfung

  let localeDe = null;

  async function ensureAxe(frame) {
    const win = frame.contentWindow;
    const doc = frame.contentDocument;
    if (!win.axe) {
      await new Promise((res, rej) => {
        const s = doc.createElement('script');
        s.src = '/vendor/axe.min.js';
        s.dataset.kcemu = 'a11y';
        s.onload = res;
        s.onerror = () => rej(new Error('axe-core nicht ladbar'));
        (doc.head || doc.documentElement).appendChild(s);
      });
    }
    if (!localeDe) {
      try { localeDe = await (await fetch('/vendor/axe-de.json')).json(); } catch (e) { localeDe = {}; }
    }
    if (localeDe.lang && !win.__kcemuAxeDe) {
      try { win.axe.configure({ locale: localeDe }); win.__kcemuAxeDe = true; } catch (e) { /* englische Texte */ }
    }
    return win.axe;
  }

  /** axe-Tags wie „wcag143“ bzw. „wcag1412“ → „1.4.3“ bzw. „1.4.12“. */
  function scOf(tags) {
    const out = [];
    for (const t of tags) {
      const m = /^wcag(\d)(\d)(\d{1,2})$/.exec(t);
      if (m) out.push(`${m[1]}.${m[2]}.${m[3]}`);
    }
    return out;
  }

  function isVisible(el) {
    if (!el || !el.getClientRects || !el.getClientRects().length) return false;
    const cs = el.ownerDocument.defaultView.getComputedStyle(el);
    return cs.visibility !== 'hidden';
  }

  function snippet(el) {
    if (!el || !el.outerHTML) return '';
    const h = el.outerHTML;
    const open = h.slice(0, h.indexOf('>') + 1);
    return open.length > 240 ? open.slice(0, 240) + '…' : open;
  }

  function text(el, max = 80) {
    const t = (el.textContent || '').replace(/\s+/g, ' ').trim();
    return t.length > max ? t.slice(0, max) + '…' : t;
  }

  /**
   * Prüft das Dokument im Frame. Ändert die Seite nicht (Symbole zeichnet erst die Ansicht).
   * Ergebnis: { items, criteria, order, headings, landmarks, contrast, rules }.
   */
  async function analyze(frame, isMail) {
    const doc = frame.contentDocument;
    if (!doc || !doc.documentElement) throw new Error('Keine Vorschau geladen');
    if (doc.contentType && doc.contentType !== 'text/html') {
      return { skipped: 'Nur HTML lässt sich prüfen – für den Text-Teil der Mail gibt es keine Prüfung.' };
    }
    const axe = await ensureAxe(frame);
    const rules = Object.fromEntries((isMail ? MAIL_OFF : []).map((r) => [r, { enabled: false }]));
    const res = await axe.run(
      { include: [doc], exclude: [['#kcemu-a11y-layer'], ['script[data-kcemu]']] },
      { runOnly: { type: 'tag', values: [...WCAG_TAGS, 'best-practice'] }, rules, elementRef: true },
    );

    const items = [];
    const ruleInfo = {};
    const add = (r, kind) => {
      const sc = scOf(r.tags);
      const ri = ruleInfo[r.id] || (ruleInfo[r.id] = { id: r.id, help: r.help, sc, bp: sc.length === 0, kinds: new Set() });
      ri.kinds.add(kind);
      return sc;
    };
    for (const r of res.violations) {
      const sc = add(r, 'fail');
      const bp = sc.length === 0;
      const cat = r.id === 'color-contrast' ? 'contrast' : bp ? 'alert' : 'error';
      for (const n of r.nodes) {
        items.push({
          cat, source: 'axe', kind: bp ? 'Best Practice' : 'Verstoß', rule: r.id, title: r.help, desc: r.description,
          sc, impact: n.impact || r.impact, el: n.element, target: n.target.join(' '), html: n.html,
          fix: n.failureSummary, helpUrl: r.helpUrl, contrast: contrastData(n),
        });
      }
    }
    for (const r of res.incomplete) {
      const sc = add(r, 'review');
      for (const n of r.nodes) {
        items.push({
          cat: 'alert', source: 'axe', kind: 'Manuell prüfen', rule: r.id, title: r.help, desc: r.description,
          sc, impact: n.impact || r.impact, el: n.element, target: n.target.join(' '), html: n.html,
          fix: n.failureSummary, helpUrl: r.helpUrl, contrast: contrastData(n),
        });
      }
    }
    for (const r of res.passes) add(r, 'pass');
    for (const r of res.inapplicable) add(r, 'na');

    const contrast = [];
    for (const [list, ok] of [[res.violations, false], [res.incomplete, null], [res.passes, true]]) {
      const r = list.find((x) => x.id === 'color-contrast');
      if (!r) continue;
      for (const n of r.nodes) {
        // ohne Farbdaten (z. B. Hintergrundbild) trotzdem auflisten – Verhältnis dann unbekannt
        contrast.push({ ok, el: n.element, text: text(n.element, 60), ...(contrastData(n) || {}) });
      }
    }

    const dom = scanDom(doc, isMail, axe);
    items.push(...dom.items);

    // Kriterien: Ergebnis je WCAG-2.2-Erfolgskriterium aus den gelaufenen axe-Regeln
    const criteria = WCAG.map((c) => {
      const rs = Object.values(ruleInfo).filter((r) => r.sc.includes(c.sc));
      let status = 'manual';
      if (rs.some((r) => r.kinds.has('fail'))) status = 'fail';
      else if (rs.some((r) => r.kinds.has('review'))) status = 'review';
      else if (rs.some((r) => r.kinds.has('pass'))) status = 'pass';
      else if (rs.length) status = 'na';
      return { ...c, status, rules: rs.map((r) => r.id) };
    });

    return { items, criteria, contrast, order: dom.order, headings: dom.headings, landmarks: dom.landmarks, rules: ruleInfo, isMail, at: new Date(), axeVersion: axe.version };
  }

  function contrastData(n) {
    const c = [...(n.any || []), ...(n.all || []), ...(n.none || [])].find((x) => x.data && x.data.fgColor);
    if (!c) return null;
    const d = c.data;
    return { fg: d.fgColor, bg: d.bgColor, ratio: d.contrastRatio, required: d.expectedContrastRatio, size: d.fontSize, weight: d.fontWeight };
  }

  /** Rolle und zugänglicher Name über axe-core (gleiche Berechnung wie die Regeln). */
  function accessor(doc, axe) {
    let ok = false;
    try { axe.setup(doc.documentElement); ok = true; } catch (e) { ok = false; }
    const node = (el) => (ok ? axe.utils.getNodeFromTree(el) : null);
    return {
      role(el) {
        try { const v = node(el); if (v) return axe.commons.aria.getRole(v) || ''; } catch (e) { /* weiter */ }
        return el.getAttribute('role') || el.localName;
      },
      name(el) {
        try { const v = node(el); if (v) return axe.commons.text.accessibleTextVirtual(v).trim(); } catch (e) { /* weiter */ }
        return (el.getAttribute('aria-label') || el.getAttribute('alt') || el.getAttribute('title') || text(el)).trim();
      },
      done() { if (ok) try { axe.teardown(); } catch (e) { /* egal */ } },
    };
  }

  const LANDMARK_ROLES = { banner: 'Kopfbereich (banner)', navigation: 'Navigation', main: 'Hauptinhalt (main)', complementary: 'Ergänzend (complementary)', contentinfo: 'Fußbereich (contentinfo)', search: 'Suche', form: 'Formular (form)', region: 'Bereich (region)' };
  const FOCUSABLE = 'a[href], area[href], button, input, select, textarea, summary, iframe, [tabindex], [contenteditable=""], [contenteditable="true"]';
  /** übliche Keycloak-Felder und der passende autocomplete-Wert (HTML „autofill“) */
  const PURPOSE = { username: 'username', email: 'email', firstName: 'given-name', lastName: 'family-name', password: 'current-password', 'password-new': 'new-password', 'password-confirm': 'new-password' };

  /** Merkmale, Struktur und ARIA (wie WAVE) sowie wenige eigene, als Heuristik markierte Hinweise. */
  function scanDom(doc, isMail, axe) {
    const items = [];
    const acc = accessor(doc, axe);
    const win = doc.defaultView;
    const skip = (el) => el.closest('#kcemu-a11y-layer, script, style, template, noscript, head');
    const push = (o) => items.push({ source: 'dom', kind: o.cat === 'alert' ? 'Hinweis' : CATS[o.cat].label, sc: [], ...o, html: snippet(o.el) });

    try {
      // Merkmale
      const html = doc.documentElement;
      if (html.getAttribute('lang')) push({ cat: 'feature', rule: 'lang', title: `Sprache der Seite: ${html.getAttribute('lang')}`, sc: ['3.1.1'], el: doc.body || html, desc: 'Das lang-Attribut am <html>-Element legt die Sprache für Screenreader fest.' });
      for (const el of doc.querySelectorAll('body [lang]')) {
        if (skip(el)) continue;
        push({ cat: 'feature', rule: 'lang-part', title: `Sprache eines Teils: ${el.getAttribute('lang')}`, sc: ['3.1.2'], el, desc: 'Abschnitte in anderer Sprache sind ausgezeichnet.' });
      }
      for (const el of doc.querySelectorAll('img[alt], input[type=image][alt], area[alt]')) {
        if (skip(el)) continue;
        const alt = el.getAttribute('alt');
        if (alt.trim()) push({ cat: 'feature', rule: 'alt', title: `Alternativtext: „${alt.trim().slice(0, 60)}“`, sc: ['1.1.1'], el, desc: 'Prüfen, ob der Text den Zweck des Bildes wiedergibt.' });
        else push({ cat: 'feature', rule: 'alt-null', title: 'Leerer Alternativtext (dekoratives Bild)', sc: ['1.1.1'], el, desc: 'alt="" lässt Screenreader das Bild überspringen – nur richtig, wenn es rein dekorativ ist.' });
      }
      for (const el of doc.querySelectorAll('input, select, textarea')) {
        if (skip(el)) continue;
        const type = (el.getAttribute('type') || '').toLowerCase();
        if (['hidden', 'submit', 'button', 'reset', 'image'].includes(type)) continue;
        const name = acc.name(el);
        if (name) push({ cat: 'feature', rule: 'label', title: `Beschriftetes Feld: „${name.slice(0, 60)}“`, sc: ['1.3.1', '3.3.2'], el, desc: 'Das Feld hat einen zugänglichen Namen (label, aria-label oder aria-labelledby).' });
        const ac = el.getAttribute('autocomplete');
        if (ac && ac !== 'off') push({ cat: 'feature', rule: 'autocomplete', title: `Eingabezweck: autocomplete="${ac}"`, sc: ['1.3.5'], el, desc: 'Der Zweck des Feldes ist maschinenlesbar angegeben.' });
        const want = PURPOSE[el.getAttribute('name')];
        if (want && (!ac || ac === 'off')) {
          push({ cat: 'alert', rule: 'kcemu-autocomplete', heuristic: true, title: `Eingabezweck fehlt (autocomplete${ac ? '="off"' : ''}) – passend wäre „${want}“`, sc: ['1.3.5'], el,
            desc: '1.3.5 verlangt, dass der Zweck von Feldern zu Angaben über die Person programmatisch bestimmbar ist. Bei Benutzername, E-Mail, Namen und Passwort geschieht das über autocomplete.' });
        }
      }
      for (const el of doc.querySelectorAll('fieldset')) {
        if (skip(el)) continue;
        const lg = el.querySelector(':scope > legend');
        if (lg && text(lg)) push({ cat: 'feature', rule: 'fieldset', title: `Gruppe mit Legende: „${text(lg, 60)}“`, sc: ['1.3.1'], el, desc: 'Zusammengehörige Felder sind gruppiert und benannt.' });
      }
      const firstFocus = [...doc.querySelectorAll(FOCUSABLE)].find((el) => !skip(el) && el.tabIndex >= 0);
      if (firstFocus && firstFocus.matches('a[href^="#"]') && firstFocus.getAttribute('href').length > 1) {
        const target = doc.getElementById(decodeURIComponent(firstFocus.getAttribute('href').slice(1)));
        if (target) push({ cat: 'feature', rule: 'skip-link', title: `Sprunglink: „${text(firstFocus, 50)}“`, sc: ['2.4.1'], el: firstFocus, desc: 'Erster fokussierbarer Link springt zu einem Ziel auf der Seite.' });
      }

      // Struktur
      const headings = [];
      for (const el of doc.querySelectorAll('h1, h2, h3, h4, h5, h6, [role=heading]')) {
        if (skip(el)) continue;
        const level = el.getAttribute('role') === 'heading' ? Number(el.getAttribute('aria-level') || 2) : Number(el.localName[1]);
        const name = acc.name(el) || text(el);
        headings.push({ el, level, name, visible: isVisible(el) });
        push({ cat: 'structure', rule: 'heading', title: `Überschrift H${level}: „${name.slice(0, 60)}“`, sc: ['1.3.1', '2.4.6'], el });
      }
      const landmarks = [];
      for (const el of doc.querySelectorAll('header, nav, main, aside, footer, form, section, search, [role]')) {
        if (skip(el)) continue;
        const role = acc.role(el);
        if (!LANDMARK_ROLES[role]) continue;
        const name = el.getAttribute('aria-label') || el.getAttribute('aria-labelledby') ? acc.name(el) : '';
        landmarks.push({ el, role, name, visible: isVisible(el) });
        push({ cat: 'structure', rule: 'landmark', title: `Landmark: ${LANDMARK_ROLES[role]}${name ? ` „${name.slice(0, 40)}“` : ''}`, sc: ['1.3.1', '2.4.1'], el });
      }
      for (const el of doc.querySelectorAll('ul, ol, dl')) {
        if (skip(el)) continue;
        const n = el.querySelectorAll(':scope > li, :scope > dt').length;
        push({ cat: 'structure', rule: 'list', title: `Liste (${el.localName}, ${n} Einträge)`, sc: ['1.3.1'], el });
      }
      for (const el of doc.querySelectorAll('table')) {
        if (skip(el)) continue;
        const role = el.getAttribute('role');
        if (role === 'presentation' || role === 'none') push({ cat: 'structure', rule: 'table-layout', title: 'Layouttabelle (role=presentation)', sc: ['1.3.1'], el });
        else if (el.querySelector('th')) push({ cat: 'structure', rule: 'table-data', title: 'Datentabelle mit Kopfzellen', sc: ['1.3.1'], el });
        else push({ cat: 'alert', rule: 'kcemu-table', heuristic: true, title: isMail ? 'Tabelle ohne Kopfzellen und ohne role="presentation"' : 'Tabelle ohne Kopfzellen', sc: ['1.3.1'], el,
          desc: isMail ? 'In HTML-Mails dienen Tabellen meist dem Layout – dann role="presentation" setzen, damit Screenreader sie nicht als Datentabelle ansagen.' : 'Ist es eine Datentabelle, fehlen <th>-Zellen; dient sie dem Layout, role="presentation" setzen.' });
      }

      // ARIA
      for (const el of doc.querySelectorAll('*')) {
        if (skip(el)) continue;
        const attrs = [...el.attributes].filter((a) => a.name === 'role' || a.name.startsWith('aria-'));
        if (!attrs.length) continue;
        const has = (n) => el.hasAttribute(n);
        const role = el.getAttribute('role');
        let title = 'ARIA';
        let sc = ['4.1.2'];
        if (has('aria-live') || role === 'alert' || role === 'status' || role === 'log') { title = `Live-Region (${role || 'aria-live=' + el.getAttribute('aria-live')})`; sc = ['4.1.3']; }
        else if (el.getAttribute('aria-hidden') === 'true') title = 'aria-hidden – für Screenreader ausgeblendet';
        else if (has('aria-label')) title = `aria-label: „${el.getAttribute('aria-label').slice(0, 50)}“`;
        else if (has('aria-labelledby')) title = 'aria-labelledby';
        else if (has('aria-describedby')) title = 'aria-describedby';
        else if (has('aria-invalid')) { title = `aria-invalid="${el.getAttribute('aria-invalid')}"`; sc = ['3.3.1', '4.1.2']; }
        else if (has('aria-expanded')) title = `aria-expanded="${el.getAttribute('aria-expanded')}"`;
        else if (role) title = `role="${role}"`;
        push({ cat: 'aria', rule: 'aria', title, sc, el, desc: attrs.map((a) => `${a.name}="${a.value}"`).join('  ') });
      }

      // Eigene Hinweise (Heuristik)
      for (const el of doc.querySelectorAll('a[target=_blank]')) {
        if (skip(el)) continue;
        push({ cat: 'alert', rule: 'kcemu-new-window', heuristic: true, title: 'Link öffnet ein neues Fenster', el, desc: 'Den Hinweis darauf im Linktext oder als Symbol mit Text geben.' });
      }
      const small = new Set();
      for (const el of doc.body ? doc.body.querySelectorAll('*') : []) {
        if (skip(el) || small.size >= 30) continue;
        const own = [...el.childNodes].some((n) => n.nodeType === 3 && n.textContent.trim());
        if (!own || !isVisible(el)) continue;
        const px = parseFloat(win.getComputedStyle(el).fontSize);
        if (px && px < 12 && ![...small].some((s) => s.contains(el))) {
          small.add(el);
          push({ cat: 'alert', rule: 'kcemu-small-text', heuristic: true, title: `Sehr kleine Schrift (${Math.round(px * 10) / 10} px)`, el, desc: 'Kein WCAG-Verstoß an sich, aber schwer lesbar. Bei 200 % Vergrößerung (1.4.4) prüfen.' });
        }
      }

      // Tab-Reihenfolge: positive tabindex zuerst (aufsteigend), dann Dokumentreihenfolge
      const focusables = [...doc.querySelectorAll(FOCUSABLE)].filter((el) => !skip(el) && el.tabIndex >= 0 && !el.disabled
        && !(el.localName === 'input' && el.type === 'hidden') && isVisible(el));
      const pos = focusables.filter((el) => el.tabIndex > 0).sort((a, b) => a.tabIndex - b.tabIndex);
      const order = [...pos, ...focusables.filter((el) => el.tabIndex === 0)].map((el, i) => ({
        n: i + 1, el, role: acc.role(el), name: acc.name(el), tabindex: el.getAttribute('tabindex'),
      }));
      return { items, headings, landmarks, order };
    } finally {
      acc.done();
    }
  }

  // ---------------------------------------------------------------- Kontrast

  function rgb(hex) {
    const m = /^#?([0-9a-f]{6})$/i.exec(String(hex || '').trim());
    if (!m) return null;
    const n = parseInt(m[1], 16);
    return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
  }
  /** relative Leuchtdichte nach WCAG 2.2 */
  function lum([r, g, b]) {
    const f = (c) => { c /= 255; return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4; };
    return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b);
  }
  function ratio(a, b) {
    const x = lum(a); const y = lum(b);
    return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
  }

  // ---------------------------------------------------------------- Ansicht

  const S = { frame: null, doc: null, result: null, selected: null, styleOff: false, gray: false, busy: false, again: false, error: '', seq: 0, ctx: null, cc: { fg: '#767676', bg: '#ffffff' } };
  let P = null; // Panel
  const q = (sel) => P.querySelector(sel);

  function mount(ctx) {
    S.ctx = ctx;
    P = ctx.panel;
    P.innerHTML = `
      <h2>Barrierefreiheit <span class="muted small">WCAG 2.2 · Stufe AA</span></h2>
      <div class="a11y-actions">
        <button type="button" class="primary" data-act="run">Prüfen</button>
        <label class="switch small" title="Bei jeder neuen Vorschau automatisch prüfen"><input type="checkbox" data-pref="auto"> automatisch</label>
        <button type="button" data-act="icons" aria-pressed="false" title="Symbole auf der Seite ein-/ausblenden">Symbole</button>
        <button type="button" data-act="styles" aria-pressed="false" title="Stylesheets der Seite abschalten (Lesereihenfolge ohne Gestaltung)">Styles aus</button>
        <button type="button" data-act="gray" aria-pressed="false" title="Seite in Graustufen zeigen">Graustufen</button>
        <button type="button" data-act="report" title="Ergebnis dieser Seite als Markdown herunterladen">Bericht</button>
      </div>
      <p class="small muted a11y-status" role="status"></p>
      <div class="a11y-tabs" role="tablist" aria-label="Ansichten der Barrierefreiheitsprüfung">
        ${[['summary', 'Übersicht'], ['details', 'Details'], ['order', 'Reihenfolge'], ['structure', 'Struktur'], ['contrast', 'Kontrast'], ['criteria', 'Kriterien']]
    .map(([k, l]) => `<button type="button" role="tab" data-tab="${k}">${l}</button>`).join('')}
      </div>
      <div class="a11y-ref" hidden></div>
      <div class="a11y-body" role="tabpanel"></div>
      <p class="small muted a11y-note">Automatische Prüfungen erkennen nur einen Teil der WCAG-Anforderungen. „Ohne Befund“ heißt nicht „barrierefrei“ –
        die Kriterienliste zeigt, was zusätzlich manuell zu prüfen ist.</p>`;
    q('[data-pref=auto]').checked = prefs.auto;
    P.addEventListener('click', onClick);
    P.addEventListener('change', onChange);
    P.addEventListener('input', onInput);
    paint();
  }

  function unmount() {
    clearPage();
    S.result = null;
    S.frame = null;
    S.doc = null;
    P = null;
  }

  function onRender() {
    S.seq++;
    S.result = null;
    S.selected = null;
    S.error = '';
    S.styleOff = false;
    S.gray = false;
    if (P) paint();
  }

  function onPreview(frame) {
    S.frame = frame;
    S.doc = frame.contentDocument;
    S.styleOff = false;
    S.gray = false;
    if (prefs.auto) run();
    else paint();
  }

  async function run() {
    const frame = S.frame || K.api.preview();
    if (!frame) return;
    if (S.busy) { S.again = true; return; } // nach der laufenden Prüfung erneut prüfen
    S.frame = frame;
    S.doc = frame.contentDocument;
    const seq = ++S.seq;
    S.busy = true;
    S.error = '';
    clearPage();
    S.styleOff = false;
    S.gray = false;
    paint();
    try {
      const r = await analyze(frame, K.api.mode === 'mail');
      if (seq !== S.seq || !P) return;
      S.result = r;
      S.selected = null;
    } catch (e) {
      if (seq === S.seq) S.error = String(e && e.message || e);
    } finally {
      S.busy = false;
    }
    if (S.again && P) { S.again = false; run(); return; }
    if (P) { paint(); drawPage(); }
  }

  function visibleItems() {
    return S.result && S.result.items ? S.result.items.filter((it) => prefs.cats[it.cat]) : [];
  }

  function counts() {
    const c = Object.fromEntries(CAT_KEYS.map((k) => [k, 0]));
    for (const it of (S.result && S.result.items) || []) c[it.cat]++;
    return c;
  }

  function paint() {
    if (!P) return;
    for (const b of P.querySelectorAll('[data-tab]')) b.setAttribute('aria-selected', String(b.dataset.tab === prefs.tab));
    q('[data-act=icons]').setAttribute('aria-pressed', String(prefs.icons));
    q('[data-act=styles]').setAttribute('aria-pressed', String(S.styleOff));
    q('[data-act=gray]').setAttribute('aria-pressed', String(S.gray));
    const status = q('.a11y-status');
    const r = S.result;
    if (S.busy) status.textContent = 'Prüfe …';
    else if (S.error) status.textContent = 'Prüfung fehlgeschlagen: ' + S.error;
    else if (r && r.skipped) status.textContent = r.skipped;
    else if (r) status.textContent = `${K.api.label} · ${K.api.theme} · geprüft ${r.at.toLocaleTimeString('de-DE')} mit axe-core ${r.axeVersion}`;
    else status.textContent = prefs.auto ? 'Wartet auf die Vorschau …' : 'Noch nicht geprüft – „Prüfen“ startet die Prüfung dieser Vorschau.';
    const body = q('.a11y-body');
    body.innerHTML = r && !r.skipped ? VIEWS[prefs.tab]() : '';
    if (q('.a11y-ratio')) updateRatio();
    paintRef();
  }

  function catPills() {
    const c = counts();
    return `<div class="a11y-cats">${CAT_KEYS.map((k) => `<label class="a11y-cat" data-cat="${k}" title="${esc(CATS[k].tip)}">`
      + `<input type="checkbox" data-catbox="${k}" ${prefs.cats[k] ? 'checked' : ''}>`
      + `<span class="a11y-ico" data-cat="${k}" aria-hidden="true">${CATS[k].icon}</span><strong>${c[k]}</strong> ${CATS[k].label}</label>`).join('')}</div>`;
  }

  const VIEWS = {
    summary() {
      const r = S.result;
      const by = (s) => r.criteria.filter((c) => c.status === s).length;
      const fails = r.criteria.filter((c) => c.status === 'fail');
      return catPills()
        + `<table class="a11y-table"><tr><th>WCAG 2.2 A/AA (${r.criteria.length} Kriterien)</th><th class="n">Anzahl</th></tr>`
        + Object.entries(STATUS).map(([k, s]) => `<tr><td><span class="a11y-st ${s.cls}">${s.label}</span></td><td class="n">${by(k)}</td></tr>`).join('')
        + '</table>'
        + (fails.length ? `<p class="small"><strong>Verstöße:</strong> ${fails.map((c) => `<a href="${c.url}" target="_blank" rel="noopener">${c.sc} ${esc(c.title)}</a> (${c.level})`).join(', ')}</p>` : '')
        + (r.isMail ? '<p class="small muted">E-Mail: seitenbezogene Regeln (Titel, Landmarks, H1, Sprache am &lt;html&gt;) sind ausgeschaltet.</p>' : '');
    },
    details() {
      const groups = {};
      for (const it of visibleItems()) {
        const key = it.cat + '|' + it.rule + '|' + it.title;
        (groups[key] = groups[key] || { cat: it.cat, title: it.title, items: [] }).items.push(it);
      }
      const all = S.result.items;
      const list = Object.values(groups).sort((a, b) => CAT_KEYS.indexOf(a.cat) - CAT_KEYS.indexOf(b.cat));
      return catPills() + (list.length ? '<ul class="a11y-list">' + list.map((g) => `<li><span class="a11y-ico" data-cat="${g.cat}" aria-hidden="true">${CATS[g.cat].icon}</span>`
        + `<span class="sr">${CATS[g.cat].label}: </span>${esc(g.title)} <span class="muted">(${g.items.length}×)</span>`
        + `<span class="a11y-nodes">${g.items.map((it, i) => `<button type="button" class="a11y-node${it === S.selected ? ' on' : ''}${it.el && isVisible(it.el) ? '' : ' hid'}" data-item="${all.indexOf(it)}" title="${esc(it.target || it.html)}">${i + 1}</button>`).join('')}</span></li>`).join('') + '</ul>'
        : '<p class="small muted">Keine Einträge in den gewählten Kategorien.</p>');
    },
    order() {
      const o = S.result.order;
      return `<label class="switch small"><input type="checkbox" data-pref="order" ${prefs.order ? 'checked' : ''}> Nummern auf der Seite zeigen</label>`
        + `<p class="small muted">${o.length} Tab-Stopps in der Reihenfolge der Tabulatortaste (2.4.3). Positive tabindex-Werte kommen zuerst.</p>`
        + `<table class="a11y-table"><tr><th class="n">#</th><th>Rolle</th><th>Zugänglicher Name</th></tr>${o.map((x, i) => `<tr data-order="${i}" class="click">`
        + `<td class="n">${x.n}</td><td>${esc(x.role)}${x.tabindex && Number(x.tabindex) > 0 ? ` <span class="a11y-st warning">tabindex=${esc(x.tabindex)}</span>` : ''}</td>`
        + `<td>${x.name ? esc(x.name) : '<span class="a11y-st error">kein Name</span>'}</td></tr>`).join('')}</table>`;
    },
    structure() {
      const r = S.result;
      let prev = 0;
      const hs = r.headings.map((h, i) => {
        const skipped = prev && h.level > prev + 1;
        prev = h.level;
        return `<li style="margin-left:${(h.level - 1) * 14}px" data-heading="${i}" class="click${h.visible ? '' : ' hid'}"><span class="a11y-h">H${h.level}</span> ${esc(h.name || '(leer)')}`
          + `${skipped ? ' <span class="a11y-st warning">Ebene übersprungen</span>' : ''}${h.name ? '' : ' <span class="a11y-st error">leer</span>'}</li>`;
      }).join('');
      const ls = r.landmarks.map((l, i) => `<li data-landmark="${i}" class="click${l.visible ? '' : ' hid'}">${esc(LANDMARK_ROLES[l.role])}${l.name ? ` – „${esc(l.name)}“` : ''}</li>`).join('');
      return `<h3>Überschriften (${r.headings.length})</h3>${hs ? `<ul class="a11y-outline">${hs}</ul>` : '<p class="small muted">Keine Überschriften.</p>'}`
        + `<h3>Landmarks (${r.landmarks.length})</h3>${ls ? `<ul class="a11y-outline">${ls}</ul>` : '<p class="small muted">Keine Landmarks.</p>'}`;
    },
    contrast() {
      const c = S.result.contrast;
      const bad = c.filter((x) => x.ok === false).length;
      const rev = c.filter((x) => x.ok === null).length;
      return `<div class="a11y-checker">
          <label>Text <input type="color" data-cc="fg" value="${esc(S.cc.fg)}"><input type="text" data-cc="fgt" size="8" spellcheck="false" value="${esc(S.cc.fg)}" aria-label="Textfarbe als Hexwert"></label>
          <label>Hintergrund <input type="color" data-cc="bg" value="${esc(S.cc.bg)}"><input type="text" data-cc="bgt" size="8" spellcheck="false" value="${esc(S.cc.bg)}" aria-label="Hintergrundfarbe als Hexwert"></label>
          <div class="a11y-sample" aria-hidden="true">Beispieltext <strong>fett</strong> <span style="font-size:24px">groß</span></div>
          <div class="a11y-ratio"></div>
        </div>`
        + `<p class="small muted">${c.length} Textelemente geprüft (1.4.3): ${bad} zu gering, ${rev} nicht sicher bestimmbar (z. B. Hintergrundbild, Verlauf). `
        + 'Nicht-Text-Kontrast (1.4.11: Rahmen von Feldern, Fokusrahmen, Symbole) prüft axe-core nicht – Farben hier eintragen und mit 3:1 vergleichen.</p>'
        + `<table class="a11y-table"><tr><th>Text</th><th>Farben</th><th class="n">Verhältnis</th><th class="n">nötig</th></tr>${c
          .map((x, i) => ({ x, i })).sort((a, b) => (a.x.ok === false ? 0 : a.x.ok === null ? 1 : 2) - (b.x.ok === false ? 0 : b.x.ok === null ? 1 : 2))
          .map(({ x, i }) => `<tr data-contrast="${i}" class="click"><td>${esc(x.text || '(ohne Text)')}</td>`
          + `<td class="nowrap"><span class="a11y-sw" style="background:${esc(x.fg)}"></span><span class="a11y-sw" style="background:${esc(x.bg || 'transparent')}"></span></td>`
          + `<td class="n"><span class="a11y-st ${x.ok === false ? 'error' : x.ok === null ? 'warning' : 'ok'}">${x.ratio ? x.ratio + ':1' : '?'}</span></td><td class="n">${esc(x.required || '')}</td></tr>`).join('')}</table>`;
    },
    criteria() {
      return `<p class="small muted">Alle ${WCAG.length} Erfolgskriterien der Stufen A und AA von WCAG 2.2 (4.1.1 ist entfallen). Status aus den axe-core-Regeln, die dem Kriterium zugeordnet sind.</p>`
        + `<table class="a11y-table"><tr><th>Kriterium</th><th>Stufe</th><th>Status</th></tr>${S.result.criteria.map((c) => `<tr><td><a href="${c.url}" target="_blank" rel="noopener">${c.sc} ${esc(c.title)}</a>`
        + `${c.rules.length ? `<div class="small muted">${c.rules.map(esc).join(', ')}</div>` : ''}</td><td>${c.level}</td>`
        + `<td><span class="a11y-st ${STATUS[c.status].cls}">${STATUS[c.status].label}</span></td></tr>`).join('')}</table>`;
    },
  };

  function paintRef() {
    const box = q('.a11y-ref');
    const it = S.selected;
    if (!it) { box.hidden = true; box.innerHTML = ''; return; }
    box.hidden = false;
    const sc = it.sc.map((n) => SC[n] ? `<a href="${SC[n].url}" target="_blank" rel="noopener">${n} ${esc(SC[n].title)}</a> (${SC[n].level})` : esc(n)).join('<br>');
    box.innerHTML = `<div class="a11y-ref-head"><span class="a11y-ico" data-cat="${it.cat}" aria-hidden="true">${CATS[it.cat].icon}</span>`
      + `<strong>${esc(it.title)}</strong><button type="button" data-act="unselect" aria-label="Referenz schließen">×</button></div>`
      + `<div class="small muted">${esc(CATS[it.cat].label)} · ${esc(it.kind)}${it.impact ? ' · Auswirkung ' + esc(IMPACT[it.impact] || it.impact) : ''}${it.heuristic ? ' · Heuristik des Emulators' : ''}${it.source === 'axe' ? ' · Regel ' + esc(it.rule) : ''}</div>`
      + (it.desc ? `<p>${esc(it.desc)}</p>` : '')
      + (it.fix ? `<p class="small"><strong>So beheben</strong></p><pre>${esc(it.fix)}</pre>` : '')
      + (it.html ? `<p class="small"><strong>Element</strong>${it.el && !isVisible(it.el) ? ' <span class="a11y-st info">nicht sichtbar</span>' : ''}</p><pre>${esc(it.html)}</pre>` : '')
      + (sc ? `<p class="small"><strong>WCAG 2.2</strong><br>${sc}</p>` : (it.source === 'axe' ? '<p class="small muted">Best Practice – keinem WCAG-Kriterium zugeordnet.</p>' : ''))
      + (it.helpUrl ? `<p class="small"><a href="${esc(it.helpUrl)}" target="_blank" rel="noopener">Regelbeschreibung (Deque, englisch) ↗</a></p>` : '');
  }

  function select(it) {
    S.selected = it;
    paint();
    drawPage();
    if (it && it.el && it.el.scrollIntoView && isVisible(it.el)) it.el.scrollIntoView({ block: 'center', inline: 'nearest' });
  }

  function onClick(e) {
    const t = e.target;
    const tab = t.closest('[data-tab]');
    if (tab) { prefs.tab = tab.dataset.tab; save(); paint(); drawPage(); return; }
    const act = t.closest('[data-act]');
    if (act) {
      const a = act.dataset.act;
      if (a === 'run') run();
      else if (a === 'icons') { prefs.icons = !prefs.icons; save(); paint(); drawPage(); }
      else if (a === 'styles') { setStyles(!S.styleOff); paint(); drawPage(); }
      else if (a === 'gray') { setGray(!S.gray); paint(); }
      else if (a === 'report') report();
      else if (a === 'unselect') select(null);
      return;
    }
    const r = S.result;
    if (!r) return;
    const item = t.closest('[data-item]');
    if (item) { select(r.items[Number(item.dataset.item)]); return; }
    const row = t.closest('[data-order], [data-heading], [data-landmark], [data-contrast]');
    if (!row) return;
    let el; let title; let cat = 'structure';
    if (row.dataset.order) { const o = r.order[Number(row.dataset.order)]; el = o.el; title = `Tab-Stopp ${o.n}: ${o.role} „${o.name}“`; }
    else if (row.dataset.heading) { const h = r.headings[Number(row.dataset.heading)]; el = h.el; title = `Überschrift H${h.level}`; }
    else if (row.dataset.landmark) { const l = r.landmarks[Number(row.dataset.landmark)]; el = l.el; title = LANDMARK_ROLES[l.role]; }
    else {
      const c = r.contrast[Number(row.dataset.contrast)];
      el = c.el; cat = c.ok === false ? 'contrast' : 'alert';
      title = `Kontrast ${c.ratio}:1 (nötig ${c.required})`;
      setChecker(c.fg, c.bg);
    }
    const hit = r.items.find((x) => x.el === el && (row.dataset.contrast ? x.rule === 'color-contrast' : x.cat === 'structure'));
    select(hit || { cat, kind: 'Element', title, sc: [], el, html: snippet(el) });
  }

  function onChange(e) {
    const t = e.target;
    if (t.dataset.pref === 'auto') { prefs.auto = t.checked; save(); if (t.checked && !S.result) run(); }
    else if (t.dataset.pref === 'order') { prefs.order = t.checked; save(); drawPage(); }
    else if (t.dataset.catbox) { prefs.cats[t.dataset.catbox] = t.checked; save(); paint(); drawPage(); }
  }

  function onInput(e) {
    const t = e.target;
    if (!t.dataset.cc) return;
    const k = t.dataset.cc.slice(0, 2);
    if (t.dataset.cc === k) q(`[data-cc=${k}t]`).value = t.value;
    else if (rgb(t.value)) q(`[data-cc=${k}]`).value = normHex(t.value);
    if (rgb(t.value)) S.cc[k] = normHex(t.value);
    updateRatio();
  }

  function normHex(v) { v = v.trim(); return (v.startsWith('#') ? v : '#' + v).toLowerCase(); }

  function setChecker(fg, bg) {
    if (rgb(fg)) S.cc.fg = normHex(fg);
    if (rgb(bg)) S.cc.bg = normHex(bg);
    if (q('[data-cc=fg]')) {
      q('[data-cc=fg]').value = q('[data-cc=fgt]').value = S.cc.fg;
      q('[data-cc=bg]').value = q('[data-cc=bgt]').value = S.cc.bg;
      updateRatio();
    }
  }

  function updateRatio() {
    const fg = rgb(q('[data-cc=fgt]').value) || rgb(q('[data-cc=fg]').value);
    const bg = rgb(q('[data-cc=bgt]').value) || rgb(q('[data-cc=bg]').value);
    const out = q('.a11y-ratio');
    if (!fg || !bg) { out.textContent = 'Farben als #rrggbb eingeben.'; return; }
    const r = ratio(fg, bg);
    const s = q('.a11y-sample');
    s.style.color = `rgb(${fg})`;
    s.style.background = `rgb(${bg})`;
    const rr = Math.floor(r * 100) / 100;
    const row = (label, need) => `<tr><td>${label}</td><td class="n">${need}:1</td><td><span class="a11y-st ${r >= need ? 'ok' : 'error'}">${r >= need ? 'erfüllt' : 'nicht erfüllt'}</span></td></tr>`;
    out.innerHTML = `<div class="a11y-big">${rr}:1</div><table class="a11y-table">`
      + row('Normaler Text (1.4.3 AA)', 4.5) + row('Großer Text ab 24 px bzw. 18,66 px fett (1.4.3 AA)', 3)
      + row('Bedienelemente und Grafiken (1.4.11 AA)', 3) + '</table>';
  }

  // ---------------------------------------------------------------- Darstellung auf der Seite

  const LAYER_CSS = `
    :host { all: initial; }
    .i { position: absolute; width: 18px; height: 18px; border-radius: 4px; font: bold 12px/18px system-ui, sans-serif; text-align: center;
         color: #fff; cursor: pointer; box-shadow: 0 0 0 1px #fff, 0 1px 3px rgba(0,0,0,.5); pointer-events: auto; user-select: none; }
    .i[data-cat=error] { background: #c4001d; }
    .i[data-cat=contrast] { background: #8f0c6d; }
    .i[data-cat=alert] { background: #f0b400; color: #1c1c1c; }
    .i[data-cat=feature] { background: #1d7a35; }
    .i[data-cat=structure] { background: #1f5fbf; }
    .i[data-cat=aria] { background: #6b35b0; }
    .i.n { background: #111; border-radius: 9px; min-width: 18px; width: auto; padding: 0 4px; box-sizing: border-box; }
    .i.on { outline: 3px solid #111; outline-offset: 1px; }
    .box { position: absolute; outline: 3px solid #c4001d; outline-offset: 2px; box-shadow: 0 0 0 9999px rgba(0,0,0,.08); pointer-events: none; }`;

  function layer() {
    const doc = S.doc;
    if (!doc || !doc.documentElement) return null;
    let host = doc.getElementById('kcemu-a11y-layer');
    if (!host) {
      host = doc.createElement('div');
      host.id = 'kcemu-a11y-layer';
      host.style.cssText = 'position:absolute;left:0;top:0;width:0;height:0;z-index:2147483647;pointer-events:none;';
      doc.documentElement.appendChild(host);
      host.attachShadow({ mode: 'open' });
      doc.defaultView.addEventListener('resize', scheduleDraw);
    }
    return host.shadowRoot;
  }

  let drawTimer = 0;
  function scheduleDraw() {
    clearTimeout(drawTimer);
    drawTimer = setTimeout(drawPage, 120);
  }

  function drawPage() {
    const r = S.result;
    if (!S.doc || !S.doc.defaultView || !r || r.skipped || !P) { removeLayer(); return; }
    const root = layer();
    if (!root) return;
    const win = S.doc.defaultView;
    const sx = win.scrollX; const sy = win.scrollY;
    let html = `<style>${LAYER_CSS}</style>`;
    const slots = new Map();
    const place = (el) => {
      const rect = el.getBoundingClientRect();
      const k = slots.get(el) || 0;
      slots.set(el, k + 1);
      return { x: Math.max(0, rect.left + sx + k * 20), y: Math.max(0, rect.top + sy) };
    };
    if (prefs.icons) {
      r.items.forEach((it, idx) => {
        if (!prefs.cats[it.cat] || !it.el || !it.el.isConnected || !isVisible(it.el)) return;
        const p = place(it.el);
        html += `<div class="i${it === S.selected ? ' on' : ''}" data-cat="${it.cat}" data-i="${idx}" style="left:${p.x}px;top:${p.y}px" title="${esc(CATS[it.cat].label + ': ' + it.title)}">${CATS[it.cat].icon}</div>`;
      });
    }
    if (prefs.order || prefs.tab === 'order') {
      for (const o of r.order) {
        if (!o.el.isConnected) continue;
        const rect = o.el.getBoundingClientRect();
        html += `<div class="i n" style="left:${Math.max(0, rect.right + sx - 9)}px;top:${Math.max(0, rect.top + sy - 9)}px" title="${esc(`${o.n}: ${o.role} „${o.name}“`)}">${o.n}</div>`;
      }
    }
    const sel = S.selected;
    if (sel && sel.el && sel.el.isConnected && isVisible(sel.el)) {
      const b = sel.el.getBoundingClientRect();
      html += `<div class="box" style="left:${b.left + sx}px;top:${b.top + sy}px;width:${b.width}px;height:${b.height}px"></div>`;
    }
    root.innerHTML = html;
    root.onclick = (e) => {
      const i = e.target.closest('.i[data-i]');
      if (!i) return;
      e.preventDefault();
      e.stopPropagation();
      const it = r.items[Number(i.dataset.i)];
      if (prefs.tab === 'order' || prefs.tab === 'criteria' || prefs.tab === 'contrast') prefs.tab = 'details';
      select(it);
      P.querySelector('.a11y-ref')?.scrollIntoView({ block: 'nearest' });
    };
  }

  function removeLayer() {
    const host = S.doc && S.doc.getElementById('kcemu-a11y-layer');
    if (host) {
      if (S.doc.defaultView) S.doc.defaultView.removeEventListener('resize', scheduleDraw);
      host.remove();
    }
  }

  function setStyles(off) {
    const doc = S.doc;
    if (!doc) return;
    for (const sh of doc.styleSheets) {
      if (sh.ownerNode && sh.ownerNode.dataset && sh.ownerNode.dataset.kcemu) continue;
      sh.disabled = off;
    }
    if (off) {
      for (const el of doc.querySelectorAll('[style]')) {
        if (el.id === 'kcemu-a11y-layer') continue;
        el.dataset.kcemuStyle = el.getAttribute('style');
        el.removeAttribute('style');
      }
    } else {
      for (const el of doc.querySelectorAll('[data-kcemu-style]')) {
        el.setAttribute('style', el.dataset.kcemuStyle);
        delete el.dataset.kcemuStyle;
      }
    }
    S.styleOff = off;
  }

  function setGray(on) {
    const doc = S.doc;
    if (!doc) return;
    let st = doc.getElementById('kcemu-a11y-gray');
    if (on && !st) {
      st = doc.createElement('style');
      st.id = 'kcemu-a11y-gray';
      st.dataset.kcemu = 'a11y';
      st.textContent = 'body { filter: grayscale(1) !important; }';
      (doc.head || doc.documentElement).appendChild(st);
    } else if (!on && st) st.remove();
    S.gray = on;
  }

  /** Vorschau in den Ausgangszustand: Symbole weg, Styles an, keine Graustufen. */
  function clearPage() {
    if (!S.doc || !S.doc.documentElement) return;
    try {
      if (S.styleOff) setStyles(false);
      if (S.gray) setGray(false);
      removeLayer();
    } catch (e) { /* Vorschau bereits neu geladen */ }
  }

  // ---------------------------------------------------------------- Bericht

  function report() {
    const r = S.result;
    if (!r || r.skipped) return;
    const c = counts();
    let md = `# Barrierefreiheit – ${K.api.label}\n\n- Theme: ${K.api.theme}\n- Seite/Mail: ${K.api.pageId}${K.api.mode === 'page' ? ' (Variante ' + K.api.variant + ')' : ''}\n`
      + `- Maßstab: WCAG 2.2, Stufe AA (A + AA)\n- Prüfwerkzeug: axe-core ${r.axeVersion} im Keycloak Theme Emulator\n- Erstellt: ${new Date().toLocaleString('de-DE')}\n\n`
      + '> Automatische Prüfungen decken nur einen Teil der Kriterien ab. „Autom. ohne Befund“ und „Nur manuell“ müssen von Hand geprüft werden.\n\n';
    md += '## Übersicht\n\n' + CAT_KEYS.map((k) => `- ${CATS[k].label}: ${c[k]}`).join('\n') + '\n\n';
    md += '## Kriterien\n\n| Kriterium | Stufe | Status | axe-Regeln |\n|---|---|---|---|\n'
      + r.criteria.map((x) => `| ${x.sc} ${x.title} | ${x.level} | ${STATUS[x.status].label} | ${x.rules.join(', ')} |`).join('\n') + '\n\n';
    md += '## Befunde\n';
    for (const k of ['error', 'contrast', 'alert']) {
      const list = r.items.filter((x) => x.cat === k);
      if (!list.length) continue;
      md += `\n### ${CATS[k].label}\n\n`;
      for (const it of list) md += `- ${it.title}${it.sc.length ? ' [' + it.sc.join(', ') + ']' : ''}${it.heuristic ? ' (Heuristik)' : ''} – \`${(it.target || it.html || '').replace(/`/g, "'")}\`\n`;
    }
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob([md], { type: 'text/markdown' }));
    a.download = `barrierefreiheit-${K.api.theme}-${K.api.pageId}-${new Date().toISOString().slice(0, 10)}.md`;
    a.click();
    URL.revokeObjectURL(a.href);
  }

  // ---------------------------------------------------------------- Gesamtprüfung

  const audit = {
    label: 'Barrierefreiheit WCAG 2.2 AA (langsamer)',
    column: 'WCAG-Verstöße',
    async run(frame, { isMail }) {
      const r = await analyze(frame, isMail);
      if (r.skipped) return { findings: [], cell: '–' };
      const findings = [];
      const groups = {};
      for (const it of r.items) {
        if (!['error', 'contrast', 'alert'].includes(it.cat) || it.source !== 'axe') continue;
        const key = it.cat + it.rule;
        (groups[key] = groups[key] || { it, n: 0 }).n++;
      }
      for (const { it, n } of Object.values(groups)) {
        findings.push({
          level: it.cat === 'alert' ? (it.kind === 'Manuell prüfen' ? 'info' : 'warning') : 'error',
          area: 'Barrierefreiheit · ' + CATS[it.cat].label,
          message: `${it.title} (${n}×)${it.sc.length ? ' – WCAG ' + it.sc.join(', ') : ''}`,
        });
      }
      const fails = r.criteria.filter((c) => c.status === 'fail').length;
      return { findings, cell: String(fails) };
    },
  };

  K.register({ id: 'a11y', name: 'Barrierefreiheit', mount, unmount, onRender, onPreview, audit, analyze });
})();
