#!/usr/bin/env node
'use strict';
// Escribir con tu pareja en el PC/Mac, de punta a punta: dos cuentas en el Firebase de mentira (con sus reglas), el
// módulo real (escritorio/src/main/escritos-pareja.js) y la revista real en Chromium.
// Yo marco y comparto; mi pareja lo ve en su Atrio, lo lee y lo anota; yo veo su nota. Lo no compartido no le llega.
// Uso (desde la raíz): node herramientas/prueba-escribir-pareja.js [carpeta-capturas]
const http = require('http');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { spawn } = require('child_process');
const { chromium } = require(process.env.PW || 'playwright');
const { crear } = require('../escritorio/src/main/escritos');
const PAREJA = require('../escritorio/src/main/escritos-pareja');

const RAIZ = path.join(__dirname, '../escritorio/src/renderer/revista');
const OUT = path.resolve(process.argv[2] || fs.mkdtempSync(path.join(os.tmpdir(), 'pareja-')));
fs.mkdirSync(OUT, { recursive: true });
const port = 9700 + Math.floor(Math.random() * 200);
const base = `http://127.0.0.1:${port}`;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let fallos = 0;
const ok = (c, m) => { console.log((c ? '  ✔ ' : '  ✘ ') + m); if (!c) fallos++; };
const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.ttf': 'font/ttf', '.png': 'image/png' };

async function pide(method, url, body) {
  const r = await fetch(url, { method, headers: body !== undefined ? { 'Content-Type': 'application/json' } : {}, body: body !== undefined ? JSON.stringify(body) : undefined });
  const t = await r.text();
  return { code: r.status, json: t && t !== 'null' ? JSON.parse(t) : null };
}
async function cuenta(nombre) {
  const su = await pide('POST', `${base}/v1/accounts:signUp?key=k`, { email: `${nombre}${Date.now()}@ronron.example.com`, password: 'secreto1' });
  const c = { uid: su.json.localId, token: su.json.idToken };
  await pide('PUT', `${base}/users/${c.uid}/name.json?auth=${c.token}`, nombre);
  return c;
}
/** Lo que el módulo usa de ronron.js, con la cuenta [c] (y las mismas respuestas: null si no hay permiso). */
const ronronDe = (c) => ({
  uid: () => c.uid,
  db: async (method, p, body, query, okDenied = true) => {
    const r = await pide(method, `${base}${p}.json?auth=${c.token}${query ? `&${query}` : ''}`, body);
    if (r.code === 401 || r.code === 403) { if (okDenied) return null; throw new Error('sin permiso'); }
    if (r.code < 200 || r.code > 299) throw new Error(`Firebase respondió ${r.code}`);
    return r.json;
  },
});
/** Los ipc del módulo para la cuenta [c] (como los registra main.js). */
function moduloDe(c) {
  const h = {};
  PAREJA.iniciar({ ronron: ronronDe(c), ipcMain: { handle: (k, f) => { h[k] = (...a) => f(null, ...a); } } });
  return h;
}

