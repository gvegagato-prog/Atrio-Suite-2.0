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

const T = { OBRA: 'p', CAP: 'c', ESCENA: 'e', FICHA: 'f', NOTA: 'n', BORRADOR: 'b', HITO: 'h', LOG: 'l' };
const LIMPIEZA = 180 * 86400000;
const VERSIONES = 40;

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
  fs.mkdirSync(dDocs, { recursive: true });
  for (const f of fs.readdirSync(dDocs)) {
    if (!f.endsWith('.json')) continue;
    try { const o = JSON.parse(fs.readFileSync(path.join(dDocs, f), 'utf8')); if (o && o.id) docs.set(o.id, o); } catch { /* archivo dañado: se ignora */ }
  }
  try { for (const [k, v] of Object.entries(JSON.parse(fs.readFileSync(path.join(dir, 'acked.json'), 'utf8')))) acked.set(k, Number(v)); } catch { /* sin nada aún */ }

  // Escritura atómica (archivo temporal y renombrar): un corte a medias no deja un documento roto
  const graba = (f, txt) => { const tmp = `${f}.tmp`; fs.writeFileSync(tmp, txt); fs.renameSync(tmp, f); };
  const guarda = (o) => { try { graba(path.join(dDocs, `${o.id}.json`), JSON.stringify(o)); } catch (e) { log('escritos: no se pudo guardar', e.message); } };
  const guardaIndice = () => { try { graba(path.join(dir, 'acked.json'), JSON.stringify(Object.fromEntries(acked))); } catch { /* */ } };
  const borrado = (o) => o && o.d === 1;
  const vivos = () => [...docs.values()].filter((o) => !borrado(o));
  const porOrden = (a, b) => (num(a.orden) - num(b.orden)) || (num(a.u) - num(b.u));
  const avisa = () => { try { alCambiar(); } catch { /* */ } };

  const api = {
    T, palabras, hoy,
    onChange(f) { alCambiar = f || (() => {}); },
    estado: () => ({ error, ultima, pendientes: api.pendientes().length }),
    todos: () => vivos(),
    get: (id) => { const o = docs.get(id); return o && !borrado(o) ? o : null; },
    tipo: (t, pid) => vivos().filter((o) => o.t === t && (pid == null || o.pid === pid)).sort(porOrden),
    obras: () => api.tipo(T.OBRA),
    capitulos: (pid) => api.tipo(T.CAP, pid),
    escenas: (cid) => vivos().filter((o) => o.t === T.ESCENA && o.cid === cid).sort(porOrden),
    escenasDe: (pid) => api.capitulos(pid).flatMap((c) => api.escenas(c.id)),
    fichas: (pid) => api.tipo(T.FICHA, pid),
    notas: (pid) => api.tipo(T.NOTA, pid),
    hitos: (pid) => api.tipo(T.HITO, pid),
    borradores: (eid) => vivos().filter((o) => o.t === T.BORRADOR && o.eid === eid).sort((a, b) => a.u - b.u),

    // Pone o cambia un documento: la hora de edición siempre sube (aunque dos cambios caigan en el mismo milisegundo)
    pon(o) {
      const antes = docs.get(o.id);
      const d = { ...o, u: Math.max(Date.now(), (antes ? num(antes.u) : 0) + 1) };
      docs.set(d.id, d);
      guarda(d);
      avisa();
      pronto(0.7);
      return d;
    },
    nuevo(t, pid, campos = {}) {
      const id = nuevoId(t);
      const o = { id, t, pid: t === T.OBRA ? id : pid, ...campos };
      if (o.orden == null) {
        const ult = vivos().filter((x) => x.t === t && x.pid === pid && (t !== T.ESCENA || x.cid === o.cid)).reduce((m, x) => Math.max(m, num(x.orden)), 0);
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
    // Borra (queda la marca, para que se borre también en tus otros dispositivos); una obra borra todo lo suyo
    borra(id) {
      const d = docs.get(id);
      if (!d) return;
      let ids;
      if (d.t === T.OBRA) ids = [...docs.values()].filter((o) => o.pid === id).map((o) => o.id);
      else if (d.t === T.CAP) ids = [id, ...[...docs.values()].filter((o) => o.t === T.ESCENA && o.cid === id).flatMap((e) => [e.id, ...[...docs.values()].filter((b) => b.t === T.BORRADOR && b.eid === e.id).map((b) => b.id)])];
      else if (d.t === T.ESCENA) ids = [id, ...[...docs.values()].filter((o) => o.t === T.BORRADOR && o.eid === id).map((o) => o.id)];
      else ids = [id];
      const ahora = Date.now();
      for (const i of ids) {
        const x = docs.get(i);
        if (!x) continue;
        const o = { id: x.id, t: x.t, pid: x.pid, d: 1, u: Math.max(ahora, num(x.u) + 1) };
        docs.set(i, o); guarda(o);
        if (x.t === T.ESCENA) { try { fs.unlinkSync(path.join(dVers, `${i}.json`)); } catch { /* */ } }
      }
      if (d.t === T.ESCENA) for (const h of api.hitos(d.pid)) if (h.eid === id) { const { eid, ...r } = h; api.pon(r); }
      avisa(); pronto(0.7);
    },
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
      for (const d of lote) { body[`escritos/${d.id}`] = d; body[`escritosIdx/${d.id}`] = d.u; }
      await net.db('PATCH', `/users/${uid}`, body, 'print=silent');
      for (const d of lote) acked.set(d.id, d.u);
      guardaIndice();
    }
  }

  async function bajaYa(uid) {
    const idx = (await net.db('GET', `/users/${uid}/escritosIdx`)) || {};
    const faltan = [];
    for (const [id, ru] of Object.entries(idx)) {
      const l = docs.get(id);
      // (el registro del día no se da por igual solo por la hora: dos dispositivos pueden apuntarlo en el mismo milisegundo)
      if (l && l.u === ru && l.t !== T.LOG) { acked.set(id, ru); continue; }
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

  /** Mezcla un documento que ha llegado de la nube. Devuelve true si cambia lo que se ve aquí. */
  function aplica(id, r) {
    r = { ...r, id: r.id || id };
    const ru = num(r.u);
    const l = docs.get(id);
    const ack = acked.has(id) ? acked.get(id) : 0;
    if (!l) { docs.set(id, r); acked.set(id, ru); guarda(r); return true; }
    if (l.t === T.LOG || r.t === T.LOG) return mezclaLog(l, r);
    const lu = num(l.u);
    if (lu === ru) { acked.set(id, ru); return false; }
    const pendiente = lu !== ack;
    if (!pendiente) {
      if (ru > lu) { docs.set(id, r); acked.set(id, ru); guarda(r); return true; }
      acked.set(id, ru); // lo de aquí es más nuevo que lo de la nube: queda pendiente y se sube
      return false;
    }
    // Se cambió en los dos sitios a la vez: gana el más nuevo y el texto del otro se guarda como borrador
    const gana = ru > lu ? r : l;
    const pierde = ru > lu ? l : r;
    if (l.t === T.ESCENA && !borrado(gana) && !borrado(pierde) && String(pierde.texto || '').trim() && pierde.texto !== gana.texto) {
      const bid = `bc_${id}_${pierde.u}`;
      if (!docs.has(bid)) {
        const nombre = `Versión de otro dispositivo (${new Intl.DateTimeFormat('es-ES', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }).format(new Date(pierde.u)).replace(/\./g, '')})`;
        const o = { id: bid, t: T.BORRADOR, pid: l.pid, eid: id, conflicto: 1, nombre, texto: pierde.texto, u: pierde.u };
        docs.set(bid, o); guarda(o);
      }
    }
    acked.set(id, ru);
    if (ru > lu) { docs.set(id, gana); guarda(gana); }
    return ru > lu;
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
    for (const o of viejos) { docs.delete(o.id); acked.delete(o.id); try { fs.unlinkSync(path.join(dDocs, `${o.id}.json`)); } catch { /* */ } }
    guardaIndice();
  };
  return api;
}

module.exports = { crear, palabras, T, hoy };
