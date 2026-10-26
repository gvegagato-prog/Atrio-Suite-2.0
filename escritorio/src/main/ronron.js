'use strict';
// Tu cuenta de Ronrón dentro de Atrio. Atrio es un dispositivo más de la cuenta: lee y edita los mismos datos
// que la app del móvil, a la vez. Mismo esquema que Account.kt de Ronrón:
//   /users/<uid>/dev/<dispositivo>    { name, user, device, updated, data }  (cada dispositivo escribe solo en el suyo)
//   /users/<uid>/stamps/<dispositivo> hora de su última subida
// Al mezclar, de cada ficha se queda la versión más reciente («updated») y lo borrado se respeta (marcas «tombs»).
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { APP_CONFIG_DIR, XDG_DATA, readJson, mkdirp } = require('./paths');

const DEFAULT_DB = 'https://app-ratear-default-rtdb.europe-west1.firebasedatabase.app';
const MAIL = 'ronron.example.com';
const STALE = 150 * 86400000;
const TOMB_AGE = 180 * 86400000;
const SESSION = path.join(APP_CONFIG_DIR, 'ronron.json');
const DATA = path.join(XDG_DATA, 'atrio', 'ronron-datos.json');

let fetchImpl = (...a) => fetch(...a);
function setFetch(f) { fetchImpl = f; }

let sess = null;
function S() {
  if (!sess) sess = readJson(SESSION, null) || {};
  if (!sess.db) sess.db = process.env.ATRIO_RONRON_DB || DEFAULT_DB;
  if (process.env.ATRIO_RONRON_AUTH) sess.authBase = process.env.ATRIO_RONRON_AUTH;
  if (!sess.dev) sess.dev = 'atrio-' + crypto.randomBytes(6).toString('hex');
  if (!sess.seen) sess.seen = {};
  return sess;
}
function saveSess() {
  try {
    mkdirp(path.dirname(SESSION));
    fs.writeFileSync(SESSION, JSON.stringify(S(), null, 1), { mode: 0o600 });
    fs.chmodSync(SESSION, 0o600);
  } catch { /* */ }
}

let local = null; // datos con la forma de syncData() de Ronrón
function L() {
  if (!local) local = (readJson(DATA, null) || {}).data || null;
  return local;
}
function saveLocal() {
  try { mkdirp(path.dirname(DATA)); fs.writeFileSync(DATA, JSON.stringify({ data: local, t: Date.now() })); } catch { /* */ }
}

// ---------------------------------------------------------------- Mezcla (igual que Store.mergeRemote)

const BASE_LISTS = ['games', 'plays', 'books', 'bplays', 'sagas', 'nube', 'lectura', 'maquetas', 'diario'];
const listNames = (kinds) => BASE_LISTS.concat(...(kinds || []).map((k) => [`items_${k.id}`, `iplays_${k.id}`]));
const upd = (o) => Number(o && o.updated) || 0;

function pruneTombs(t) {
  const lim = Date.now() - TOMB_AGE;
  for (const k of Object.keys(t)) if (!(Number(t[k]) >= lim)) delete t[k];
  return t;
}

// Los apuntes rápidos se suman de las dos versiones de una ficha (el móvil y el PC pueden añadirlos a la
// vez); un apunte borrado queda marcado («del») para que no reviva.
function unionBy(x, y, key, better) {
  if (!Array.isArray(x) && !Array.isArray(y)) return undefined;
  const m = new Map();
  for (const it of [...(x || []), ...(y || [])]) {
    if (!it) continue;
    const k = String(it[key]);
    const cur = m.get(k);
    m.set(k, cur ? better(cur, it) : it);
  }
  return [...m.values()];
}
function unite(win, lose) {
  const memos = unionBy(win.memos, lose.memos, 't', (p, q) => (q.del && !p.del ? q : (Number(q.u) || 0) > (Number(p.u) || 0) ? q : p));
  const out = { ...win };
  if (memos) out.memos = memos.sort((p, q) => p.t - q.t);
  // Las partidas para valorar ya no existen: lo que quede de ellas de versiones antiguas se descarta
  return sinPartidas(out);
}