/** Una página de la revista con mis escritos [escr] y el módulo de la pareja [mod]. */
async function pagina(b, escr, mod, nombre) {
  const srv = http.createServer((q, r) => {
    if (q.url.startsWith('/favicon.ico')) { r.writeHead(204); return r.end(); }
    const f = path.join(RAIZ, decodeURIComponent(new URL(q.url, 'http://x').pathname));
    if (!f.startsWith(RAIZ) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { r.writeHead(404); return r.end(); }
    r.writeHead(200, { 'content-type': MIME[path.extname(f)] || 'application/octet-stream' }); r.end(fs.readFileSync(f));
  }).listen(0);
  const ctx = await b.newContext({ viewport: { width: 1400, height: 900 }, locale: 'es-ES' });
  const p = await ctx.newPage();
  const errores = [];
  p.on('pageerror', (e) => errores.push(e.message));
  p.on('console', (m) => { if (m.type() === 'error' && !/^Failed to load resource/.test(m.text())) errores.push(m.text()); });
  await p.exposeFunction('__escr', async (op, ...a) => { const r = escr[op](...a); return r === undefined ? null : r; });
  await p.exposeFunction('__todo', async () => ({ docs: escr.todos(), estado: { ...escr.estado(), signed: true } }));
  await p.exposeFunction('__pareja', async (k, ...a) => mod[k](...a));
  escr.onChange(() => { p.evaluate((x) => window.__emit && window.__emit('escritos', x), { docs: escr.todos(), estado: { ...escr.estado(), signed: true } }).catch(() => {}); });
  await p.addInitScript(() => {
    const cb = {};
    window.__emit = (c, x) => (cb[c] || []).forEach((f) => f(x));
    window.atrio = new Proxy({
      platform: 'linux',
      on: (c, f) => { (cb[c] = cb[c] || []).push(f); },
      state: async () => ({ config: { revista: { tema: 'claro' } }, games: [], play: { sessions: [], active: [] } }),
      ronron: async () => ({ status: { signed: false }, data: null }),
      fotoList: async () => ({}),
      escritosTodo: () => window.__todo(),
      escritos: (op, ...a) => window.__escr(op, ...a),
      parejaInfo: () => window.__pareja('pareja:info'),
      parejaCompartido: () => window.__pareja('pareja:compartido'),
      parejaMisNotas: () => window.__pareja('pareja:misNotas'),
      parejaNotasDeMiPareja: () => window.__pareja('pareja:notasDeMiPareja'),
      parejaAnota: (n) => window.__pareja('pareja:anota', n),
      parejaBorraNota: (id) => window.__pareja('pareja:borraNota', id),
      log: async () => {},
    }, { get: (t, k) => (k in t ? t[k] : async () => null) });
  });
  await p.goto(`http://127.0.0.1:${srv.address().port}/index.html`);
  await p.waitForFunction(() => window.RV && window.RV.E);
  await p.evaluate(() => window.RV.cambiarModo('revista'));
  p.__errores = errores; p.__nombre = nombre;
  return p;
}

(async () => {
  const fb = spawn('node', [path.join(__dirname, 'fakefirebase.js'), String(port)], { stdio: 'ignore' });
  try {
    await sleep(800);
    console.log('Lo que se comparte (sin interfaz)');
    const docs = [
      { id: 'p_1', t: 'p', titulo: 'Guadaña azabache', meta: 90000 },
      { id: 'a_1', t: 'a', pid: 'p_1', titulo: 'Segunda parte', comp: 1 },
      { id: 'c_1', t: 'c', pid: 'p_1', titulo: 'El juramento', comp: 1, hecho: 1 },
      { id: 'c_2', t: 'c', pid: 'p_1', titulo: 'Secreto' },
      { id: 'c_3', t: 'c', pid: 'p_1', titulo: 'La corte', aid: 'a_1' },
      { id: 'e_1', t: 'e', pid: 'p_1', cid: 'c_1', texto: 'Uno.' },
      { id: 'e_2', t: 'e', pid: 'p_1', cid: 'c_2', texto: 'No lo leas.' },
      { id: 'n_1', t: 'n', pid: 'p_1', texto: 'privada' },
      { id: 'c_4', t: 'c', pid: 'p_1', titulo: 'Borrado', comp: 1, d: 1 },
    ];
    const sc = PAREJA.soloLoCompartido(docs);
    ok(sc.map((o) => o.id).sort().join() === 'a_1,c_1,c_3,e_1,p_1', `solo lo compartido (${sc.map((o) => o.id).sort().join()})`);
    ok(!('meta' in sc.find((o) => o.id === 'p_1')), 'de la obra no va la meta');

    console.log('Dos cuentas enlazadas');
    const yo = await cuenta('Gonzalo'); const ella = await cuenta('Laura');
    await pide('PUT', `${base}/users/${yo.uid}/partner.json?auth=${yo.token}`, ella.uid);
    await pide('PUT', `${base}/users/${ella.uid}/partner.json?auth=${ella.token}`, yo.uid);
    const escrYo = crear({ dir: fs.mkdtempSync(path.join(os.tmpdir(), 'par-yo-')), dev: 'pc', net: { uid: () => yo.uid, db: ronronDe(yo).db } });
    escrYo.cuenta && escrYo.cuenta(yo.uid);
    const escrElla = crear({ dir: fs.mkdtempSync(path.join(os.tmpdir(), 'par-ella-')), dev: 'mac', net: { uid: () => ella.uid, db: ronronDe(ella).db } });
    const o = escrYo.nuevo('p', '', { titulo: 'Guadaña azabache' });
    const c1 = escrYo.nuevo('c', o.id, { titulo: 'El juramento' });
    const c2 = escrYo.nuevo('c', o.id, { titulo: 'Secreto' });
    escrYo.nuevo('e', o.id, { cid: c1.id, titulo: 'Escena 2', texto: 'Presencia de esas frágiles cadenas mágicas.\n\n-Fausto, Lord Fausto- dijo.' });
    escrYo.nuevo('e', o.id, { cid: c2.id, texto: 'Esto no lo debe leer nadie.' });

    const b = await chromium.launch().catch(() => chromium.launch({ channel: 'chrome' }));
    const modYo = moduloDe(yo); const modElla = moduloDe(ella);

    console.log('Yo: marco terminado y comparto el capítulo');
    const pYo = await pagina(b, escrYo, modYo, 'yo');
    await pYo.evaluate((id) => window.RV.ir({ v: 'capitulo', id }), c1.id); await sleep(900);
    await pYo.evaluate(() => window.RV.refrescar()); await sleep(400);
    ok(await pYo.locator('.e-marcas button', { hasText: 'Compartir con Laura' }).count() === 1, 'el capítulo ofrece «Compartir con Laura» (el nombre de su cuenta)');
    await pYo.locator('.e-marcas button', { hasText: 'Marcar como terminado' }).click(); await sleep(400);
    await pYo.locator('.e-marcas button', { hasText: 'Compartir con Laura' }).click(); await sleep(400);
    ok(escrYo.get(c1.id).hecho === 1 && escrYo.get(c1.id).comp === 1, 'se guardan «hecho» y «comp» en el capítulo');
    ok(await pYo.locator('.e-marcas button[aria-pressed="true"]').count() === 2, 'los dos botones quedan marcados');
    await pYo.screenshot({ path: path.join(OUT, 'pareja-1-capitulo.png') });
    await pYo.evaluate((id) => window.RV.ir({ v: 'obra', id, tab: 0 }), o.id); await sleep(600);
    ok(/Terminado · Compartido/i.test(await pYo.locator('.e-cuadro-cap', { hasText: 'El juramento' }).innerText()), 'el cuadro del capítulo dice «Terminado · Compartido»');
    await escrYo.sincroniza();

    console.log('Ella: lo ve, lo lee y lo anota');
    const pElla = await pagina(b, escrElla, modElla, 'ella');
    await pElla.evaluate(() => window.RV.ir({ v: 'escribir' })); await sleep(1500);
    await pElla.evaluate(() => window.RV.refrescar()); await sleep(500);
    ok(await pElla.locator('.e-te-comparte').count() === 1 && /Te comparte Gonzalo/i.test(await pElla.locator('.e-te-comparte').innerText()), 'en Escribir sale «Te comparte Gonzalo»');
    await pElla.screenshot({ path: path.join(OUT, 'pareja-2-te-comparte.png') });
    await pElla.locator('.e-te-comparte').click(); await sleep(800);
    const lista = await pElla.locator('.e-cuadro-cap').allInnerTexts();
    ok(lista.length === 1 && /El juramento/.test(lista[0]) && /Terminado/i.test(lista[0]), `solo el capítulo compartido, y terminado (${lista.join(' | ').replace(/\n/g, ' ')})`);
    ok(!(await pElla.content()).includes('no lo debe leer'), 'lo no compartido no aparece');
    await pElla.screenshot({ path: path.join(OUT, 'pareja-3-de-gonzalo.png') });
    await pElla.locator('.e-cuadro-cap').click(); await sleep(500);
    { const n = await pElla.locator('.e-anotable').count(); ok(n === 2, `se lee con sus dos párrafos anotables (${n})`); }
    await pElla.locator('.e-anotable', { hasText: 'Lord Fausto' }).click(); await sleep(300);
    await pElla.locator('.e-anotar textarea').fill('Aquí me ha dado un escalofrío');
    await pElla.screenshot({ path: path.join(OUT, 'pareja-4-anotar.png') });
    await pElla.locator('.dialogo.e-anotar button', { hasText: 'Guardar' }).click(); await sleep(600);
    ok(await pElla.locator('.e-mi-nota').count() === 1 && /escalofrío/.test(await pElla.locator('.e-mi-nota').innerText()), 'su nota sale bajo el párrafo');
    await pElla.screenshot({ path: path.join(OUT, 'pareja-5-nota.png') });
    const enSuCuenta = (await pide('GET', `${base}/users/${ella.uid}/notasLectura/${yo.uid}.json?auth=${ella.token}`)).json;
    const n = Object.values(enSuCuenta || {})[0];
    ok(n && n.cid === c1.id && n.p === 1 && /Fausto, Lord Fausto/.test(n.cita), 'la nota se guarda en SU cuenta, con el capítulo, el párrafo y la cita');
    // Ella no puede escribir en mi cuenta (las reglas): la nota no puede ir allí
    const intruso = await pide('PUT', `${base}/users/${yo.uid}/notasLectura/x.json?auth=${ella.token}`, { texto: 'x' });
    ok(intruso.code === 401 || intruso.code === 403, `las reglas no la dejan escribir en mi cuenta (${intruso.code})`);

    console.log('Yo: veo su nota en el capítulo');
    await pYo.evaluate((id) => window.RV.ir({ v: 'capitulo', id }), c1.id); await sleep(1200);
    ok(await pYo.locator('.e-nota-pareja').count() === 1 && /escalofrío/.test(await pYo.locator('.e-nota-pareja').innerText()) && /Lord Fausto/.test(await pYo.locator('.e-nota-pareja').innerText()), 'el capítulo enseña «Lo que te anota Laura» con la cita');
    await pYo.screenshot({ path: path.join(OUT, 'pareja-6-lo-que-te-anota.png') });

    console.log('Yo dejo de compartir: a ella ya no le llega');
    await pYo.locator('.e-marcas button', { hasText: 'Compartido con Laura' }).click(); await sleep(400);
    await escrYo.sincroniza();
    const r2 = await modElla['pareja:compartido']();
    ok(r2.docs.filter((x) => x.t === 'c').length === 0, 'sin capítulos compartidos');

    for (const p of [pYo, pElla]) ok(!p.__errores.length, `sin errores en la página (${p.__nombre}) ${p.__errores.join(' | ')}`);
    await b.close();
  } finally { fb.kill(); }
  console.log(fallos ? `${fallos} fallos` : 'Todo bien');
  process.exit(fallos ? 1 : 0);
})().catch((e) => { console.error(e); process.exit(1); });
