#!/usr/bin/env node
'use strict';
// El brillo del lector: el gesto del borde izquierdo y la barra del panel empiezan en el brillo de verdad (en automático,
// el del móvil), la franja del borde es estrecha y, con el menú abierto, deslizar no toca el brillo.
// Uso: node herramientas/lector/prueba-brillo.js libro.epub
const http = require('http');
const fs = require('fs');
const path = require('path');
const { chromium } = require(process.env.PW || 'playwright');
const ASSETS = path.resolve(__dirname, '../../movil/app/src/main/assets');
const MIME = { html: 'text/html', js: 'text/javascript', mjs: 'text/javascript', css: 'text/css' };
const [libro] = process.argv.slice(2);
const srv = http.createServer((req, res) => {
  const u = decodeURIComponent(new URL(req.url, 'http://x').pathname);
  let f = null;
  if (u.startsWith('/assets/')) f = path.join(ASSETS, u.slice(8));
  if (u.startsWith('/libro/')) f = libro;
  if (!f || !fs.existsSync(f)) { res.writeHead(404); return res.end('no'); }
  res.writeHead(200, { 'Content-Type': MIME[path.extname(f).slice(1)] || 'application/octet-stream' });
  fs.createReadStream(f).pipe(res);
});
const ok = (c, m) => { console.log((c ? '  ✔ ' : '  ✘ ') + m); if (!c) process.exitCode = 1; };
(async () => {
  await new Promise((r) => srv.listen(0, r));
  const browser = await chromium.launch({ executablePath: process.env.CHROME || undefined });
  const ctx = await browser.newContext({ viewport: { width: 412, height: 860 }, deviceScaleFactor: 2, hasTouch: true, isMobile: true });
  const page = await ctx.newPage();
  const errores = [];
  page.on('pageerror', (e) => errores.push(e.message));
  await page.addInitScript(() => {
    const st = { brillos: [] };
    window.__b = st;
    window.Atrio = new Proxy({
      state: () => JSON.stringify({ title: 'Prueba', file: 'l.epub', settings: { brightness: -1, brVer: 2 }, annotations: [], bookmarks: [], stats: {}, accent: { light: '#C8372D', dark: '#E0533F' } }),
      setBrightness: (v) => st.brillos.push(v),
      // el móvil está al 62 % de su barra
      brilloActual: () => 0.62,
      saveSettings: (j) => { st.ajustes = JSON.parse(j); },
    }, { get: (t, k) => (k in t ? t[k] : () => {}) });
  });
  await page.goto(`http://localhost:${srv.address().port}/assets/lector/index.html`);
  for (let k = 0; k < 120; k++) { await page.waitForTimeout(500); if (await page.$('#carga.fuera')) break; }
  await page.waitForTimeout(2500);   // el menú del principio se esconde solo
  const cdp = await ctx.newCDPSession(page);
  const desliza = async (x, y0, y1) => {
    await cdp.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x, y: y0 }] });
    const n = 12;
    for (let i = 1; i <= n; i++) { await page.waitForTimeout(16); await cdp.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ x, y: y0 + ((y1 - y0) * i) / n }] }); }
    await cdp.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
    await page.waitForTimeout(400);
  };
  const ultimo = () => page.evaluate(() => window.__b.brillos.at(-1));
  const cuantos = () => page.evaluate(() => window.__b.brillos.length);
  console.log('Brillo del lector');
  const menuAbierto = await page.$eval('#abajo', (e) => !e.classList.contains('oculta'));
  if (menuAbierto) { await page.evaluate(() => document.querySelector('#velo')?.click()); await page.waitForTimeout(500); }
  // 1. Deslizar hacia abajo por el borde, en automático: baja desde el 62 % del móvil (no desde el 50 %)
  const antes = await cuantos();
  await desliza(10, 300, 420);
  const b1 = await ultimo();
  const esperado = 0.62 - 120 / (860 * 0.7);
  ok((await cuantos()) > antes && Math.abs(b1 - esperado) < 0.05, `deslizar 120 px por el borde: del 62 % del móvil al ${Math.round(b1 * 100)} % (≈ ${Math.round(esperado * 100)} %)`);
  // 2. Fuera de la franja (a 60 px del borde) no se toca el brillo
  let n0 = await cuantos();
  await desliza(60, 300, 450);
  ok((await cuantos()) === n0, 'a 60 px del borde, deslizar no cambia el brillo');
  // 3. Con el menú abierto, deslizar hacia abajo (para cerrarlo) no toca el brillo
  await page.evaluate(() => window.atrioMenu ? window.atrioMenu() : null);
  await desliza(206, 600, 380);   // abre el menú
  const abierto = await page.$eval('#abajo', (e) => !e.classList.contains('oculta'));
  n0 = await cuantos();
  await desliza(10, 300, 460);
  ok(abierto && (await cuantos()) === n0, `con el menú abierto, deslizar por el borde no toca el brillo (menú: ${abierto})`);
  // 4. El panel de brillo: la barra está donde está el brillo de ahora
  await page.evaluate(() => { document.querySelector('#b-brillo')?.click(); });
  await page.waitForTimeout(400);
  const barra = await page.$eval('#br', (e) => parseFloat(e.value));
  ok(Math.abs(barra - b1) < 0.011, `la barra del panel está en el brillo de ahora (${barra})`);
  await page.evaluate(() => document.querySelector('#br-auto')?.click()); await page.waitForTimeout(300);
  const barra2 = await page.$eval('#br', (e) => parseFloat(e.value));
  ok(Math.abs(barra2 - 0.62) < 0.011 && (await ultimo()) < 0, `en automático, la barra se pone donde está la del móvil (${barra2}) y se le deja el brillo al móvil`);
  ok(errores.length === 0, errores.length ? 'errores: ' + errores.join(' | ') : 'sin errores en la página');
  await browser.close(); srv.close();
})();
