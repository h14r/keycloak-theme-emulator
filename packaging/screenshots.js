/*
 * Erzeugt die Bilder für den Überblick beim Start (src/main/resources/ui/welcome/*.jpg)
 * aus der laufenden Anwendung mit dem Beispiel-Theme.
 *
 *   java -jar target/keycloak-theme-emulator.jar          (im Projektordner, Szenario mit Theme „beispiel“)
 *   node packaging/screenshots.js [http://127.0.0.1:8899] [Zielordner]
 *
 * Voraussetzung: Node.js und Playwright mit Chromium (npm i playwright && npx playwright install chromium).
 */
'use strict';
const path = require('path');
const { chromium } = require('playwright');

const BASE = process.argv[2] || 'http://127.0.0.1:8899';
const OUT = process.argv[3] || path.join(__dirname, '..', 'src', 'main', 'resources', 'ui', 'welcome');
const JPEG = { type: 'jpeg', quality: 82 };

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1, colorScheme: 'light' });
  const shot = async (name, opts = {}) => {
    await page.waitForTimeout(400);
    await page.screenshot({ path: path.join(OUT, name), ...JPEG, ...opts });
    console.log('  ' + name);
  };
  const shotEl = async (name, sel) => {
    await page.waitForTimeout(400);
    await page.locator(sel).screenshot({ path: path.join(OUT, name), ...JPEG });
    console.log('  ' + name);
  };
  const checked = () => page.waitForFunction(() => /geprüft/.test(document.querySelector('.a11y-status')?.textContent || ''), null, { timeout: 60000 });
  const previewLoaded = () => page.waitForFunction(() => document.getElementById('f1').contentDocument?.readyState === 'complete', null, { timeout: 30000 });

  // feste Ausgangslage: Desktop, an Breite angepasst, Symbole der Barrierefreiheit zunächst aus
  await page.goto(BASE + '/?nowelcome');
  await page.evaluate(() => {
    localStorage.setItem('kcemu', JSON.stringify({ mode: 'page', id: 'login', variant: 'standard', locale: '', width: 1280, zoom: 1, fit: true }));
    localStorage.setItem('kcemu-a11y', JSON.stringify({ auto: true, icons: false, tab: 'summary', order: false }));
  });
  await page.goto(BASE + '/?nowelcome');
  await checked();
  console.log('Bilder nach ' + OUT);

  await shot('01-uebersicht.jpg');

  const variant = await page.$$eval('#variant option', (os) => (os.find((o) => /falsch/i.test(o.textContent)) || os[1]).value);
  await page.click('#viewports button[data-w="375"]');
  await page.click('#zoomLevel');
  await page.selectOption('#variant', variant);
  await previewLoaded();
  await checked();
  await shot('02-varianten.jpg');
  await page.click('#viewports button[data-w="1280"]');
  await page.click('#zoomFit');
  await page.selectOption('#variant', 'standard');
  await checked();

  await page.click('#btnThemes');
  await page.waitForSelector('#themeTable tr');
  await shotEl('03-themes.jpg', '#dlgWelcome ~ #dlgThemes, #dlgThemes');
  await page.click('#themesClose');

  await page.click('#viewports button[data-w="375"]');
  await page.check('#compare');
  await page.selectOption('#theme2', 'keycloak.v2');
  await page.waitForTimeout(1500);
  await shot('04-vergleich.jpg');
  await page.uncheck('#compare');
  await page.click('#viewports button[data-w="1280"]');

  await page.click('#tabMails');
  const mail = await page.$$eval('#list button[data-id]', (bs) => (bs.find((b) => /verify-email|bestätigen/i.test(b.dataset.id + b.textContent)) || bs[0]).dataset.id);
  await page.click(`#list button[data-id="${mail}"]`);
  await checked();
  await shot('05-mail.jpg');
  await page.click('#tabPages');
  await checked();

  await page.click('#btnAudit');
  await page.click('#auditRun');
  await page.waitForFunction(() => !document.getElementById('auditRun').disabled && document.querySelectorAll('#auditTable tr').length > 10, null, { timeout: 600000 });
  // zu den ersten Zeilen mit Befunden scrollen
  await page.evaluate(() => {
    const dlg = document.getElementById('dlgAudit');
    const rows = [...document.querySelectorAll('#auditTable tr')].slice(1);
    const i = rows.findIndex((r) => r.lastElementChild.textContent.trim());
    if (i > 2) dlg.scrollTop += rows[i - 2].getBoundingClientRect().top - dlg.getBoundingClientRect().top - 20;
  });
  await shotEl('06-pruefung.jpg', '#dlgAudit');
  await page.click('#auditClose');

  await page.click('.module[data-module=a11y] [data-act=icons]');
  await page.click('.module[data-module=a11y] [data-tab=details]');
  await page.click('.module[data-module=a11y] .a11y-node >> nth=0');
  await shot('07-barrierefreiheit.jpg');

  await page.click('.module[data-module=a11y] [data-tab=contrast]');
  await page.click('.module[data-module=a11y] tr[data-contrast] >> nth=1');
  await page.click('.module[data-module=a11y] [data-act=unselect]');
  const box = await page.locator('.module[data-module=a11y]').boundingBox();
  await shot('08-kontrast.jpg', { clip: { x: box.x - 8, y: box.y, width: box.width + 16, height: Math.min(box.height, 900 - box.y) } });
  await page.click('.module[data-module=a11y] [data-tab=summary]');
  await page.click('.module[data-module=a11y] [data-act=icons]');

  await page.click('#btnScenario');
  await shotEl('09-szenario.jpg', '#dlgScenario');
  await page.keyboard.press('Escape');

  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
