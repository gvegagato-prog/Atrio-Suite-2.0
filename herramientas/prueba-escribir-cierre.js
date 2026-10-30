#!/usr/bin/env node
'use strict';
// Escribir: lo último que se teclea está guardado al cerrar la ventana o salir de Atrio (⌘W, ⌘Q), aunque el editor espere
// 600 ms antes de guardar solo, y al cerrar queda una copia de seguridad con ello. (Guarda contra regresiones: con el
// código de antes lo tecleado también se guardaba; lo que no había era la copia ni la espera.) Atrio de verdad (Electron bajo Xvfb).
// Con SOLO=1, la edición del Mac. Uso (desde la raíz): node herramientas/prueba-escribir-cierre.js [carpeta]
const { spawn } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { chromium } = require(process.env.PW || 'playwright');

const ROOT = path.resolve(__dirname, '..');
const DESK = path.join(ROOT, 'escritorio');
const OUT = path.resolve(process.argv[2] || fs.mkdtempSync(path.join(os.tmpdir(), 'esc-cierre-')));
fs.mkdirSync(OUT, { recursive: true });
const PORT = 9400 + Math.floor(Math.random() * 200);
const FB = `http://127.0.0.1:${PORT}`;
const HOME = path.join(OUT, 'home');
const COMO = process.env.COMO === 'salir' ? 'salir' : 'ventana';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let fallos = 0;
const ok = (c, m) => { console.log((c ? '  ✔ ' : '  ✘ ') + m); if (!c) fallos++; };
const j = async (method, url, body) => {
  const r = await fetch(url, { method, headers: body === undefined ? {} : { 'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body) });
  const t = await r.text(); try { return JSON.parse(t); } catch { return t; }
};
async function hasta(f, ms = 15000, paso = 250) { const fin = Date.now() + ms; while (Date.now() < fin) { const v = await f().catch(() => null); if (v) return v; await sleep(paso); } return null; }
const leeEscena = (id) => { try { return JSON.parse(fs.readFileSync(path.join(HOME, '.local', 'share', 'atrio', 'escritos', 'd', `${id}.json`), 'utf8')); } catch { return null; } };

(async () => {
  fs.rmSync(HOME, { recursive: true, force: true });
  const CONF = path.join(HOME, '.config', 'atrio');
  fs.mkdirSync(CONF, { recursive: true });
  const fb = spawn(process.execPath, [path.join(__dirname, 'fakefirebase.js'), String(PORT)], { stdio: 'ignore' });
  await sleep(700);
  const su = await j('POST', `${FB}/v1/accounts:signUp?key=prueba`, { email: 'gonzalo@ronron.example.com', password: 'secreto1', returnSecureToken: true });
  await j('PUT', `${FB}/config/webKey.json?auth=${su.idToken}`, 'prueba');
  fs.writeFileSync(path.join(CONF, 'ronron.json'), JSON.stringify({ db: FB, authBase: FB, webKey: 'prueba', uid: su.localId, user: 'gonzalo', refresh: su.refreshToken, dev: 'atrio-prueba', seen: {} }));
  fs.writeFileSync(path.join(CONF, 'config.json'), JSON.stringify({ sources: { steam: false, heroic: false, lutris: false, emulators: false, custom: false }, customGames: [], behavior: { autostart: 'off', fullscreen: false, onLaunch: 'none' } }));
  const env = { ...process.env, ATRIO_PRUEBA_CIERRE: '1', ATRIO_HOME: HOME, ATRIO_RONRON_DB: FB, ATRIO_RONRON_AUTH: FB, ATRIO_RONRON_EVERY: '3000', ATRIO_NO_SANDBOX: '1', ATRIO_POWER_DRY: '1' };
  if (process.env.SOLO === '1') env.ATRIO_SOLO_REVISTA = '1';
  for (const k of ['XDG_CONFIG_HOME', 'XDG_DATA_HOME', 'XDG_CACHE_HOME', 'XDG_CURRENT_DESKTOP', 'WAYLAND_DISPLAY']) delete env[k];
  const electron = path.join(DESK, 'node_modules', 'electron', 'dist', 'electron');
  const CDP = 9700 + Math.floor(Math.random() * 200);
  const logs = [];
  const atrio = spawn('xvfb-run', ['-a', '-s', '-screen 0 1700x1000x24', electron, '--no-sandbox', '--ventana', '--revista', `--remote-debugging-port=${CDP}`, DESK], { env, stdio: ['ignore', 'pipe', 'pipe'], detached: true });
  atrio.stdout.on('data', (d) => logs.push(String(d))); atrio.stderr.on('data', (d) => logs.push(String(d)));
  let cerrado = false; atrio.on('exit', () => { cerrado = true; });
  let navegador = null;
  try {
    const abierto = await hasta(async () => { navegador = await chromium.connectOverCDP(`http://127.0.0.1:${CDP}`); return navegador.contexts()[0].pages().find((x) => !/devtools/.test(x.url())) || null; }, 60000, 1000);
    ok(!!abierto, 'Atrio abierto');
    const p = navegador.contexts()[0].pages().find((x) => !/devtools/.test(x.url()));
    await p.setViewportSize({ width: 1500, height: 950 });
    await hasta(() => p.evaluate(() => !!(window.RV && window.RV.E && window.atrio && window.atrio.escritos)), 30000);
    const A = (op, ...a) => p.evaluate(([o, x]) => window.atrio.escritos(o, ...x), [op, a]);
    const o = await A('nuevo', 'p', '', { titulo: 'Novela de prueba' });
    const c = await A('nuevo', 'c', o.id, { titulo: 'Capítulo 1' });
    const e = await A('nuevo', 'e', o.id, { cid: c.id, titulo: 'Escena 1', texto: 'Empieza así.' });
    await p.evaluate(() => { try { window.RV.cambiarModo('revista'); } catch (e) { /* ya está */ } });
    await sleep(600);
    // Copias de seguridad: la pantalla de Escribir las enseña, se hace una a mano y se saca una obra como obra nueva
    await p.evaluate(() => window.RV.ir({ v: 'escribir' }));
    await p.locator('.e-ir-papelera', { hasText: /Copias de seguridad/i }).click();
    await p.locator('text=Todavía no hay ninguna copia').waitFor({ timeout: 8000 }).catch(() => {});
    await p.getByRole('button', { name: /Hacer una copia ahora/i }).click();
    await p.locator('.e-pap').first().waitFor({ timeout: 8000 });
    ok(/1 obra/.test(await p.locator('.e-pap').first().innerText()), 'la pantalla de copias enseña la copia hecha (1 obra)');
    await p.screenshot({ path: path.join(OUT, 'copias.png') });
    await p.locator('.e-pap button', { hasText: /Ver las obras/i }).first().click();
    await p.locator('.e-copia-obra').first().waitFor({ timeout: 8000 });
    await p.locator('.e-copia-obra button').first().click();
    await hasta(() => p.evaluate(() => window.atrio.escritosTodo().then((x) => x.docs.filter((d) => d.t === 'p').length === 2)), 8000, 300);
    const obras = await p.evaluate(() => window.atrio.escritosTodo().then((x) => x.docs.filter((d) => d.t === 'p').map((d) => d.titulo)));
    ok(obras.length === 2 && obras.some((t) => /Novela de prueba \(copia del/.test(t)), `sacada como obra nueva (${obras.join(' | ')})`);
    await sleep(500);
    await p.evaluate((id) => window.RV.ir({ v: 'obra', id }), o.id);
    // (la obra en cuadrados: se entra al capítulo y de ahí a la escena)
    await hasta(() => p.evaluate(() => document.querySelectorAll('.e-cuadro-cap').length > 0), 15000, 300);
    await p.screenshot({ path: path.join(OUT, 'obra.png') });
    await p.locator('.e-cuadro-cap').first().click();
    await hasta(() => p.evaluate(() => document.querySelectorAll('.e-cuadro-esc').length > 0), 15000, 300);
    await p.locator('.e-cuadro-esc').first().click();
    await p.locator('.ed-texto').waitFor({ timeout: 15000 });
    await p.locator('.ed-texto').click();
    await p.keyboard.press('Control+End');
    const FRASE = ' Y esto se escribió justo antes de cerrar, con ñ y «comillas».';
    // Se teclea y se cierra la ventana AL MOMENTO (sin esperar los 600 ms que tarda en guardarse solo)
    await p.keyboard.type(FRASE, { delay: 5 });
    const antes = leeEscena(e.id);
    ok(antes && !antes.texto.includes('justo antes de cerrar'), 'justo al terminar de teclear, aún no está guardado en el disco (el editor espera un rato)');
    // (por el camino de siempre: ⌘W cierra la ventana y ⌘Q sale; así se prueba cada una)
    p.evaluate((como) => window.atrio.pruebaCierre(como), COMO).catch(() => {});
    const salio = await hasta(async () => (cerrado ? true : null), 15000, 200);
    ok(!!salio, COMO === 'salir' ? 'Atrio sale' : 'Atrio se cierra solo al cerrar la ventana');
    const despues = leeEscena(e.id);
    ok(despues && despues.texto.endsWith(FRASE), 'lo último que se tecleó está guardado en el disco');
    const copias = path.join(HOME, '.local', 'share', 'atrio', 'escritos-copias');
    const lista = fs.existsSync(copias) ? fs.readdirSync(copias).filter((f) => /^copia-.*\.json$/.test(f)) : [];
    ok(lista.length >= 1, `y al cerrar queda una copia de seguridad (${lista.length})`);
    const ult = lista.sort().pop();
    const copia = ult && JSON.parse(fs.readFileSync(path.join(copias, ult), 'utf8'));
    ok(copia && copia.docs.some((d) => d.id === e.id && d.texto.endsWith(FRASE)), 'que ya lleva esa última frase');
  } catch (err) { console.log('ERROR', err); fallos++; } finally {
    try { process.kill(-atrio.pid, 'SIGTERM'); } catch { atrio.kill('SIGTERM'); }
    await sleep(1200); fb.kill();
    fs.writeFileSync(path.join(OUT, 'atrio.log'), logs.join(''));
    console.log(fallos ? `\n${fallos} comprobaciones fallidas (registro en ${OUT}/atrio.log)` : '\nTodo bien');
    process.exit(fallos ? 1 : 0);
  }
})();
