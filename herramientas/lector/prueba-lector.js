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
    // Barras (tocar el centro). En el EPUB, en una página de texto: en el índice del libro el centro es un enlace.
    if (name.endsWith('epub')) { await page.evaluate(() => window.__view.goTo(13)); await page.waitForTimeout(1500); }
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
      await page.click('#v-parar'); await page.waitForTimeout(300);
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
    const ctx = await browser.newContext({ viewport: { width: 412, height: 860 }, deviceScaleFactor: 2, hasTouch: true, isMobile: true });
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
        openUrl: noop, close: noop, buzz: noop, saveStats: noop, speak: noop, stopSpeaking: noop, lookup: noop, translate: noop,
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
      const pts = [[206, 700], [206, 650], [206, 550], [206, 400], [206, 250], [206, 200]];
      await cdp.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x: pts[0][0], y: pts[0][1] }] });
      for (const p of pts.slice(1)) { await page.waitForTimeout(16); await cdp.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ x: p[0], y: p[1] }] }); }
      await cdp.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
      await page.waitForTimeout(400);
    }
    await page.waitForTimeout(1200);
    const tras = await pagina();
    const f1 = await page.evaluate(() => window.__atrio.saved.progress?.f || 0);
    ok(/Página [3-9] de 12/.test(tras) && f1 > 0.15, `${name}: deslizando hacia arriba se baja por el libro («${tras}», ${Math.round(f1 * 100)} %)`);
    await page.screenshot({ path: path.join(out, name + '-2-bajando.png') });
    // Botones de volumen: una pantalla más
    const y0 = await page.evaluate(() => document.querySelector('foliate-view').renderer.scrollTop);
    await page.evaluate(() => window.atrioPage(1)); await page.waitForTimeout(600);
    const y1 = await page.evaluate(() => document.querySelector('foliate-view').renderer.scrollTop);
    ok(y1 > y0 + 300, `${name}: el botón de volumen baja una pantalla (${Math.round(y0)} → ${Math.round(y1)})`);
    // Tocar el centro: el menú (no pasa nada más)
    await page.mouse.click(206, 430); await page.waitForTimeout(700);
    ok(await page.$eval('#abajo', (e) => !e.classList.contains('oculta')), `${name}: tocar abre el menú`);
    // Cambiar a página a página y volver: se queda en la misma página
    const antes = await pagina();
    await page.click('#b-ajustes'); await page.waitForTimeout(500);
    await page.screenshot({ path: path.join(out, name + '-3-ajustes.png') });
    await page.click('[data-k="pdfFlow"][data-v="pages"]');
    await page.waitForSelector('#carga.fuera', { timeout: 30000 }).catch(() => {}); await page.waitForTimeout(2500);
    const r2 = await page.evaluate(() => document.querySelector('foliate-view').renderer.localName);
    ok(r2 === 'foliate-fxl' && (await pagina()) === antes, `${name}: «Página a página» reabre en la misma página (${r2}, «${await pagina()}»)`);
    await page.mouse.click(206, 430); await page.waitForTimeout(600);
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
  await browser.close();
  srv.close();
  const errs = log.filter((l) => !/favicon/.test(l));
  console.log(errs.length ? 'Errores de la página:\n  ' + errs.slice(0, 15).join('\n  ') : 'Sin errores en la página');
})();
