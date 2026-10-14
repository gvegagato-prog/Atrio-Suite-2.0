'use strict';
// La suite: Atrio y Ronrón conectados en tiempo real a través de tu cuenta (Firebase Realtime Database).
//
//   /users/<uid>/suite/live/<disp>        quién está conectado y a qué se juega ahora mismo (cada dispositivo el suyo;
//                                         el PC añade «st»: su Polaris para el streaming)
//   /users/<uid>/suite/lib/<disp>         la biblioteca de cada PC (para lanzar juegos desde el móvil)
//   /users/<uid>/suite/cmd/<disp>/<id>    órdenes para un dispositivo: { op, a, t, by } → él responde en «res»
//   /users/<uid>/suite/shots/<id>         capturas: datos de la foto (los trozos van en /users/<uid>/shotdata/<id>)
//   /users/<uid>/stamps                   (lo de siempre) se escucha para mezclar al momento lo que cambie el móvil
//
// Todo cuelga de /users/<uid>, así que las reglas de la base de datos de siempre ya lo cubren.
const fs = require('fs');
const os = require('os');
const path = require('path');
const crypto = require('crypto');
const { watch } = require('./stream');
const host = require('./polaris');
const { XDG_DATA, readJson, mkdirp } = require('./paths');

const CHUNK = 512 * 1024;           // caracteres de base64 por trozo (Firebase admite hasta 10 MB por texto)
const ONLINE_MS = 3 * 60 * 1000;    // sin latido en este tiempo = desconectado
const CMD_MAX_AGE = 10 * 60 * 1000; // órdenes más viejas se ignoran (el PC estaba apagado)
const SHOT_KEEP = 7 * 86400000;     // los trozos de una captura que nadie ha bajado se borran a la semana
const INDEX = path.join(XDG_DATA, 'atrio', 'capturas.json');

let ctx = null;          // lo que da main.js
let rr = null;           // módulo ronron
let suiteWatch = null;
let stampsWatch = null;
let tree = {};           // copia de /users/<uid>/suite
let heart = null;
let lastLive = '';
let lastLib = '';
let lastUid = null;
let conn = 'desconectado';
const busy = new Set();
const done = new Set();

const log = (...a) => { try { ctx.log('suite:', ...a); } catch { /* */ } };
const now = () => Date.now();
const me = () => rr.deviceId();
const base = () => `/users/${rr.uid()}`;

// ---------------------------------------------------------------- Capturas guardadas en este PC
let index = null;
function idx() { if (!index) index = readJson(INDEX, null) || { shots: [] }; return index; }
function saveIdx() { try { mkdirp(path.dirname(INDEX)); fs.writeFileSync(INDEX, JSON.stringify(idx())); } catch { /* */ } }
function localShots(key) { return idx().shots.filter((s) => !key || s.key === key).sort((a, b) => b.t - a.t); }
function shotFile(id) { const s = idx().shots.find((x) => x.id === id); return s && fs.existsSync(s.file) ? s.file : null; }
function forgetShot(id) {
  const s = idx().shots.find((x) => x.id === id);
  index.shots = idx().shots.filter((x) => x.id !== id);
  saveIdx();
  return s;
}

