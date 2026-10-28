'use strict';
// El menú del botón derecho de la ventana de Atrio: las correcciones del corrector ortográfico (en español), «añadir al
// diccionario», los sinónimos de la palabra (del tesauro libre de RLA-ES, ver sinonimos.js) y cortar, copiar y pegar.
// Electron no trae ninguno: sin esto, el botón derecho no hace nada.
const { Menu } = require('electron');
const sinonimos = require('./sinonimos');

const PALABRA = /^[\p{L}][\p{L}'’-]*$/u;

/** Las entradas del menú para unos «params» de context-menu (aparte, para poder probarlo sin ventana). */
function entradas(params, wc, buscaSinonimos = sinonimos.busca) {
  const items = [];
  const sep = () => { if (items.length && items[items.length - 1].type !== 'separator') items.push({ type: 'separator' }); };
  if (params.misspelledWord) {
    const sug = (params.dictionarySuggestions || []).slice(0, 6);
    for (const s of sug) items.push({ label: s, click: () => wc.replaceMisspelling(s) });
    if (!sug.length) items.push({ label: 'Sin sugerencias', enabled: false });
    items.push({ label: `Añadir «${params.misspelledWord}» al diccionario`, click: () => wc.session.addWordToSpellCheckerDictionary(params.misspelledWord) });
    sep();
  }
  const palabra = String(params.selectionText || params.misspelledWord || '').trim();
  if (params.isEditable && palabra && palabra.length <= 40 && PALABRA.test(palabra)) {
    let grupos = [];
    try { grupos = buscaSinonimos(palabra); } catch { /* sin diccionario */ }
    if (grupos.length) {
      const sub = [];
      grupos.slice(0, 8).forEach((g, i) => {
        if (i) sub.push({ type: 'separator' });
        for (const s of g.slice(0, 8)) sub.push({ label: s, click: () => wc.replace(s) });
      });
      items.push({ label: `Sinónimos de «${palabra}»`, submenu: sub });
    } else items.push({ label: `Sin sinónimos para «${palabra}»`, enabled: false });
    sep();
  }
  const f = params.editFlags || {};
  if (params.isEditable) {
    items.push({ role: 'undo', label: 'Deshacer', enabled: f.canUndo !== false }, { role: 'redo', label: 'Rehacer', enabled: f.canRedo !== false }, { type: 'separator' },
      { role: 'cut', label: 'Cortar', enabled: !!f.canCut }, { role: 'copy', label: 'Copiar', enabled: !!f.canCopy }, { role: 'paste', label: 'Pegar', enabled: !!f.canPaste },
      { type: 'separator' }, { role: 'selectAll', label: 'Seleccionar todo' });
  } else if (params.selectionText && String(params.selectionText).trim()) items.push({ role: 'copy', label: 'Copiar' });
  while (items.length && items[items.length - 1].type === 'separator') items.pop();
  return items;
}

/** Pone el menú y el corrector en español a la ventana. */
function poner(win, log = () => {}) {
  const wc = win.webContents;
  // En Windows y Linux hay que decir el idioma (en macOS manda el del sistema y esto no hace nada)
  try {
    const hay = wc.session.availableSpellCheckerLanguages || [];
    const es = ['es-ES', 'es', 'es-419', 'es-MX'].filter((x) => hay.includes(x));
    if (process.platform !== 'darwin' && es.length) { wc.session.setSpellCheckerLanguages([es[0]]); log('corrector en', es[0]); }
    else log('corrector', process.platform === 'darwin' ? 'el del sistema' : `sin español (${hay.length} idiomas)`);
  } catch (e) { log('corrector', e.message); }
  wc.on('context-menu', (_e, params) => {
    const items = entradas(params, wc);
    if (items.length) Menu.buildFromTemplate(items).popup({ window: win });
  });
}

module.exports = { poner, entradas };