// Lo más lejos que has llegado en un libro (en cualquier dispositivo): lo demás de la versión más nueva y esto del que
// más lejos llegó (igual que Store.uniteLectura en el móvil)
const LEJOS = ['bestCfi', 'bestFrac', 'bestLabel'];
// El tiempo leído, por dispositivo (stats.dev): de cada uno lo más alto y «total»/«days» = la suma (igual que
// ReadStats.merge en el móvil; un tiempo antiguo sin repartir cuenta solo si no hay cubos por dispositivo)
function mergeStats(x, y) {
  const devOf = (st) => (st && st.dev) || ((st && Number(st.total) > 0) ? { legacy: { total: Number(st.total), days: st.days || {} } } : {});
  const hasReal = !!((x && x.dev) || (y && y.dev));
  const dev = {};
  for (const src of [devOf(x), devOf(y)]) for (const [id, b] of Object.entries(src)) {
    if (hasReal && id === 'legacy') continue;
    const cur = dev[id];
    if (!cur) { dev[id] = { total: Number(b.total) || 0, days: { ...(b.days || {}) } }; continue; }
    const days = { ...cur.days };
    for (const [k, v] of Object.entries(b.days || {})) days[k] = Math.max(Number(days[k]) || 0, Number(v) || 0);
    dev[id] = { total: Math.max(cur.total, Number(b.total) || 0), days };
  }
  let total = 0; const days = {};
  for (const b of Object.values(dev)) {
    total += b.total;
    for (const [k, v] of Object.entries(b.days)) days[k] = (days[k] || 0) + (Number(v) || 0);
  }
  return { dev, total, days };
}

function uniteLectura(a, b) {
  const [base, otra] = upd(b) > upd(a) ? [b, a] : [a, b];
  const so = otra.s || {};
  // Lo que solo tenga la otra versión se conserva; en lo demás manda la más nueva
  const out = { ...base, s: { ...so, ...(base.s || {}) } };
  if ((Number(so.bestFrac) || 0) <= (Number((base.s || {}).bestFrac) || 0)) for (const k of LEJOS) if (base.s && k in base.s) out.s[k] = base.s[k];
  if ((Number(so.bestFrac) || 0) > (Number(out.s.bestFrac) || 0)) for (const k of LEJOS) if (k in so) out.s[k] = so[k];
  if ((a.s && a.s.stats) || (b.s && b.s.stats)) out.s.stats = mergeStats(a.s && a.s.stats, b.s && b.s.stats);
  out.updated = Math.max(upd(a), upd(b));
  return out;
}

function merge(a, b) {
  a = a || {};
  b = b || {};
  const t = { ...(a.tombs || {}) };
  for (const [k, v] of Object.entries(b.tombs || {})) if (Number(v) > (Number(t[k]) || 0)) t[k] = Number(v);
  const lk = a.kinds || [];
  const rk = b.kinds || [];
  const remoteNewer = (Number(b.kindsAt) || 0) > (Number(a.kindsAt) || 0);
  const ids = [...new Set([...lk.map((k) => k.id), ...rk.map((k) => k.id)])].filter((id) => !(`kind:${id}` in t));
  const mk = ids.map((id) => {
    const x = lk.find((k) => k.id === id);
    const y = rk.find((k) => k.id === id);
    return x && y ? (remoteNewer ? y : x) : x || y;
  });
  const out = { ...b, ...a };
  for (const n of listNames(mk)) {
    const by = new Map();
    for (const o of a[n] || []) if (o) by.set(Number(o.id), o);
    for (const o of b[n] || []) {
      if (!o) continue;
      const cur = by.get(Number(o.id));
      if (!cur) by.set(Number(o.id), o);
      else if (n === 'lectura') by.set(Number(o.id), uniteLectura(cur, o));
      else by.set(Number(o.id), unite(upd(o) > upd(cur) ? o : cur, upd(o) > upd(cur) ? cur : o));
    }
    out[n] = [...by.values()].filter((o) => !(t[`${n}:${o.id}`] !== undefined && Number(t[`${n}:${o.id}`]) >= upd(o)));
  }
  for (const k of Object.keys(out)) if (/^(items|iplays)_\d+$/.test(k) && !listNames(mk).includes(k)) delete out[k];
  out.kinds = mk;
  out.kindsAt = Math.max(Number(a.kindsAt) || 0, Number(b.kindsAt) || 0);
  out.tombs = pruneTombs(t);
  const recs = [...(a.recs || [])];
  const have = new Set(recs.map((r) => r && r.id));
  for (const r of b.recs || []) if (r && !have.has(r.id)) recs.push(r);
  out.recs = recs;
  out.reacts = { ...(b.reacts || {}), ...(a.reacts || {}) };
  const ka = a.keys || {};
  const kb = b.keys || {};
  out.keys = {};
  for (const k of new Set([...Object.keys(ka), ...Object.keys(kb)])) out.keys[k] = ka[k] || kb[k] || '';
  const ga = a.goals || {};
  const gb = b.goals || {};
  out.goals = { g: ga.g || gb.g || 0, b: ga.b || gb.b || 0 };
  out.likes = a.likes || b.likes || { 0: [], 1: [] };
  return out;
}

