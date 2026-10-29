'use strict';
// La ventana del lector y lo que la revista le pide (abrir un libro, adjuntar su archivo, bajarlo de tu nube). Ver lector.js.
const fs = require('fs');
const path = require('path');

const ASSETS = () => [
  process.resourcesPath && path.join(process.resourcesPath, 'lector'),
  path.join(__dirname, '..', '..', '..', 'movil', 'app', 'src', 'main', 'assets', 'lector'),
].filter(Boolean).find((d) => { try { return fs.statSync(path.join(d, 'index.html')).isFile(); } catch { return false; } }) || null;

const FONDOS = { papel: '#FBF8F1', sepia: '#F1E6CF', gris: '#2B2D31', noche: '#121317', negro: '#000000', app: '#FBF8F1' };

function iniciar({ electron, dir, ronron, nube, log, send, ventana, fetchImpl, ajustes = () => ({}) }) {
  const { BrowserWindow, ipcMain, protocol, dialog, clipboard, shell, powerSaveBlocker } = electron;
  const assetsDir = ASSETS();
  if (!assetsDir) { log('lector: no encuentro la página del lector'); return null; }
  const L = require('./lector').crear({
    dir, assetsDir, ronron, nube, log, fetchImpl, ajustes,
    alCambiar: (id) => send('lector', { id }),
  });
  protocol.handle('atrio-lector', (req) => L.manejaProtocolo(req));

  const ventanas = new Map();   // id → BrowserWindow
  const bloqueos = new Map();   // id → powerSaveBlocker
  const idDe = (e) => { for (const [id, w] of ventanas) if (!w.isDestroyed() && w.webContents === e.sender) return id; return null; };

  const abre = (id, { titulo = '', goto = null } = {}) => {
    id = Number(id);
    if (!L.archivoDe(id)) throw new Error('Este libro no tiene archivo en este equipo.');
    const abierta = ventanas.get(id);
    if (abierta && !abierta.isDestroyed()) { abierta.show(); abierta.focus(); return true; }
    const w = new BrowserWindow({
      width: 980, height: 1100, minWidth: 420, minHeight: 480, show: false, title: titulo || 'Libro',
      backgroundColor: FONDOS.papel,
      ...(process.platform === 'darwin' ? { titleBarStyle: 'hiddenInset' } : {}),
      webPreferences: { preload: path.join(__dirname, 'lector-preload.js'), contextIsolation: true, nodeIntegration: false, sandbox: false, backgroundThrottling: false },
    });
    w.setMenuBarVisibility(false);
    ventanas.set(id, w);
    w.__datos = { titulo, goto };
    w.once('ready-to-show', () => w.show());
    // Teclado: ← → y PgUp/PgDn pasan página, Esc cierra lo abierto (y luego enseña u oculta el menú)
    w.webContents.on('before-input-event', (ev, i) => {
      if (i.type !== 'keyDown' || i.control || i.meta || i.alt) return;
      const k = i.key;
      if (k === 'ArrowRight' || k === 'PageDown' || (k === ' ' && !i.shift)) { ev.preventDefault(); w.webContents.send('lector:pagina', 1); }
      else if (k === 'ArrowLeft' || k === 'PageUp' || (k === ' ' && i.shift)) { ev.preventDefault(); w.webContents.send('lector:pagina', -1); }
      else if (k === 'Escape') { ev.preventDefault(); w.webContents.send('lector:atras'); }
    });
    w.on('close', () => { L.cierra(id).catch((e) => log('lector: cerrar', e.message)); });
    w.on('closed', () => {
      ventanas.delete(id);
      const b = bloqueos.get(id); if (b != null) { try { powerSaveBlocker.stop(b); } catch { /* */ } bloqueos.delete(id); }
      send('lector', { id });
    });
    w.loadURL(`atrio-lector://l${id}/index.html`);
    return true;
  };

  // ---- Lo que pide la página del lector ----
  ipcMain.on('lector:state', (e, id) => {
    const w = ventanas.get(Number(id));
    e.returnValue = JSON.stringify(L.paraLaPagina(Number(id), (w && w.__datos) || {}));
  });
  const on = (canal, f) => ipcMain.on(`lector:${canal}`, (e, id, ...a) => { try { f(Number(id), ...a); } catch (err) { log(`lector: ${canal}`, err.message); } });
  on('progreso', (id, cfi, fr, label) => L.puente.saveProgress(id, String(cfi || ''), Number(fr) || 0, String(label || '')));
  on('ajustes', (id, json) => L.puente.saveSettings(id, json));
  on('notas', (id, json) => L.puente.saveAnnotations(id, json));
  on('marcas', (id, json) => L.puente.saveBookmarks(id, json));
  on('tiempo', (id, json) => L.puente.saveStats(id, json));
  on('pantalla', (id, enc) => {
    const b = bloqueos.get(id);
    if (enc && b == null) bloqueos.set(id, powerSaveBlocker.start('prevent-display-sleep'));
    else if (!enc && b != null) { try { powerSaveBlocker.stop(b); } catch { /* */ } bloqueos.delete(id); }
  });
  on('tema', (id, t) => { const w = ventanas.get(id); if (w && !w.isDestroyed()) w.setBackgroundColor(FONDOS[t] || FONDOS.papel); });
  on('copiar', (_id, t) => clipboard.writeText(String(t)));
  on('imagen', (_id, b64) => { const { nativeImage } = electron; clipboard.writeImage(nativeImage.createFromBuffer(Buffer.from(String(b64), 'base64'))); });
  on('abrir-url', (_id, u) => { if (/^https?:\/\//i.test(String(u))) shell.openExternal(String(u)); });
  on('cerrar', (id) => { const w = ventanas.get(id); if (w && !w.isDestroyed()) w.close(); });
  ipcMain.handle('lector:buscar', (_e, _id, palabra) => L.busca(String(palabra || '').slice(0, 80)));
  ipcMain.handle('lector:traducir', () => ({ msg: 'La traducción sin conexión solo existe en el móvil; en el ordenador, usa el significado.' }));

  // Solo para las pruebas (el teclado de mentira de Playwright no pasa por el de Electron): manda una tecla a la ventana del libro
  if (process.env.ATRIO_PRUEBA_TECLAS) ipcMain.handle('lector:prueba-tecla', (_e, id, tecla, shift) => {
    const w = ventanas.get(Number(id)); if (!w || w.isDestroyed()) return false;
    w.webContents.focus();
    const m = shift ? ['shift'] : [];
    w.webContents.sendInputEvent({ type: 'keyDown', keyCode: tecla, modifiers: m }); w.webContents.sendInputEvent({ type: 'keyUp', keyCode: tecla, modifiers: m });
    return true;
  });

  // ---- Lo que pide la revista (la ficha del libro) ----
  ipcMain.handle('lector:estado', (_e, id) => {
    const o = L.estado(Number(id));
    const d = new Date();
    const hoy = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    return { ...o, archivo: !!L.archivoDe(Number(id)), enNube: L.enNube(Number(id)), queda: require('./lector-estado').quedaPorLeer(o, hoy), hoy };
  });
  ipcMain.handle('lector:abrir', (_e, id, titulo, goto) => abre(Number(id), { titulo: String(titulo || ''), goto: goto ? String(goto) : null }));
  ipcMain.handle('lector:adjuntar', async (_e, id) => {
    const r = await dialog.showOpenDialog(ventana() || undefined, {
      title: 'Elige el libro', properties: ['openFile'],
      filters: [{ name: 'Libros', extensions: L.EXTENSIONES }, { name: 'Todos los archivos', extensions: ['*'] }],
    });
    if (r.canceled || !r.filePaths[0]) return null;
    L.adjunta(Number(id), r.filePaths[0]);
    return L.estado(Number(id));
  });
  ipcMain.handle('lector:adjuntar-ruta', (_e, id, ruta) => { L.adjunta(Number(id), String(ruta)); return L.estado(Number(id)); });
  ipcMain.handle('lector:quitar', (_e, id) => { const w = ventanas.get(Number(id)); if (w && !w.isDestroyed()) w.close(); L.quita(Number(id)); return L.estado(Number(id)); });
  ipcMain.handle('lector:bajar', async (_e, id) => { await L.bajaDeNube(Number(id)); return L.estado(Number(id)); });

  return { ...L, abre, ventanas, parar: () => L.sube() };
}

module.exports = { iniciar };
