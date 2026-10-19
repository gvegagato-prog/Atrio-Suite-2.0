'use strict';
// Tu nube (kDrive de Infomaniak), igual que en el móvil (Nube.kt): las capturas que hace este PC se suben a
// «Atrio/Capturas/<juego>» y las fotos de portada que elegiste en el móvil se bajan aquí. El token se guarda solo en
// este PC (config.json → nube); a la cuenta va únicamente la lista de lo subido (la lista «nube»), con su enlace.
const fs = require('fs');
const path = require('path');

let API = process.env.ATRIO_KDRIVE_API || 'https://api.infomaniak.com';
// La web de los enlaces públicos (en las pruebas, el mismo kDrive de mentira)
let SHARE = process.env.ATRIO_KDRIVE_SHARE || 'https://kdrive.infomaniak.com';
let ctx = null; // { config(), patch(o), edit(op), data(), log(), fotosDir, onFotos() }

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
    for (const x of o.data || []) out.push({ name: x.name, id: Number(x.id), dir: x.type === 'dir', visibility: x.visibility || '' });
    cursor = o.has_more ? o.cursor || '' : '';
  } while (cursor && out.length < 5000);
  return out;
}

// Tu carpeta privada («Mi carpeta»): en la raíz del kDrive no se puede escribir. La app oficial de kDrive la
// reconoce por su visibilidad «is_private_space».
const homes = new Map();
async function home(c = cfg()) {
  if (homes.has(c.drive)) return homes.get(c.drive);
  const root = await children(1, c);
  const h = root.find((x) => x.dir && x.visibility === 'is_private_space');
  if (!h) throw new Fail(`No encuentro tu carpeta privada en el kDrive (hay: ${root.map((x) => x.name).join(', ') || 'nada'})`);
  homes.set(c.drive, h.id);
  return h.id;
}

const CHUNKED = 5 * 1024 * 1024;

