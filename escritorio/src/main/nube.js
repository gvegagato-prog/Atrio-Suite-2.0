'use strict';
// Tu nube (kDrive de Infomaniak), igual que en el móvil (Nube.kt): las capturas que hace este PC se suben a
// «Atrio/Capturas/<juego>» y las fotos de portada que elegiste en el móvil se bajan aquí. El token se guarda solo en
// este PC (config.json → nube); a la cuenta va únicamente la lista de lo subido (la lista «nube»), con su enlace.
const fs = require('fs');
const path = require('path');

let API = process.env.ATRIO_KDRIVE_API || 'https://api.infomaniak.com';
let ctx = null; // { config(), patch(o), edit(op), data(), log(), fotosDir, onFotos() }
const dirs = new Map();

class Fail extends Error {}

const cfg = () => (ctx && ctx.config().nube) || {};
const on = () => !!(cfg().token && cfg().drive);

// El mismo id que en el móvil (FNV-1a de 64 bits, recortado a 53 para que quepa en un número de JavaScript)
function nubeId(k) {
  let h = 0xcbf29ce484222325n;
  for (const b of Buffer.from(k, 'utf8')) { h ^= BigInt(b); h = (h * 0x100000001b3n) & 0xffffffffffffffffn; }
  const v = Number(h & 0x1fffffffffffffn);
  return v < 1 ? 1 : v;
}

async function answer(r) {
  const txt = await r.text();
  let o = null;
  try { o = JSON.parse(txt); } catch { /* */ }
  if (!r.ok || (o && o.result === 'error')) {
    const why = o && o.error ? (o.error.description || o.error.code) : '';
    if (r.status === 401) throw new Fail('kDrive no acepta el token (revísalo o crea otro)');
    if (r.status === 403) throw new Fail('El token no tiene permiso para este kDrive');
    if (r.status === 404) throw new Fail('No encuentro ese kDrive (revisa su número)');
    throw new Fail(`kDrive: ${why || `error ${r.status}`}`);
  }
  return o || {};
}

const auth = (c) => ({ Authorization: `Bearer ${c.token}` });

async function call(method, p, body, c = cfg()) {
  const r = await fetch(API + p, {
    method, headers: { ...auth(c), ...(body ? { 'Content-Type': 'application/json' } : {}) }, body: body ? JSON.stringify(body) : undefined,
  });
  return answer(r);
}

async function children(dir, c = cfg()) {
  const out = [];
  let cursor = '';
  do {
    const o = await call('GET', `/3/drive/${c.drive}/files/${dir}/files${cursor ? `?cursor=${encodeURIComponent(cursor)}` : ''}`, null, c);
    for (const x of o.data || []) out.push({ name: x.name, id: Number(x.id), dir: x.type === 'dir' });
    cursor = o.has_more ? o.cursor || '' : '';
  } while (cursor && out.length < 5000);
  return out;
}

// La carpeta «Atrio/…» (se crea si no está)
async function dir(...parts) {
  const key = `${cfg().drive}:${parts.join('/')}`;
  if (dirs.has(key)) return dirs.get(key);
  let cur = 1;
  for (const name of ['Atrio', ...parts]) {
    const f = (await children(cur)).find((x) => x.dir && x.name === name);
    if (f) cur = f.id;
    else {
      const o = await call('POST', `/3/drive/${cfg().drive}/files/${cur}/directory`, { name });
      cur = Number(o.data && o.data.id);
      if (!cur) throw new Fail(`No se pudo crear la carpeta ${name}`);
    }
  }
  dirs.set(key, cur);
  return cur;
}

async function upload(dirId, name, buf) {
  const q = new URLSearchParams({ directory_id: String(dirId), file_name: name, total_size: String(buf.length) });
  const r = await fetch(`${API}/3/drive/${cfg().drive}/upload?${q}`, {
    method: 'POST', headers: { ...auth(cfg()), 'Content-Type': 'application/octet-stream' }, body: buf,
  });
  const o = await answer(r);
  const id = Number(o.data && o.data.id);
  if (!id) throw new Fail('kDrive no ha devuelto el archivo subido');
  return id;
}

async function link(file) {
  try {
    const o = await call('POST', `/2/drive/${cfg().drive}/files/${file}/link`, { right: 'public', can_download: true });
    return (o.data && (o.data.url || (typeof o.data === 'string' ? o.data : ''))) || '';
  } catch { return ''; }
}

async function download(file) {
  const r = await fetch(`${API}/2/drive/${cfg().drive}/files/${file}/download`, { headers: auth(cfg()) });
  if (!r.ok) await answer(r);
  return Buffer.from(await r.arrayBuffer());
}

const safe = (s) => String(s || '').replace(/[\\/:*?"<>|]+/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 80) || 'Escritorio';

// Comprueba un token y un kDrive antes de guardarlos
async function test(token, drive) {
  const d = String(drive || '').match(/drive\/(\d+)/)?.[1] || String(drive || '').replace(/\D/g, '');
  const n = (await children(1, { token: String(token || '').trim(), drive: d })).length;
  return { drive: d, msg: `Conectado: tu kDrive tiene ${n} elementos en la carpeta principal` };
}

const lista = () => ((ctx && ctx.data()) || {}).nube || [];

// Una captura recién hecha: a «Atrio/Capturas/<juego>/» con su enlace (en segundo plano: no frena la captura)
async function shot({ id, name, buf, game, sg, t, w, h, mime, pc }) {
  if (!on() || cfg().shots === false) return null;
  const g = safe(game);
  const f = await upload(await dir('Capturas', g), name, buf);
  const k = `shot:${id}`;
  const item = {
    id: nubeId(k), k, f, n: name, s: buf.length, l: await link(f), updated: Date.now(),
    m: { game: game || '', sg: sg || 0, t, w, h, mime, pc, by: 'Atrio' },
  };
  await ctx.edit({ op: 'nubeAdd', items: [item] });
  ctx.log('nube: captura subida', name);
  return item;
}

// Las fotos de portada que elegiste en el móvil (están en tu nube) y faltan en este PC
let bajando = false;
async function fotos() {
  if (!on() || bajando) return 0;
  bajando = true;
  let n = 0;
  try {
    const have = new Set((() => { try { return fs.readdirSync(ctx.fotosDir).map((x) => x.replace(/\.[^.]+$/, '')); } catch { return []; } })());
    for (const x of lista()) {
      if (!x || !String(x.k).startsWith('foto:')) continue;
      const base = String(x.k).slice(5).replace(/\.[^.]+$/, '');
      if (!/^[A-Za-z0-9_.-]{1,80}$/.test(base) || have.has(base)) continue;
      try {
        const buf = await download(x.f);
        const ext = (String(x.n).match(/\.([A-Za-z0-9]{2,5})$/) || [null, 'jpg'])[1].toLowerCase();
        fs.mkdirSync(ctx.fotosDir, { recursive: true });
        fs.writeFileSync(path.join(ctx.fotosDir, `${base}.${ext}`), buf);
        n++;
      } catch (e) { ctx.log('nube: foto', x.n, e.message); }
    }
  } finally { bajando = false; }
  if (n) ctx.onFotos();
  return n;
}

function start(c) { ctx = c; }
function setApi(u) { API = u; dirs.clear(); }

module.exports = { start, on, test, shot, fotos, nubeId, setApi, Fail };
