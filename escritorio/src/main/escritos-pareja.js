'use strict';
// Escribir con tu pareja (igual que EscribirPareja.kt en el móvil; ver docs/escribir.md, «Compartir con tu pareja»):
// - lo que te comparte: se lee de /users/<pareja>/escritos y solo se queda lo compartido (capítulos con «comp», o de
//   una parte con «comp», con sus escenas, sus partes y su obra, recortados a lo que hace falta para leerlos);
// - lo que anotas: en TU cuenta, /users/<tú>/notasLectura/<pareja>/<id> = { id, cid, eid, p, cita, texto, u };
// - lo que te anota: de SU cuenta, /users/<pareja>/notasLectura/<tú>.
// Las reglas de siempre ya lo permiten (cada uno lee la cuenta de su pareja y solo escribe en la suya).

const T = { OBRA: 'p', CAP: 'c', ESCENA: 'e', PARTE: 'a' };
const b = (o, k) => !!o && (o[k] === 1 || o[k] === true);

/** De todos los documentos de alguien, solo lo que comparte (y solo los campos para leerlo). */
function soloLoCompartido(docs) {
  const vivos = (docs || []).filter((o) => o && o.d !== 1 && o.id && o.t);
  const porId = new Map(vivos.map((o) => [o.id, o]));
  const caps = vivos.filter((o) => o.t === T.CAP && (b(o, 'comp') || b(porId.get(o.aid), 'comp')));
  const cids = new Set(caps.map((o) => o.id));
  const pids = new Set(caps.map((o) => o.pid));
  const recorta = (o, ...k) => { const r = {}; for (const x of ['id', 't', 'pid', 'u', 'orden', ...k]) if (o[x] !== undefined) r[x] = o[x]; return r; };
  return [
    ...vivos.filter((o) => o.t === T.OBRA && pids.has(o.id)).map((o) => recorta(o, 'titulo', 'autor', 'genero', 'sinopsis', 'color')),
    ...vivos.filter((o) => o.t === T.PARTE && pids.has(o.pid)).map((o) => recorta(o, 'titulo', 'resumen', 'color', 'comp', 'hecho')),
    ...caps.map((o) => recorta(o, 'titulo', 'resumen', 'color', 'aid', 'comp', 'hecho')),
    ...vivos.filter((o) => o.t === T.ESCENA && cids.has(o.cid)).map((o) => recorta(o, 'titulo', 'cid', 'texto', 'palabras')),
  ];
}

/** Las notas guardadas (un objeto id → nota) en una lista ordenada. */
function listaNotas(o) {
  if (!o || typeof o !== 'object') return [];
  return Object.values(o).filter((n) => n && n.id && String(n.texto || '').trim()).sort((x, y) => (x.u || 0) - (y.u || 0));
}

function iniciar({ ronron, ipcMain }) {
  let cache = { uid: null, pareja: null, nombre: '', hasta: 0 };
  /** El uid de tu pareja (de /users/<tú>/partner) y su nombre; se recuerda un minuto. */
  async function pareja() {
    const uid = ronron.uid();
    if (!uid) return null;
    if (cache.uid === uid && Date.now() < cache.hasta) return cache.pareja ? cache : null;
    let p = null; let nombre = '';
    try { p = await ronron.db('GET', `/users/${uid}/partner`, undefined, '', true); } catch { p = null; }
    if (typeof p !== 'string' || !p) p = null;
    if (p) { try { nombre = (await ronron.db('GET', `/users/${p}/name`, undefined, '', true)) || ''; } catch { /* */ } }
    cache = { uid, pareja: p, nombre: typeof nombre === 'string' ? nombre : '', hasta: Date.now() + 60_000 };
    return p ? cache : null;
  }
  const valida = (n) => {
    if (!n || typeof n !== 'object' || !/^nl_[a-z0-9]{6,40}$/i.test(String(n.id || ''))) throw new Error('Nota no válida.');
    return { id: String(n.id), cid: String(n.cid || ''), eid: String(n.eid || ''), p: Number.isInteger(n.p) ? n.p : -1, cita: String(n.cita || '').slice(0, 200), texto: String(n.texto || '').slice(0, 5000), u: Date.now() };
  };

  ipcMain.handle('pareja:info', async () => { const p = await pareja(); return p ? { hay: true, nombre: p.nombre || 'tu pareja' } : { hay: false, nombre: 'tu pareja' }; });
  ipcMain.handle('pareja:compartido', async () => {
    const p = await pareja();
    if (!p) return { hay: false, docs: [] };
    const todo = await ronron.db('GET', `/users/${p.pareja}/escritos`, undefined, '', true);
    return { hay: true, nombre: p.nombre || 'tu pareja', docs: soloLoCompartido(Object.values(todo || {})) };
  });
  ipcMain.handle('pareja:misNotas', async () => {
    const p = await pareja();
    return p ? listaNotas(await ronron.db('GET', `/users/${ronron.uid()}/notasLectura/${p.pareja}`, undefined, '', true)) : [];
  });
  ipcMain.handle('pareja:notasDeMiPareja', async () => {
    const p = await pareja();
    return p ? listaNotas(await ronron.db('GET', `/users/${p.pareja}/notasLectura/${ronron.uid()}`, undefined, '', true)) : [];
  });
  ipcMain.handle('pareja:anota', async (_e, n) => {
    const p = await pareja();
    if (!p) throw new Error('Para anotar, tu cuenta tiene que estar enlazada con la de tu pareja.');
    const nota = valida(n);
    await ronron.db('PUT', `/users/${ronron.uid()}/notasLectura/${p.pareja}/${nota.id}`, nota, '', false);
    return nota;
  });
  ipcMain.handle('pareja:borraNota', async (_e, id) => {
    const p = await pareja();
    if (!p || !/^nl_[a-z0-9]{6,40}$/i.test(String(id || ''))) return false;
    await ronron.db('DELETE', `/users/${ronron.uid()}/notasLectura/${p.pareja}/${id}`, undefined, '', false);
    return true;
  });
}

module.exports = { iniciar, soloLoCompartido, listaNotas };
