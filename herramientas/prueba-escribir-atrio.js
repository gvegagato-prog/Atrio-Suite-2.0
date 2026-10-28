#!/usr/bin/env node
'use strict';
// Escribir en Atrio de verdad (Electron bajo Xvfb) con la cuenta en el Firebase de mentira: lo que se escribe en Atrio
// sube solo; lo que sube «el móvil» (la base de datos, con el mismo formato) llega en directo; y sin conexión se sigue
// escribiendo y, al volver la red, sube solo. Uso (desde la raíz): node herramientas/prueba-escribir-atrio.js [carpeta]
const { spawn } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { chromium } = require(process.env.PW || 'playwright');

const ROOT = path.resolve(__dirname, '..');
const DESK = path.join(ROOT, 'escritorio');
const OUT = path.resolve(process.argv[2] || fs.mkdtempSync(path.join(os.tmpdir(), 'esc-atrio-')));
fs.mkdirSync(OUT, { recursive: true });
const PORT = 9500 + Math.floor(Math.random() * 200);
const FB = `http://127.0.0.1:${PORT}`;
const HOME = path.join(OUT, 'home');
const STATE = path.join(OUT, 'fb.json');
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let fallos = 0;
const ok = (c, m) => { console.log((c ? '  ✔ ' : '  ✘ ') + m); if (!c) fallos++; };
const j = async (method, url, body) => {
  const r = await fetch(url, { method, headers: body === undefined ? {} : { 'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body) });
  const t = await r.text(); try { return JSON.parse(t); } catch { return t; }
};
async function hasta(f, ms = 15000, paso = 250) { const fin = Date.now() + ms; while (Date.now() < fin) { const v = await f().catch(() => null); if (v) return v; await sleep(paso); } return null; }
const fake = () => spawn(process.execPath, [path.join(__dirname, 'fakefirebase.js'), String(PORT), STATE], { stdio: 'ignore' });

(async () => {
  fs.rmSync(HOME, { recursive: true, force: true }); fs.rmSync(STATE, { force: true });
  fs.mkdirSync(path.join(HOME, '.config', 'atrio'), { recursive: true });
  let fb = fake(); await sleep(700);
  const su = await j('POST', `${FB}/v1/accounts:signUp?key=prueba`, { email: 'gonzalo@ronron.example.com', password: 'secreto1', returnSecureToken: true });
  const uid = su.localId; const tok = su.idToken;
  const db = (m, p, b) => j(m, `${FB}${p}.json?auth=${tok}`, b);
  await db('PUT', '/config/webKey', 'prueba');
  fs.writeFileSync(path.join(HOME, '.config', 'atrio', 'ronron.json'), JSON.stringify({ db: FB, authBase: FB, webKey: 'prueba', uid, user: 'gonzalo', refresh: su.refreshToken, dev: 'atrio-prueba', seen: {} }));
  fs.writeFileSync(path.join(HOME, '.config', 'atrio', 'config.json'), JSON.stringify({ sources: { steam: false, heroic: false, lutris: false, emulators: false, custom: true }, customGames: [], behavior: { autostart: 'off', fullscreen: false, onLaunch: 'none' }, ui: 'revista' }));
  const env = { ...process.env, ATRIO_HOME: HOME, ATRIO_RONRON_DB: FB, ATRIO_RONRON_AUTH: FB, ATRIO_RONRON_EVERY: '3000', ATRIO_SUITE_BEAT: '4000', ATRIO_NO_SANDBOX: '1', ATRIO_POWER_DRY: '1' };
  for (const k of ['XDG_CONFIG_HOME', 'XDG_DATA_HOME', 'XDG_CACHE_HOME', 'XDG_CURRENT_DESKTOP', 'WAYLAND_DISPLAY']) delete env[k];
  const electron = path.join(DESK, 'node_modules', 'electron', 'dist', 'electron');
  const CDP = 9700 + Math.floor(Math.random() * 200);
  const logs = [];
  const atrio = spawn('xvfb-run', ['-a', '-s', '-screen 0 1700x1000x24', electron, '--no-sandbox', '--ventana', '--revista', `--remote-debugging-port=${CDP}`, DESK], { env, stdio: ['ignore', 'pipe', 'pipe'], detached: true });
  atrio.stdout.on('data', (d) => logs.push(String(d))); atrio.stderr.on('data', (d) => logs.push(String(d)));
  let navegador = null;
  try {
    const conectado = await hasta(async () => { navegador = await chromium.connectOverCDP(`http://127.0.0.1:${CDP}`); const pg = navegador.contexts()[0].pages().find((x) => !/devtools/.test(x.url())); return pg || null; }, 60000, 500);
    ok(!!conectado, 'Atrio abierto');
    const p = navegador.contexts()[0].pages().find((x) => !/devtools/.test(x.url()));
    await p.setViewportSize({ width: 1500, height: 950 });
    await hasta(() => p.evaluate(() => !!(window.RV && window.RV.E && window.atrio && window.atrio.escritos)), 30000);
    const A = (op, ...a) => p.evaluate(([o, x]) => window.atrio.escritos(o, ...x), [op, a]);
    const todo = () => p.evaluate(() => window.atrio.escritosTodo());

    console.log('Lo que se escribe en Atrio sube solo');
    const o = await A('nuevo', 'p', '', { titulo: 'Desde Atrio', meta: 50000, diaria: 400 });
    const c = await A('nuevo', 'c', o.id, { titulo: 'Capítulo 1' });
    const s = await A('nuevo', 'e', o.id, { cid: c.id, titulo: 'Escena 1', texto: 'Escrito en el PC.' });
    const sube = await hasta(async () => { const d = await db('GET', `/users/${uid}/escritos/${s.id}`); return d && d.texto === 'Escrito en el PC.' ? d : null; }, 8000);
    ok(!!sube, 'la escena llega a la cuenta en unos segundos');
    const idx = await db('GET', `/users/${uid}/escritosIdx`);
    ok(idx && idx[s.id] === sube.u && idx[o.id] && idx[c.id], 'y su índice (el que escucha el móvil)');
    ok((await todo()).estado.pendientes === 0, 'sin nada pendiente');

    console.log('Lo que sube el móvil llega en directo');
    const t0 = Date.now();
    const u = Date.now() + 5;
    await db('PATCH', `/users/${uid}`, {
      [`escritos/m_escena`]: { id: 'm_escena', t: 'e', pid: o.id, cid: c.id, titulo: 'Escrita en el móvil', texto: 'Hola desde el móvil, con ñ y «comillas».', orden: 99000, palabras: 7, u },
      [`escritosIdx/m_escena`]: u,
    });
    const llega = await hasta(async () => (await todo()).docs.find((d) => d.id === 'm_escena'), 8000, 150);
    ok(!!llega, `llega a Atrio en ${Date.now() - t0} ms, sin recargar nada`);
    ok(llega && llega.texto === 'Hola desde el móvil, con ñ y «comillas».', 'con su texto tal cual');

    console.log('Atrio por dentro (la revista de verdad)');
    await p.evaluate(() => { window.RV.cambiarModo('revista'); window.RV.ir({ v: 'obra', id: arguments[0] }); }).catch(() => {});
    await p.evaluate((id) => window.RV.ir({ v: 'obra', id }), o.id);
    await sleep(800);
    ok(await p.locator('.e-escena').count() === 2, 'el manuscrito enseña las dos escenas (la del PC y la del móvil)');
    await p.screenshot({ path: path.join(OUT, 'atrio-escribir-obra.png') });
    await p.evaluate((id) => window.RV.ir({ v: 'editor', id }), 'm_escena');
    await sleep(700);
    ok((await p.locator('.ed-texto').innerText()).includes('Hola desde el móvil'), 'el editor abre la escena del móvil');
    // El móvil cambia esa escena mientras está abierta
    const u2 = Date.now() + 5;
    await db('PATCH', `/users/${uid}`, { 'escritos/m_escena': { id: 'm_escena', t: 'e', pid: o.id, cid: c.id, titulo: 'Escrita en el móvil', texto: 'Hola desde el móvil, con ñ y «comillas». Y sigo escribiendo allí.', orden: 99000, palabras: 12, u: u2 }, 'escritosIdx/m_escena': u2 });
    const vivo = await hasta(async () => (await p.locator('.ed-texto').innerText()).includes('Y sigo escribiendo allí'), 8000, 200);
    ok(!!vivo, 'lo que escribe el móvil se ve al momento en el editor abierto');
    await p.screenshot({ path: path.join(OUT, 'atrio-escribir-editor.png') });

    console.log('Sin conexión');
    await j('GET', `${FB}/__offline?on=1`); await sleep(800);
    await p.locator('.ed-texto').click(); await p.keyboard.press('Control+End');
    await p.keyboard.type(' Escribo en el tren, sin cobertura.');
    await sleep(1500);
    const st1 = (await todo()).estado;
    ok(st1.pendientes >= 1, `sin red queda pendiente (${st1.pendientes}) y sigo escribiendo`);
    const guardadoLocal = (await todo()).docs.find((d) => d.id === 'm_escena').texto;
    ok(guardadoLocal.endsWith('Escribo en el tren, sin cobertura.'), 'el texto está guardado en este PC');
    await j('GET', `${FB}/__offline?on=0`); await sleep(500);
    const sube2 = await hasta(async () => { const d = await db('GET', `/users/${uid}/escritos/m_escena`); return d && d.texto.endsWith('Escribo en el tren, sin cobertura.') ? d : null; }, 40000, 500);
    ok(!!sube2, 'vuelve la red: sube solo');
    ok(await hasta(async () => (await todo()).estado.pendientes === 0, 10000), 'y ya no queda nada pendiente');

    console.log('Exportar y estado en la página');
    ok(!/Error|error/.test(logs.join('').split('\n').filter((l) => /escrib/i.test(l)).join('\n')), 'sin errores de Escribir en el registro');
  } catch (e) { console.error(e); fallos++; }
  try { if (navegador) await navegador.close(); } catch { /* */ }
  try { process.kill(-atrio.pid); } catch { /* */ }
  try { fb.kill(); } catch { /* */ }
  fs.writeFileSync(path.join(OUT, 'atrio.log'), logs.join(''));
  console.log(fallos ? `FALLAN ${fallos}` : 'Todo bien');
  process.exit(fallos ? 1 : 0);
})();
