#!/usr/bin/env node
'use strict';
// Prueba del lector (la parte web) en Chromium, con un puente de mentira en lugar de la app.
// Uso: node herramientas/lector/prueba-lector.js libro.epub libro.pdf carpeta-capturas
const http = require('http');
const fs = require('fs');
const path = require('path');
const { chromium } = require(process.env.PW || 'playwright');

const ASSETS = path.resolve(__dirname, '../../movil/app/src/main/assets');
const [epub, pdf, out] = process.argv.slice(2);
fs.mkdirSync(out, { recursive: true });
const MIME = { html: 'text/html', js: 'text/javascript', mjs: 'text/javascript', css: 'text/css', json: 'application/json' };
let book = epub;
const srv = http.createServer((req, res) => {
  const u = decodeURIComponent(new URL(req.url, 'http://x').pathname);
  let f = null;
  if (u.startsWith('/assets/')) f = path.join(ASSETS, u.slice(8));
  if (u.startsWith('/libro/')) f = book;
  if (!f || !fs.existsSync(f)) { res.writeHead(404); return res.end('no'); }
  res.writeHead(200, { 'Content-Type': MIME[path.extname(f).slice(1)] || 'application/octet-stream' });
  fs.createReadStream(f).pipe(res);
});
const log = [];
(async () => {
  await new Promise((r) => srv.listen(0, r));
  const port = srv.address().port;
  const browser = await chromium.launch({ executablePath: process.env.CHROME || undefined });
  const ok = (c, m) => { console.log((c ? '  ✔ ' : '  ✘ ') + m); if (!c) process.exitCode = 1; };
  for (const [file, name] of [[epub, 'quijote.epub'], [pdf, 'prueba.pdf']]) {
    book = file;
    const ctx = await browser.newContext({ viewport: { width: 412, height: 860 }, deviceScaleFactor: 2, hasTouch: true, isMobile: true });
    const page = await ctx.newPage();
    page.on('console', (m) => { if (m.type() === 'error') log.push(m.text()); });
    page.on('pageerror', (e) => log.push('pageerror ' + e.message));
    // Como un WebView antiguo (Chrome 113, el de Android 14 sin actualizar): sin las funciones nuevas
    if (process.env.VIEJO !== '0') await page.addInitScript(() => {
      delete Object.groupBy; delete Map.groupBy; delete Promise.withResolvers; delete Promise.try;
    });
    await page.addInitScript((nm) => {
      const store = { saved: {} };
      window.__atrio = store;
      window.Atrio = {
        state: () => JSON.stringify({ title: nm === 'quijote.epub' ? 'Don Quijote de la Mancha' : 'PDF de prueba', file: nm, location: null, settings: {}, annotations: [], bookmarks: [] }),
        saveProgress: (cfi, f, l) => { store.saved.progress = { cfi, f, l }; },
        saveSettings: (j) => { store.saved.settings = JSON.parse(j); },
        saveAnnotations: (j) => { store.saved.notes = JSON.parse(j); },
        saveBookmarks: (j) => { store.saved.marks = JSON.parse(j); },
        setBrightness: () => {}, keepScreenOn: () => {}, setTheme: () => {}, systemBars: () => {},
        copy: () => {}, share: (t) => { store.shared = t; }, openUrl: () => {}, close: () => {},
      };
    }, name);
    await page.goto(`http://localhost:${port}/assets/lector/index.html`);
    await page.waitForSelector('#carga.fuera', { timeout: 60000 }).catch(() => {});
    const loaded = await page.$('#carga.fuera');
    ok(!!loaded, `${name}: se abre`);
    if (!loaded) { console.log((await page.textContent('#carga-txt'))); await page.screenshot({ path: path.join(out, name + '-error.png') }); continue; }
    await page.waitForTimeout(2200);
    await page.screenshot({ path: path.join(out, name + '-1-abierto.png') });
    // Pasar unas páginas
    for (let i = 0; i < (name.endsWith('epub') ? 14 : 2); i++) { await page.evaluate(() => window.atrioPage(1)); await page.waitForTimeout(350); }
    await page.waitForTimeout(900);
    const prog = await page.evaluate(() => window.__atrio.saved.progress);
    ok(prog && prog.cfi, `${name}: guarda la posición (${prog && (Math.round(prog.f * 1000) / 10)} % · ${prog && prog.l})`);
    await page.screenshot({ path: path.join(out, name + '-2-paginas.png') });
    // Toques y deslizamientos (con eventos táctiles de verdad)
    const cdp = await ctx.newCDPSession(page);
    const touch = async (pts) => {
      await cdp.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x: pts[0][0], y: pts[0][1] }] });
      for (const p of pts.slice(1, -1)) { await page.waitForTimeout(p[2] || 16); await cdp.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ x: p[0], y: p[1] }] }); }
      const l = pts[pts.length - 1]; await page.waitForTimeout(l[2] || 16);
      if (pts.length > 1) await cdp.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ x: l[0], y: l[1] }] });
      await cdp.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
    };
    const frac = async () => (await page.evaluate(() => window.__atrio.saved.progress?.f)) ?? 0;
    await page.waitForTimeout(900);
    let f0 = await frac();
    // Tocar el lado derecho de un PDF: abre el menú y no pasa de página
    if (name.endsWith('pdf')) {
      await touch([[380, 430]]); await page.waitForTimeout(900);
      const menu = await page.$eval('#abajo', (e) => !e.classList.contains('oculta'));
      ok(menu && (await frac()) === f0, `${name}: tocar el borde abre el menú y no pasa de página`);
      await touch([[206, 430]]); await page.waitForTimeout(700);
      // Deslizar hacia la izquierda: página siguiente
      await touch([[330, 430], [280, 432], [200, 434], [120, 436]]); await page.waitForTimeout(1300);
      const f1 = await frac();
      ok(f1 > f0, `${name}: deslizar pasa de página (${Math.round(f0 * 1000) / 10} → ${Math.round(f1 * 1000) / 10} %)`);
      await touch([[90, 430], [150, 432], [230, 434], [310, 436]]); await page.waitForTimeout(1300);
      ok((await frac()) < f1, `${name}: deslizar al otro lado vuelve atrás`);
    } else {
      // (Los deslizamientos del EPUB no se pueden simular aquí: foliate usa screenX y las pruebas lo mandan a 0.
      // Mantener pulsado, seleccionar y deslizar se prueban en el emulador con toques de verdad.)
      const m = await page.evaluate(() => window.__view?.renderer?.getAttribute('margin'));
      ok(m && parseInt(m) >= 42, `${name}: margen arriba y abajo ${m}`);
    }
    // Barras (tocar el centro)
    await page.mouse.click(206, 430); await page.waitForTimeout(600);
    await page.screenshot({ path: path.join(out, name + '-3-barras.png') });
    // Ajustes: tema noche y letra más grande
    await page.click('#b-ajustes'); await page.waitForTimeout(500);
    await page.screenshot({ path: path.join(out, name + '-4-ajustes.png') });
    await page.click('[data-theme-btn="noche"]'); await page.waitForTimeout(400);
    if (name.endsWith('epub')) { await page.click('[data-step="size"][data-d="10"]'); await page.waitForTimeout(300); await page.click('[data-k="font"][data-v="serif"]'); }
    await page.waitForTimeout(400);
    const set = await page.evaluate(() => window.__atrio.saved.settings);
    ok(set && set.theme === 'noche', `${name}: guarda los ajustes (${JSON.stringify(set)})`);
    await page.mouse.click(206, 120); await page.waitForTimeout(700);
    await page.evaluate(() => { document.querySelector('#panel').classList.remove('on'); document.querySelector('#velo').classList.remove('on'); });
    await page.screenshot({ path: path.join(out, name + '-5-noche.png') });
    // Subrayar: seleccionar texto dentro del libro
    const sel = await page.evaluate(async () => {
      for (const { doc: d } of [...(window.__docs || [])].reverse()) {
        const f = d.defaultView?.frameElement; if (!f || !d.body) continue;
        const r = f.getBoundingClientRect();
        const w = d.createTreeWalker(d.body, NodeFilter.SHOW_TEXT);
        let n;
        while ((n = w.nextNode())) {
          if (n.textContent.trim().length < 20) continue;
          const range = d.createRange(); range.setStart(n, 0); range.setEnd(n, Math.min(n.textContent.length, 60));
          const rr = range.getBoundingClientRect();
          const x = r.left + rr.left, y = r.top + rr.top;
          if (x < 0 || x > window.innerWidth || y < 0 || y > window.innerHeight || rr.width === 0) continue;
          d.getSelection().removeAllRanges(); d.getSelection().addRange(range);
          d.dispatchEvent(new Event('selectionchange'));
          return range.toString();
        }
      }
      return null;
    });
    await page.waitForTimeout(800);
    const barVisible = await page.$eval('#sel', (e) => !e.classList.contains('oculta'));
    ok(sel && barVisible, `${name}: al seleccionar sale la barra de subrayar («${(sel || '').slice(0, 30)}…»)`);
    await page.screenshot({ path: path.join(out, name + '-6-seleccion.png') });
    if (barVisible) {
      await page.click('#sel .c.verde'); await page.waitForTimeout(900);
      const notes = await page.evaluate(() => window.__atrio.saved.notes);
      ok(notes && notes.length === 1 && notes[0].color === 'verde', `${name}: subrayado guardado (${notes && notes.length})`);
      await page.screenshot({ path: path.join(out, name + '-7-subrayado.png') });
      await page.mouse.click(206, 430); await page.waitForTimeout(500);
      await page.click('#b-notas'); await page.waitForTimeout(600);
      await page.screenshot({ path: path.join(out, name + '-8-notas.png') });
      await page.evaluate(() => { document.querySelector('#panel').classList.remove('on'); document.querySelector('#velo').classList.remove('on'); });
    }
    // Índice y búsqueda
    await page.mouse.click(206, 430); await page.waitForTimeout(500);
    if (!(await page.$eval('#abajo', (e) => !e.classList.contains('oculta')))) { await page.mouse.click(206, 430); await page.waitForTimeout(500); }
    await page.click('#b-indice'); await page.waitForTimeout(600);
    const tocN = await page.$$eval('#panel-cuerpo li', (l) => l.length);
    ok(name.endsWith('pdf') || tocN >= 3, `${name}: índice con ${tocN} entradas`);
    await page.screenshot({ path: path.join(out, name + '-9-indice.png') });
    await page.evaluate(() => { document.querySelector('#panel').classList.remove('on'); document.querySelector('#velo').classList.remove('on'); });
    await page.click('#b-buscar').catch(() => {}); await page.waitForTimeout(400);
    await page.fill('#q', name.endsWith('epub') ? (process.env.BUSCA || 'Mancha') : 'Mancha'); await page.click('#q-ir');
    await page.waitForFunction(() => /resultados|Nada/.test(document.querySelector('#q-estado')?.textContent || ''), null, { timeout: 60000 }).catch(() => {});
    const est = await page.textContent('#q-estado');
    ok(/resultados/.test(est), `${name}: búsqueda → ${est}`);
    await page.screenshot({ path: path.join(out, name + '-10-busqueda.png') });
    await ctx.close();
  }
  await browser.close();
  srv.close();
  const errs = log.filter((l) => !/favicon/.test(l));
  console.log(errs.length ? 'Errores de la página:\n  ' + errs.slice(0, 15).join('\n  ') : 'Sin errores en la página');
})();
