/*
 * Vom Emulator nur bei „Vorschau: Hell/Dunkel“ eingefügt – nicht Teil der Keycloak-Ausgabe.
 * Simuliert die Systemeinstellung hell/dunkel für diese Seite: Abfragen auf
 * (prefers-color-scheme: …) in matchMedia, in CSS-@media-Regeln, in media-Attributen
 * (<link>, <source>, <style>) beantwortet die Seite so, als wäre das Betriebssystem
 * entsprechend eingestellt. color-scheme folgt der Angabe der Seite (<meta name="color-scheme">).
 */
(function (forced) {
  'use strict';
  var TRUE = '(min-width: 0px)';
  var FALSE = '(max-width: 0.01px)';
  var HAS = /\(\s*prefers-color-scheme\s*:\s*(light|dark)\s*\)/i;
  var ALL = /\(\s*prefers-color-scheme\s*:\s*(light|dark)\s*\)/gi;
  function sub(q) {
    return String(q).replace(ALL, function (_, v) { return v.toLowerCase() === forced ? TRUE : FALSE; });
  }

  var matchMedia = window.matchMedia;
  window.matchMedia = function (q) {
    return matchMedia.call(window, HAS.test(q) ? sub(q) : q);
  };

  function rules(list) {
    if (!list) return;
    for (var i = 0; i < list.length; i++) {
      var r = list[i];
      try {
        if (r.media && HAS.test(r.media.mediaText)) r.media.mediaText = sub(r.media.mediaText);
        if (r.cssRules) rules(r.cssRules);
        if (r.styleSheet) sheet(r.styleSheet);
      } catch (e) { /* Regel nicht änderbar */ }
    }
  }
  function sheet(s) {
    try {
      if (s.media && HAS.test(s.media.mediaText)) s.media.mediaText = sub(s.media.mediaText);
      rules(s.cssRules);
    } catch (e) { /* fremde Herkunft – nicht lesbar */ }
  }
  function apply() {
    var els = document.querySelectorAll('[media]');
    for (var i = 0; i < els.length; i++) {
      var m = els[i].getAttribute('media');
      if (HAS.test(m)) els[i].setAttribute('media', sub(m));
    }
    for (var j = 0; j < document.styleSheets.length; j++) sheet(document.styleSheets[j]);
    var meta = document.querySelector('meta[name="color-scheme"]');
    if (meta && document.documentElement) {
      var supported = (meta.getAttribute('content') || '').toLowerCase().split(/\s+/);
      var other = forced === 'dark' ? 'light' : 'dark';
      // so wie der Browser wählen würde: die bevorzugte Variante, sofern die Seite sie anbietet
      var used = supported.indexOf(forced) >= 0 ? forced : supported.indexOf(other) >= 0 ? other : '';
      if (used) document.documentElement.style.colorScheme = used;
    }
  }

  document.addEventListener('DOMContentLoaded', apply);
  document.addEventListener('load', apply, true); // nachgeladene Stylesheets und Bilder
  window.addEventListener('load', apply);
  if (window.MutationObserver) {
    new MutationObserver(apply).observe(document, { childList: true, subtree: true });
    window.addEventListener('load', function () { setTimeout(apply, 0); });
  }
})('__SCHEME__');
