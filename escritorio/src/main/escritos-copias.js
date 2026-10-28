'use strict';
// Copias de seguridad de Escribir: una novela no se puede volver a escribir. Cada cierto rato (y al cerrar), si algo ha
// cambiado, se guarda una copia completa (todas las obras, con su papelera) en una carpeta de este equipo, aparte de la
// cuenta. Se puede abrir la carpeta, y de cualquier copia se puede sacar una obra como obra NUEVA (nunca pisa lo que hay).
//
// Archivo: copia-AAAA-MM-DD_HHMMSS-<n>obras-<p>pal.json   { v: 1, t: ms, docs: [...] }
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const GUARDAR = 40;            // copias que se conservan
const CADA = 15 * 60 * 1000;   // cada cuánto se mira si hay algo nuevo
const NOMBRE = /^copia-(\d{4})-(\d{2})-(\d{2})_(\d{2})(\d{2})(\d{2})-(\d+)obras-(\d+)pal\.json$/;
const num = (v) => (Number.isFinite(Number(v)) ? Number(v) : 0);
const dos = (n) => String(n).padStart(2, '0');

function iniciar({ escr, carpeta, log = () => {}, ahora = () => Date.now() }) {
  fs.mkdirSync(carpeta, { recursive: true });
  let ultima = '';

  // Lo que identifica el estado de los escritos: si no cambia, no se hace otra copia igual
  const huella = (docs) => {
    let suma = 0; let max = 0;
    for (const o of docs) { suma = (suma + num(o.u) + (o.d === 1 ? 7 : 0)) % 1e12; max = Math.max(max, num(o.u)); }
    return `${docs.length}:${max}:${suma}`;
  };
  const graba = (f, txt) => {
    const tmp = `${f}.tmp`;
    const fd = fs.openSync(tmp, 'w');
    try { fs.writeSync(fd, txt); fs.fsyncSync(fd); } finally { fs.closeSync(fd); }
    fs.renameSync(tmp, f);
  };

  const lista = () => {
    let fs_ = [];
    try { fs_ = fs.readdirSync(carpeta); } catch { /* aún no hay carpeta */ }
    return fs_.map((f) => {
      const m = NOMBRE.exec(f);
      if (!m) return null;
      const t = new Date(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], +m[6]).getTime();
      let bytes = 0;
      try { bytes = fs.statSync(path.join(carpeta, f)).size; } catch { /* */ }
      return { archivo: f, t, obras: +m[7], palabras: +m[8], bytes };
    }).filter(Boolean).sort((a, b) => b.t - a.t);
  };

  const poda = () => { for (const c of lista().slice(GUARDAR)) { try { fs.unlinkSync(path.join(carpeta, c.archivo)); } catch { /* */ } } };

  // Hace una copia si algo ha cambiado desde la última. Devuelve la copia o null.
  const copia = (fuerza = false) => {
    const docs = escr.copiaTodo();
    const vivos = docs.filter((o) => o.d !== 1);
    if (!vivos.some((o) => o.t === 'p')) return null;       // sin ninguna obra no hay nada que guardar
    const h = huella(docs);
    if (!fuerza && h === ultima) return null;
    const d = new Date(ahora());
    const obras = vivos.filter((o) => o.t === 'p').length;
    const pal = vivos.filter((o) => o.t === 'e').reduce((s, o) => s + num(o.palabras), 0);
    const nombre = `copia-${d.getFullYear()}-${dos(d.getMonth() + 1)}-${dos(d.getDate())}_${dos(d.getHours())}${dos(d.getMinutes())}${dos(d.getSeconds())}-${obras}obras-${pal}pal.json`;
    graba(path.join(carpeta, nombre), JSON.stringify({ v: 1, t: d.getTime(), docs }));
    ultima = h;
    poda();
    log('escribir: copia de seguridad', nombre);
    return lista().find((c) => c.archivo === nombre) || null;
  };

  // Las obras que lleva una copia (para elegir cuál sacar)
  const obrasDe = (archivo) => {
    const f = path.join(carpeta, path.basename(archivo));
    if (!NOMBRE.test(path.basename(archivo)) || !fs.existsSync(f)) throw new Error('No encuentro esa copia.');
    const j = JSON.parse(fs.readFileSync(f, 'utf8'));
    const docs = (j.docs || []).filter((o) => o && o.d !== 1);
    return docs.filter((o) => o.t === 'p').map((o) => ({
      id: o.id, titulo: o.titulo || 'Sin título',
      escenas: docs.filter((x) => x.t === 'e' && x.pid === o.id).length,
      palabras: docs.filter((x) => x.t === 'e' && x.pid === o.id).reduce((s, x) => s + num(x.palabras), 0),
    }));
  };

  // Saca una obra de una copia como OBRA NUEVA (ids nuevos, «(copia …)» en el título): no toca lo que hay ahora
  const restaura = (archivo, obraId) => {
    const f = path.join(carpeta, path.basename(archivo));
    if (!NOMBRE.test(path.basename(archivo)) || !fs.existsSync(f)) throw new Error('No encuentro esa copia.');
    const docs = (JSON.parse(fs.readFileSync(f, 'utf8')).docs || []).filter((o) => o && o.d !== 1 && o.t !== 'l');
    const obra = docs.find((o) => o.t === 'p' && o.id === obraId);
    if (!obra) throw new Error('Esa obra no está en la copia.');
    const suyos = docs.filter((o) => o.pid === obraId || o.id === obraId);
    const nuevo = new Map(suyos.map((o) => [o.id, `${o.t}_${crypto.randomBytes(6).toString('hex')}`]));
    const mapea = (id) => (id && nuevo.has(id) ? nuevo.get(id) : id);
    const d = new Date(num(JSON.parse(fs.readFileSync(f, 'utf8')).t) || ahora());
    const etiqueta = `${dos(d.getDate())}/${dos(d.getMonth() + 1)}/${d.getFullYear()}`;
    // La obra primero, después lo demás (el orden en el que se crean no importa, pero así la obra existe cuando llegan sus cosas)
    const orden = [...suyos].sort((a, b) => (a.id === obraId ? -1 : 0) - (b.id === obraId ? -1 : 0));
    let escenas = 0;
    for (const o of orden) {
      const n = { ...o };
      for (const k of ['u', 'pu', 'dt', 'dc', 'd']) delete n[k];
      n.id = nuevo.get(o.id);
      n.pid = o.id === obraId ? n.id : mapea(o.pid);
      for (const k of ['cid', 'eid', 'pov', 'lugar']) if (n[k]) n[k] = mapea(n[k]);
      if (o.id === obraId) { n.titulo = `${o.titulo || 'Sin título'} (copia del ${etiqueta})`; delete n.arch; }
      if (o.t === 'e') escenas++;
      escr.pon(n);
    }
    return { id: nuevo.get(obraId), titulo: `${obra.titulo || 'Sin título'} (copia del ${etiqueta})`, escenas };
  };

  // Primera copia a los 20 s de abrir (por si lo último que hubo fue un cierre brusco) y luego cada rato
  const t1 = setTimeout(() => { try { copia(); } catch (e) { log('escribir: copia', e.message); } }, 20 * 1000);
  const t2 = setInterval(() => { try { copia(); } catch (e) { log('escribir: copia', e.message); } }, CADA);
  if (t1.unref) t1.unref();
  if (t2.unref) t2.unref();

  return { copia, lista, obrasDe, restaura, carpeta, parar: () => { clearTimeout(t1); clearInterval(t2); } };
}

module.exports = { iniciar, NOMBRE };
