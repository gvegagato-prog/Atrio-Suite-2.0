'use strict';
// El lector de libros (EPUB, PDF, MOBI/AZW3, FB2 y CBZ) en el ordenador: la MISMA página que el móvil (movil/.../assets/lector,
// foliate-js y pdf.js), dentro de una ventana, con el mismo puente (window.Atrio, en lector-preload.js). Lo leído viaja con la
// cuenta (lista «lectura») con la lógica de lector-estado.js, la misma que el móvil.
//
// En el disco (carpeta de datos de Atrio):  lector/estado.json { "<id>": estado }   y   lector/libros/<id>/<archivo>
const fs = require('fs');
const path = require('path');
const E = require('./lector-estado');
const diccionario = require('./diccionario');

const TIPOS = {
  html: 'text/html; charset=utf-8', js: 'text/javascript; charset=utf-8', mjs: 'text/javascript; charset=utf-8', css: 'text/css; charset=utf-8',
  json: 'application/json', svg: 'image/svg+xml', png: 'image/png', jpg: 'image/jpeg', jpeg: 'image/jpeg', gif: 'image/gif', webp: 'image/webp',
  ttf: 'font/ttf', otf: 'font/otf', woff: 'font/woff', woff2: 'font/woff2', wasm: 'application/wasm', txt: 'text/plain; charset=utf-8',
  bcmap: 'application/octet-stream', pfb: 'application/octet-stream',
  epub: 'application/epub+zip', pdf: 'application/pdf', mobi: 'application/x-mobipocket-ebook', azw3: 'application/vnd.amazon.ebook',
  fb2: 'application/x-fictionbook+xml', cbz: 'application/vnd.comicbook+zip', zip: 'application/zip',
};
const EXTENSIONES = ['epub', 'pdf', 'mobi', 'azw', 'azw3', 'fb2', 'cbz', 'zip'];
const tipoDe = (f) => TIPOS[path.extname(f).slice(1).toLowerCase()] || 'application/octet-stream';
const SUBE_CADA = 30 * 1000;   // por dónde vas cambia en cada página: a la cuenta, como mucho cada medio minuto (y al cerrar)

