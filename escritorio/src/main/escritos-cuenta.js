'use strict';
// Escribir en Atrio: lo une con la cuenta (ronron.js), con la ventana (ipc y avisos) y con la escucha en directo
// del índice de escritos de la cuenta (Firebase en streaming), como hace suite.js con las capturas.
const path = require('path');
const { XDG_DATA } = require('./paths');
const { crear } = require('./escritos');
const { watch } = require('./stream');
const fs = require('fs');
const X = require('./escritos-export');

const OPS = new Set(['nuevo', 'edita', 'escribe', 'borra', 'mueve', 'mueveEscena', 'duplicaEscena', 'migraPartes', 'mueveCapitulo', 'registra', 'instantanea', 'reemplaza', 'versiones', 'sincroniza',
  'papelera', 'papeleraDetalle', 'conElBorrado', 'cualquiera', 'restaura', 'purga', 'vaciaPapelera']);

function iniciar({ ronron, ipcMain, send, log, fetchImpl, dir, dialog, ventana = () => null }) {
  const escr = crear({
    dir: dir || path.join(XDG_DATA, 'atrio', 'escritos'),
    dev: ronron.deviceId(),
    net: { uid: () => ronron.uid(), db: (m, p, b, q) => ronron.db(m, p, b, q || '', false) },
    log,
  });
  // Copias de seguridad en una carpeta de este equipo (aparte de la cuenta); ver escritos-copias.js
  const copias = require('./escritos-copias').iniciar({ escr, carpeta: dir ? path.join(dir, '..', 'escritos-copias') : path.join(XDG_DATA, 'atrio', 'escritos-copias'), log });
  escr.copias = copias;
  ipcMain.handle('escritos:copias', () => ({ carpeta: copias.carpeta, lista: copias.lista() }));
  ipcMain.handle('escritos:copiaAhora', () => copias.copia(true));
  ipcMain.handle('escritos:copiaObras', (_e, archivo) => copias.obrasDe(archivo));
  ipcMain.handle('escritos:copiaRestaurar', (_e, archivo, obraId) => copias.restaura(archivo, obraId));
  ipcMain.handle('escritos:copiaCarpeta', async () => { const { shell } = require('electron'); return shell.openPath(copias.carpeta); });
  const estado = () => ({ ...escr.estado(), signed: !!ronron.uid() });
  let pend = null;
  // Se avisa a la ventana con todo lo de ahora (junta cambios seguidos)
  escr.onChange(() => {
    if (pend) return;
    pend = setTimeout(() => { pend = null; try { send('escritos', { docs: escr.todos(), estado: estado() }); } catch { /* ventana cerrada */ } }, 60);
  });
  ipcMain.handle('escritos:todo', () => ({ docs: escr.todos(), estado: estado() }));
  ipcMain.handle('escritos:op', async (_e, op, ...args) => {
    if (!OPS.has(op)) throw new Error('Operación desconocida.');
    return escr[op](...args);
  });

  // Exportar a un archivo (Word, EPUB, Markdown o texto) e importar un texto (Word, Markdown o texto)
  ipcMain.handle('escritos:exportar', async (_e, pid, formato, opciones) => {
    const obra = escr.get(pid);
    if (!obra) throw new Error('No encuentro esa obra.');
    const ext = ['docx', 'pdf', 'epub', 'md', 'txt'].includes(formato) ? formato : 'docx';
    const nombre = (String(obra.titulo || 'obra').replace(/[^\p{L}\p{N} _-]/gu, '').trim() || 'obra') + (opciones && opciones.estilo === 'manuscrito' && (ext === 'docx' || ext === 'pdf') ? ' (manuscrito)' : '');
    let destino;
    // (las pruebas guardan sin preguntar en una carpeta)
    if (process.env.ATRIO_PRUEBA_EXPORTA) destino = path.join(process.env.ATRIO_PRUEBA_EXPORTA, `${nombre}.${ext}`);
    else {
      const r = await dialog.showSaveDialog(ventana(), { title: 'Exportar la obra', defaultPath: `${nombre}.${ext}`, filters: [{ name: ext.toUpperCase(), extensions: [ext] }] });
      if (r.canceled || !r.filePath) return null;
      destino = r.filePath;
    }
    const datos = ext === 'docx' ? X.docx(escr, pid, opciones) : ext === 'pdf' ? await require('./escritos-pdf').pdf(escr, pid, opciones)
      : ext === 'epub' ? X.epub(escr, pid, opciones) : Buffer.from(ext === 'md' ? X.markdown(escr, pid, opciones) : X.texto(escr, pid, opciones), 'utf8');
    fs.writeFileSync(destino, datos);
    return destino;
  });
  ipcMain.handle('escritos:importar', async () => {
    const r = await dialog.showOpenDialog(ventana(), { title: 'Importar un texto', properties: ['openFile'], filters: [{ name: 'Texto, Markdown o Word', extensions: ['txt', 'md', 'markdown', 'docx'] }] });
    if (r.canceled || !r.filePaths[0]) return null;
    const f = r.filePaths[0];
    const buf = fs.readFileSync(f);
    const txt = /\.docx$/i.test(f) ? X.textoDeDocx(buf) : buf.toString('utf8');
    return X.importaTexto(escr, path.basename(f).replace(/\.[^.]+$/, ''), txt);
  });

  let uidAhora = null;
  let w = null;
  const mira = () => {
    const uid = ronron.uid();
    if (uid === uidAhora) return;
    uidAhora = uid;
    if (w) { w.stop(); w = null; }
    if (!uid) return;
    escr.cuenta(uid);
    // Cada documento que sube otro dispositivo cambia su hora en el índice: se baja al momento
    let primera = true;
    w = watch({
      url: (force) => ronron.authUrl(`/users/${uid}/escritosIdx`, force), fetchImpl, log,
      onData: () => { if (primera) { primera = false; escr.pronto(0.1); return; } escr.bajaPronto(); },
    });
    escr.pronto(0.3);
  };
  setInterval(mira, 4000);
  setTimeout(mira, 1200);
  // Por si algo quedó sin subir (sin red un buen rato): se vuelve a intentar
  setInterval(() => { if (ronron.uid() && escr.pendientes().length) escr.pronto(0.5); }, 30000);
  // Lo de la papelera de hace más de 30 días se borra del todo, y las marcas de borrado muy viejas se quitan
  const ordena = () => { try { escr.purga(); } catch { /* */ } escr.limpia().catch(() => {}); };
  setTimeout(ordena, 90 * 1000);
  setInterval(ordena, 6 * 3600 * 1000);
  return escr;
}

module.exports = { iniciar };