// ---------------------------------------------------------------- HTTP y sesión

class Fail extends Error {}

function authMsg(text) {
  let raw = text;
  try { raw = (JSON.parse(text).error || {}).message || text; } catch { /* */ }
  if (/API key not valid|API_KEY_INVALID/i.test(raw)) return 'La clave web de Firebase no es válida.';
  const code = String(raw).split(/[ :]/)[0];
  return ({
    EMAIL_EXISTS: 'Ese nombre de usuario ya está cogido.',
    EMAIL_NOT_FOUND: 'Usuario o contraseña incorrectos.',
    INVALID_PASSWORD: 'Usuario o contraseña incorrectos.',
    INVALID_LOGIN_CREDENTIALS: 'Usuario o contraseña incorrectos.',
    INVALID_EMAIL: 'Usuario o contraseña incorrectos.',
    OPERATION_NOT_ALLOWED: 'En Firebase no está activado el acceso con correo y contraseña.',
    PASSWORD_LOGIN_DISABLED: 'En Firebase no está activado el acceso con correo y contraseña.',
    TOO_MANY_ATTEMPTS_TRY_LATER: 'Demasiados intentos seguidos. Espera un rato.',
    USER_DISABLED: 'Esta cuenta está desactivada.',
    TOKEN_EXPIRED: 'La sesión ha caducado. Vuelve a entrar con tu contraseña.',
    INVALID_REFRESH_TOKEN: 'La sesión ha caducado. Vuelve a entrar con tu contraseña.',
    USER_NOT_FOUND: 'La sesión ha caducado. Vuelve a entrar con tu contraseña.',
    INVALID_ID_TOKEN: 'La sesión ha caducado. Vuelve a entrar con tu contraseña.',
  })[code] || `Firebase: ${raw}`;
}

async function http(method, url, body, form) {
  let res;
  try {
    res = await fetchImpl(url, {
      method,
      headers: body === undefined ? {} : { 'Content-Type': form ? 'application/x-www-form-urlencoded' : 'application/json; charset=utf-8' },
      body: body === undefined ? undefined : body,
    });
  } catch (e) {
    throw new Fail('Sin conexión con Firebase.');
  }
  return { code: res.status, text: await res.text() };
}

const idUrl = (op) => `${S().authBase || 'https://identitytoolkit.googleapis.com'}/v1/accounts:${op}?key=${encodeURIComponent(S().webKey || '')}`;
const tokenUrl = () => `${S().authBase || 'https://securetoken.googleapis.com'}/v1/token?key=${encodeURIComponent(S().webKey || '')}`;

function keep(user, o) {
  const s = S();
  if (s.uid && s.uid !== o.localId) { s.seen = {}; local = null; try { fs.unlinkSync(DATA); } catch { /* */ } }
  s.user = user;
  s.uid = o.localId;
  s.token = o.idToken;
  s.refresh = o.refreshToken;
  s.exp = Date.now() + (Number(o.expiresIn) || 3600) * 1000;
  saveSess();
}