function crear({ dir, assetsDir, ronron, nube, log = () => {}, fetchImpl, ajustes = () => ({}), acento = () => null, ahora = () => Date.now(), alCambiar = () => {} }) {
  const carpeta = path.join(dir, 'lector');
  const archivoEstado = path.join(carpeta, 'estado.json');
  fs.mkdirSync(path.join(carpeta, 'libros'), { recursive: true });
  let estados = {};
  try { estados = JSON.parse(fs.readFileSync(archivoEstado, 'utf8')) || {}; } catch { /* aún no hay nada leído */ }
  const dispositivo = () => { try { return ronron.deviceId(); } catch { return 'local'; } };

  const graba = () => {
    const tmp = `${archivoEstado}.tmp`;
    const fd = fs.openSync(tmp, 'w');
    try { fs.writeSync(fd, JSON.stringify(estados)); fs.fsyncSync(fd); } finally { fs.closeSync(fd); }
    fs.renameSync(tmp, archivoEstado);
  };

  const estado = (id) => estados[String(id)] || {};
  const todos = () => estados;
  const dirLibro = (id) => path.join(carpeta, 'libros', String(Number(id)));
  const archivoDe = (id) => {
    const n = estado(id).name;
    if (!n) return null;
    const f = path.join(dirLibro(id), path.basename(n));
    return fs.existsSync(f) ? f : null;
  };

  // ---- Subir lo leído a la cuenta (como mucho cada 30 s mientras lees; ya, al cerrar) ----
  const pendiente = new Set();
  let temporizador = null;
  const sube = async () => {
    clearTimeout(temporizador); temporizador = null;
    for (const id of [...pendiente]) {
      pendiente.delete(id);
      try { await ronron.edit({ op: 'lecturaPut', id: Number(id), s: E.paraLaCuenta(estado(id)) }); } catch (e) { log('lector: subir', e.message); pendiente.add(id); }
    }
  };
  const subeLuego = (id, ya = false) => {
    pendiente.add(String(id));
    if (ya) { sube(); return; }
    if (!temporizador) { temporizador = setTimeout(sube, SUBE_CADA); if (temporizador.unref) temporizador.unref(); }
  };

  /** Guarda el estado de un libro. Lo que viaja con la cuenta se sube; lo que llega de otro dispositivo (quieto) no. */
  const pon = (id, o, { quieto = false, ya = false } = {}) => {
    o.syncAt = ahora();
    estados[String(id)] = o;
    graba();
    alCambiar(Number(id));
    if (!quieto) subeLuego(id, ya);
  };
  const edita = (id, f, opciones) => { const o = JSON.parse(JSON.stringify(estado(id))); f(o); pon(id, o, opciones); return o; };

  /** Lo que llega de otros dispositivos con la cuenta (lista «lectura»): por dónde vas, subrayados y tiempo. */
  const recibe = (lista) => {
    let cambios = 0;
    for (const r of Array.isArray(lista) ? lista : []) {
      if (!r || !Number(r.id)) continue;
      const nuevo = E.aplicaRemoto(estado(r.id), r, dispositivo());
      if (!nuevo) continue;
      estados[String(r.id)] = nuevo; cambios++;
    }
    if (cambios) { graba(); alCambiar(null); }
    return cambios;
  };

  // ---- El archivo del libro ----
  const adjunta = (id, origen) => {
    const nombre = path.basename(origen);
    const ext = path.extname(nombre).slice(1).toLowerCase();
    if (!EXTENSIONES.includes(ext)) throw new Error('Ese archivo no es un libro que Atrio sepa abrir (EPUB, PDF, MOBI, AZW3, FB2 o CBZ).');
    const d = dirLibro(id);
    const viejo = estado(id);
    fs.mkdirSync(d, { recursive: true });
    // Un archivo distinto empieza de cero (los subrayados de otro archivo no encajarían); lo leído que llegó de otro
    // dispositivo (aún sin archivo aquí) se conserva, y también si es el mismo archivo
    const mismo = !viejo.name || viejo.name === nombre;
    const tmp = path.join(d, `${nombre}.parte`);
    fs.copyFileSync(origen, tmp);
    fs.renameSync(tmp, path.join(d, nombre));
    if (viejo.name && viejo.name !== nombre) { try { fs.unlinkSync(path.join(d, path.basename(viejo.name))); } catch { /* */ } }
    const o = mismo ? JSON.parse(JSON.stringify(viejo)) : {};
    o.name = nombre; o.added = ahora();
    pon(id, o);
    return o;
  };
  const adjuntaBuffer = (id, nombre, buf) => {
    const d = dirLibro(id);
    fs.mkdirSync(d, { recursive: true });
    const tmp = path.join(d, `${path.basename(nombre)}.parte`);
    fs.writeFileSync(tmp, buf);
    fs.renameSync(tmp, path.join(d, path.basename(nombre)));
    // (lo leído en otro dispositivo se conserva: llega con la cuenta)
    edita(id, (o) => { o.name = path.basename(nombre); o.added = ahora(); }, { quieto: true });
  };
  const quita = (id) => {
    const f = archivoDe(id);
    if (f) { try { fs.unlinkSync(f); } catch { /* */ } }
    edita(id, (o) => { delete o.name; delete o.added; }, { quieto: true });
  };
  /** Un libro de tu nube (lista «nube», clave libro:<id>): se baja y queda como archivo de Atrio. */
  const bajaDeNube = async (id) => {
    const x = ((ronron.data() || {}).nube || []).find((n) => n && n.k === `libro:${id}`);
    if (!x) throw new Error('Ese libro no está en tu nube.');
    const buf = await nube.bajar(x);
    adjuntaBuffer(id, x.n || x.name || `libro-${id}.epub`, buf);
    return estado(id);
  };
  const enNube = (id) => !!(((ronron.data() || {}).nube || []).find((n) => n && n.k === `libro:${id}`));

  // ---- Lo que ve la página del lector ----
  const paraLaPagina = (id, { titulo = '', goto = null } = {}) => {
    const o = estado(id);
    const [cfi, frac] = E.empiezaEn(o);
    const base = ajustes() || {};
    const sets = { ...base, ...(o.settings || {}) };
    delete sets.pdfTexto; delete sets.pdfForzar;
    if (o.settings && 'pdfTexto' in o.settings) sets.pdfTexto = o.settings.pdfTexto;
    if (o.settings && 'pdfForzar' in o.settings) sets.pdfForzar = o.settings.pdfForzar;
    const libro = ((ronron.data() || {}).books || []).find((b) => Number(b.id) === Number(id)) || {};
    return {
      title: titulo, file: o.name || 'libro', location: cfi || null, fraction: frac, goto,
      stats: o.stats || {}, device: dispositivo(), accent: acento(), fondo: null, meta: 0, autor: libro.creator || '',
      settings: sets, annotations: o.notes || [], bookmarks: o.marks || [],
    };
  };

  // ---- Lo que la página le pide al ordenador (el puente) ----
  const puente = {
    saveProgress(id, cfi, fraction, label) {
      const o = JSON.parse(JSON.stringify(estado(id)));
      // Al abrir el libro el lector «llega» a la posición guardada y avisa igualmente: eso no es leer
      if ('cfi' in o && Math.abs((Number(o.fraction) || -1) - fraction) < 0.0005) return;
      o.cfi = cfi; o.fraction = fraction; o.label = label; o.read = ahora();
      pon(id, o);
      puente.candidata(id, cfi, fraction, label);
    },
    // Lo más lejos que has llegado cuenta si te quedas ahí unos segundos o al salir
    candidata(id, cfi, fraction, label) {
      const o = estado(id);
      clearTimeout(candidatas[id]);
      if (fraction > (Number(o.bestFrac) || 0)) {
        pendientes[id] = [cfi, fraction, label];
        candidatas[id] = setTimeout(() => puente.confirma(id), 6000);
        if (candidatas[id].unref) candidatas[id].unref();
      } else delete pendientes[id];
    },
    confirma(id) {
      clearTimeout(candidatas[id]);
      const c = pendientes[id]; delete pendientes[id];
      if (!c) return;
      const o = estado(id);
      if (c[1] <= (Number(o.bestFrac) || 0)) return;
      edita(id, (x) => { x.bestCfi = c[0]; x.bestFrac = c[1]; x.bestLabel = c[2]; });
      subeLuego(id);   // (tu otro dispositivo la ve casi al momento)
    },
    saveSettings(id, json) { edita(id, (o) => { o.settings = JSON.parse(json); }); },
    saveAnnotations(id, json) { edita(id, (o) => { o.notes = JSON.parse(json); }); },
    saveBookmarks(id, json) { edita(id, (o) => { o.marks = JSON.parse(json); }); },
    saveStats(id, json) {
      edita(id, (o) => { o.stats = E.mezclaStats(o.stats, JSON.parse(json), dispositivo(), dispositivo()); });
    },
  };
  const candidatas = {}; const pendientes = {};

  // Significado de una palabra: el Wikcionario en español. (El idioma de la frase no se detecta aquí: se busca en español.)
  const busca = async (palabra) => {
    const o = { lang: 'es' };
    try {
      const d = await diccionario.define(palabra, fetchImpl);
      o.defs = d.defs;
      if (d.defs.length && d.palabra.toLowerCase() !== String(palabra).toLowerCase()) o.lemma = d.palabra;
      if (d.nota) o.nota = d.nota.replace(/\.$/, '');
      if (!d.defs.length) o.msg = 'El Wikcionario no tiene esta palabra. Prueba con la RAE.';
    } catch { o.msg = 'No se ha podido consultar el Wikcionario (¿sin conexión?).'; }
    return o;
  };

  // ---- Servir la página del lector: atrio-lector://l<id>/… (la página) y …/libro/<archivo> (el libro) ----
  const respuesta = (id, ruta) => {
    const r = decodeURIComponent(ruta.replace(/^\/+/, ''));
    if (r.startsWith('libro/')) {
      const f = archivoDe(id);
      if (!f) return new Response('No hay archivo', { status: 404 });
      return new Response(fs.readFileSync(f), { headers: { 'content-type': tipoDe(f), 'cache-control': 'no-store' } });
    }
    const f = path.normalize(path.join(assetsDir, r || 'index.html'));
    if (!f.startsWith(path.normalize(assetsDir + path.sep)) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) return new Response('No encontrado', { status: 404 });
    return new Response(fs.readFileSync(f), { headers: { 'content-type': tipoDe(f), 'cache-control': 'no-store' } });
  };
  const manejaProtocolo = (req) => {
    const u = new URL(req.url);
    const m = /^l(\d+)$/.exec(u.hostname);
    if (!m) return new Response('No encontrado', { status: 404 });
    return respuesta(Number(m[1]), u.pathname);
  };

  /** Al cerrar la ventana de un libro (o la app): lo último sube ya. */
  const cierra = async (id) => {
    puente.confirma(id);
    if (id != null) pendiente.add(String(id));
    await sube();
  };

  return { estado, todos, archivoDe, adjunta, adjuntaBuffer, quita, bajaDeNube, enNube, recibe, paraLaPagina, puente, busca, manejaProtocolo, cierra, sube, pon, edita, carpeta, EXTENSIONES };
}

module.exports = { crear, EXTENSIONES, tipoDe };
