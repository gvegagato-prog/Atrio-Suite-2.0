'use strict';
// «Escribir» en Atrio (el PC): las obras, capítulos, escenas, fichas y notas de tu cuenta. Mismo formato y mismas reglas
// de mezcla que el móvil (movil/.../Escritos.kt; el formato está en docs/escribir.md):
//   /users/<uid>/escritos/<id>      el documento
//   /users/<uid>/escritosIdx/<id>   su hora de edición «u» (el índice que se escucha en directo)
// Se guarda en archivos de este PC (un documento por archivo), se puede escribir sin conexión y, con tu cuenta, cada
// cambio sube en cuanto hay red y llega al momento a tus otros dispositivos.
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const M = require('./escritos-mezcla');
const EST = require('../renderer/revista/js/escribir-estructura.js');

const T = { OBRA: 'p', CAP: 'c', ESCENA: 'e', FICHA: 'f', NOTA: 'n', BORRADOR: 'b', HITO: 'h', LOG: 'l', PARTE: 'a', RAMA: 'r', REL: 'v' };
const LIMPIEZA = 180 * 86400000;
// Lo borrado se queda en la papelera 30 días; luego solo queda la marca de borrado (para tus otros dispositivos)
const PAPELERA = 30 * 86400000;
const VERSIONES = 40;
// Cuántas versiones de la nube se guardan de cada documento (la base para juntar lo cambiado a la vez en dos sitios)
const BASES = 3;
const LAPIDA = new Set(['id', 't', 'pid', 'u', 'pu', 'd']);
// Una marca de borrado sin nada dentro (lo que queda al vaciar la papelera, o lo que dejaban las versiones antiguas)
const lapida = (o) => !!o && o.d === 1 && Object.keys(o).every((k) => LAPIDA.has(k));