async function token(force) {
  const s = S();
  if (!force && s.token && Date.now() < (s.exp || 0) - 60000) return s.token;
  if (!s.refresh) throw new Fail('No has iniciado sesión.');
  const r = await http('POST', tokenUrl(), `grant_type=refresh_token&refresh_token=${encodeURIComponent(s.refresh)}`, true);
  if (r.code < 200 || r.code > 299) {
    const m = authMsg(r.text);
    if (/caducado/.test(m)) { s.token = ''; s.refresh = ''; s.err = m; saveSess(); }
    throw new Fail(m);
  }
  const o = JSON.parse(r.text);
  s.token = o.id_token;
  s.refresh = o.refresh_token || s.refresh;
  s.exp = Date.now() + (Number(o.expires_in) || 3600) * 1000;
  saveSess();
  return s.token;
}

// Petición a la base de datos con tu sesión. null = no hay nada (o no tienes permiso, si okDenied)
async function db(method, p, body, query = '', okDenied = true) {
  const go = async (force) => http(method, `${S().db}${p}.json?auth=${await token(force)}${query ? '&' + query : ''}`,
    body === undefined ? undefined : JSON.stringify(body));
  let r = await go(false);
  if (r.code === 401) r = await go(true);
  if (r.code === 401 || r.code === 403) {
    if (okDenied) return null;
    throw new Fail('Firebase no deja hacer eso. Revisa las reglas de la base de datos (Atrio en el móvil → Ajustes → Cuenta y pareja).');
  }
  if (r.code === 404) throw new Fail('No encuentro la base de datos.');
  if (r.code < 200 || r.code > 299) throw new Fail(`Firebase respondió ${r.code}.`);
  const t = r.text.trim();
  if (t.startsWith('<')) throw new Fail('La dirección no es la de la base de datos.');
  return !t || t === 'null' ? null : JSON.parse(t);
}

// Dirección con tu sesión para escuchar un sitio en tiempo real (null si no has entrado)
async function authUrl(p, force) {
  const s = S();
  if (!s.uid || !s.refresh) return null;
  return `${s.db}${p}.json?auth=${await token(force)}`;
}

// ---------------------------------------------------------------- Cuenta

const normUser = (u) => String(u || '').trim().toLowerCase();
const validUser = (u) => /^[a-z0-9][a-z0-9._-]{2,23}$/.test(u);

function status() {
  const s = S();
  const d = L();
  return {
    signed: !!(s.uid && s.refresh),
    user: s.user || '',
    lastSync: s.lastSync || 0,
    lastPush: s.lastPush || 0,
    err: s.err || '',
    count: d ? countItems(d) : 0,
    db: s.db,
    needKey: !s.webKey,
  };
}

function countItems(d) {
  return Object.keys(d).filter((k) => BASE_LISTS.includes(k) || /^(items|iplays)_\d+$/.test(k))
    .reduce((n, k) => n + (Array.isArray(d[k]) ? d[k].length : 0), 0);
}

async function findWebKey() {
  const s = S();
  if (s.webKey) return s.webKey;
  try {
    const r = await http('GET', `${s.db}/config/webKey.json`);
    if (r.code === 200) {
      const k = JSON.parse(r.text);
      if (typeof k === 'string' && k) { s.webKey = k; saveSess(); }
    }
  } catch { /* sin conexión */ }
  return s.webKey || '';
}

async function signIn(user, pass, webKey) {
  const u = normUser(user);
  if (!validUser(u)) throw new Fail('El usuario tiene que tener de 3 a 24 letras o números.');
  if (!pass) throw new Fail('Falta la contraseña.');
  const s = S();
  if (webKey) { s.webKey = String(webKey).trim(); saveSess(); }
  if (!(await findWebKey())) throw new Fail('FALTA_CLAVE');
  const r = await http('POST', idUrl('signInWithPassword'), JSON.stringify({ email: `${u}@${MAIL}`, password: pass, returnSecureToken: true }));
  if (r.code < 200 || r.code > 299) throw new Fail(authMsg(r.text));
  keep(u, JSON.parse(r.text));
  s.err = '';
  saveSess();
  await sync(true);
  return status();
}

