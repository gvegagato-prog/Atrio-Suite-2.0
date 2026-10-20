#!/usr/bin/env node
'use strict';
// Prueba del lector (la parte web) en Chromium, con un puente de mentira en lugar de la app.
// Uso: node herramientas/lector/prueba-lector.js libro.epub libro.pdf carpeta-capturas
const http = require('http');
const fs = require('fs');
const path = require('path');
// El color de un píxel de la pantalla (decodifica el PNG de la captura con zlib, sin dependencias)
async function pixel(page, x, y) {
  const zlib = require('zlib');
  const b = await page.screenshot({ clip: { x, y, width: 1, height: 1 } });
  let o = 8, w = 0, ct = 0; const idat = [];
  while (o < b.length) {
    const len = b.readUInt32BE(o), type = b.toString('ascii', o + 4, o + 8), d = b.subarray(o + 8, o + 8 + len);
    if (type === 'IHDR') { w = d.readUInt32BE(0); ct = d[9]; }
    if (type === 'IDAT') idat.push(d);
    o += 12 + len;
  }
  const raw = zlib.inflateSync(Buffer.concat(idat));
  return [raw[1], raw[2], raw[3]]; // 1×1: byte de filtro (0 en la primera fila) y luego R, G, B
}
const { chromium } = require(process.env.PW || 'playwright');
// Tamaño de la pantalla (en dp): el de un móvil; LECTOR_TAM=1260x840 para la tableta
const TAM = (() => { const [w, h] = (process.env.LECTOR_TAM || '412x860').split('x').map(Number); return { width: w, height: h }; })();
// Las coordenadas de los toques están pensadas para 412 × 860: se escalan al tamaño de la pantalla
const X = (x) => Math.round((x * TAM.width) / 412);
const Y = (y) => Math.round((y * TAM.height) / 860);

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
    const ctx = await browser.newContext({ viewport: TAM, deviceScaleFactor: 2, hasTouch: true, isMobile: true });
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
        state: () => JSON.stringify({ title: nm === 'quijote.epub' ? 'Don Quijote de la Mancha' : 'PDF de prueba', file: nm, location: null, settings: {}, annotations: [], bookmarks: [], stats: {}, accent: { light: '#2F5D8A', dark: '#7FA7C4' } }),
        saveProgress: (cfi, f, l) => { store.saved.progress = { cfi, f, l }; },
        saveSettings: (j) => { store.saved.settings = JSON.parse(j); },
        saveAnnotations: (j) => { store.saved.notes = JSON.parse(j); },
        saveBookmarks: (j) => { store.saved.marks = JSON.parse(j); },
        setBrightness: () => {}, keepScreenOn: () => {}, setTheme: () => {}, systemBars: () => {},
        copy: () => {}, share: (t) => { store.shared = t; }, openUrl: (u) => { store.url = u; }, close: () => {},
        buzz: () => { store.buzz = (store.buzz || 0) + 1; },
        lookup: (w, s, id) => { store.lookup = { w, s }; setTimeout(() => window.atrioResult(id, { lang: 'es', langName: 'Español', defs: ['Primera acepción de prueba.', 'Segunda.'] }), 50); },
        translate: (w, s, l, id) => { setTimeout(() => window.atrioResult(id, { word: 'traducida', sentence: 'frase traducida' }), 50); },
        saveStats: (j) => { store.saved.stats = JSON.parse(j); },
        // La voz: cada frase «se lee» en 30 ms
        speak: (t, id) => { (store.spoken ||= []).push(t); setTimeout(() => window.atrioSpoken && window.atrioSpoken(id, false), 30); },
        stopSpeaking: () => { store.stopped = (store.stopped || 0) + 1; },
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
    // Leyendo no queda nada animándose sin fin (la rueda de carga oculta seguía girando y gastaba batería)
    const sinFin = await page.evaluate(() => document.getAnimations().filter((a) => a.playState === 'running' && a.effect?.getComputedTiming().iterations === Infinity).length);
    ok(sinFin === 0, `${name}: ninguna animación sin fin mientras lees (${sinFin})`);
    let f0 = await frac();
    // Tocar el lado derecho de un PDF: abre el menú y no pasa de página
    if (name.endsWith('pdf')) {
      // En horizontal (tableta) el PDF va de dos en dos páginas: con el de prueba (5) ya se está al final
      if (TAM.width > TAM.height) { await page.evaluate(() => window.__view.goTo(0)); await page.waitForTimeout(1200); f0 = await frac(); }
      // Tocar fuera del centro no abre el menú ni pasa de página; en el centro, lo abre (sin mover nada)
      await touch([[X(380), Y(430)]]); await page.waitForTimeout(900);
      const menu = await page.$eval('#abajo', (e) => !e.classList.contains('oculta'));
      ok(!menu && (await frac()) === f0, `${name}: tocar fuera del centro no abre el menú ni pasa de página`);
      await touch([[X(206), Y(430)]]); await page.waitForTimeout(500);
      ok((await page.$eval('#abajo', (e) => !e.classList.contains('oculta'))) && (await frac()) === f0, `${name}: tocar el centro abre el menú`);
      await touch([[X(206), Y(430)]]); await page.waitForTimeout(700);
      ok(await page.$eval('#abajo', (e) => e.classList.contains('oculta')), `${name}: tocar con el menú abierto lo cierra`);
      await touch([[X(206), Y(600)], [X(207), Y(520)], [X(208), Y(440)], [X(208), Y(380)]]); await page.waitForTimeout(700);
      ok((await page.$eval('#abajo', (e) => !e.classList.contains('oculta'))) && (await frac()) === f0, `${name}: deslizar hacia arriba abre el menú`);
      await touch([[X(206), Y(380)], [X(207), Y(460)], [X(208), Y(540)], [X(208), Y(600)]]); await page.waitForTimeout(700);
      ok(await page.$eval('#abajo', (e) => e.classList.contains('oculta')), `${name}: deslizar hacia abajo lo cierra`);
      // Deslizar hacia la izquierda: página siguiente
      await touch([[X(330), Y(430)], [X(280), Y(432)], [X(200), Y(434)], [X(120), Y(436)]]); await page.waitForTimeout(1300);
      const f1 = await frac();
      ok(f1 > f0, `${name}: deslizar pasa de página (${Math.round(f0 * 1000) / 10} → ${Math.round(f1 * 1000) / 10} %)`);
      await touch([[X(90), Y(430)], [X(150), Y(432)], [X(230), Y(434)], [X(310), Y(436)]]); await page.waitForTimeout(1300);
      ok((await frac()) < f1, `${name}: deslizar al otro lado vuelve atrás`);
      // Un desliz corto (30 px) también basta
      const f2 = await frac();
      await touch([[X(250), Y(430)], [X(235), Y(431)], [X(220), Y(432)]]); await page.waitForTimeout(1300);
      ok((await frac()) > f2, `${name}: un desliz corto (30 px) pasa de página`);
    } else {
      // (Los deslizamientos del EPUB no se pueden simular aquí: foliate usa screenX y las pruebas lo mandan a 0.
      // Mantener pulsado, seleccionar y deslizar se prueban en el emulador con toques de verdad.)
      const m = await page.evaluate(() => window.__view?.renderer?.getAttribute('margin'));
      ok(m && parseInt(m) >= 42, `${name}: margen arriba y abajo ${m}`);
      // Un desliz corto pasa de página (antes había que arrastrar media pantalla). Toques de mentira con screenX,
      // sobre el paginador, como los de un dedo: 40 px en ~150 ms → página siguiente; 6 px → se queda.
      const pagina = () => page.evaluate(() => window.__view.renderer.page);
      const desliz = (dx, pasos = 6) => page.evaluate(async ({ dx, pasos }) => {
        const r = window.__view.renderer;
        const mk = (x) => new Touch({ identifier: 1, target: r, clientX: x, clientY: 400, screenX: x, screenY: 400 });
        const fire = (type, x) => r.dispatchEvent(new TouchEvent(type, { touches: type === 'touchend' ? [] : [mk(x)], changedTouches: [mk(x)], bubbles: true, cancelable: true, composed: true }));
        const x0 = 260;
        fire('touchstart', x0);
        for (let i = 1; i <= pasos; i++) { await new Promise((ok) => setTimeout(ok, 25)); fire('touchmove', x0 - (dx * i) / pasos); }
        fire('touchend', x0 - dx);
      }, { dx, pasos });
      // (unas páginas atrás: así hay página siguiente aunque se esté al final del libro de prueba)
      for (let i = 0; i < 2; i++) { await page.evaluate(() => window.atrioPage(-1)); await page.waitForTimeout(500); }
      // (se mira lo leído: al pasar al capítulo siguiente, el número de página vuelve a empezar)
      await page.waitForTimeout(900);
      const g0 = await frac();
      await desliz(40); await page.waitForTimeout(1400);
      const g1 = await frac();
      ok(g1 > g0, `${name}: un desliz corto (40 px) pasa de página (${Math.round(g0 * 1000) / 10} → ${Math.round(g1 * 1000) / 10} %)`);
      await desliz(-40); await page.waitForTimeout(1400);
      const g2 = await frac();
      ok(g2 < g1, `${name}: y al otro lado vuelve (${Math.round(g2 * 1000) / 10} %)`);
      const pr = await pagina();
      await desliz(6, 2); await page.waitForTimeout(1100);
      ok((await pagina()) === pr && (await frac()) === g2, `${name}: un roce de 6 px no pasa de página`);
      // Deslizar hacia arriba abre el menú; hacia abajo lo cierra (y no pasa de página)
      const menuAbierto = () => page.$eval('#abajo', (e) => !e.classList.contains('oculta'));
      await touch([[X(206), Y(600)], [X(207), Y(520)], [X(208), Y(440)], [X(208), Y(380)]]); await page.waitForTimeout(700);
      ok((await menuAbierto()) && (await frac()) === g2, `${name}: deslizar hacia arriba abre el menú`);
      await touch([[X(206), Y(380)], [X(207), Y(460)], [X(208), Y(540)], [X(208), Y(600)]]); await page.waitForTimeout(700);
      ok(!(await menuAbierto()), `${name}: deslizar hacia abajo lo cierra`);
    }
    // Barras (tocar el centro). En el EPUB, en una página de texto: en el índice del libro el centro es un enlace.
    if (name.endsWith('epub')) { await page.evaluate(() => window.__view.goTo(13)); await page.waitForTimeout(1500); }
    // Las barras: tocando el centro, encima del texto (sin recolocarlo); un poco fuera del centro, no
    await page.mouse.click(X(100), Y(430)); await page.waitForTimeout(600);
    ok(await page.$eval('#abajo', (e) => e.classList.contains('oculta')), `${name}: tocar fuera del centro no abre el menú`);
    const textoAntes = await page.evaluate(() => { const r = document.querySelector('foliate-view').renderer; return [r.start, r.scrollTop, window.__atrio.saved.progress?.f] .join() });
    await page.mouse.click(X(206), Y(430)); await page.waitForTimeout(600);
    const textoDespues = await page.evaluate(() => { const r = document.querySelector('foliate-view').renderer; return [r.start, r.scrollTop, window.__atrio.saved.progress?.f] .join() });
    ok(await page.$eval('#abajo', (e) => !e.classList.contains('oculta')), `${name}: tocar el centro abre el menú`);
    ok(textoAntes === textoDespues, `${name}: abrir el menú no mueve el texto (${textoAntes} → ${textoDespues})`);
    await page.screenshot({ path: path.join(out, name + '-3-barras.png') });
    // El tiempo leído: corre mientras pasas páginas, como mucho 5 min sin moverla, y nada mientras está en pausa
    // (otra app o pantalla apagada). Con un reloj de mentira para no esperar de verdad.
    {
      const total = async () => { await page.evaluate(() => { window.atrioPausa(true); }); const t = await page.evaluate(() => window.__atrio.saved.stats?.total || 0); await page.evaluate(() => window.atrioPausa(false)); return t; };
      await page.evaluate(() => { window.__t = Date.now(); window.__reloj = () => window.__t; });
      const t0 = await total();
      const paso = async (ms, d) => { await page.evaluate(([ms, d]) => { window.__t += ms; window.atrioPage(d); }, [ms, d]); await page.waitForTimeout(900); };
      await paso(2 * 60000, -1);                               // 2 min en una página: cuentan
      await paso(20 * 60000, 1);                               // 20 min sin moverla: solo 5
      await page.evaluate(() => { window.atrioPausa(true); window.__t += 30 * 60000; window.atrioPausa(false); }); // en pausa: nada
      await paso(60000, -1);                                   // 1 min más
      const t1 = await total();
      await page.evaluate(() => { delete window.__reloj; });
      ok(t1 - t0 === 8 * 60000, `${name}: tiempo leído con tope de 5 min y sin contar la pausa (${(t1 - t0) / 60000} min, deben ser 8)`);
    }
    // Lo que queda: páginas del capítulo (y del libro, aproximadas) en la línea de abajo
    const estado = () => page.evaluate(() => [document.querySelector('#e-izq').textContent, document.querySelector('#e-der').textContent, document.querySelector('foliate-view').renderer.pages]);
    const e0 = await estado();
    if (name.endsWith('epub')) ok(/^(\d+ págs?\..*para acabar el capítulo|Última página del capítulo)$/.test(e0[0]) && /^≈ \d+ págs?\. · \d+%$/.test(e0[1]), `${name}: páginas que quedan («${e0[0]}» · «${e0[1]}»)`);
    else ok(/^quedan \d+ págs?\. · \d+%$/.test(e0[1]), `${name}: páginas que quedan del PDF («${e0[1]}»)`);
    // Ajustes: tema noche y letra más grande
    await page.click('#b-ajustes'); await page.waitForTimeout(500);
    await page.screenshot({ path: path.join(out, name + '-4-ajustes.png') });
    await page.click('[data-theme-btn="noche"]'); await page.waitForTimeout(400);
    if (name.endsWith('epub')) { await page.click('[data-step="size"][data-d="10"]'); await page.waitForTimeout(300); await page.click('[data-k="font"][data-v="serif"]'); }
    await page.waitForTimeout(400);
    const set = await page.evaluate(() => window.__atrio.saved.settings);
    ok(set && set.theme === 'noche', `${name}: guarda los ajustes (${JSON.stringify(set)})`);
    if (name.endsWith('epub')) {
      // Con la letra más grande caben menos palabras: el capítulo tiene más páginas y se vuelven a contar
      // (bastante más grande: con el capítulo corto de prueba, a doble página, un 10 % más aún cabe igual)
      for (let i = 0; i < 6; i++) { await page.click('[data-step="size"][data-d="10"]'); await page.waitForTimeout(150); }
      await page.waitForTimeout(1500);
      const e1 = await estado();
      ok(e1[2] > e0[2] && /págs?\.|Última/.test(e1[0]), `${name}: con letra más grande, más páginas en el capítulo (${e0[2] - 2} → ${e1[2] - 2}; «${e1[0]}»)`);
      for (let i = 0; i < 6; i++) { await page.click('[data-step="size"][data-d="-10"]'); await page.waitForTimeout(150); }
      await page.waitForTimeout(1200);
    }
    // Negro (OLED): fondo negro puro; en PDF, la página invertida del todo (el blanco pasa a negro)
    await page.click('[data-theme-btn="negro"]'); await page.waitForTimeout(400);
    const negro = await page.evaluate(() => [getComputedStyle(document.body).backgroundColor, getComputedStyle(document.querySelector('foliate-view')).filter]);
    ok(negro[0] === 'rgb(0, 0, 0)' && (name.endsWith('epub') || /invert\(1\)/.test(negro[1])), `${name}: tema Negro, negro puro (${negro.join(' · ')})`);
    if (name.endsWith('epub')) {
      // Y en la pantalla de verdad, dentro de la página del libro (entre las líneas, junto al margen izquierdo)
      await page.evaluate(() => { document.querySelector('#panel').classList.remove('on'); document.querySelector('#velo').classList.remove('on'); });
      await page.waitForTimeout(500);
      const box = await page.evaluate(() => { const f = [...(window.__docs || [])].reverse().map((d) => d.doc.defaultView?.frameElement).find((f) => f && f.getBoundingClientRect().width > 0); const r = f.getBoundingClientRect(); return { x: Math.max(r.left, 0) + 3, y: r.top + r.height / 2 }; });
      const px = await pixel(page, Math.round(box.x), Math.round(box.y));
      ok(px.every((c) => c === 0), `${name}: tema Negro, el fondo de la página del libro es negro puro en pantalla (rgb ${px.join(', ')})`);
      await page.click('#b-ajustes'); await page.waitForTimeout(500);
    }
    await page.click('[data-theme-btn="noche"]'); await page.waitForTimeout(300);
    await page.mouse.click(X(206), Y(120)); await page.waitForTimeout(700);
    await page.evaluate(() => { document.querySelector('#panel').classList.remove('on'); document.querySelector('#velo').classList.remove('on'); });
    await page.screenshot({ path: path.join(out, name + '-5-noche.png') });
    // Mantener pulsada una palabra: sale su menú (significado); «Subrayar» y mantener pulsada la última palabra
    const wordsAt = await page.evaluate(() => {
      const out = [];
      for (const { doc: d } of [...(window.__docs || [])].reverse()) {
        const f = d.defaultView?.frameElement; if (!f || !d.body) continue;
        const fr = f.getBoundingClientRect();
        const w = d.createTreeWalker(d.body, NodeFilter.SHOW_TEXT);
        let n;
        while ((n = w.nextNode()) && out.length < 40) {
          const re = /[\p{L}]{4,}/gu; let m;
          while ((m = re.exec(n.textContent)) && out.length < 40) {
            const r = d.createRange(); r.setStart(n, m.index); r.setEnd(n, m.index + m[0].length);
            const b = r.getBoundingClientRect();
            const x = fr.left + b.left + b.width / 2, y = fr.top + b.top + b.height / 2;
            if (b.width > 0 && x > 20 && x < innerWidth - 20 && y > 60 && y < innerHeight - 60) out.push({ w: m[0], x, y });
          }
        }
        if (out.length) break;
      }
      return out;
    });
    const hold = async (p) => {
      await cdp.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x: p.x, y: p.y }] });
      await page.waitForTimeout(700);
      await cdp.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
      await page.waitForTimeout(500);
    };
    const w1 = wordsAt[0], w2 = wordsAt[Math.min(5, wordsAt.length - 1)];
    let menuOk = false;
    if (w1) {
      await hold(w1);
      const shown = await page.evaluate(() => ({ vis: !document.querySelector('#palabra').classList.contains('oculta'), w: document.querySelector('#pal-w').textContent, def: document.querySelector('#pal-def').textContent }));
      menuOk = shown.vis && shown.w === w1.w && /Primera acepción/.test(shown.def);
      ok(menuOk, `${name}: mantener pulsado «${w1.w}» abre su menú con el significado (${shown.w} · ${shown.def.slice(0, 30)})`);
      await page.screenshot({ path: path.join(out, name + '-6-palabra.png') });
    } else ok(false, `${name}: no encuentro palabras en pantalla`);
    if (menuOk) {
      await page.click('#p-subrayar'); await page.waitForTimeout(400);
      ok(await page.$eval('#pend', (e) => !e.classList.contains('oculta')), `${name}: «Subrayar» pide la última palabra`);
      await hold(w2); await page.waitForTimeout(400);
      const barVisible = await page.$eval('#sel', (e) => !e.classList.contains('oculta'));
      ok(barVisible, `${name}: al mantener pulsada la última palabra se subraya y sale su barra`);
      const notes = await page.evaluate(() => window.__atrio.saved.notes);
      const t = notes?.[0]?.text || '';
      ok(notes && notes.length === 1 && t.startsWith(w1.w) && t.endsWith(w2.w), `${name}: subrayado de «${w1.w}» a «${w2.w}» guardado al momento (${t.length} letras)`);
      // Se ve: en EPUB con ::highlight, en PDF con los rectángulos que invierten la página
      const pintado = await page.evaluate(() => {
        let n = 0;
        for (const { doc: d } of window.__docs || []) {
          if (!d.defaultView) continue;
          n += d.defaultView.CSS?.highlights?.get('atrio')?.size || 0;
          n += d.getElementById('atrio-sub')?.children.length || 0;
        }
        return n;
      });
      ok(pintado > 0, `${name}: el subrayado se pinta (${pintado})`);
      await page.screenshot({ path: path.join(out, name + '-7-subrayado.png') });
      if (barVisible) {
        ok(!(await page.$('#sel .c')), `${name}: ya no hay colores para elegir`);
        await page.click('#s-nota'); await page.waitForTimeout(500);
        await page.fill('#nota-txt', 'Nota de prueba'); await page.click('#nota-si'); await page.waitForTimeout(600);
        const n2 = await page.evaluate(() => window.__atrio.saved.notes);
        ok(n2?.[0]?.note === 'Nota de prueba', `${name}: se añade una nota al subrayado`);
        // Tocar el subrayado vuelve a sacar su barra
        await page.mouse.click(w1.x, w1.y); await page.waitForTimeout(700);
        const again = await page.evaluate(() => ({ vis: !document.querySelector('#sel').classList.contains('oculta'), b: document.querySelector('#s-nota').textContent, t: document.querySelector('#s-txt').textContent }));
        ok(again.vis && again.b === 'Editar nota' && again.t === 'Nota de prueba', `${name}: tocar el subrayado abre su barra con la nota (${JSON.stringify(again)})`);
        await page.screenshot({ path: path.join(out, name + '-8-tocar.png') });
        await page.click('#s-copiar'); await page.waitForTimeout(400);
      }
      if (name.endsWith('pdf')) {
        const bgc = await page.evaluate(() => window.__view?.style.background || '');
        ok(/rgb/.test(bgc), `${name}: alrededor de la página, el color del papel (${bgc})`);
      }
    }
    // Índice y búsqueda
    await page.mouse.click(X(206), Y(430)); await page.waitForTimeout(500);
    if (!(await page.$eval('#abajo', (e) => !e.classList.contains('oculta')))) { await page.evaluate(() => window.atrioMenu()); await page.waitForTimeout(500); }
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
    await page.evaluate(() => { document.querySelector('#panel').classList.remove('on'); document.querySelector('#velo').classList.remove('on'); });
    // Línea de estado: lo que queda del capítulo (o la página) y el %
    const izq = await page.textContent('#e-izq'), der = await page.textContent('#e-der');
    ok(/%/.test(der) && izq.length > 3, `${name}: línea de estado «${izq} · ${der}»`);
    // Leer en voz alta (EPUB): lee frases seguidas y se para
    if (name.endsWith('epub')) {
      await page.evaluate(() => document.querySelector('#b-voz').click()); await page.waitForTimeout(2500);
      const said = await page.evaluate(() => window.__atrio.spoken || []);
      ok(said.length >= 3, `${name}: lee en voz alta (${said.length} frases: «${(said[0] || '').slice(0, 40)}…»)`);
      await page.screenshot({ path: path.join(out, name + '-11-voz.png') });
      await page.evaluate(() => document.querySelector('#v-parar').click()); await page.waitForTimeout(300);
      ok(await page.$eval('#voz', (e) => e.classList.contains('oculta')), `${name}: «Parar» quita la voz`);
    }
    await ctx.close();
  }
  // ---------------- PDF leyendo hacia abajo (manga): un PDF de 12 páginas hecho aquí mismo con Chromium
  {
    const name = 'manga.pdf';
    const tmp = await browser.newPage();
    await tmp.setContent('<style>@page{size:600px 900px;margin:0}body{margin:0}div{width:600px;height:900px;display:flex;align-items:center;justify-content:center;font:bold 260px serif;break-after:page}</style>'
      + Array.from({ length: 12 }, (_, i) => `<div style="background:hsl(${i * 30},60%,80%)">${i + 1}</div>`).join(''));
    book = path.join(out, name);
    await tmp.pdf({ path: book, width: '600px', height: '900px', printBackground: true });
    await tmp.close();
    const ctx = await browser.newContext({ viewport: TAM, deviceScaleFactor: 2, hasTouch: true, isMobile: true });
    const page = await ctx.newPage();
    page.on('console', (m) => { if (m.type() === 'error') log.push(m.text()); });
    page.on('pageerror', (e) => log.push('pageerror ' + e.message));
    await page.addInitScript(() => {
      const store = { saved: {} };
      window.__atrio = store;
      const noop = () => {};
      window.Atrio = {
        state: () => JSON.stringify({ title: 'Manga de prueba', file: 'manga.pdf', location: null, settings: { pdfFlow: 'scroll' }, annotations: [], bookmarks: [], stats: {} }),
        saveProgress: (cfi, f, l) => { store.saved.progress = { cfi, f, l }; }, saveSettings: (j) => { store.saved.settings = JSON.parse(j); },
        saveAnnotations: noop, saveBookmarks: noop, setBrightness: noop, keepScreenOn: noop, setTheme: noop, systemBars: noop, copy: noop, share: noop,
        openUrl: noop, close: noop, buzz: noop, saveStats: noop, speak: noop, stopSpeaking: noop, lookup: noop, translate: noop, volumeKeys: (on) => { store.vol = on; },
      };
    });
    await page.goto(`http://localhost:${port}/assets/lector/index.html`);
    await page.waitForSelector('#carga.fuera', { timeout: 60000 }).catch(() => {});
    ok(!!(await page.$('#carga.fuera')), `${name}: se abre`);
    await page.waitForTimeout(2500);
    const rend = await page.evaluate(() => document.querySelector('foliate-view').renderer.localName);
    ok(rend === 'foliate-fxl-scroll', `${name}: «Deslizando hacia abajo» usa las páginas una debajo de otra (${rend})`);
    const cargadas = await page.evaluate(() => new Set((window.__docs || []).filter((d) => d.doc.defaultView).map((d) => d.index)).size);
    ok(cargadas >= 2 && cargadas < 12, `${name}: carga solo las páginas cercanas (${cargadas} de 12)`);
    await page.screenshot({ path: path.join(out, name + '-1-arriba.png') });
    const pagina = async () => (await page.textContent('#e-izq')).trim();
    ok(/Página 1 de 12/.test(await pagina()), `${name}: «${await pagina()}»`);
    // Deslizar el dedo hacia arriba (con un toque de verdad, empezando encima de la página)
    const cdp = await ctx.newCDPSession(page);
    // (Input.synthesizeScrollGesture con «touch» no desplaza nada en Chromium sin pantalla: se usan toques de verdad)
    for (let i = 0; i < 3; i++) {
      const pts = [[X(206), Y(700)], [X(206), Y(650)], [X(206), Y(550)], [X(206), Y(400)], [X(206), Y(250)], [X(206), Y(200)]];
      await cdp.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x: pts[0][0], y: pts[0][1] }] });
      for (const p of pts.slice(1)) { await page.waitForTimeout(16); await cdp.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ x: p[0], y: p[1] }] }); }
      await cdp.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
      await page.waitForTimeout(400);
    }
    await page.waitForTimeout(3000);   // (con una máquina más lenta, el desplazamiento tarda más en asentarse)
    const tras = await pagina();
    const f1 = await page.evaluate(() => window.__atrio.saved.progress?.f || 0);
    // (En horizontal cada página es más alta que la pantalla: los mismos deslizamientos avanzan menos)
    ok((TAM.width > TAM.height ? /Página [2-9] de 12/ : /Página [3-9] de 12/).test(tras) && f1 > (TAM.width > TAM.height ? 0.08 : 0.15), `${name}: deslizando hacia arriba se baja por el libro («${tras}», ${Math.round(f1 * 100)} %)`);
    await page.screenshot({ path: path.join(out, name + '-2-bajando.png') });
    // Botones de volumen: no pasan página (la app los deja para el volumen)
    const y0 = await page.evaluate(() => document.querySelector('foliate-view').renderer.scrollTop);
    await page.evaluate(() => window.atrioPage(1)); await page.waitForTimeout(600);
    const y1 = await page.evaluate(() => document.querySelector('foliate-view').renderer.scrollTop);
    const vol = await page.evaluate(() => window.__atrio.vol);
    ok(y1 === y0 && vol === false, `${name}: los botones de volumen no pasan página (${Math.round(y0)} → ${Math.round(y1)}, volumen para la app: ${vol})`);
    // Deslizar de lado: no pasa de página (leyendo hacia abajo solo se desplaza)
    {
      const pg0 = await pagina();
      const s0 = await page.evaluate(() => document.querySelector('foliate-view').renderer.scrollTop);
      const pts = [[X(330), Y(430)], [X(280), Y(432)], [X(200), Y(434)], [X(120), Y(436)]];
      await cdp.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x: pts[0][0], y: pts[0][1] }] });
      for (const p of pts.slice(1)) { await page.waitForTimeout(16); await cdp.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ x: p[0], y: p[1] }] }); }
      await cdp.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
      await page.waitForTimeout(900);
      const s1 = await page.evaluate(() => document.querySelector('foliate-view').renderer.scrollTop);
      ok((await pagina()) === pg0 && Math.abs(s1 - s0) < 30, `${name}: deslizar de lado no pasa de página («${await pagina()}», ${Math.round(s0)} → ${Math.round(s1)})`);
    }
    // El menú, tocando el centro (también leyendo hacia abajo)
    await page.mouse.click(X(206), Y(430)); await page.waitForTimeout(700);
    ok(await page.$eval('#abajo', (e) => !e.classList.contains('oculta')), `${name}: tocar el centro abre el menú`);
    // Cambiar a página a página y volver: se queda en la misma página
    const antes = await pagina();
    await page.click('#b-ajustes'); await page.waitForTimeout(500);
    await page.screenshot({ path: path.join(out, name + '-3-ajustes.png') });
    await page.click('[data-k="pdfFlow"][data-v="pages"]');
    await page.waitForSelector('#carga.fuera', { timeout: 30000 }).catch(() => {}); await page.waitForTimeout(2500);
    const r2 = await page.evaluate(() => document.querySelector('foliate-view').renderer.localName);
    ok((await page.evaluate(() => window.__atrio.vol)) === true, `${name}: en «Página a página» los botones de volumen vuelven a pasar página`);
    ok(r2 === 'foliate-fxl' && (await pagina()) === antes, `${name}: «Página a página» reabre en la misma página (${r2}, «${await pagina()}»)`);
    await page.evaluate(() => window.atrioMenu()); await page.waitForTimeout(600);
    await page.click('#b-ajustes'); await page.waitForTimeout(500);
    await page.click('[data-k="pdfFlow"][data-v="strip"]');
    await page.waitForSelector('#carga.fuera', { timeout: 30000 }).catch(() => {}); await page.waitForTimeout(2500);
    const r3 = await page.evaluate(() => document.querySelector('foliate-view').renderer.localName);
    ok(r3 === 'foliate-fxl-scroll' && (await pagina()) === antes, `${name}: «Tira continua» vuelve hacia abajo en la misma página («${await pagina()}»)`);
    const set = await page.evaluate(() => window.__atrio.saved.settings);
    ok(set && set.pdfFlow === 'strip', `${name}: guarda el modo (${set && set.pdfFlow})`);
    await page.screenshot({ path: path.join(out, name + '-4-tira.png') });
    await ctx.close();
  }
  // Una posición guardada que no encaja con este archivo (llega de otro dispositivo con otra edición): abre igual,
  // por el mismo porcentaje, en lugar de quedarse en «mostrar la primera página»
  {
    book = epub;
    const ctx = await browser.newContext({ viewport: TAM, deviceScaleFactor: 2, hasTouch: true, isMobile: true });
    const page = await ctx.newPage();
    page.on('pageerror', (e) => log.push('pageerror ' + e.message));
    await page.addInitScript(() => {
      window.Atrio = new Proxy({ state: () => JSON.stringify({ title: 'Otro dispositivo', file: 'quijote.epub', location: 'epubcfi(/6/4!/4/80/3:5)', fraction: 0.6, settings: {}, annotations: [], bookmarks: [], stats: {} }) },
        { get: (t, k) => t[k] || (() => {}) });
    });
    await page.goto(`http://localhost:${port}/assets/lector/index.html`);
    await page.waitForSelector('#carga.fuera', { timeout: 30000 }).catch(() => {});
    const abierto = !!(await page.$('#carga.fuera'));
    ok(abierto, `posición de otro dispositivo que no encaja: abre igual (${abierto ? 'abierto' : await page.textContent('#carga-txt')})`);
    await ctx.close();
  }
  await browser.close();
  srv.close();
  const errs = log.filter((l) => !/favicon/.test(l));
  console.log(errs.length ? 'Errores de la página:\n  ' + errs.slice(0, 15).join('\n  ') : 'Sin errores en la página');
})();