// Cuántas palabras tiene un texto (los signos del formato no cuentan); igual que Escritos.palabras del móvil
const PALABRA = /[\p{L}\p{N}]+(?:['’-][\p{L}\p{N}]+)*/gu;
const palabras = (t) => (String(t || '').match(PALABRA) || []).length;
const nuevoId = (t) => `${t}_${crypto.randomBytes(6).toString('hex')}`;
const hoy = () => Math.floor((Date.now() - new Date().getTimezoneOffset() * 60000) / 86400000);
const ordenEntre = (a, b) => (a == null && b == null ? 1000 : a == null ? b - 1000 : b == null ? a + 1000 : (a + b) / 2);
const norma = (s) => String(s || '').normalize('NFD').replace(/\p{M}+/gu, '').toLowerCase();
const num = (v) => (Number.isFinite(Number(v)) ? Number(v) : 0);

/**
 * @param dir carpeta donde se guarda
 * @param dev id de este dispositivo
 * @param net { uid(): string|null, db(method, path, body, query): Promise<any> } (la cuenta)
 */
function crear({ dir, dev, net, log = () => {} }) {
  const docs = new Map();
  const acked = new Map();
  let alCambiar = () => {};
  let error = '';
  let ultima = 0;
  let cola = Promise.resolve();
  let programada = null;
  let fallos = 0;

  const dDocs = path.join(dir, 'd');
  const dVers = path.join(dir, 'v');
  const dBases = path.join(dir, 'b');
  const baseHecha = new Map();
  const intentos = new Map();
  fs.mkdirSync(dDocs, { recursive: true });
  for (const f of fs.readdirSync(dDocs)) {
    if (!f.endsWith('.json')) continue;
    try { const o = JSON.parse(fs.readFileSync(path.join(dDocs, f), 'utf8')); if (o && o.id) docs.set(o.id, o); } catch { /* archivo dañado: se ignora */ }
  }
  try { for (const [k, v] of Object.entries(JSON.parse(fs.readFileSync(path.join(dir, 'acked.json'), 'utf8')))) acked.set(k, Number(v)); } catch { /* sin nada aún */ }

  // Escritura atómica (archivo temporal y renombrar): un corte a medias no deja un documento roto
  // (con fsync: si se va la luz o se cuelga el sistema justo después, el archivo ya está en el disco y no queda vacío)
  const graba = (f, txt) => {
    const tmp = `${f}.tmp`;
    const fd = fs.openSync(tmp, 'w');
    try { fs.writeSync(fd, txt); fs.fsyncSync(fd); } finally { fs.closeSync(fd); }
    fs.renameSync(tmp, f);
  };
  const guarda = (o) => { try { graba(path.join(dDocs, `${o.id}.json`), JSON.stringify(o)); } catch (e) { log('escritos: no se pudo guardar', e.message); } };
  const guardaIndice = () => { try { graba(path.join(dir, 'acked.json'), JSON.stringify(Object.fromEntries(acked))); } catch { /* */ } };
  const borrado = (o) => !!o && o.d === 1;
  const enPapelera = (o) => borrado(o) && !lapida(o);
  const vivos = () => [...docs.values()].filter((o) => !borrado(o));
  const porOrden = (a, b) => (num(a.orden) - num(b.orden)) || (num(a.u) - num(b.u));
  const avisa = () => { try { alCambiar(); } catch { /* */ } };

  const api = {
    T, palabras, hoy,
    onChange(f) { alCambiar = f || (() => {}); },
    estado: () => ({ error, ultima, pendientes: api.pendientes().length, papelera: api.papelera().length }),
    todos: () => vivos(),
    // Todo lo que hay (también la papelera y las marcas de borrado), para las copias de seguridad
    copiaTodo: () => [...docs.values()].map((o) => ({ ...o })),
    get: (id) => { const o = docs.get(id); return o && !borrado(o) ? o : null; },
    tipo: (t, pid) => vivos().filter((o) => o.t === t && (pid == null || o.pid === pid)).sort(porOrden),
    obras: () => api.tipo(T.OBRA),
    // Los capítulos en el orden del libro (los sueltos y los de cada parte, ver escribir-estructura.js)
    capitulos: (pid) => EST.estructura(api.tipo(T.CAP, pid), api.tipo(T.PARTE, pid)).libro,
    partes: (pid) => api.tipo(T.PARTE, pid),
    ramas: (pid) => api.tipo(T.RAMA, pid),
    relaciones: (pid) => api.tipo(T.REL, pid),
    escenas: (cid) => vivos().filter((o) => o.t === T.ESCENA && o.cid === cid).sort(porOrden),
    escenasDe: (pid) => api.capitulos(pid).flatMap((c) => api.escenas(c.id)),
    fichas: (pid) => api.tipo(T.FICHA, pid),
    notas: (pid) => api.tipo(T.NOTA, pid),
    hitos: (pid) => api.tipo(T.HITO, pid),
    borradores: (eid) => vivos().filter((o) => o.t === T.BORRADOR && o.eid === eid).sort((a, b) => a.u - b.u),

    // Pone o cambia un documento: la hora de edición siempre sube (aunque dos cambios caigan en el mismo milisegundo) y
    // apunta en «pu» de qué versión de la nube parte, para poder juntarlo luego con lo cambiado a la vez en otro sitio
    pon(o) {
      const d = ponSin(o);
      avisa();
      pronto(0.7);
      return d;
    },
    nuevo(t, pid, campos = {}) {
      const id = nuevoId(t);
      const o = { id, t, pid: t === T.OBRA ? id : pid, ...campos };
      if (o.orden == null) {
        // Al final de sus hermanos: las escenas de su capítulo; los capítulos de su parte; arriba del manuscrito, las partes y
        // los capítulos sueltos juntos; lo demás, los de su tipo
        const arriba = (x) => (x.t === T.PARTE || (x.t === T.CAP && !(x.aid && docs.get(x.aid) && !borrado(docs.get(x.aid)))));
        const hermano = (x) => x.pid === pid && (t === T.ESCENA ? x.t === t && x.cid === o.cid
          : t === T.CAP && o.aid ? x.t === t && x.aid === o.aid
            : t === T.CAP || t === T.PARTE ? arriba(x) : x.t === t);
        const ult = vivos().filter(hermano).reduce((m, x) => Math.max(m, num(x.orden)), 0);
        o.orden = ult + 1000;
      }
      if (t === T.OBRA && !o.creado) o.creado = Date.now();
      if (t === T.ESCENA && o.palabras == null) o.palabras = palabras(o.texto);
      return api.pon(o);
    },
    edita(id, cambios) {
      const d = api.get(id);
      if (!d) return null;
      const o = { ...d, ...cambios };
      for (const k of Object.keys(o)) if (o[k] === undefined || o[k] === null) delete o[k];
      if (d.t === T.ESCENA && (o.texto || '') !== (d.texto || '')) o.palabras = palabras(o.texto);
      return api.pon(o);
    },
    // El texto de una escena: cuenta lo escrito hoy y guarda
    escribe(eid, texto) {
      const d = api.get(eid);
      if (!d || (d.texto || '') === texto) return d || null;
      const antes = num(d.palabras);
      const r = api.edita(eid, { texto });
      const ahora = palabras(texto);
      if (ahora > antes) api.registra(d.pid, ahora - antes);
      return r;
    },
    // Manda a la papelera (con lo que va dentro: una obra con todo lo suyo, un capítulo con sus escenas, una escena con sus
    // borradores). Se borra también en tus otros dispositivos, pero se puede recuperar durante 30 días. Las palabras
    // escritas cada día no se borran (las escribiste).
    borra(id) {
      const d = api.get(id);
      if (!d) return;
      const v = vivos();
      const conBorradores = (e) => [e.id, ...v.filter((b) => b.t === T.BORRADOR && b.eid === e.id).map((b) => b.id)];
      let ids;
      if (d.t === T.OBRA) ids = [id, ...v.filter((o) => o.pid === id && o.id !== id && o.t !== T.LOG).map((o) => o.id)];
      else if (d.t === T.CAP) ids = [id, ...v.filter((o) => o.t === T.ESCENA && o.cid === id).flatMap(conBorradores)];
      // Una parte, con sus capítulos (y sus escenas); una rama, con sus sucesos; una ficha, con sus uniones del árbol
      else if (d.t === T.PARTE) ids = [id, ...v.filter((o) => o.t === T.CAP && o.aid === id).flatMap((c) => [c.id, ...v.filter((o) => o.t === T.ESCENA && o.cid === c.id).flatMap(conBorradores)])];
      else if (d.t === T.RAMA) ids = [id, ...v.filter((o) => o.t === T.HITO && o.rama === id).map((o) => o.id)];
      else if (d.t === T.FICHA) ids = [id, ...v.filter((o) => o.t === T.REL && (o.a === id || o.b === id)).map((o) => o.id)];
      else if (d.t === T.ESCENA) ids = conBorradores(d);
      else ids = [id];
      const ahora = Date.now();
      for (const i of ids) {
        const x = docs.get(i);
        if (!x) continue;
        const o = { ...x, d: 1, dt: ahora };
        if (i === id) delete o.dc; else o.dc = id;
        ponSin(o);
      }
      avisa(); pronto(0.7);
    },
    // Lo que hay en la papelera (lo que se borró, sin lo que se fue con ello), lo último primero
    papelera: () => [...docs.values()].filter((o) => enPapelera(o) && o.t !== T.LOG && (!o.dc || !enPapelera(docs.get(o.dc))))
      .sort((a, b) => (num(b.dt || b.u) - num(a.dt || a.u)) || (num(b.u) - num(a.u))),
    // Para enseñar la papelera: cada cosa con lo que se fue con ella y el título de su obra
    papeleraDetalle: () => api.papelera().map((d) => {
      const obra = d.t === T.OBRA ? null : api.cualquiera(d.pid);
      return { d, con: api.conElBorrado(d.id).map((x) => x.t), obra: obra ? obra.titulo || 'Sin título' : '' };
    }),
    // Lo que se fue a la papelera con id (las escenas de un capítulo, lo de una obra…)
    conElBorrado: (id) => [...docs.values()].filter((o) => enPapelera(o) && o.dc === id),
    // Un documento vivo o de la papelera (para enseñar de dónde era lo borrado)
    cualquiera: (id) => { const o = docs.get(id); return o && (!borrado(o) || enPapelera(o)) ? o : null; },
    // Saca algo de la papelera, con lo que se fue con ello. Si estaba dentro de algo que también se borró (una escena de un
    // capítulo borrado, un capítulo de una obra borrada), eso vuelve también, para que aparezca donde estaba.
    restaura(id) {
      const d = docs.get(id);
      if (!enPapelera(d)) return;
      const ids = new Set();
      const con = (x) => { if (ids.has(x.id)) return; ids.add(x.id); for (const y of api.conElBorrado(x.id)) con(y); };
      const dentroDe = (x) => (x.t === T.OBRA ? [] : x.t === T.ESCENA ? [x.cid, x.pid] : x.t === T.BORRADOR ? [x.eid, x.pid]
        : x.t === T.CAP && x.aid ? [x.aid, x.pid] : x.t === T.HITO && x.rama ? [x.rama, x.pid] : x.t === T.REL ? [x.a, x.b, x.pid].filter(Boolean) : [x.pid]);
      const padres = (x) => { for (const pid of dentroDe(x)) { const p = docs.get(pid); if (enPapelera(p) && !ids.has(p.id)) { con(p); padres(p); } } };
      con(d); padres(d);
      for (const i of ids) {
        const x = docs.get(i);
        if (!x) continue;
        const { d: _d, dt, dc, ...o } = x;
        ponSin(o);
      }
      avisa(); pronto(0.7);
    },
    // Borra para siempre lo de la papelera: id (con lo suyo), o lo de antes de «antesDe» (por defecto, hace 30 días)
    purga(id = null, antesDe = Date.now() - PAPELERA) {
      const ids = new Set();
      const con = (x) => { if (ids.has(x.id)) return; ids.add(x.id); for (const y of api.conElBorrado(x.id)) con(y); };
      if (id) { const x = docs.get(id); if (enPapelera(x)) con(x); } else for (const x of [...docs.values()]) if (enPapelera(x) && num(x.dt || x.u) < antesDe) con(x);
      if (!ids.size) return;
      for (const i of ids) {
        const x = docs.get(i);
        if (!x) continue;
        ponSin({ id: x.id, t: x.t, pid: x.pid, d: 1 });
        for (const f of [path.join(dVers, `${i}.json`), path.join(dBases, `${i}.json`)]) { try { fs.unlinkSync(f); } catch { /* */ } }
      }
      avisa(); pronto(0.7);
    },
    vaciaPapelera() { api.purga(null, Infinity); },
    mueve(id, antes, despues) {
      const a = antes && docs.get(antes) ? num(docs.get(antes).orden) : null;
      const b = despues && docs.get(despues) ? num(docs.get(despues).orden) : null;
      return api.edita(id, { orden: ordenEntre(a, b) });
    },
    mueveEscena(id, cid, antes, despues) {
      const a = antes && docs.get(antes) ? num(docs.get(antes).orden) : null;
      const b = despues && docs.get(despues) ? num(docs.get(despues).orden) : null;
      return api.edita(id, { cid, orden: ordenEntre(a, b) });
    },
    // Pasa la «parte» antigua de los capítulos (un texto) a partes de verdad, con el mismo id en todos los dispositivos (si
    // otro lo hizo a la vez, salen las mismas partes y no se duplican). Devuelve cuántos capítulos ha movido.
    migraPartes(pid) {
      const plan = EST.planMigracion(pid, api.tipo(T.CAP, pid), [...docs.values()].filter((o) => o.t === T.PARTE && o.pid === pid));
      if (!plan.asigna.length) return 0;
      for (const p of plan.partes) ponSin({ id: p.id, t: T.PARTE, pid, titulo: p.titulo, orden: p.orden });
      for (const x of plan.asigna) { const c = docs.get(x.cid); if (c) ponSin({ ...c, aid: x.aid, parte: '' }); }
      avisa(); pronto(0.7);
      return plan.asigna.length;
    },
    // Un capítulo a otra parte (o suelto, con aid vacío), al final o entre dos
    mueveCapitulo(id, aid, antes, despues) {
      const a = antes && docs.get(antes) ? num(docs.get(antes).orden) : null;
      const b = despues && docs.get(despues) ? num(docs.get(despues).orden) : null;
      let orden = ordenEntre(a, b);
      if (a == null && b == null) {
        const arriba = (x) => (x.t === T.PARTE || (x.t === T.CAP && !(x.aid && api.get(x.aid))));
        const hs = vivos().filter((x) => x.id !== id && x.pid === (api.get(id) || {}).pid && (aid ? x.t === T.CAP && x.aid === aid : arriba(x)));
        orden = hs.reduce((m, x) => Math.max(m, num(x.orden)), 0) + 1000;
      }
      return api.edita(id, { aid: aid || null, orden });
    },
    duplicaEscena(id) {
      const d = api.get(id);
      if (!d) return null;
      const sig = api.escenas(d.cid).find((e) => num(e.orden) > num(d.orden));
      return api.nuevo(T.ESCENA, d.pid, { cid: d.cid, titulo: `${d.titulo || ''} (copia)`, texto: d.texto || '', resumen: d.resumen || '', orden: ordenEntre(num(d.orden), sig ? num(sig.orden) : null) });
    },

    // ---- Palabras del día (por dispositivo; el total es la suma)
    registra(pid, n) {
      if (!(n > 0)) return;
      const id = `l_${pid}_${hoy()}`;
      const d = docs.get(id);
      const dv = { ...((d && d.dev) || {}) };
      dv[dev] = num(dv[dev]) + n;
      api.pon({ id, t: T.LOG, pid, dia: hoy(), dev: dv });
    },
    escritas(dia, pid) {
      let n = 0;
      for (const o of vivos()) if (o.t === T.LOG && o.dia === dia && (pid == null || o.pid === pid)) n += Object.values(o.dev || {}).reduce((s, v) => s + num(v), 0);
      return n;
    },
    ultimosDias(dias, pid) { const h = hoy(); return Array.from({ length: dias }, (_, i) => { const d = h - dias + 1 + i; return [d, api.escritas(d, pid)]; }); },
    racha(pid) { let d = hoy(); if (api.escritas(d, pid) === 0) d--; let n = 0; while (api.escritas(d, pid) > 0) { n++; d--; } return n; },
    palabrasObra: (pid) => api.escenasDe(pid).reduce((s, e) => s + num(e.palabras), 0),
    palabrasCapitulo: (cid) => api.escenas(cid).reduce((s, e) => s + num(e.palabras), 0),

    // ---- Versiones (solo en este dispositivo)
    versiones(eid) { try { return JSON.parse(fs.readFileSync(path.join(dVers, `${eid}.json`), 'utf8')); } catch { return []; } },
    instantanea(eid, texto, { hueco = 300000, fuerza = false } = {}) {
      if (!String(texto || '').trim()) return false;
      const v = api.versiones(eid);
      const ult = v[v.length - 1];
      if (ult && ult.t === texto) return false;
      if (!fuerza && ult && Date.now() - ult.u < hueco) return false;
      v.push({ u: Date.now(), p: palabras(texto), t: texto });
      try { fs.mkdirSync(dVers, { recursive: true }); graba(path.join(dVers, `${eid}.json`), JSON.stringify(v.slice(-VERSIONES))); } catch { /* */ }
      return true;
    },

    // ---- Buscar
    busca(pid, q, limite = 200) {
      const n = norma(q);
      if (!n.trim()) return [];
      const out = [];
      const mira = (doc, donde, txt) => {
        const t = norma(txt);
        let i = t.indexOf(n); let veces = 0;
        while (i >= 0 && out.length < limite && veces < 8) {
          const a = Math.max(0, i - 40); const b = Math.min(txt.length, i + n.length + 60);
          out.push({ doc, donde, extracto: (a > 0 ? '…' : '') + txt.slice(a, b).replace(/\n/g, ' ') + (b < txt.length ? '…' : ''), desde: i });
          veces++; i = t.indexOf(n, i + n.length);
        }
      };
      for (const e of api.escenasDe(pid)) { mira(e, `Escena · ${e.titulo || ''}`, e.texto || ''); mira(e, `Escena · ${e.titulo || ''}`, e.resumen || ''); }
      for (const f of api.fichas(pid)) mira(f, `Ficha · ${f.nombre || ''}`, `${f.nombre || ''} ${f.alias || ''} ${f.resumen || ''} ${f.texto || ''}`);
      for (const nt of api.notas(pid)) mira(nt, `Nota · ${nt.titulo || ''}`, `${nt.titulo || ''} ${nt.texto || ''}`);
      return out;
    },
    reemplaza(pid, q, por, mayusculas = false) {
      if (!q) return 0;
      const rx = new RegExp(q.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), mayusculas ? 'g' : 'gi');
      let n = 0;
      for (const e of api.escenasDe(pid)) {
        const t = e.texto || '';
        const c = (t.match(rx) || []).length;
        if (c > 0) { n += c; api.instantanea(e.id, t, { fuerza: true }); api.escribe(e.id, t.replace(rx, () => por)); }
      }
      return n;
    },
    fichasEn(pid, texto) {
      const t = ` ${norma(texto).replace(/[^\p{L}\p{N}]+/gu, ' ')} `;
      return api.fichas(pid).filter((f) => [f.nombre, ...String(f.alias || '').split(',')].map((x) => norma(x).trim().replace(/[^\p{L}\p{N}]+/gu, ' ')).filter((x) => x.length >= 2).some((x) => t.includes(` ${x} `)));
    },

    // ---- Sincronización
    pendientes: () => [...docs.values()].filter((o) => (acked.has(o.id) ? acked.get(o.id) : -1) !== o.u),
    volcado: () => [...docs.values()].sort((a, b) => (a.id < b.id ? -1 : 1)),
    olvidaNube() { acked.clear(); guardaIndice(); },
    // Si ahora estás en otra cuenta que la última vez, todo lo escrito aquí se sube a la nueva
    cuenta(uid) {
      if (!uid) return;
      const f = path.join(dir, 'cuenta.txt');
      let antes = '';
      try { antes = fs.readFileSync(f, 'utf8'); } catch { /* primera vez */ }
      if (antes === uid) return;
      if (antes) api.olvidaNube();
      try { fs.writeFileSync(f, uid); } catch { /* */ }
    },

    /** Sube lo pendiente y baja lo nuevo. Una a la vez. Lanza un error si no hay conexión. */
    sincroniza() {
      const p = cola.then(async () => {
        const uid = net.uid();
        if (!uid) return;
        await bajaYa(uid);
        await subeYa(uid);
        error = ''; ultima = Date.now(); fallos = 0;
        avisa();
      });
      cola = p.catch(() => {});
      return p.catch((e) => { error = e.message || 'sin conexión'; avisa(); throw e; });
    },
    /** Programa subir (y bajar) dentro de [s] segundos; sin conexión se reintenta solo (5 s, 15 s, 45 s… hasta 5 min). */
    pronto(s = 0.7) { pronto(s); },
    bajaPronto() { pronto(0.15); },
    parar() { clearTimeout(programada); programada = null; },
  };

  function pronto(s) {
    if (!net.uid()) return;
    clearTimeout(programada);
    programada = setTimeout(async () => {
      programada = null;
      try { await api.sincroniza(); fallos = 0; if (api.pendientes().length) pronto(0.7); } catch (e) {
        fallos++;
        if (fallos <= 8) pronto(Math.min(300, 5 * 3 ** (fallos - 1)));
      }
    }, s * 1000);
  }

  async function subeYa(uid) {
    for (;;) {
      const quedan = api.pendientes().sort((a, b) => a.u - b.u);
      if (!quedan.length) return;
      const lote = []; let peso = 0;
      for (const d of quedan) { const len = JSON.stringify(d).length; if (lote.length && (peso + len > 600000 || lote.length >= 40)) break; lote.push(d); peso += len; }
      const body = {};
      for (const d of lote) {
        body[`escritos/${d.id}`] = d; body[`escritosIdx/${d.id}`] = d.u;
        if (!intentos.has(d.id)) intentos.set(d.id, new Set());
        intentos.get(d.id).add(d.u);
      }
      await net.db('PATCH', `/users/${uid}`, body, 'print=silent');
      for (const s of lote) {
        acked.set(s.id, s.u);
        intentos.delete(s.id);
        // Si mientras subía se siguió escribiendo, lo de ahora parte de lo que acaba de subir
        const ahora = docs.get(s.id);
        if (ahora && ahora.u !== s.u && ahora.t !== T.LOG) { apuntaBase(s); const o = { ...ahora, pu: s.u }; docs.set(s.id, o); guarda(o); }
      }
      guardaIndice();
    }
  }

  async function bajaYa(uid) {
    const idx = (await net.db('GET', `/users/${uid}/escritosIdx`)) || {};
    const faltan = [];
    for (const [id, ru] of Object.entries(idx)) {
      // (con la misma hora que lo de aquí también se baja: dos dispositivos pueden guardar en el mismo milisegundo)
      if (acked.get(id) === ru) continue;
      faltan.push(id);
    }
    if (!faltan.length) { guardaIndice(); return; }
    let remotos = {};
    if (faltan.length > 15) remotos = (await net.db('GET', `/users/${uid}/escritos`)) || {};
    else for (const id of faltan) { const r = await net.db('GET', `/users/${uid}/escritos/${id}`); if (r) remotos[id] = r; }
    let cambio = false;
    for (const id of faltan) if (remotos[id] && aplica(id, remotos[id])) cambio = true;
    guardaIndice();
    if (cambio) avisa();
  }

  function ponSin(o) {
    const antes = docs.get(o.id);
    // Se deja de estar igual que en la nube: esa versión es la base si luego hay que juntar
    if (antes && antes.t !== T.LOG && acked.get(o.id) === num(antes.u)) apuntaBase(antes);
    const d = { ...o, u: Math.max(Date.now(), (antes ? num(antes.u) : 0) + 1) };
    if (d.t === T.LOG) delete d.pu; else d.pu = acked.has(d.id) ? acked.get(d.id) : 0;
    docs.set(d.id, d);
    guarda(d);
    return d;
  }

  // ---- Bases: versiones que estuvieron en la nube, para juntar lo cambiado a la vez en dos sitios
  function apuntaBase(o) {
    const u = num(o.u);
    if (!o.id || o.t === T.LOG || lapida(o) || baseHecha.get(o.id) === u) return;
    baseHecha.set(o.id, u);
    const f = path.join(dBases, `${o.id}.json`);
    let l = [];
    try { l = JSON.parse(fs.readFileSync(f, 'utf8')); } catch { /* primera */ }
    l = [...l.filter((x) => num(x.u) !== u), o].slice(-BASES);
    try { fs.mkdirSync(dBases, { recursive: true }); graba(f, JSON.stringify(l)); } catch { /* */ }
  }
  function base(id, u) {
    if (!(u > 0)) return null;
    try { return JSON.parse(fs.readFileSync(path.join(dBases, `${id}.json`), 'utf8')).filter((x) => num(x.u) === u).pop() || null; } catch { return null; }
  }
  function adopta(r) { docs.set(r.id, r); acked.set(r.id, num(r.u)); guarda(r); }

  /**
   * Mezcla un documento que ha llegado de la nube. Devuelve true si cambia lo que se ve aquí.
   * Si aquí no había cambios y lo de fuera parte de lo de aquí, se queda lo de fuera. Si se cambió en los dos sitios a la
   * vez (o lo de fuera se subió sin haber visto lo de aquí), se juntan a partir de la versión de la que salen los dos (la
   * base): lo que cambió en uno solo se queda, y si los dos cambiaron el mismo campo o el mismo párrafo, manda el más nuevo;
   * en una escena, el texto que no se queda se guarda como borrador (no se pierde nada). Ver docs/escribir.md.
   */
  function aplica(id, r) {
    r = { ...r, id: r.id || id };
    const ru = num(r.u);
    const l = docs.get(id);
    const ack = acked.has(id) ? acked.get(id) : 0;
    if (!l) { adopta(r); return true; }
    if (l.t === T.LOG || r.t === T.LOG) return mezclaLog(l, r);
    const lu = num(l.u);
    // Misma hora y mismo contenido: es la misma versión. Misma hora y otro contenido: se guardó a la vez en otro sitio
    const choca = lu === ru && M.contenido(l) !== M.contenido(r);
    if (lu === ru && !choca) { acked.set(id, ru); return false; }
    // Lo subió este dispositivo y no llegó a confirmarse (se cortó la red a mitad): ya está en lo de aquí
    if (!choca && intentos.has(id) && intentos.get(id).has(ru)) {
      intentos.delete(id);
      acked.set(id, ru);
      apuntaBase(r);
      const o = { ...l, pu: ru }; docs.set(id, o); guarda(o);
      return false;
    }
    const pendiente = lu !== ack || choca;
    const rpu = r.pu == null ? -1 : num(r.pu);
    if (!pendiente) {
      // Lo de fuera parte de lo de aquí (o viene de un dispositivo con una versión antigua, sin «pu»)
      if (rpu === lu || (rpu === -1 && ru > lu)) { adopta(r); return true; }
      if (rpu === -1) { acked.set(id, ru); return false; } // lo de aquí es más nuevo: queda pendiente y se sube
    }
    const anc = base(id, rpu) || (pendiente ? base(id, ack) : null);
    // Sin base y sin cambios aquí: lo de fuera viene de lo de aquí por versiones que no se llegaron a ver
    if (!pendiente && !anc) { adopta(r); return true; }
    if (lapida(l) || lapida(r)) {
      // Una marca de borrado sin nada dentro no se junta: manda la más nueva
      acked.set(id, ru);
      if (ru > lu) { adopta(r); return true; }
      return false;
    }
    return junta(l, r, anc);
  }

  function junta(l, r, anc) {
    const lu = num(l.u); const ru = num(r.u);
    const nuestro = lu > ru || (lu === ru && M.contenido(l) > M.contenido(r));
    const m = M.docs(anc, l, r, nuestro, palabras);
    if (l.t === T.ESCENA && m.chocan.has('texto')) aparta(l, nuestro ? r : l, m.o.texto || '');
    apuntaBase(r);
    acked.set(l.id, ru);
    const antes = M.contenido(l);
    if (M.canon(m.o) === M.contenido(r)) { adopta(r); return antes !== M.contenido(r); }
    const d = { ...m.o, u: Math.max(lu, ru) + 1, pu: ru };
    docs.set(l.id, d); guarda(d);
    return antes !== M.contenido(d);
  }

  // El texto de «pierde» se guarda como borrador de la escena (si no es el que se ha quedado). Mismo id en todos los dispositivos.
  function aparta(l, pierde, queda) {
    const txt = String(pierde.texto || '');
    if (!txt.trim() || txt === queda) return;
    const bid = `bc_${l.id}_${num(pierde.u)}`;
    if (docs.has(bid)) return;
    const nombre = `Versión en conflicto (${new Intl.DateTimeFormat('es-ES', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }).format(new Date(num(pierde.u))).replace(/\./g, '').replace(',', '')})`;
    const o = { id: bid, t: T.BORRADOR, pid: l.pid, eid: l.id, conflicto: 1, nombre, texto: txt, u: num(pierde.u) };
    docs.set(bid, o); guarda(o);
  }

  function mezclaLog(l, r) {
    const ru = num(r.u);
    const a = l.dev || {}; const b = r.dev || {};
    const m = {};
    for (const k of new Set([...Object.keys(a), ...Object.keys(b)])) m[k] = Math.max(num(a[k]), num(b[k]));
    const igual = Object.keys(m).length === Object.keys(b).length && Object.keys(m).every((k) => num(m[k]) === num(b[k]));
    const o = { ...r, dev: m, u: igual ? ru : Math.max(num(l.u), ru) + 1 };
    const cambia = JSON.stringify(o) !== JSON.stringify(l);
    docs.set(l.id, o); acked.set(l.id, ru); guarda(o);
    return cambia;
  }

  /** Quita de aquí y de la nube las marcas de borrado de hace más de 180 días. */
  api.limpia = async () => {
    const hace = Date.now() - LIMPIEZA;
    const viejos = [...docs.values()].filter((o) => borrado(o) && o.u < hace && acked.get(o.id) === o.u);
    if (!viejos.length) return;
    const uid = net.uid();
    if (uid) {
      const body = {};
      for (const o of viejos) { body[`escritos/${o.id}`] = null; body[`escritosIdx/${o.id}`] = null; }
      try { await net.db('PATCH', `/users/${uid}`, body, 'print=silent'); } catch { return; }
    }
    for (const o of viejos) { docs.delete(o.id); acked.delete(o.id); for (const f of [path.join(dDocs, `${o.id}.json`), path.join(dBases, `${o.id}.json`)]) { try { fs.unlinkSync(f); } catch { /* */ } } }
    guardaIndice();
  };
  return api;
}

module.exports = { crear, palabras, T, hoy, lapida, PAPELERA };