function signOut() {
  const s = S();
  for (const k of ['uid', 'token', 'refresh', 'exp', 'user', 'lastSync', 'lastPush', 'err']) delete s[k];
  s.seen = {};
  saveSess();
  local = null;
  try { fs.unlinkSync(DATA); } catch { /* */ }
  hc = null;
  try { fs.unlinkSync(HOURS); } catch { /* */ }
  return status();
}

// ---------------------------------------------------------------- Sincronización

let queue = Promise.resolve();
const serial = (fn) => { const p = queue.then(fn, fn); queue = p.catch(() => {}); return p; };
let onChange = () => {};

// Baja y mezcla lo nuevo de los dispositivos de tu cuenta. full: aunque ya se hubiera visto (al entrar)
async function pullOwn(full) {
  const s = S();
  const st = (await db('GET', `/users/${s.uid}/stamps`)) || {};
  let changed = false;
  // Si Atrio llevaba mucho sin sincronizar, lo de la nube manda (las marcas de borrado ya podrían haber caducado)
  if (L() && s.lastSync && Date.now() - s.lastSync > STALE) { local = null; full = true; }
  for (const [dev, ts] of Object.entries(st)) {
    if (dev === s.dev || Date.now() - ts > STALE) continue;
    if (!full && ts <= (s.seen[dev] || 0)) continue;
    const raw = await db('GET', `/users/${s.uid}/dev/${dev}/data`);
    if (typeof raw !== 'string') continue;
    let remote;
    try { remote = JSON.parse(raw); } catch { continue; }
    const before = JSON.stringify(L());
    local = merge(L(), remote);
    if (JSON.stringify(local) !== before) changed = true;
    s.seen[dev] = ts;
  }
  s.lastSync = Date.now();
  saveSess();
  if (changed) saveLocal();
  return changed;
}

async function upload() {
  const s = S();
  const d = L();
  if (!d) return;
  const node = { name: s.user, user: s.user, device: 'atrio', updated: { '.sv': 'timestamp' }, data: JSON.stringify(d) };
  await db('PUT', `/users/${s.uid}/dev/${s.dev}`, node, 'print=silent', false);
  const ts = await db('GET', `/users/${s.uid}/dev/${s.dev}/updated`);
  if (typeof ts !== 'number') throw new Fail('Firebase no ha guardado los cambios.');
  await db('PATCH', `/users/${s.uid}`, { [`stamps/${s.dev}`]: ts }, 'print=silent', false);
  s.lastPush = ts;
  saveSess();
}

function sync(full) {
  return serial(async () => {
    const s = S();
    if (!s.uid || !s.refresh) return false;
    try {
      const changed = await pullOwn(full);
      s.err = '';
      saveSess();
      if (changed) onChange();
      return changed;
    } catch (e) {
      s.err = e.message;
      saveSess();
      throw e;
    }
  });
}

// ---------------------------------------------------------------- Edición desde Atrio

const today = () => Math.floor((Date.now() - new Date().getTimezoneOffset() * 60000) / 86400000);
const gk = (kind) => (kind === 0 ? 'games' : kind === 1 ? 'books' : `items_${kind}`);
const pk = (kind) => (kind === 0 ? 'plays' : kind === 1 ? 'bplays' : `iplays_${kind}`);

