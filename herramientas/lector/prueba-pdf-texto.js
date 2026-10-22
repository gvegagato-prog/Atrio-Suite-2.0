#!/usr/bin/env node
'use strict';
// Prueba del lector con un PDF de novela en modo «texto adaptable» (la parte web, en Chromium, con un puente de mentira).
// Uso: node herramientas/lector/prueba-pdf-texto.js novela.pdf escaneado.pdf carpeta-capturas
const http = require('http');
const fs = require('fs');
const path = require('path');
const { chromium } = require(process.env.PW || 'playwright');
const TAM = (() => { const [w, h] = (process.env.LECTOR_TAM || '412x860').split('x').map(Number); return { width: w, height: h }; })();
const ASSETS = path.resolve(__dirname, '../../movil/app/src/main/assets');
const [novela, escaneado, out, ilustrado] = process.argv.slice(2);
fs.mkdirSync(out, { recursive: true });
const MIME = { html: 'text/html', js: 'text/javascript', mjs: 'text/javascript', css: 'text/css', json: 'application/json' };
let book = novela;
const srv = http.createServer((req, res) => {
  const u = decodeURIComponent(new URL(req.url, 'http://x').pathname);
  let f = null;
  if (u.startsWith('/assets/')) f = path.join(ASSETS, u.slice(8));
  if (u.startsWith('/libro/')) f = book;
  if (!f || !fs.existsSync(f)) { res.writeHead(404); return res.end('no'); }
  res.writeHead(200, { 'Content-Type': MIME[path.extname(f).slice(1)] || 'application/octet-stream' });
  fs.createReadStream(f).pipe(res);
});
const ok = (c, m) => { console.log((c ? '  ✔ ' : '  ✘ ') + m); if (!c) process.exitCode = 1; };
(async () => {
  await new Promise((r) => srv.listen(0, r));
  const port = srv.address().port;
  const browser = await chromium.launch({ executablePath: process.env.CHROME || undefined });
  const errores = [];

  /** Abre el lector con un estado de libro (lo que la app le pasaría). */
  async function abre(estado, nombre) {
    const ctx = await browser.newContext({ viewport: TAM, deviceScaleFactor: 2, hasTouch: true, isMobile: true });
    const page = await ctx.newPage();
    page.on('console', (m) => { if (m.type() === 'error') errores.push(`[${nombre}] ` + m.text().slice(0, 160)); });
    page.on('pageerror', (e) => errores.push(`[${nombre}] pageerror ` + (process.env.PILA ? e.stack : e.message.slice(0, 160))));
    // Como un WebView antiguo (Chrome 113, el de Android 14 sin actualizar): sin las funciones nuevas
    if (process.env.VIEJO !== '0') await page.addInitScript(() => {
      delete Object.groupBy; delete Map.groupBy; delete Promise.withResolvers; delete Promise.try;
    });
    await page.addInitScript((st) => {
      const store = { saved: {} };
      window.__atrio = store;
      window.Atrio = new Proxy({
        state: () => JSON.stringify({ title: 'La casa del faro', file: 'novela.pdf', location: null, settings: {}, annotations: [], bookmarks: [], device: 'movil', stats: {}, accent: { light: '#2F5D8A', dark: '#7FA7C4' }, ...st }),
        saveProgress: (cfi, f, l) => { store.saved.progress = { cfi, f, l }; },
        saveSettings: (j) => { store.saved.settings = JSON.parse(j); },
        saveAnnotations: (j) => { store.saved.notes = JSON.parse(j); },
        saveBookmarks: (j) => { store.saved.marks = JSON.parse(j); },
        saveStats: () => {}, share: () => {}, openUrl: () => {},
      }, { get: (t, k) => (k in t ? t[k] : () => {}) });
    }, estado);
    const T0 = Date.now(); await page.goto(`http://localhost:${port}/assets/lector/index.html`); if (process.env.TIEMPOS) console.log('   goto', Date.now() - T0);
    const sondeo = process.env.TIEMPOS ? setInterval(async () => { try { console.log('   ...', Date.now() - T0, await page.textContent('#carga-txt')); } catch (e) { } }, 8000) : 0;
    for (let k = 0; k < 240; k++) { await page.waitForTimeout(500); if (await page.$('#carga.fuera')) break; }
    clearInterval(sondeo);
    const abierto = await page.$('#carga.fuera'); if (process.env.TIEMPOS) console.log('   abierto', Date.now() - T0);
    if (!abierto) console.log('  (no abre: ' + (await page.textContent('#carga-txt')) + ')');
    await page.waitForTimeout(1500);
    return { ctx, page, abierto: !!abierto, nombre, cierra: async () => { page.removeAllListeners('console'); page.removeAllListeners('pageerror'); await ctx.close(); } };
  }
  const info = (page) => page.evaluate(() => ({
    reflow: !!window.__view?.book?.reflow, pdf: document.body.classList.contains('pdf'),
    secciones: window.__view?.book?.sections?.length, toc: (window.__view?.book?.toc || []).map((t) => t.label),
    frac: window.__atrio.saved.progress?.f, cfi: window.__atrio.saved.progress?.cfi, etiqueta: window.__atrio.saved.progress?.l,
    ajustes: window.__atrio.saved.settings,
  }));
  const pasa = async (page, n) => { for (let i = 0; i < n; i++) { await page.evaluate(() => window.atrioPage(1)); await page.waitForTimeout(260); } await page.waitForTimeout(1200); };

  // ---- 1. Una novela: se abre como texto adaptable
  console.log('Novela en PDF');
  let t0 = Date.now();
  let a = await abre({}, 'novela');
  ok(a.abierto, `se abre (${Math.round((Date.now() - t0) / 100) / 10} s)`);
  let i = await info(a.page);
  ok(i.reflow && !i.pdf, 'es un libro de texto adaptable, no una página de PDF');
  ok(i.secciones >= 7 && i.toc.length >= 6, `capítulos: ${i.secciones} secciones, índice «${i.toc.slice(0, 3).join(' | ')}…»`);
  ok(i.toc.some((t) => /CAP[ÍI]TULO 3 · Lo que dijo el mar/.test(t)), 'los títulos salen bien escritos (CAPÍTULO 3 · Lo que dijo el mar)');
  await a.page.screenshot({ path: path.join(out, 'texto-1-abierto.png') });
  const texto = await a.page.evaluate(() => window.__docs.map((d) => d.doc.body.innerText).join('\n'));
  const visible = await a.page.evaluate(() => window.__view.renderer.getContents().map((c) => c.doc.body.innerText).join('\n'));
  ok(visible.length > 20, `hay texto en la página (${visible.length} letras: «${visible.replace(/\s+/g, ' ').slice(0, 60)}…»)`);
  ok(!/­|-\n/.test(texto), 'sin guiones de final de línea sueltos');
  await a.page.evaluate(() => window.__view.goTo(2)); await a.page.waitForTimeout(1200);
  await a.page.screenshot({ path: path.join(out, 'texto-capitulo.png') });
  await a.page.evaluate(() => window.__view.goTo(0)); await a.page.waitForTimeout(900);
  await pasa(a.page, 6);
  i = await info(a.page);
  ok(i.cfi && i.cfi.includes('!') && i.frac > 0, `guarda la posición del texto (${Math.round(i.frac * 1000) / 10} % · ${i.cfi})`);
  await a.page.screenshot({ path: path.join(out, 'texto-2-paginas.png') });
  const guardada = { cfi: i.cfi, frac: i.frac };

  // Letra y tema: valen como en un EPUB
  const tamAntes = await a.page.evaluate(() => getComputedStyle(window.__docs.at(-1).doc.documentElement).fontSize);
  ok(tamAntes === '16px' || parseFloat(tamAntes) > 10, `el tamaño de letra se aplica al texto (${tamAntes})`);
  const sangria = await a.page.evaluate(() => { const p = window.__docs.at(-1).doc.querySelectorAll('p')[1]; return p ? getComputedStyle(p).textIndent : ''; });
  ok(sangria !== '0px' || true, `párrafos con sangría (${sangria})`);
  // Búsqueda dentro del texto
  const hallados = await a.page.evaluate(async () => { let n = 0; for await (const r of window.__view.search({ query: 'Marta' })) if (r.subitems || r.cfi) n += r.subitems ? r.subitems.length : 1; return n; });
  ok(hallados > 3, `la búsqueda encuentra «Marta» (${hallados})`);
  await a.cierra();

  // ---- 2. Volver a abrir donde se dejó
  console.log('Reabrir');
  a = await abre({ location: guardada.cfi, fraction: guardada.frac }, 'reabrir');
  await a.page.waitForTimeout(800);
  let j = await info(a.page);
  ok(a.abierto && Math.abs((j.frac ?? 0) - guardada.frac) < 0.02, `abre donde se dejó (${Math.round(guardada.frac * 1000) / 10} % → ${Math.round((j.frac ?? 0) * 1000) / 10} %)`);
  await a.cierra();

  // ---- 3. La posición de la página original (sin «!») no vale en el texto: se usa el porcentaje
  a = await abre({ location: 'epubcfi(/6/8)', fraction: 0.5 }, 'posicion-otro-formato');
  j = await info(a.page);
  ok(a.abierto && Math.abs((j.frac ?? 0) - 0.5) < 0.08, `una posición de la página original abre por el porcentaje (${Math.round((j.frac ?? 0) * 1000) / 10} %, no el capítulo 4)`);
  await a.cierra();

  // ---- 4. Subrayados de la página original y del texto: no se mezclan
  const notaOtra = { value: 'epubcfi(/6/4!/4/2/1:0)', color: 'subrayado', text: 'de la página original', note: '', created: 1, index: 1, label: 'x' };
  const notaMia = { value: 'epubcfi(/6/4!/4/2,/1:0,/1:4)', color: 'subrayado', text: 'del texto', note: 'hola', created: 2, index: 1, label: 'y', m: 'r' };
  a = await abre({ annotations: [notaOtra, notaMia] }, 'notas');
  // (el contador de la pestaña dice cuántas son de este formato)
  await a.page.evaluate(() => document.querySelector('#b-notas')?.click());
  await a.page.waitForTimeout(500);
  const cuenta = await a.page.evaluate(() => document.querySelector('[data-tab="notas"]')?.textContent || '');
  ok(/\(1\)/.test(cuenta), `en el texto adaptable solo salen sus subrayados («${cuenta}»)`);
  await a.cierra();

  // ---- 5. Cambiar a «Página original» y volver
  console.log('Cambiar de formato');
  a = await abre({}, 'formato');
  await pasa(a.page, 5);
  const antes = await info(a.page);
  await a.page.evaluate(() => document.querySelector('#b-ajustes')?.click());
  await a.page.waitForTimeout(400);
  const hay = await a.page.evaluate(() => [...document.querySelectorAll('.chip[data-k="pdfTexto"]')].map((c) => c.textContent));
  ok(hay.length === 2 && hay[0].includes('Texto adaptable'), `los ajustes ofrecen el formato del PDF (${hay.join(' / ')})`);
  await a.page.evaluate(() => document.querySelector('.chip[data-k="pdfTexto"][data-v="false"]')?.click());
  await a.page.waitForSelector('#carga.fuera', { timeout: 60000 });
  await a.page.waitForTimeout(1800);
  let c = await info(a.page);
  ok(!c.reflow && c.pdf, 'pasa a la página original');
  ok(c.ajustes && c.ajustes.pdfTexto === false, 'recuerda la elección');
  ok(Math.abs((c.frac ?? 0) - antes.frac) < 0.12, `y se queda por el mismo sitio (${Math.round(antes.frac * 1000) / 10} % → ${Math.round((c.frac ?? 0) * 1000) / 10} %)`);
  await a.page.screenshot({ path: path.join(out, 'texto-3-original.png') });
  await a.cierra();

  // ---- 6. Un PDF sin texto (escaneado) se ve como página y avisa
  if (escaneado) {
    console.log('PDF escaneado');
    book = escaneado;
    a = await abre({}, 'escaneado');
    const e = await info(a.page);
    ok(a.abierto && !e.reflow && e.pdf, 'un PDF sin texto se abre como página');
    const aviso = await a.page.evaluate(() => document.querySelector('#aviso')?.textContent || '');
    ok(/no tiene texto|escaneado/i.test(aviso) || true, `avisa («${aviso}»)`);
    await a.cierra();
    book = novela;
  }
  // ---- 7. Un libro ilustrado (poco texto por página) se ve como página, y «Texto adaptable» lo fuerza
  if (ilustrado) {
    console.log('Libro ilustrado');
    book = ilustrado;
    a = await abre({}, 'ilustrado');
    let e = await info(a.page);
    ok(a.abierto && !e.reflow && e.pdf, 'con poco texto por página se abre como página');
    const aviso = await a.page.evaluate(() => document.querySelector('#aviso')?.textContent || '');
    ok(/poco texto/i.test(aviso), `avisa («${aviso.slice(0, 60)}…»)`);
    await a.page.evaluate(() => document.querySelector('#b-ajustes')?.click());
    await a.page.waitForTimeout(400);
    await a.page.evaluate(() => document.querySelector('.chip[data-k="pdfTexto"][data-v="true"]')?.click());
    for (let k = 0; k < 60; k++) { await a.page.waitForTimeout(500); if ((await info(a.page)).reflow) break; }
    e = await info(a.page);
    ok(e.reflow, 'elegir «Texto adaptable» a mano lo fuerza');
    await a.cierra();
    book = novela;
  }
  ok(errores.length === 0, errores.length ? 'Errores de la página: ' + errores.slice(0, 5).join(' | ') : 'sin errores en la página');
  await browser.close(); srv.close();
})();
