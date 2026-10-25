#!/usr/bin/env node
'use strict';
// El brillo en el lector: no tiene brillo propio (usa el del móvil). Deslizar por el borde izquierdo empieza en el brillo
// del móvil y, al soltar, lo pone en el móvil (con permiso) o solo mientras lees (sin él, y avisa con «Permitir»). La
// franja del borde es estrecha y, con el menú abierto, deslizar no toca el brillo.
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
    const st = { brillos: [], movil: [], permiso: false, pedido: 0 };
    window.__b = st;
    window.Atrio = new Proxy({
      // (un brillo guardado de antes, del 20 %: ya no se usa)
      state: () => JSON.stringify({ title: 'Prueba', file: 'l.epub', settings: { brightness: 0.2, brVer: 2 }, annotations: [], bookmarks: [], stats: {}, accent: { light: '#C8372D', dark: '#E0533F' } }),
      setBrightness: (v) => st.brillos.push(v),
      // el móvil está al 62 % de su barra (o donde lo haya puesto el lector)
      brilloActual: () => st.actual ?? 0.62,
      brilloMovil: (v) => { st.movil.push(v); if (!st.permiso) return 'permiso'; st.actual = v; return 'ok'; },
      pedirPermisoBrillo: () => { st.pedido++; },
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
  // 0. Sin brillo propio: al abrir no pone ninguno (el 20 % guardado de antes no vale), y no hay botón «Luz»
  ok((await page.evaluate(() => window.__b.brillos.filter((v) => v >= 0).length)) === 0 && !(await page.$('#b-brillo')), 'al abrir, el brillo es el del móvil (ningún brillo propio ni botón «Luz»)');
  // 1. Sin permiso: deslizar baja desde el 62 % del móvil; al soltar, se le pide al móvil, no deja, y se queda solo
  //    mientras lees, con un aviso para dar el permiso
  const antes = await cuantos();
  await desliza(10, 300, 420);
  const b1 = await ultimo();
  const esperado = 0.62 - 120 / (860 * 0.7);
  const m1 = await page.evaluate(() => window.__b.movil.at(-1));
  ok((await cuantos()) > antes && Math.abs(b1 - esperado) < 0.05 && Math.abs(m1 - b1) < 0.001, `deslizar 120 px por el borde: del 62 % del móvil al ${Math.round(b1 * 100)} % (≈ ${Math.round(esperado * 100)} %), y se le pide al móvil`);
  await page.waitForTimeout(1200);
  const avisoPermiso = await page.$eval('#aviso', (e) => e.classList.contains('on') && /permiso/.test(e.textContent) && /Permitir/.test(e.textContent));
  ok(avisoPermiso, 'sin permiso, avisa con «Permitir»');
  if (avisoPermiso) { await page.click('#aviso button'); await page.waitForTimeout(200); }
  ok((await page.evaluate(() => window.__b.pedido)) === 1, '«Permitir» abre los ajustes de Android para el permiso');
  // 2. El siguiente desliz empieza donde lo dejaste (aunque el móvil no lo tenga)
  await page.evaluate(() => { window.__b.permiso = true; });
  await desliza(10, 300, 360);
  const b2 = await ultimo();
  ok(Math.abs(b2 - (b1 - 60 / (860 * 0.7))) < 0.05, `el siguiente desliz sigue desde ahí (${Math.round(b2 * 100)} %)`);
  // 3. Con permiso, al soltar va al brillo del móvil
  ok(Math.abs((await page.evaluate(() => window.__b.actual)) - b2) < 0.001, `con permiso, el brillo queda en el móvil (${Math.round(b2 * 100)} %)`);
  // 4. Fuera de la franja (a 60 px del borde) no se toca el brillo
  let n0 = await cuantos();
  await desliza(60, 300, 450);
  ok((await cuantos()) === n0, 'a 60 px del borde, deslizar no cambia el brillo');
  // 5. Con el menú abierto, deslizar hacia abajo (para cerrarlo) no toca el brillo
  await page.evaluate(() => window.atrioMenu ? window.atrioMenu() : null);
  await desliza(206, 600, 380);   // abre el menú
  const abierto = await page.$eval('#abajo', (e) => !e.classList.contains('oculta'));
  n0 = await cuantos();
  await desliza(10, 300, 460);
  ok(abierto && (await cuantos()) === n0, `con el menú abierto, deslizar por el borde no toca el brillo (menú: ${abierto})`);
  // 6. La luz cálida está ahora en «Letra»
  await page.evaluate(() => document.querySelector('#b-ajustes').click()); await page.waitForTimeout(500);
  ok(!!(await page.$('#panel-cuerpo #warm')), 'la luz cálida está en el panel «Letra»');
  // 7. Lo guardado ya no lleva brillo del lector
  const aj = await page.evaluate(() => window.__b.ajustes);
  ok(!aj || !(aj.brightness >= 0), `no se guarda ningún brillo del lector (${aj ? aj.brightness : 'sin guardar'})`);
  ok(errores.length === 0, errores.length ? 'errores: ' + errores.join(' | ') : 'sin errores en la página');
  await browser.close(); srv.close();
})();