// Lo que quede de las partidas para valorar (versiones antiguas) no se vuelve a guardar
function sinPartidas(item) {
  if (!item || (!('segs' in item) && !('noParts' in item) && !(item.entries || []).some((e) => e && ('seg' in e || 'part' in e)))) return item;
  const { segs, noParts, ...r } = item;
  if (Array.isArray(r.entries)) r.entries = r.entries.map((e) => { if (!e || (!('seg' in e) && !('part' in e))) return e; const { seg, part, ...x } = e; return x; });
  return r;
}
function put(d, list, item) {
  const now = Date.now();
  item = { ...sinPartidas(item), updated: now };
  d[list] = (d[list] || []).filter((o) => Number(o.id) !== Number(item.id)).concat(item);
  delete d.tombs[`${list}:${item.id}`];
}
function drop(d, list, id) {
  const before = (d[list] || []).length;
  d[list] = (d[list] || []).filter((o) => Number(o.id) !== Number(id));
  if (d[list].length !== before) d.tombs[`${list}:${id}`] = Date.now();
}

function avg(entries, f) {
  return entries.length ? Math.round(entries.reduce((s, e) => s + (Number(e[f]) || 0), 0) / entries.length) : 50;
}

// op: { op: 'saveGame'|'deleteGame'|'savePlay'|'deletePlay'|'finish', kind, item?, id? }
function edit(op) {
  return serial(async () => {
    const s = S();
    if (!s.uid) throw new Fail('No has iniciado sesión en tu cuenta de Atrio.');
    // Primero lo último de tus otros dispositivos, para editar sobre lo más reciente
    try { await pullOwn(false); } catch { /* sin conexión: se edita lo de aquí y se sube después */ }
    const d = L() || merge({}, {});
    d.tombs = d.tombs || {};
    const kind = Number(op.kind) || 0;
    if (op.op === 'saveGame') put(d, gk(kind), op.item);
    else if (op.op === 'deleteGame') drop(d, gk(kind), op.id);
    else if (op.op === 'savePlay') {
      // Sin perder apuntes rápidos ni partes que hayan llegado mientras se editaba
      const cur = (d[pk(kind)] || []).find((o) => Number(o.id) === Number(op.item.id));
      put(d, pk(kind), cur ? unite(op.item, cur) : op.item);
    }
    else if (op.op === 'deletePlay') drop(d, pk(kind), op.id);
    else if (op.op === 'finish') {
      // Igual que «Terminado» en Ronrón: la media de tus apuntes pasa a ser la nota y las notas, la opinión
      const p = (d[pk(kind)] || []).find((o) => Number(o.id) === Number(op.id));
      if (!p) throw new Fail('No encuentro esa ficha.');
      const es = p.entries || [];
      const notes = es.filter((e) => e.note && e.note.trim()).map((e) => e.note).join('\n\n');
      const prev = (d[gk(kind)] || []).find((o) => Number(o.id) === Number(p.id));
      const g = {
        id: p.id, name: p.name, cover: p.cover || '',
        general: avg(es, 'general'), gfx: avg(es, 'gfx'), story: avg(es, 'story'), music: avg(es, 'music'), gameplay: avg(es, 'gameplay'),
        review: prev ? [prev.review, notes].filter((x) => x && x.trim()).join('\n\n') : notes,
        hero: p.hero || (prev && prev.hero) || '', logo: p.logo || (prev && prev.logo) || '', start: p.start || (prev && prev.start) || 0, end: p.end > 0 ? p.end : today(),
        creator: p.creator || (prev && prev.creator) || '', fav: !!(prev && prev.fav), platform: p.platform || (prev && prev.platform) || '',
        pages: p.pages || (prev && prev.pages) || 0, minutes: (Number(p.minutes) || 0) + (prev ? Number(prev.minutes) || 0 : 0),
        ttbMain: p.ttbMain || (prev && prev.ttbMain) || 0, ttbExtra: p.ttbExtra || (prev && prev.ttbExtra) || 0,
        ttbFull: p.ttbFull || (prev && prev.ttbFull) || 0, ttbSrc: p.ttbSrc || (prev && prev.ttbSrc) || '',
      };
      if (op.scores) Object.assign(g, op.scores);
      put(d, gk(kind), g);
      drop(d, pk(kind), p.id);
    } else if (op.op === 'addMin') {
      // Lo jugado en el PC al cerrar el juego: se suma a las horas de la ficha
      const p = (d[pk(kind)] || []).find((o) => Number(o.id) === Number(op.id));
      if (!p) throw new Fail('No encuentro esa ficha.');
      put(d, pk(kind), { ...p, minutes: (Number(p.minutes) || 0) + Math.max(0, Math.round(Number(op.min) || 0)) });
    } else if (op.op === 'sagaCover') {
      // La portada de una saga (vacía = la automática, con las de sus fichas)
      const sg = (d.sagas || []).find((o) => Number(o.id) === Number(op.id));
      if (!sg) throw new Fail('No encuentro esa saga.');
      const { cover, ...resto } = sg;
      put(d, 'sagas', op.cover ? { ...resto, cover: String(op.cover) } : resto);
    } else if (op.op === 'nubeAdd') {
      // Lo subido a tu nube desde este PC (capturas): la lista que leen tus otros dispositivos y tu pareja
      for (const it of op.items || []) if (it && it.id && it.k) put(d, 'nube', it);
    } else if (op.op === 'memo') {
      // Apunte rápido (texto, y si quieres una captura) mientras juegas
      const p = (d[pk(kind)] || []).find((o) => Number(o.id) === Number(op.id));
      if (!p) throw new Fail('No encuentro esa ficha.');
      const memos = [...(p.memos || [])].filter((m) => m && m.t !== op.memo.t);
      memos.push(op.remove ? { t: op.memo.t, del: 1, u: Date.now() } : { ...op.memo, u: Date.now() });
      put(d, pk(kind), { ...p, memos: memos.sort((a, b) => a.t - b.t).slice(-500) });
    } else if (op.op === 'diarioPut') {
      // Una entrada del diario (la misma lista que el móvil: { id, dia, x, sobre?, foto?, comp?, updated })
      const n = op.item || {};
      if (!Number(n.id) || !Number.isFinite(Number(n.dia))) throw new Fail('Esa entrada del diario no está bien.');
      const prev = (d.diario || []).find((o) => Number(o.id) === Number(n.id)) || {};
      const item = { ...prev, id: Number(n.id), dia: Number(n.dia), x: String(n.x || '') };
      for (const k of ['sobre', 'foto', 'comp']) if (k in n) item[k] = n[k];
      put(d, 'diario', item);
    } else if (op.op === 'diarioDel') drop(d, 'diario', op.id);
    else throw new Fail('Operación desconocida.');
    local = d;
    saveLocal();
    onChange();
    try {
      await upload();
      s.err = '';
    } catch (e) {
      s.err = `Guardado en Atrio; falta subirlo: ${e.message}`;
      pending = true;
    }
    saveSess();
    return true;
  });
}

