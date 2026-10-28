#!/usr/bin/env node
'use strict';
// La maqueta de una ficha (la que se hace en el móvil: qué casilla va en cada fila, cuánto mide, sus textos y sus fotos)
// en Atrio de verdad (Electron bajo Xvfb), con la cuenta en el Firebase de mentira. Con SOLO=1, la edición del Mac.
// Uso (desde la raíz): node herramientas/prueba-maqueta-atrio.js [carpeta]
const { spawn } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { chromium } = require(process.env.PW || 'playwright');

const ROOT = path.resolve(__dirname, '..');
const DESK = path.join(ROOT, 'escritorio');
const OUT = path.resolve(process.argv[2] || fs.mkdtempSync(path.join(os.tmpdir(), 'maq-atrio-')));
fs.mkdirSync(OUT, { recursive: true });
const PORT = 9300 + Math.floor(Math.random() * 200);
const FB = `http://127.0.0.1:${PORT}`;
const HOME = path.join(OUT, 'home');
const SOLO = process.env.SOLO === '1';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let fallos = 0;
const ok = (c, m) => { console.log((c ? '  ✔ ' : '  ✘ ') + m); if (!c) fallos++; };
const j = async (method, url, body) => {
  const r = await fetch(url, { method, headers: body === undefined ? {} : { 'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body) });
  const t = await r.text(); try { return JSON.parse(t); } catch { return t; }
};
async function hasta(f, ms = 15000, paso = 250) { const fin = Date.now() + ms; while (Date.now() < fin) { const v = await f().catch(() => null); if (v) return v; await sleep(paso); } return null; }

(async () => {
  fs.rmSync(HOME, { recursive: true, force: true });
  const CONF = path.join(HOME, '.config', 'atrio');
  const DATA = path.join(HOME, '.local', 'share', 'atrio');
  fs.mkdirSync(CONF, { recursive: true }); fs.mkdirSync(path.join(DATA, 'fotos'), { recursive: true });
  // Dos fotos del móvil (ya bajadas, hechas aquí sin programas externos): una vertical (800×1000) con una franja roja arriba y
  // azul abajo, y otra apaisada
  const zlib = require('zlib');
  const crcT = Array.from({ length: 256 }, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
  const crc = (b) => { let c = 0xFFFFFFFF; for (const x of b) c = crcT[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xFFFFFFFF) >>> 0; };
  const trozo = (tipo, datos) => { const t = Buffer.concat([Buffer.from(tipo), datos]); const l = Buffer.alloc(4); l.writeUInt32BE(datos.length); const c = Buffer.alloc(4); c.writeUInt32BE(crc(t)); return Buffer.concat([l, t, c]); };
  const png = (nombre, w, h) => {
    const fila = (y) => { const b = Buffer.alloc(1 + w * 3); for (let x = 0; x < w; x++) { const rojo = y < h / 10; const azul = y >= h - h / 10; b[1 + x * 3] = rojo ? 255 : azul ? 0 : 128; b[2 + x * 3] = rojo ? 0 : azul ? 0 : 128; b[3 + x * 3] = azul ? 255 : rojo ? 0 : 128; } return b; };
    const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 2;
    const raw = Buffer.concat(Array.from({ length: h }, (_, y) => fila(y)));
    fs.writeFileSync(path.join(DATA, 'fotos', nombre), Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), trozo('IHDR', ihdr), trozo('IDAT', zlib.deflateSync(raw)), trozo('IEND', Buffer.alloc(0))]));
  };
  png('foto_1700000000001.png', 800, 1000); png('foto_1700000000002.png', 1000, 500);
  const fb = spawn(process.execPath, [path.join(__dirname, 'fakefirebase.js'), String(PORT)], { stdio: 'ignore' });
  await sleep(700);
  const su = await j('POST', `${FB}/v1/accounts:signUp?key=prueba`, { email: 'gonzalo@ronron.example.com', password: 'secreto1', returnSecureToken: true });
  const uid = su.localId; const tok = su.idToken;
  const db = (m, p, b) => j(m, `${FB}${p}.json?auth=${tok}`, b);
  await db('PUT', '/config/webKey', 'prueba');
  fs.writeFileSync(path.join(CONF, 'ronron.json'), JSON.stringify({ db: FB, authBase: FB, webKey: 'prueba', uid, user: 'gonzalo', refresh: su.refreshToken, dev: 'atrio-prueba', seen: {} }));
  fs.writeFileSync(path.join(CONF, 'config.json'), JSON.stringify({ sources: { steam: false, heroic: false, lutris: false, emulators: false, custom: false }, customGames: [], behavior: { autostart: 'off', fullscreen: false, onLaunch: 'none' } }));

  // Lo que «el móvil» tiene en su cuenta: tres fichas de videojuegos terminados y sus maquetas
  const ficha = (id, name) => ({ id, name, cover: '', hero: '', logo: '', creator: 'Supergiant', platform: 'PC', general: 91, gfx: 90, story: 92, music: 93, gameplay: 89, review: 'Un juegazo.', start: 19900, end: 20000, updated: Date.now() });
  const foto = (n) => `file:///data/user/0/com.gonzalo.gamerate/files/covers/${n}.png`;
  const filasPropia = [
    { s: 0.45, a: 0, c: [{ t: 'imagen', i: foto('foto_1700000000001'), z: 1.2, ox: 0, oy: 0.5, sh: 'llenar', as: 1.0 }, { t: 'nota' }] },
    { s: 0.5, a: 0, c: [{ t: 'texto', h: 'Mi veredicto', x: 'El mejor roguelike que he jugado.' }] },
    { s: 0.5, a: 200, c: [{ t: 'imagen', i: foto('foto_1700000000002'), z: 1, ox: 0.3, oy: 0.2, sh: 'llenar' }, { t: 'datos' }] },
    { s: 0.5, a: 0, c: [{ t: 'guias' }, { t: 'opinion' }] },
  ];
  const filasCategoria = [{ s: 0.5, a: 0, c: [{ t: 'opinion' }, { t: 'datos' }] }];
  const data = {
    games: [ficha(4242, 'Hades II'), ficha(4343, 'Otro juego'), ficha(4444, 'Un tercero')], plays: [], books: [], bplays: [], kinds: [], tombs: {}, kindsAt: 0, recs: [], reacts: {}, keys: {}, goals: {}, likes: {},
    maquetas: [
      { id: 1, k: 'v:0:4242', j: JSON.stringify(filasPropia), updated: Date.now() },
      { id: 2, k: 'tpl:v:0', j: JSON.stringify(filasCategoria), updated: Date.now() - 1000 },
      { id: 3, k: 'v:0:4444', j: JSON.stringify([{ s: 0.5, a: 0, c: [{ t: 'imagen', i: foto('foto_1700000000009'), as: 1.3 }] }]), updated: Date.now() },
    ],
  };
  // (4343 usa la de su categoría; 4444 también; una ficha de libros no tiene ninguna: la de siempre)
  await db('PUT', `/users/${uid}/dev/mtest`, { name: 'gonzalo', user: 'gonzalo', device: 'android', updated: { '.sv': 'timestamp' }, data: JSON.stringify(data) });
  await db('PATCH', `/users/${uid}`, { 'stamps/mtest': await db('GET', `/users/${uid}/dev/mtest/updated`) });

  const env = { ...process.env, ATRIO_HOME: HOME, ATRIO_RONRON_DB: FB, ATRIO_RONRON_AUTH: FB, ATRIO_RONRON_EVERY: '3000', ATRIO_NO_SANDBOX: '1', ATRIO_POWER_DRY: '1' };
  if (SOLO) env.ATRIO_SOLO_REVISTA = '1';
  for (const k of ['XDG_CONFIG_HOME', 'XDG_DATA_HOME', 'XDG_CACHE_HOME', 'XDG_CURRENT_DESKTOP', 'WAYLAND_DISPLAY']) delete env[k];
  const electron = path.join(DESK, 'node_modules', 'electron', 'dist', 'electron');
  const CDP = 9700 + Math.floor(Math.random() * 200);
  const logs = [];
  const atrio = spawn('xvfb-run', ['-a', '-s', '-screen 0 1700x1000x24', electron, '--no-sandbox', '--ventana', '--revista', `--remote-debugging-port=${CDP}`, DESK], { env, stdio: ['ignore', 'pipe', 'pipe'], detached: true });
  atrio.stdout.on('data', (d) => logs.push(String(d))); atrio.stderr.on('data', (d) => logs.push(String(d)));
  let navegador = null;
  try {
    const abierto = await hasta(async () => { navegador = await chromium.connectOverCDP(`http://127.0.0.1:${CDP}`); return navegador.contexts()[0].pages().find((x) => !/devtools/.test(x.url())) || null; }, 60000, 1000);
    ok(!!abierto, SOLO ? 'Atrio abierto (edición del Mac)' : 'Atrio abierto');
    const p = navegador.contexts()[0].pages().find((x) => !/devtools/.test(x.url()));
    await p.setViewportSize({ width: 1400, height: 1000 });
    const llega = await hasta(() => p.evaluate(() => !!(window.RV && window.RV.D && window.RV.D.buscar(window.RV.D.tipo(0), 'g', 4242))), 40000, 500);
    ok(!!llega, 'La ficha y su maqueta llegan de la cuenta');
    const ir = async (id, k = 0) => { await p.evaluate(([i, kk]) => window.RV.ir({ v: 'ficha', k: kk, t: 'g', id: i }), [id, k]); await sleep(1200); };

    console.log('La ficha con su propia maqueta');
    await ir(4242);
    await p.screenshot({ path: path.join(OUT, 'ficha-propia.png') });
    const filas = await p.evaluate(() => [...document.querySelectorAll('.casillas > .fila-cas')].map((f) => {
      const r = f.getBoundingClientRect();
      return { cls: f.className, h: r.height, w: r.width, cas: [...f.children].map((c) => { const b = c.getBoundingClientRect(); return { w: b.width, texto: c.innerText.slice(0, 40), img: !!c.querySelector('.maq-img img') }; }) };
    }));
    ok(filas.length === 4, `4 filas, como la maqueta (hay ${filas.length})`);
    ok(filas[0] && filas[0].cas.length === 2 && filas[0].cas[0].img, 'fila 1: la foto y la nota');
    ok(filas[0] && Math.abs(filas[0].cas[0].w / (filas[0].cas[0].w + filas[0].cas[1].w) - 0.45) < 0.03, `la línea de la fila 1 está donde la puso el móvil (${filas[0] ? (filas[0].cas[0].w / (filas[0].cas[0].w + filas[0].cas[1].w)).toFixed(2) : '-'} ≈ 0,45)`);
    ok(filas[1] && /Mi veredicto/i.test(filas[1].cas[0].texto), 'fila 2: el texto libre con su título');
    ok(filas[2] && /fija/.test(filas[2].cls) && Math.abs(filas[2].h - 200) <= 3, `fila 3: alto fijo de 200 (mide ${filas[2] ? Math.round(filas[2].h) : '-'})`);
    ok(filas[3] && filas[3].cas.length === 1 && /Opini/i.test(filas[3].cas[0].texto), 'fila 4: sin «Guías» (el ordenador aún no la tiene) queda solo la opinión');
    ok(await p.locator('.maq-nota').count() === 1 && /guías/i.test(await p.locator('.maq-nota').innerText()), 'y avisa de que falta «Guías»');

    console.log('Las imágenes encuadradas: sin hueco, como en el móvil');
    const imgs = await p.evaluate(() => [...document.querySelectorAll('.maq-img')].map((c) => {
      const i = c.querySelector('img'); const bc = c.getBoundingClientRect(); const bi = i ? i.getBoundingClientRect() : null;
      return { caja: { l: bc.left, t: bc.top, r: bc.right, b: bc.bottom, w: bc.width, h: bc.height }, img: bi && { l: bi.left, t: bi.top, r: bi.right, b: bi.bottom }, cargada: !!(i && i.naturalWidth) };
    }));
    ok(imgs.length === 2 && imgs.every((x) => x.cargada), 'las dos fotos del móvil (ya bajadas) se ven');
    const cubre = (x) => x.img && x.img.l <= x.caja.l + 1 && x.img.t <= x.caja.t + 1 && x.img.r >= x.caja.r - 1 && x.img.b >= x.caja.b - 1;
    ok(imgs.every(cubre), 'cada foto cubre toda su casilla (ningún hueco)');
    ok(Math.abs(imgs[0].caja.w / imgs[0].caja.h - 1.0) < 0.03, `la casilla de la foto tiene la forma que dejó el encuadre (${(imgs[0].caja.w / imgs[0].caja.h).toFixed(2)} ≈ 1,00)`);
    ok(Math.abs(imgs[0].img.t - imgs[0].caja.t) <= 1.5, 'foto pegada arriba (la de la franja roja): su borde de arriba está en el de la casilla, sin franja vacía');
    ok(Math.abs(imgs[1].caja.h - (filas[2].h - 14 - 28)) < 6 || imgs[1].caja.h > 100, `la foto de la fila de alto fijo llena la casilla (${Math.round(imgs[1].caja.h)} px)`);

    console.log('Una foto de la galería del móvil que aún no está aquí se puede pedir');
    await ir(4444);
    ok(await p.locator('.maq-img.sin-foto').count() === 1, 'se avisa de que la foto aún no se ha bajado (sin romper la página)');
    ok(/traerla del móvil/i.test(await p.locator('.maq-img.sin-foto button').innerText()), 'y ofrece «Traerla del móvil»');

    console.log('Una ficha sin maqueta propia usa la de su categoría');
    await ir(4343);
    const cat = await p.evaluate(() => [...document.querySelectorAll('.casillas > .fila-cas')].map((f) => f.innerText.replace(/\s+/g, ' ').slice(0, 50)));
    ok(cat.length === 1 && /Opini/i.test(cat[0]) && /Datos/i.test(cat[0]), `la de la categoría: opinión y datos en una fila (${cat.length} fila)`);
    ok(await p.locator('.maq-img').count() === 0, 'sin imágenes');
    await p.screenshot({ path: path.join(OUT, 'ficha-categoria.png') });
    // Se cambia la maqueta desde el «móvil»: llega y se pinta
    const nueva = [{ s: 0.5, a: 0, c: [{ t: 'texto', h: 'Cambiada', x: 'Desde el móvil.' }] }];
    data.maquetas.push({ id: 2, k: 'tpl:v:0', j: JSON.stringify(nueva), updated: Date.now() + 5 });
    data.maquetas = data.maquetas.filter((m, i, a) => !(m.id === 2 && a.findIndex((n) => n.id === 2 && n.updated > m.updated) >= 0));
    await db('PUT', `/users/${uid}/dev/mtest`, { name: 'gonzalo', user: 'gonzalo', device: 'android', updated: { '.sv': 'timestamp' }, data: JSON.stringify(data) });
    await db('PATCH', `/users/${uid}`, { 'stamps/mtest': await db('GET', `/users/${uid}/dev/mtest/updated`) });
    const t0 = Date.now();
    const cambiada = await hasta(async () => { await ir(4343); return (await p.locator('.casillas').innerText()).includes('Desde el móvil.') ? true : null; }, 20000, 800);
    ok(!!cambiada, `un cambio de maqueta hecho en el móvil llega y se pinta (${Date.now() - t0} ms)`);
  } catch (e) { console.log('ERROR', e); fallos++; } finally {
    try { await navegador.close(); } catch { /* */ }
    try { process.kill(-atrio.pid, 'SIGTERM'); } catch { atrio.kill('SIGTERM'); }
    await sleep(1500); fb.kill();
    fs.writeFileSync(path.join(OUT, 'atrio.log'), logs.join(''));
    console.log(fallos ? `\n${fallos} comprobaciones fallidas (capturas y registro en ${OUT})` : `\nTodo bien (capturas en ${OUT})`);
    process.exit(fallos ? 1 : 0);
  }
})();