// Sube a «Mi carpeta/<dir>/<name>» (kDrive crea las carpetas que falten; con el mismo nombre, versión nueva).
// Los de más de 5 MB, a trozos, como la app oficial.
async function upload(dirPath, name, buf) {
  const c = cfg();
  const h = await home();
  const authH = { ...auth(c), 'Content-Type': 'application/octet-stream' };
  if (buf.length <= CHUNKED) {
    const q = new URLSearchParams({ directory_id: String(h), directory_path: dirPath, file_name: name, total_size: String(buf.length), conflict: 'version' });
    const o = await answer(await fetch(`${API}/3/drive/${c.drive}/upload?${q}`, { method: 'POST', headers: authH, body: buf }));
    const id = Number(o.data && o.data.id);
    if (!id) throw new Fail('kDrive no ha devuelto el archivo subido');
    return id;
  }
  const chunk = Math.max(1024 * 1024, Math.ceil(buf.length / 200));
  const total = Math.ceil(buf.length / chunk);
  const st = await call('POST', `/3/drive/${c.drive}/upload/session/start`, {
    conflict: 'version', directory_id: h, directory_path: dirPath, file_name: name,
    last_modified_at: Math.floor(Date.now() / 1000), total_chunks: total, total_size: buf.length,
  });
  const tok = st.data && st.data.token;
  if (!tok) throw new Fail('kDrive no ha abierto la subida');
  const host = String((st.data && st.data.upload_url) || API).replace(/\/+$/, '');
  // Cuatro trozos a la vez (como la app oficial); un trozo que falla por la red se reintenta, no todo el archivo
  let next = 1;
  const one = async (n) => {
    const part = buf.subarray((n - 1) * chunk, Math.min(buf.length, n * chunk));
    for (let t = 0; ; t++) {
      try {
        return await answer(await fetch(`${host}/3/drive/${c.drive}/upload/session/${tok}/chunk?chunk_number=${n}&chunk_size=${part.length}`, { method: 'POST', headers: authH, body: part }));
      } catch (e) {
        if (e instanceof Fail || t >= 2) throw e;
        await new Promise((r) => setTimeout(r, 1500 * (t + 1)));
      }
    }
  };
  await Promise.all(Array.from({ length: Math.min(4, total) }, async () => { while (next <= total) await one(next++); }));
  const fin = await call('POST', `/3/drive/${c.drive}/upload/session/${tok}/finish`);
  const id = Number(fin.data && fin.data.file && fin.data.file.id);
  if (!id) throw new Fail('kDrive no ha cerrado la subida');
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

// Un archivo de la lista de la nube: con tu cuenta y, si falla (este PC conectado a otro kDrive…), por su enlace
// público, que no depende de la cuenta (como en el móvil, NubeSync.bajar)
async function bajar(x) {
  try { return await download(x.f); } catch (e) {
    const m = String(x.l || '').match(/\/app\/share\/(\d+)\/([a-z0-9-]+)/);
    if (m) {
      const r = await fetch(`${SHARE}/2/app/${m[1]}/share/${m[2]}/files/${x.f}/download`);
      if (r.ok) return Buffer.from(await r.arrayBuffer());
    }
    if (m && cfg().drive && m[1] !== String(cfg().drive)) throw new Fail(`Este PC está conectado a otro kDrive (nº ${cfg().drive}) y el archivo está en el nº ${m[1]}`);
    throw e;
  }
}

// Un nombre que vale para una carpeta o un archivo en cualquier sistema (igual que NubeSync.safe en el móvil)
const safe = (s) => String(s || '').replace(/[\\/:*?"<>|\u0000-\u001f]+/g, ' ').replace(/\s+/g, ' ').trim().replace(/\.+$/, '').slice(0, 80).trim() || 'Sin nombre';
// «El nombre del viento (Crónica…, #1)» → «El nombre del viento», como en el móvil
const limpio = (t) => String(t || '').replace(/\(([^()#]+?),?\s*#\s*(\d+(?:[.,]\d+)?)\)\s*$/, '').trim() || String(t || '');
// Atrio/<Categoría>/<Ficha>/<Libro|Capturas|Portadas>, bajo «Mi carpeta»
const carpeta = (cat, ficha, que) => `Atrio/${safe(cat)}/${safe(limpio(ficha))}/${que}`;

// Comprueba un token y un kDrive antes de guardarlos
async function test(token, drive) {
  const d = String(drive || '').match(/drive\/(\d+)/)?.[1] || String(drive || '').replace(/\D/g, '');
  const c = { token: String(token || '').trim(), drive: d };
  homes.delete(d);
  await home(c);
  return { drive: d, msg: 'Conectado: Atrio guardará todo en «Mi carpeta/Atrio» de tu kDrive' };
}

const lista = () => ((ctx && ctx.data()) || {}).nube || [];

// Una captura recién hecha: a «Atrio/Capturas/<juego>/» con su enlace (en segundo plano: no frena la captura)
async function shot({ id, name, buf, game, sg, t, w, h, mime, pc }) {
  if (!on() || cfg().shots === false) return null;
  // A la carpeta de su ficha (con el nombre de la ficha, no el del PC, si la tienes)
  const d = (ctx && ctx.data()) || {};
  const ficha = (sg && [...(d.plays || []), ...(d.games || [])].find((x) => Number(x.id) === Number(sg))) || null;
  const f = await upload(carpeta('Videojuegos', (ficha && ficha.name) || game || 'Escritorio', 'Capturas'), name, buf);
  const k = `shot:${id}`;
  const item = {
    id: nubeId(k), k, f, n: name, s: buf.length, l: await link(f), updated: Date.now(),
    m: { game: game || '', sg: sg || 0, t, w, h, mime, pc, by: 'Atrio' },
  };
  await ctx.edit({ op: 'nubeAdd', items: [item] });
  ctx.log('nube: captura subida', name);
  return item;
}

// ---------------------------------------------------------------- Explorar tu kDrive (elegir una imagen de tu nube)
const IMG = /\.(jpe?g|png|webp|gif|heic|heif|avif|bmp)$/i;
async function explorar(dirId) {
  const c = cfg();
  const h = await home();
  let id = Number(dirId) || 0;
  if (!id) id = ((await children(h)).find((x) => x.dir && x.name === 'Atrio') || { id: h }).id; // empieza en «Mi carpeta/Atrio»
  const o = [];
  let cursor = '';
  do {
    const r = await call('GET', `/3/drive/${c.drive}/files/${id}/files${cursor ? `?cursor=${encodeURIComponent(cursor)}` : ''}`);
    for (const x of r.data || []) {
      const dir = x.type === 'dir';
      const img = !dir && (x.extension_type === 'image' || IMG.test(x.name || ''));
      if (dir || img) o.push({ id: Number(x.id), name: x.name, dir, img });
    }
    cursor = r.has_more ? r.cursor || '' : '';
  } while (cursor && o.length < 5000);
  o.sort((a, b) => (b.dir - a.dir) || a.name.localeCompare(b.name, 'es'));
  return { id, home: h, items: o };
}
// La miniatura (la misma dirección que usa la app oficial), como data: para la revista
async function miniatura(file) {
  const r = await fetch(`${API}/2/drive/${cfg().drive}/files/${file}/thumbnail`, { headers: auth(cfg()) });
  if (!r.ok) return '';
  return `data:${r.headers.get('content-type') || 'image/jpeg'};base64,${Buffer.from(await r.arrayBuffer()).toString('base64')}`;
}

// Una foto tuya puesta desde el PC (de un archivo o de tu nube) en una ficha o una saga: a la carpeta de su ficha,
// registrada como «foto:<nombre>» igual que las del móvil, para que el móvil la baje y la vea.
async function foto({ name, buf, cat, ficha, saga }) {
  if (!on()) return null;
  const dir = saga ? `Atrio/${safe(cat)}/Sagas/${safe(ficha)}/Portadas` : carpeta(cat, ficha, 'Portadas');
  const f = await upload(dir, name, buf);
  const k = `foto:${name.replace(/\.[^.]+$/, '')}.jpg`;
  const item = { id: nubeId(k), k, f, n: name, s: buf.length, updated: Date.now() };
  await ctx.edit({ op: 'nubeAdd', items: [item] });
  ctx.log('nube: foto subida', dir, name);
  return item;
}

// Una imagen del lanzador que pusiste desde un archivo (portada, horizontal, fondo o logo de un juego del PC):
// también a tu nube, a «Atrio/Videojuegos/<juego>/Portadas/Portada.png» (con el mismo nombre, versión nueva)
const TIPO = { cover: 'Portada', wide: 'Horizontal', hero: 'Fondo', logo: 'Logo' };
async function pcArt({ key, name, sg, type, file }) {
  if (!on() || !TIPO[type]) return null;
  const buf = fs.readFileSync(file);
  const ext = (String(file).match(/\.([A-Za-z0-9]{2,5})$/) || [null, 'png'])[1].toLowerCase();
  const d = (ctx && ctx.data()) || {};
  const ficha = (sg && [...(d.plays || []), ...(d.games || [])].find((x) => Number(x.id) === Number(sg))) || null;
  const n = `${TIPO[type]}.${ext}`;
  const f = await upload(carpeta('Videojuegos', (ficha && ficha.name) || name || 'Escritorio', 'Portadas'), n, buf);
  const k = `pcart:${key}:${type}`;
  const item = { id: nubeId(k), k, f, n, s: buf.length, updated: Date.now() };
  await ctx.edit({ op: 'nubeAdd', items: [item] });
  ctx.log('nube: imagen del juego subida', n);
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
        const buf = await bajar(x);
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
function setApi(u, share) { API = u; SHARE = share || u; homes.clear(); }

module.exports = { start, on, test, shot, pcArt, foto, explorar, miniatura, download, fotos, nubeId, setApi, Fail };