// Atrio ha detectado que estás jugando a algo: si no está en Ronrón (o estaba en tu lista o dropeado),
// pasa a «Jugando» desde hoy. Así sale en el calendario todos los días hasta que lo termines, como en Ronrón.
// Devuelve qué ha hecho ('nuevo', 'retomado') o null si no hacía falta.
function autoPlay(base) {
  return serial(async () => {
    const s = S();
    if (!s.uid || !s.refresh || !base || !base.id) return null;
    try { await pullOwn(false); } catch { /* sin conexión */ }
    const d = L() || merge({}, {});
    d.tombs = d.tombs || {};
    const id = Number(base.id);
    const p = (d.plays || []).find((o) => Number(o.id) === id);
    let what = null;
    if (p) {
      if (p.status === 1) return null;
      put(d, 'plays', { ...p, status: 1, start: p.start || today() });
      what = 'retomado';
    } else {
      // Si ya lo tienes valorado no se toca (para rejugarlo está «Rejugar»)
      if ((d.games || []).some((o) => Number(o.id) === id)) return null;
      put(d, 'plays', {
        id, name: base.name, cover: base.cover || '', hero: base.hero || '', logo: base.logo || '', status: 1, entries: [],
        chapter: 0, every: 3, start: today(), end: 0, creator: '', platform: 'PC', pages: 0, minutes: Math.round(base.minutes || 0),
        ttbMain: 0, ttbExtra: 0, ttbFull: 0, ttbSrc: '', parts: [], memos: [],
      });
      what = 'nuevo';
    }
    local = d;
    saveLocal();
    onChange();
    try { await upload(); } catch { pending = true; }
    return what;
  });
}

