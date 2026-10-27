#!/usr/bin/env node
'use strict';
// Prueba de la rejilla propia de ArtStation y Pinterest (Internet.kt del móvil): contra un servidor que imita sus webs y
// sus API (búsqueda, páginas, imágenes grandes, «Usar esta imagen», qué pasa si la API falla) y, si hay red, contra la
// web de verdad de Pinterest (ArtStation no deja entrar a navegadores automáticos: Cloudflare).
// Uso (desde la raíz): node herramientas/prueba-rejilla.js [carpeta-capturas]
const { chromium } = require(process.env.PW || 'playwright');
const http = require('http');
const fs = require('fs');
const path = require('path');
const src = fs.readFileSync(path.join(__dirname, '../movil/app/src/main/java/com/gonzalo/gamerate/Internet.kt'), 'utf8');
const out = process.argv[2] || require('os').tmpdir();
const get = (n) => new RegExp('const val ' + n + ' = """([\\s\\S]*?)"""').exec(src)[1];
const UI = get('JS_REJILLA').replace(/PAPEL/g, '#F3EEE4').replace(/TINTA/g, '#15120F').replace(/GRIS/g, '#7C746A');
const EL = get('JS_ELEGIDA');
const busca = (f, q) => UI + `;window.atrioRej&&atrioRej.busca(${JSON.stringify(f)},${JSON.stringify(q)})`;
let modo = 'bien';
const pedidas = [];
const svg = (c, w, h) => `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}"><rect width="${w}" height="${h}" fill="${c}"/></svg>`;
const srv = http.createServer((q, r) => {
  let body = '';
  q.on('data', (c) => { body += c; });
  q.on('end', () => {
    pedidas.push(q.method + ' ' + q.url);
    const js = (o, code = 200) => { r.writeHead(code, { 'content-type': 'application/json' }); r.end(JSON.stringify(o)); };
    const u = new URL(q.url, 'http://x');
    // ---- ArtStation
    if (u.pathname === '/api/v2/csrf_protection/token.json') return q.method === 'POST' ? js({ public_csrf_token: 'tok123' }) : js({}, 405);
    if (u.pathname === '/api/v2/search/projects.json') {
      if (modo === 'mal') return js({ error: 'no' }, 403);
      if (q.headers['public-csrf-token'] !== 'tok123') return js({ error: 'csrf' }, 422);
      const b = JSON.parse(body); const n = b.page;
      const data = Array.from({ length: n === 1 ? 50 : 10 }, (_, i) => ({ hash_id: `h${n}_${i}`, title: `${b.query} ${n}-${i}`, smaller_square_cover_url: `/img/s.svg?${n}${i}`, user: { full_name: `Artista ${i}` } }));
      return js({ data, total_count: 60 });
    }
    if (u.pathname === '/projects/h1_3.json') return js({ title: 'x', assets: [{ has_image: true, image_url: '/img/grande1.svg' }, { has_image: false, has_embedded_player: true }, { has_image: true, image_url: '/img/grande2.svg' }] });
    // ---- Pinterest
    if (u.pathname === '/resource/BaseSearchResource/get/') {
      if (modo === 'mal') return js({ error: 'no' }, 403);
      if (q.headers['x-requested-with'] !== 'XMLHttpRequest') return js({ error: 'xhr' }, 400);
      const d = JSON.parse(u.searchParams.get('data'));
      const bm = d.options.bookmarks[0];
      const n = bm ? 2 : 1;
      const results = Array.from({ length: n === 1 ? 25 : 5 }, (_, i) => ({ id: `p${n}_${i}`, grid_title: `${d.options.query} pin ${n}-${i}`,
        images: { '236x': { url: `/img/p236.svg?${n}${i}` }, '474x': { url: `/img/p474.svg?${n}${i}` }, '736x': { url: `/img/pgrande.svg?${n}${i}` } } }));
      return js({ resource_response: { data: { results }, bookmark: n === 1 ? 'BM2' : '-end-' } });
    }
    if (u.pathname.startsWith('/img/')) { r.writeHead(200, { 'content-type': 'image/svg+xml' }); return r.end(/grande/.test(q.url) ? svg('#a33', 1600, 1000) : svg('#36a', 300, 300)); }
    // Sus webs: con sus propias imágenes debajo (y el aviso de iniciar sesión de Pinterest)
    r.writeHead(200, { 'content-type': 'text/html' });
    r.end('<html><body style="margin:0"><div id="app"><img src="/img/grande1.svg" style="width:100%"><p>web de la fuente</p></div></body></html>');
  });
}).listen(8766);
let fallos = 0;
const ok = (c, m) => { console.log((c ? '  ✔ ' : '  ✘ ') + m); if (!c) fallos++; };
const n = (p, sel) => p.evaluate((s) => document.querySelectorAll(s).length, sel);
(async () => {
  const b = await chromium.launch();
  const p = await (await b.newContext({ viewport: { width: 800, height: 1100 } })).newPage();
  const errores = [];
  p.on('pageerror', (e) => errores.push(e.message));
  console.log('ArtStation (simulado)');
  await p.goto('http://localhost:8766/search?sort_by=relevance&query=dragon');
  await p.evaluate(busca('artstation', 'dragon'));
  await p.waitForFunction(() => document.querySelectorAll('#atrio-rej img').length === 50, null, { timeout: 5000 }).catch(() => {});
  ok(await n(p, '#atrio-rej img') === 50, 'rejilla propia con los 50 primeros resultados');
  ok(await p.evaluate(() => /60 en ArtStation/.test(document.querySelector('#atrio-rej').textContent)), 'dice cuántos hay');
  ok(pedidas.some((x) => x.startsWith('POST /api/v2/csrf_protection/token.json')) && pedidas.some((x) => x.startsWith('POST /api/v2/search/projects.json')), 'pide el token y busca con él');
  const r0 = JSON.parse(await p.evaluate(EL));
  ok(/\/img\/s\.svg/.test(r0.u), `en la rejilla, la elegida es un resultado (no la imagen de debajo): ${r0.u}`);
  await p.screenshot({ path: path.join(out, 'rejilla-1-artstation.png') });
  await p.click('#atrio-rej button');
  await p.waitForFunction(() => document.querySelectorAll('#atrio-rej img').length === 60, null, { timeout: 5000 }).catch(() => {});
  ok(await n(p, '#atrio-rej img') === 60, 'MÁS RESULTADOS añade la página 2');
  ok(await p.evaluate(() => getComputedStyle(document.querySelector('#atrio-rej button')).display === 'none'), 'y se esconde cuando ya están todos');
  await p.evaluate(() => document.querySelectorAll('#atrio-rej > div:nth-child(2) > div')[3].click());
  await p.waitForFunction(() => document.querySelectorAll('#atrio-rej img[src*="grande"]').length === 2, null, { timeout: 5000 }).catch(() => {});
  ok(await n(p, '#atrio-rej img[src*="grande"]') === 2 && await p.evaluate(() => window.atrioRej.abierto()), 'el proyecto abre sus 2 imágenes grandes (sin el vídeo)');
  await p.waitForTimeout(300);
  const r1 = JSON.parse(await p.evaluate(EL));
  ok(/grande1/.test(r1.u), `con el proyecto abierto, la elegida es la grande que se ve: ${r1.u}`);
  await p.screenshot({ path: path.join(out, 'rejilla-2-proyecto.png') });
  await p.evaluate(() => window.atrioRej.cierra());
  ok(await p.evaluate(() => !window.atrioRej.abierto() && getComputedStyle(document.querySelector('#atrio-rej > div:nth-child(2)')).display === 'grid'), 'Atrás vuelve a la rejilla');
  await p.evaluate(() => window.atrioRej.busca('artstation', 'castillo'));
  await p.waitForFunction(() => /castillo 1-0/.test(document.querySelector('#atrio-rej').textContent), null, { timeout: 5000 }).catch(() => {});
  ok(await p.evaluate(() => /castillo 1-0/.test(document.querySelector('#atrio-rej').textContent)) && await n(p, '#atrio-rej img') === 50, 'otra búsqueda en el mismo sitio');

  console.log('Pinterest (simulado)');
  await p.goto('http://localhost:8766/search/pins/?q=dragon');
  await p.evaluate(busca('pinterest', 'dragon'));
  await p.waitForFunction(() => document.querySelectorAll('#atrio-rej img').length === 25, null, { timeout: 5000 }).catch(() => {});
  ok(await n(p, '#atrio-rej img') === 25, 'rejilla con los pines (sin pedir que inicies sesión)');
  await p.click('#atrio-rej button');
  await p.waitForFunction(() => document.querySelectorAll('#atrio-rej img').length === 30, null, { timeout: 5000 }).catch(() => {});
  ok(await n(p, '#atrio-rej img') === 30 && await p.evaluate(() => getComputedStyle(document.querySelector('#atrio-rej button')).display === 'none'), 'MÁS RESULTADOS sigue con su «bookmark» hasta el final');
  await p.evaluate(() => document.querySelectorAll('#atrio-rej > div:nth-child(2) > div')[0].click());
  await p.waitForFunction(() => document.querySelectorAll('#atrio-rej img[src*="pgrande"]').length === 1, null, { timeout: 5000 }).catch(() => {});
  const r3 = JSON.parse(await p.evaluate(EL));
  ok(/pgrande/.test(r3.u), `al abrir un pin, la elegida es la grande: ${r3.u}`);

  console.log('Si su API no responde');
  modo = 'mal';
  for (const [f, u] of [['artstation', '/search?query=dragon'], ['pinterest', '/search/pins/?q=dragon']]) {
    await p.goto('http://localhost:8766' + u);
    await p.evaluate(busca(f, 'dragon'));
    await p.waitForTimeout(800);
    ok(await p.evaluate(() => !document.getElementById('atrio-rej') && /web de la fuente/.test(document.body.textContent)), `${f}: se queda su web, sin capa encima`);
    const r2 = JSON.parse(await p.evaluate(EL));
    ok(/grande1/.test(r2.u), `${f}: y se puede elegir de su web como antes`);
  }
  ok(errores.length === 0, `sin errores en la página ${errores.join(' | ')}`);

  // ---- Pinterest de verdad (si hay red)
  console.log('Pinterest de verdad');
  const ctx = await b.newContext({ viewport: { width: 800, height: 1100 }, isMobile: true, hasTouch: true, locale: 'es-ES',
    userAgent: 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36' });
  const v = await ctx.newPage();
  let red = true;
  try { await v.goto('https://www.pinterest.com/search/pins/?q=Coiling+Dragon', { timeout: 30000, waitUntil: 'domcontentloaded' }); await v.waitForTimeout(2500); } catch { red = false; }
  if (!red) console.log('  (sin red: no se prueba)');
  else {
    await v.evaluate(busca('pinterest', 'Coiling Dragon'));
    await v.waitForFunction(() => document.querySelectorAll('#atrio-rej img').length > 0, null, { timeout: 15000 }).catch(() => {});
    const k = await n(v, '#atrio-rej img');
    ok(k > 0, `la web real devuelve pines en la rejilla propia (${k})`);
    if (k > 0) {
      await v.evaluate(() => document.querySelectorAll('#atrio-rej > div:nth-child(2) > div')[0].click());
      await v.waitForTimeout(2500);
      const r4 = JSON.parse(await v.evaluate(EL));
      ok(/i\.pinimg\.com\/(736x|originals)\//.test(r4.u), `al abrir un pin se ve el grande: ${r4.u}`);
      await v.screenshot({ path: path.join(out, 'rejilla-3-pinterest-real.png') });
    }
  }
  await b.close(); srv.close();
  console.log(fallos ? `FALLAN ${fallos}` : 'Todo bien');
  process.exit(fallos ? 1 : 0);
})();
