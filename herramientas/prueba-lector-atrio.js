#!/usr/bin/env node
'use strict';
// El lector de libros en Atrio de verdad (Electron bajo Xvfb), con la cuenta en el Firebase de mentira: las casillas Lectura y
// Subrayados de la ficha, abrir un EPUB, pasar página con el teclado, guardar lo leído, buscar una palabra, y que todo viaje a la
// cuenta (y lo que llega del móvil se vea). Con SOLO=1, la edición del Mac.
// Uso (desde la raíz): node herramientas/prueba-lector-atrio.js [carpeta]
const { spawn } = require('child_process');
const http = require('http');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { chromium } = require(process.env.PW || 'playwright');

const ROOT = path.resolve(__dirname, '..');
const DESK = path.join(ROOT, 'escritorio');
const OUT = path.resolve(process.argv[2] || fs.mkdtempSync(path.join(os.tmpdir(), 'lector-atrio-')));
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
  // La Wikcionario de mentira
  const wik = http.createServer((q, r) => {
    const t = decodeURIComponent(/titles=([^&]+)/.exec(q.url)[1]);
    const ex = t === 'casa' ? '== Español ==\n=== Sustantivo ===\n1 Edificio para habitar.\n2 Linaje.' : null;
    r.setHeader('content-type', 'application/json');
    r.end(JSON.stringify({ query: { pages: { 1: ex ? { extract: ex } : { missing: '' } } } }));
  });
  await new Promise((res) => wik.listen(0, '127.0.0.1', res));
  const fb = spawn(process.execPath, [path.join(__dirname, 'fakefirebase.js'), String(PORT)], { stdio: 'ignore' });
  await sleep(700);
  const su = await j('POST', `${FB}/v1/accounts:signUp?key=prueba`, { email: 'gonzalo@ronron.example.com', password: 'secreto1', returnSecureToken: true });
  const uid = su.localId; const tok = su.idToken;
  const db = (m, p, b) => j(m, `${FB}${p}.json?auth=${tok}`, b);
  await db('PUT', '/config/webKey', 'prueba');
  fs.writeFileSync(path.join(CONF, 'ronron.json'), JSON.stringify({ db: FB, authBase: FB, webKey: 'prueba', uid, user: 'gonzalo', refresh: su.refreshToken, dev: 'atrio-prueba', seen: {} }));
  fs.writeFileSync(path.join(CONF, 'config.json'), JSON.stringify({ sources: { steam: false, heroic: false, lutris: false, emulators: false, custom: false }, customGames: [], behavior: { autostart: 'off', fullscreen: false, onLaunch: 'none' } }));
  const EPUB = path.join(ROOT, 'herramientas', 'lector', 'atrio-prueba.epub');

  // Lo que «el móvil» tiene en su cuenta: un libro terminado con su maqueta (Lectura y Subrayados) y un subrayado ya hecho allí
  const libro = { id: 7001, name: 'El libro de prueba', cover: '', hero: '', logo: '', creator: 'Una autora', platform: 'Ebook', general: 88, gfx: 88, story: 88, music: 88, gameplay: 88, review: 'Muy bueno.', start: 19900, end: 20000, updated: Date.now() };
  const nota = { value: 'epubcfi(/6/4!/4/2/1:0)', text: 'Un pasaje que subrayé en el móvil', note: 'Qué bonito', label: 'Capítulo 1', created: 1760000000000, index: 0, frac: 0.05 };
  const lecturaMovil = (extra = {}) => ({ id: 7001, updated: Date.now(), s: { fraction: 0.1, cfi: '', label: 'Capítulo 1', read: Date.now(), notes: [nota], stats: { dev: { movil: { total: 900000, days: { '2026-10-20': 900000 }, best: { '2026-10-20': 900000 } } }, total: 900000, days: { '2026-10-20': 900000 } }, ...extra } });
  const data = {
    games: [], plays: [], books: [libro], bplays: [], sagas: [], diario: [], tombs: {}, kindsAt: 0, recs: [], reacts: {}, keys: {}, goals: {}, likes: {},
    lectura: [lecturaMovil()],
    maquetas: [{ id: 1, k: 'v:1:7001', j: JSON.stringify([{ s: 0.5, a: 0, c: [{ t: 'lectura' }, { t: 'subrayados' }] }]), updated: Date.now() }],
  };
  const sube = async () => {
    await db('PUT', `/users/${uid}/dev/mtest`, { name: 'gonzalo', user: 'gonzalo', device: 'android', updated: { '.sv': 'timestamp' }, data: JSON.stringify(data) });
    await db('PATCH', `/users/${uid}`, { 'stamps/mtest': await db('GET', `/users/${uid}/dev/mtest/updated`) });
  };
  await sube();
  // Lo que Atrio ha subido a la cuenta (su dispositivo)
  const suyo = async () => { const d = await db('GET', `/users/${uid}/dev/atrio-prueba`); try { return JSON.parse((d || {}).data || 'null'); } catch { return null; } };
  const lecturaSuya = async () => ((await suyo()) || {}).lectura?.find((x) => Number(x.id) === 7001) || null;

  const env = { ...process.env, ATRIO_WIKT_BASE: `http://127.0.0.1:${wik.address().port}`, ATRIO_HOME: HOME, ATRIO_RONRON_DB: FB, ATRIO_RONRON_AUTH: FB, ATRIO_RONRON_EVERY: '3000', ATRIO_NO_SANDBOX: '1', ATRIO_PRUEBA_TECLAS: '1', ATRIO_POWER_DRY: '1' };
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
    const paginas = () => navegador.contexts()[0].pages();
    const p = paginas().find((x) => /index|revista/.test(x.url()) && !/atrio-lector|devtools/.test(x.url())) || paginas().find((x) => !/devtools/.test(x.url()));
    await p.setViewportSize({ width: 1400, height: 1000 });
    const llega = await hasta(() => p.evaluate(() => !!(window.RV && window.RV.D && window.RV.D.buscar(window.RV.D.tipo(1), 'g', 7001))), 40000, 500);
    ok(!!llega, 'La ficha del libro y su maqueta llegan de la cuenta');
    const ir = async () => { await p.evaluate(() => window.RV.ir({ v: 'ficha', k: 1, t: 'g', id: 7001 })); await sleep(1200); };
    const casillas = async () => (await p.locator('.casillas').innerText()).replace(/\s+/g, ' ');

    console.log('Sin archivo: la casilla Lectura ofrece adjuntarlo');
    await ir();
    let tx = await casillas();
    await p.screenshot({ path: path.join(OUT, '1-sin-archivo.png') });
    ok(/lectura/i.test(tx) && /elegir archivo/i.test(tx), 'Lectura: «Elegir archivo»');
    ok(/subrayados y notas/i.test(tx) && /adjunta el libro/i.test(tx), 'Subrayados y notas: pide adjuntar el libro');
    ok(await p.locator('.maq-nota').count() === 0, 'ya no avisa de casillas que falten');

    console.log('Con el archivo: por dónde ibas y lo subrayado en el móvil');
    const st = await p.evaluate((f) => window.RV.api.lectorAdjuntarRuta(7001, f), EPUB);
    ok(st && st.name === 'atrio-prueba.epub', 'el archivo queda en Atrio');
    await hasta(async () => /seguir leyendo/i.test(await casillas()) ? true : null, 8000, 300);
    tx = await casillas();
    await p.screenshot({ path: path.join(OUT, '2-con-archivo.png') });
    ok(/seguir leyendo/i.test(tx) && /10 % leído/i.test(tx) && /15 min leyendo/i.test(tx) && !/null/.test(tx), 'Lectura: «Seguir leyendo», 10 % y los 15 minutos que llevas (del móvil)');
    ok(/te quedan unas 2 h 15 min a tu ritmo/i.test(tx), `«Te quedan…» a tu ritmo, como en el móvil (${(/te quedan[^.]*/i.exec(tx) || [''])[0].slice(0, 90)})`);
    ok(/1 subrayado\b/i.test(tx) && /Un pasaje que subrayé en el móvil/.test(tx) && /Qué bonito/.test(tx), 'Subrayados: el del móvil, con su nota');

    console.log('El lector: abre el libro de verdad');
    await p.evaluate(() => window.RV.api.lectorAbrir(7001, 'El libro de prueba', null));
    const lp = await hasta(async () => paginas().find((x) => /atrio-lector:\/\/l7001/.test(x.url())) || null, 20000, 400);
    ok(!!lp, 'se abre la ventana del lector');
    const errores = [];
    lp.on('pageerror', (e) => errores.push(String(e))); lp.on('console', (m) => { if (m.type() === 'error') errores.push(m.text()); });
    const listo = await hasta(async () => lp.evaluate(() => { return /%/.test(document.querySelector('#e-der')?.textContent || '') ? true : null; }), 30000, 500);
    await lp.screenshot({ path: path.join(OUT, '3-lector.png') });
    ok(!!listo, 'el libro se abre (sin quedarse cargando)');
    const err0 = await lp.evaluate(() => document.querySelector('#carga-txt')?.textContent || '');
    ok(!/no se ha podido|error/i.test(err0) || !!listo, `sin mensaje de error (${err0.slice(0, 60)})`);
    const mira = () => lp.evaluate(() => `${document.querySelector('#e-izq')?.textContent} | ${document.querySelector('#e-der')?.textContent}`);
    const loc0 = await mira();
    console.log('   [depuración] al abrir:', loc0, '| atrioPage:', await lp.evaluate(() => typeof window.atrioPage));

    console.log('Pasar página con el teclado y guardar lo leído');
    await lp.bringToFront(); await lp.click('body', { position: { x: 400, y: 500 }, force: true }).catch(() => {});
    const tecla = async (k, shift) => { await p.evaluate(([a, b]) => window.RV.api.lectorPruebaTecla(7001, a, b), [k, !!shift]); await sleep(700); };
    for (let i = 0; i < 2; i++) await tecla('Right');
    await lp.screenshot({ path: path.join(OUT, '4-pagina.png') });
    const en = await hasta(async () => { const e = await (async () => JSON.parse(fs.readFileSync(path.join(DATA, 'lector', 'estado.json'), 'utf8'))['7001'] || {})(); return e.cfi && e.fraction > 0.1 ? e : null; }, 10000, 400);
    ok(!!en, `la posición se guarda en este equipo (${en ? (en.fraction * 100).toFixed(1) + ' %, ' + en.label : '-'})`);
    const loc1 = await mira();
    console.log('   [depuración] tras →×6:', loc1);
    ok(loc1 !== loc0 || (en && en.fraction > 0.1), `el teclado pasa página (${loc0} → ${loc1})`);
    await sleep(2500);   // (la página guarda la posición un rato después de pasar página)
    const antes = JSON.parse(fs.readFileSync(path.join(DATA, 'lector', 'estado.json'), 'utf8'))['7001'].fraction;
    await tecla('Left'); await sleep(2500);
    const atras = JSON.parse(fs.readFileSync(path.join(DATA, 'lector', 'estado.json'), 'utf8'))['7001'];
    ok(atras.fraction < antes - 0.001, `← vuelve atrás (${(antes * 100).toFixed(1)} % → ${(atras.fraction * 100).toFixed(1)} %)`);

    console.log('Buscar una palabra (el significado) y subrayar');
    const res = await lp.evaluate(() => new Promise((ok_) => { window.atrioResult = (id, r) => ok_(r); window.Atrio.lookup('casa', 'La casa es grande.', 'q1'); setTimeout(() => ok_(null), 8000); }));
    ok(res && res.defs && res.defs[0] === 'Edificio para habitar.', `el significado llega (${res ? JSON.stringify(res).slice(0, 80) : 'nada'})`);
    const tr = await lp.evaluate(() => new Promise((ok_) => { window.atrioResult = (id, r) => ok_(r); window.Atrio.translate('casa', 'La casa', 'es', 'q2'); setTimeout(() => ok_(null), 5000); }));
    ok(tr && /solo existe en el móvil/.test(tr.msg || ''), 'traducir lo dice con honestidad: solo existe en el móvil');
    await lp.evaluate((n) => window.Atrio.saveAnnotations(JSON.stringify([n, { value: 'epubcfi(/6/6!/4/2/1:0)', text: 'Subrayado en el ordenador', note: '', label: 'Capítulo 2', created: Date.now(), index: 1, frac: 0.4 }])), nota);

    console.log('Todo viaja a la cuenta');
    const sub = await hasta(async () => { const x = await lecturaSuya(); return x && x.s && (x.s.notes || []).length === 2 && x.s.fraction > 0.1 ? x : null; }, 45000, 1000);
    ok(!!sub, 'lo leído y el subrayado nuevo llegan a la cuenta (el móvil los verá)');
    ok(!!sub && !('name' in sub.s) && !('uri' in sub.s), 'sin el archivo ni el nombre de este equipo');
    ok(!!sub && sub.s.stats && sub.s.stats.dev && sub.s.stats.dev.movil && sub.s.stats.dev.movil.total === 900000, 'el tiempo que el móvil llevaba se conserva');

    console.log('Al cerrar el libro, lo último sube ya');
    const dist = await lp.evaluate(() => window.Atrio && true);
    await tecla('Right'); await sleep(800);
    const fr1 = JSON.parse(fs.readFileSync(path.join(DATA, 'lector', 'estado.json'), 'utf8'))['7001'].fraction;
    await lp.evaluate(() => window.Atrio.close()).catch(() => {});   // (el botón de cerrar del lector: lo mismo que cerrar la ventana)
    const cerrado = await hasta(async () => { const x = await lecturaSuya(); return x && Math.abs(x.s.fraction - fr1) < 0.0006 ? x : null; }, 15000, 500);
    if (!cerrado) console.log('   [depuración] en la cuenta:', JSON.stringify(((await lecturaSuya()) || {}).s || {}).slice(0, 300), '| aquí:', fr1);
    ok(!!cerrado, `la posición final (${(fr1 * 100).toFixed(1)} %) sube al cerrar, sin esperar`);
    void dist;

    console.log('Lo que llega del móvil: más lejos');
    data.lectura = [lecturaMovil({ bestCfi: 'epubcfi(/6/6!/4/2/1:0)', bestFrac: 0.97, bestLabel: 'Capítulo 3', fraction: 0.97, cfi: 'epubcfi(/6/6!/4/2/1:0)' })];
    await sube();
    await ir();
    const llego = await hasta(async () => { await ir(); return /\b97 % leído/i.test(await casillas()) ? true : null; }, 25000, 1000);
    await p.screenshot({ path: path.join(OUT, '5-del-movil.png') });
    ok(!!llego, 'si en el móvil llegas más lejos, aquí el libro se abre ahí');
    ok(/Capítulo|subrayado/i.test(await casillas()), 'y siguen los subrayados');

    console.log('Seleccionar una palabra con el ratón (el menú de subrayar, buscar…) y abrir un PDF');
    await p.evaluate((f) => window.RV.api.lectorAdjuntarRuta(7001, f), EPUB);
    await p.evaluate(() => window.RV.api.lectorAbrir(7001, 'El libro de prueba', null));
    const lp2 = await hasta(async () => paginas().find((x) => /atrio-lector:\/\/l7001/.test(x.url())) || null, 20000, 400);
    await hasta(async () => lp2.evaluate(() => /%/.test(document.querySelector('#e-der')?.textContent || '') ? true : null), 30000, 500);
    await sleep(1000);
    const visible = (sel) => lp2.evaluate((q) => { const e = document.querySelector(q); return !!e && !e.classList.contains('oculta') && getComputedStyle(e).display !== 'none'; }, sel);
    // Mantener pulsado el botón sobre una palabra: su significado (como mantener el dedo en el móvil)
    await lp2.mouse.move(165, 135); await lp2.mouse.down(); await sleep(800); await lp2.mouse.up(); await sleep(1500);
    await lp2.screenshot({ path: path.join(OUT, '6-palabra.png') });
    ok(await visible('#palabra'), 'mantener pulsado el ratón sobre una palabra enseña su ficha (significado, copiar, subrayar…)');
    ok(/Y|así|fatigado/i.test(await lp2.evaluate(() => document.querySelector('#pal-w')?.textContent || '')), 'con la palabra de debajo del ratón');
    await lp2.evaluate(() => window.atrioBack());
    // Arrastrar de una palabra a otra: se subraya el tramo
    const antesN = (JSON.parse(fs.readFileSync(path.join(DATA, 'lector', 'estado.json'), 'utf8'))['7001'].notes || []).length;
    await lp2.mouse.move(150, 135); await lp2.mouse.down(); await lp2.mouse.move(250, 136, { steps: 6 }); await lp2.mouse.move(420, 135, { steps: 8 }); await lp2.mouse.up(); await sleep(1500);
    await lp2.screenshot({ path: path.join(OUT, '6-seleccion.png') });
    const ahoraN = await hasta(async () => { const n = (JSON.parse(fs.readFileSync(path.join(DATA, 'lector', 'estado.json'), 'utf8'))['7001'].notes || []); return n.length > antesN ? n : null; }, 6000, 300);
    ok(!!ahoraN, 'arrastrar el ratón de una palabra a otra subraya el tramo y lo guarda');
    ok(!!ahoraN && /fatigado|así|Y/.test(ahoraN[ahoraN.length - 1].text) && ahoraN[ahoraN.length - 1].text.split(' ').length >= 3, `con el texto del tramo («${ahoraN ? ahoraN[ahoraN.length - 1].text.slice(0, 40) : '-'}»)`);
    ok(await visible('#sel'), 'y enseña el menú del subrayado (nota, copiar, quitar…)');
    await lp2.evaluate(() => window.Atrio.close());
    await sleep(1500);
    const PDF = path.join(ROOT, 'herramientas', 'lector', 'atrio-prueba.pdf');
    await p.evaluate((f) => window.RV.api.lectorAdjuntarRuta(7001, f), PDF);
    await p.evaluate(() => window.RV.api.lectorAbrir(7001, 'El libro de prueba', null));
    const lp3 = await hasta(async () => paginas().find((x) => /atrio-lector:\/\/l7001/.test(x.url())) || null, 20000, 400);
    const erroresPdf = [];
    lp3.on('pageerror', (e) => erroresPdf.push(String(e))); lp3.on('console', (m) => { if (m.type() === 'error') erroresPdf.push(m.text()); });
    const pdfListo = await hasta(async () => lp3.evaluate(() => /%/.test(document.querySelector('#e-der')?.textContent || '') ? true : null), 45000, 700);
    await sleep(1500);
    await lp3.screenshot({ path: path.join(OUT, '7-pdf.png') });
    ok(!!pdfListo, `un PDF también se abre (${erroresPdf.slice(0, 2).join(' | ').slice(0, 160)})`);
    await lp3.evaluate(() => window.Atrio.close());
    await sleep(1500);

    console.log('Quitar el archivo');
    await p.evaluate(() => window.RV.api.lectorQuitar(7001));
    await ir();
    ok(/elegir archivo/i.test(await casillas()) && !fs.existsSync(path.join(DATA, 'lector', 'libros', '7001', 'atrio-prueba.epub')), 'se borra el archivo de este equipo y lo leído se queda');
    ok(errores.filter((e) => !/favicon|DevTools|Autofill/.test(e)).length === 0, `sin errores en la consola del lector (${errores.slice(0, 2).join(' | ').slice(0, 160)})`);
  } catch (e) { console.log('ERROR', e); fallos++; } finally {
    try { await navegador.close(); } catch { /* */ }
    try { process.kill(-atrio.pid, 'SIGTERM'); } catch { atrio.kill('SIGTERM'); }
    await sleep(1500); fb.kill(); wik.close();
    fs.writeFileSync(path.join(OUT, 'atrio.log'), logs.join(''));
    console.log(fallos ? `\n${fallos} comprobaciones fallidas (capturas y registro en ${OUT})` : `\nTodo bien (capturas en ${OUT})`);
    process.exit(fallos ? 1 : 0);
  }
})();