function knows(id) {
  const d = L();
  if (!d) return false;
  return (d.plays || []).some((o) => Number(o.id) === Number(id) && o.status === 1);
}

let pending = false;
let timer = null;
function start(cb) {
  onChange = cb || (() => {});
  clearInterval(timer);
  const tick = async () => {
    if (!S().uid || !S().refresh) return;
    try {
      await sync(false);
      if (pending) { await serial(upload); pending = false; S().err = ''; saveSess(); }
    } catch { /* sin conexión: se reintenta */ }
  };
  timer = setInterval(tick, Number(process.env.ATRIO_RONRON_EVERY || 20000));
  setTimeout(tick, 1500);
}

function data() { return L(); }

// ---------------------------------------------------------------- Horas de juego de la cuenta
// Cada Atrio sube sus partidas a /users/<uid>/horas/<dispositivo> y apunta la hora en /users/<uid>/horasAt.
// Así el calendario y las horas son los de la cuenta, juegues en el equipo que juegues.
const HOURS = path.join(XDG_DATA, 'atrio', 'horas-cuenta.json');
let hc = null;
function H() {
  const uid = S().uid || '';
  if (!hc) hc = readJson(HOURS, null) || {};
  if (hc.uid !== uid) hc = { uid, seen: {}, devs: {}, sent: '' };
  return hc;
}
function saveHours() { try { mkdirp(path.dirname(HOURS)); fs.writeFileSync(HOURS, JSON.stringify(hc)); } catch { /* */ } }

function uid() { const s = S(); return s.uid && s.refresh ? s.uid : null; }
function deviceId() { return S().dev; }

// Sube las partidas de este equipo si han cambiado. payload: { name, games, s, forgot }
function hoursPush(payload) {
  return serial(async () => {
    const s = S();
    if (!s.uid || !s.refresh) return false;
    const h = H();
    const txt = JSON.stringify(payload);
    if (h.sent === txt) return false;
    await db('PUT', `/users/${s.uid}/horas/${s.dev}`, { t: { '.sv': 'timestamp' }, d: txt }, 'print=silent', false);
    const ts = await db('GET', `/users/${s.uid}/horas/${s.dev}/t`);
    if (typeof ts !== 'number') throw new Fail('Firebase no ha guardado las horas.');
    await db('PATCH', `/users/${s.uid}`, { [`horasAt/${s.dev}`]: ts }, 'print=silent', false);
    h.sent = txt;
    saveHours();
    return true;
  });
}

// Baja las partidas nuevas de los otros equipos de la cuenta. Devuelve true si algo cambió
function hoursPull() {
  return serial(async () => {
    const s = S();
    if (!s.uid || !s.refresh) return false;
    const h = H();
    const st = (await db('GET', `/users/${s.uid}/horasAt`)) || {};
    let changed = false;
    for (const [dev, ts] of Object.entries(st)) {
      if (dev === s.dev || typeof ts !== 'number' || ts <= (h.seen[dev] || 0)) continue;
      const raw = await db('GET', `/users/${s.uid}/horas/${dev}/d`);
      if (typeof raw !== 'string') continue;
      try { h.devs[dev] = JSON.parse(raw); } catch { continue; }
      h.seen[dev] = ts;
      changed = true;
    }
    if (changed) saveHours();
    return changed;
  });
}

// Partidas de los otros equipos (de la cuenta con la que has entrado)
function hoursOthers() {
  if (!uid()) return {};
  const h = H();
  const out = {};
  for (const [dev, p] of Object.entries(h.devs || {})) if (dev !== S().dev) out[dev] = p;
  return out;
}

function user() { return S().user || ''; }
function setOnChange(cb) { onChange = cb || (() => {}); }

module.exports = { setFetch, status, signIn, signOut, sync, edit, data, start, merge, normUser, autoPlay, knows, DEFAULT_DB,
  uid, deviceId, hoursPush, hoursPull, hoursOthers, authUrl, db, user, setOnChange };