function picturesDir() {
  const cfg = ctx.config().suite || {};
  if (cfg.shotDir) return cfg.shotDir;
  try { return path.join(require('electron').app.getPath('pictures'), 'Atrio'); } catch { return path.join(os.homedir(), 'Atrio'); }
}
const safe = (s) => String(s || 'Escritorio').replace(/[\\/:*?"<>|]+/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 80) || 'Escritorio';

// ---------------------------------------------------------------- Escritura en la nube
async function put(p, v, q = 'print=silent') { return rr.db('PUT', `${base()}${p}`, v, q, false); }
async function patch(p, v) { return rr.db('PATCH', `${base()}${p}`, v, 'print=silent', false); }
async function del(p) { return rr.db('DELETE', `${base()}${p}`, undefined, '', true); }

// ---------------------------------------------------------------- Presencia: a qué se juega ahora
function livePayload() {
  const g = ctx.current();
  return {
    app: 'atrio', name: os.hostname(), os: process.platform, v: ctx.version(), on: true,
    game: g || null,
    today: ctx.today ? ctx.today() : 0,
    shot: (ctx.config().suite || {}).remoteShot !== false,
    st: streamPayload(),
  };
}

// Streaming: lo que necesita el móvil para abrir Nova en este PC (y encenderlo por red)
function streamPayload() {
  const i = host.info();
  if (!i.installed) return { on: false, linux: !!i.linux };
  const ts = i.ts || {};
  return {
    on: true, run: !!i.running, name: i.name || os.hostname(), uuid: i.uuid || '', app: i.app || 'Desktop', appUuid: i.appUuid || '',
    lan: i.ips || [], mac: i.macs || [], ts: ts.up ? ts.ip : '', tsn: ts.up ? (ts.dns || '') : '', tsi: !!ts.installed,
    creds: !!i.creds, ok: (ctx.config().stream || {}).remote !== false, host: 'polaris',
  };
}
async function pushLive(force) {
  if (!rr.uid()) return;
  const p = livePayload();
  const txt = JSON.stringify(p);
  if (!force && txt === lastLive) return;
  try {
    await put(`/suite/live/${me()}`, { ...p, at: { '.sv': 'timestamp' } });
    lastLive = txt;
  } catch (e) { log('presencia', e.message); }
}

async function pushLib() {
  if (!rr.uid()) return;
  const games = ctx.library().map((g) => {
    const o = { k: g.key, n: g.name };
    if (g.cover) o.c = g.cover;
    if (g.sg) o.sg = g.sg;
    if (g.fav) o.f = 1;
    if (g.lp) o.lp = g.lp;
    if (g.src) o.s = g.src;
    if (g.min) o.m = g.min;
    if (g.links && g.links.length) o.w = g.links;
    return o;
  });
  const txt = JSON.stringify(games);
  if (txt === lastLib) return;
  try {
    await put(`/suite/lib/${me()}`, { at: { '.sv': 'timestamp' }, name: os.hostname(), games });
    lastLib = txt;
  } catch (e) { log('biblioteca', e.message); }
}

// Dispositivos conectados (el móvil, otros PC)
function devices() {
  const live = tree.live || {};
  return Object.entries(live).filter(([d]) => d !== me()).map(([d, v]) => ({
    dev: d, app: v.app, name: v.name, os: v.os, game: v.game || null, at: Number(v.at) || 0,
    online: !!v.on && now() - (Number(v.at) || 0) < ONLINE_MS,
  }));
}

// ---------------------------------------------------------------- Órdenes que llegan del móvil
const HANDLERS = {
  async shot(a, by) {
    const r = await takeAndUpload({ by, reason: 'móvil', ...(a || {}) });
    return { ok: true, msg: `Captura de ${r.w}×${r.h}`, shot: r.id };
  },
  async launch(a) {
    if ((ctx.config().suite || {}).remoteLaunch === false) throw new Error('En Atrio no está permitido lanzar juegos desde el móvil.');
    const g = await ctx.launch(a && a.key);
    return { ok: true, msg: `Abriendo ${g.name}` };
  },
  async power(a) {
    if ((ctx.config().suite || {}).remotePower === false) throw new Error('En Atrio no está permitido apagar el equipo desde el móvil.');
    // La orden ya está marcada como recibida («ack»): si el equipo se apaga antes de contestar, al encenderlo no se repite.
    // Se espera al resultado para que el móvil sepa si de verdad se está apagando o qué lo impide.
    await ctx.power(a && a.action);
    return { ok: true, msg: ({ apagar: 'Apagando', reiniciar: 'Reiniciando', suspender: 'Suspendiendo' })[a && a.action] || 'Hecho' };
  },
  async focus() { ctx.focus(); return { ok: true, msg: 'Atrio en primer plano' }; },
  async page(a) { ctx.focus(); ctx.send('remote', { op: 'page', key: a && a.key }); return { ok: true, msg: 'Abierto en Atrio' }; },
  async toast(a, by) { ctx.send('notice', `${by || 'Móvil'}: ${String((a && a.text) || '').slice(0, 200)}`); return { ok: true, msg: 'Enseñado en la pantalla' }; },
  async rescan() { await ctx.rescan(); return { ok: true, msg: 'Biblioteca actualizada' }; },
  // Abrir en el PC la página del juego con una de sus webs
  async guide(a) {
    ctx.focus();
    ctx.send('remote', { op: 'guide', key: a && a.key, url: a && a.url });
    return { ok: true, msg: 'Abierta en el PC' };
  },
  // Jugar en streaming: Polaris en marcha y (si viene) el juego lanzado; el móvil abre Nova con lo que devuelve
  async stream(a) {
    if ((ctx.config().stream || {}).remote === false) throw new Error('En Atrio no está permitido preparar el streaming desde el móvil.');
    const i = await host.startService();
    let msg = 'Polaris listo';
    if (a && a.key) {
      const cur = ctx.current();
      if (cur && cur.key === a.key) msg = `${cur.name} ya está en marcha`;
      else {
        if ((ctx.config().suite || {}).remoteLaunch === false) throw new Error('En Atrio no está permitido lanzar juegos desde el móvil.');
        const g = await ctx.launch(a.key);
        msg = `Abriendo ${g.name}`;
      }
    }
    const appUuid = i.appUuid || (await host.appUuid());
    return { ok: true, msg, name: i.name, uuid: i.uuid || '', app: i.app || 'Desktop', appUuid };
  },
  // Emparejar con Nova: con un código de un solo uso (auto: el móvil se lo pasa a Nova) o con el PIN que enseña Nova
  async pair(a, by) {
    if ((ctx.config().stream || {}).remote === false) throw new Error('En Atrio no está permitido emparejar desde el móvil.');
    const name = (a && a.name) || by || 'Móvil';
    if (a && a.auto) {
      const o = await host.otp(name);
      return { ok: true, msg: 'Código listo: Nova lo usa para emparejarse', ...o };
    }
    await host.pin(a && a.pin, name);
    return { ok: true, msg: 'Emparejado: ya puedes jugar en streaming' };
  },
  async polaris() {
    const i = await host.startService();
    return { ok: true, msg: `Polaris en marcha en ${i.name}` };
  },
  async ping() { return { ok: true, msg: `Atrio ${ctx.version()} en ${os.hostname()}` }; },
};

async function handleCommands() {
  const mine = ((tree.cmd || {})[me()]) || {};
  // Webs adjuntadas desde el móvil (buzón: llegan aunque el PC estuviera apagado al mandarlas)
  for (const [id, it] of Object.entries(((tree.inbox || {})[me()]) || {})) {
    if (!it || busy.has(`in:${id}`) || done.has(`in:${id}`)) continue;
    busy.add(`in:${id}`);
    (async () => {
      try {
        if (it.op === 'link') {
          const name = ctx.addLink(it.key, it.u, it.n);
          ctx.send('notice', `${it.by || 'Móvil'}: web añadida a ${name}`);
        }
      } catch (e) { log('buzón', e.message); ctx.send('notice', `No se ha podido añadir la web: ${e.message}`); }
      try { await del(`/suite/inbox/${me()}/${id}`); } catch { /* */ }
      busy.delete(`in:${id}`);
      done.add(`in:${id}`);
    })();
  }
  for (const [id, c] of Object.entries(mine)) {
    if (!c || c.res || busy.has(id) || done.has(id)) continue;
    // Recibida antes de apagar/reiniciar y sin respuesta: no se vuelve a ejecutar al arrancar
    if (c.ack) { done.add(id); continue; }
    const t = Number(c.t) || 0;
    if (now() - t > CMD_MAX_AGE) { done.add(id); del(`/suite/cmd/${me()}/${id}`).catch(() => {}); continue; }
    busy.add(id);
    (async () => {
      let res;
      const fn = HANDLERS[c.op];
      try {
        if (!fn) throw new Error('Orden desconocida: actualiza Atrio.');
        log('orden', c.op, 'de', c.by || '?');
        if (c.op === 'power') await put(`/suite/cmd/${me()}/${id}/ack`, true);
        res = await fn(c.a || {}, c.by);
      } catch (e) {
        res = { ok: false, msg: String(e.message || e).slice(0, 300) };
      }
      try { await put(`/suite/cmd/${me()}/${id}/res`, { ...res, t: { '.sv': 'timestamp' } }); } catch (e) { log('respuesta', e.message); }
      busy.delete(id);
      done.add(id);
      // La orden y su respuesta se borran al rato (el móvil ya la ha leído)
      setTimeout(() => del(`/suite/cmd/${me()}/${id}`).catch(() => {}), 5 * 60 * 1000);
    })();
  }
}

// ---------------------------------------------------------------- Capturas
let shooting = null;
async function takeAndUpload(opts = {}) {
  if (shooting) return shooting;
  shooting = (async () => {
    const cfg = ctx.config().suite || {};
    if (opts.by && cfg.remoteShot === false) throw new Error('En Atrio no está permitido hacer capturas desde el móvil.');
    const g = ctx.current();
    ctx.send('shot-start', { by: opts.by || null });
    const cap = await require('./capture').take({
      method: cfg.shotMethod || 'auto', scope: cfg.shotScope || 'juego', format: opts.format || cfg.shotFormat || 'png',
    });
    const id = `${now().toString(36)}${crypto.randomBytes(3).toString('hex')}`;
    const ext = cap.mime === 'image/jpeg' ? 'jpg' : 'png';
    // Copia en este PC (Imágenes/Atrio/<juego>)
    const d = new Date();
    const pad = (n) => String(n).padStart(2, '0');
    const name = `${safe(g && g.name)} ${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}.${pad(d.getMinutes())}.${pad(d.getSeconds())}.${ext}`;
    let file = null;
    try {
      const dir = path.join(picturesDir(), safe(g && g.name));
      mkdirp(dir);
      file = path.join(dir, name);
      fs.writeFileSync(file, cap.buf);
      idx().shots.push({ id, file, t: now(), key: g ? g.key : null, game: g ? g.name : null, w: cap.w, h: cap.h, mime: cap.mime, bytes: cap.buf.length });
      saveIdx();
    } catch (e) { log('guardar captura', e.message); }
    ctx.send('shot-taken', { id, w: cap.w, h: cap.h, game: g ? g.name : null, by: opts.by || null, method: cap.method });
    // Subida a la nube por trozos, para que el móvil la baje tal cual (sin recomprimir)
    if (rr.uid() && cfg.shotUpload !== false) {
      const b64 = cap.buf.toString('base64');
      const n = Math.ceil(b64.length / CHUNK);
      const meta = {
        id, dev: me(), pc: os.hostname(), w: cap.w, h: cap.h, mime: cap.mime, bytes: cap.buf.length, n, up: 0, done: false,
        m: cap.method, name, by: opts.by || 'Atrio',
        game: g ? { n: g.name, k: g.key, sg: g.sg || 0 } : null,
        t: { '.sv': 'timestamp' },
      };
      await put(`/suite/shots/${id}`, meta);
      const BATCH = 6;
      for (let i = 0; i < n; i += BATCH) {
        const part = {};
        for (let j = i; j < Math.min(n, i + BATCH); j++) part[j] = b64.slice(j * CHUNK, (j + 1) * CHUNK);
        await patch(`/shotdata/${id}`, part);
        await patch(`/suite/shots/${id}`, { up: Math.min(n, i + BATCH) });
      }
      await patch(`/suite/shots/${id}`, { done: true });
      log('captura subida', id, `${cap.w}x${cap.h}`, `${Math.round(cap.buf.length / 1024)} KB`, cap.method);
    }
    return { id, w: cap.w, h: cap.h, file, method: cap.method };
  })();
  try { return await shooting; } finally { shooting = null; }
}

// Limpieza: trozos ya bajados por el móvil o viejos
async function cleanup() {
  const shots = tree.shots || {};
  for (const [id, s] of Object.entries(shots)) {
    if (!s || s.dev !== me()) continue;
    const got = s.got && Object.keys(s.got).length;
    const old = now() - (Number(s.t) || 0) > SHOT_KEEP;
    if ((got || old) && !s.gone) {
      try { await del(`/shotdata/${id}`); await patch(`/suite/shots/${id}`, { gone: true }); } catch { /* otra vez será */ }
    }
    // La ficha de la captura (sin la imagen) se olvida al mes: la foto ya está en el móvil y en el PC
    else if (s.gone && now() - (Number(s.t) || 0) > 30 * 86400000) {
      try { await del(`/suite/shots/${id}`); } catch { /* */ }
    }
  }
}

// ---------------------------------------------------------------- Arranque y parada
function stopWatches() {
  if (suiteWatch) suiteWatch.stop();
  if (stampsWatch) stampsWatch.stop();
  suiteWatch = null;
  stampsWatch = null;
  tree = {};
}

let syncSoon = null;
function startWatches() {
  stopWatches();
  const uid = rr.uid();
  if (!uid) return;
  suiteWatch = watch({
    url: (force) => rr.authUrl(`/users/${uid}/suite`, force),
    fetchImpl: ctx.fetch,
    log,
    onState: (s) => { conn = s; ctx.send('suite', state()); },
    onData: (t) => {
      tree = t || {};
      handleCommands();
      ctx.send('suite', state());
    },
  });
  // Lo que cambie el móvil llega aquí al momento (su hora de subida cambia en /stamps)
  let first = true;
  stampsWatch = watch({
    url: (force) => rr.authUrl(`/users/${uid}/stamps`, force),
    fetchImpl: ctx.fetch,
    log,
    onData: (_t, ch) => {
      if (first) { first = false; return; }
      if (ch && String(ch.path).includes(me())) return; // es mi propia subida
      clearTimeout(syncSoon);
      syncSoon = setTimeout(() => rr.sync(false).catch(() => {}), 400);
    },
  });
}

function check() {
  const uid = rr.uid();
  if (uid !== lastUid) {
    lastUid = uid;
    lastLive = '';
    lastLib = '';
    if (uid) { startWatches(); pushLive(true); setTimeout(pushLib, 3000); } else stopWatches();
    ctx.send('suite', state());
  }
}

function start(c, ronron) {
  ctx = c;
  rr = ronron;
  host.init({ config: () => ctx.config().stream || {}, log, onChange: (i) => { pushLive(false); try { ctx.send('stream', i); } catch { /* */ } } });
  check();
  clearInterval(heart);
  heart = setInterval(() => {
    check();
    pushLive(true);
    cleanup().catch(() => {});
  }, Number(process.env.ATRIO_SUITE_BEAT || 60000));
  setInterval(() => { check(); pushLive(false); }, 5000);
}

async function stop() {
  clearInterval(heart);
  if (rr && rr.uid()) {
    try { await patch(`/suite/live/${me()}`, { on: false, game: null, at: { '.sv': 'timestamp' } }); } catch { /* */ }
  }
  stopWatches();
}

function state() {
  const shots = Object.values(tree.shots || {}).filter(Boolean).sort((a, b) => (b.t || 0) - (a.t || 0)).slice(0, 30);
  return { signed: !!(rr && rr.uid()), conn, me: rr ? me() : null, devices: devices(), shots };
}

module.exports = {
  start, stop, state, takeAndUpload, localShots, shotFile, forgetShot, pushLib, pushLive,
  libChanged: () => setTimeout(pushLib, 1500), liveChanged: () => pushLive(false), devices,
};
