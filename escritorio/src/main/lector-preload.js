'use strict';
// El puente del lector (window.Atrio), el mismo que el del móvil: la página del lector (la misma que la del móvil) le pide
// al ordenador guardar lo leído, leer en voz alta, buscar una palabra… state va por sendSync; lo demás, por mensajes.
// El libro es el de la dirección de la ventana: atrio-lector://l<id>/
const { contextBridge, ipcRenderer, webFrame } = require('electron');

const m = /^l(\d+)$/.exec(location.hostname);
const id = m ? Number(m[1]) : 0;
const envia = (ch, ...a) => ipcRenderer.send(`lector:${ch}`, id, ...a);
const pagina = (js) => { try { webFrame.executeJavaScript(js).catch(() => {}); } catch { /* */ } };

// ---- Leer en voz alta: la voz del sistema (la del Mac) ----
let hablando = null;
const para = () => { hablando = null; try { speechSynthesis.cancel(); } catch { /* */ } };
const habla = (texto, ident, velocidad, idioma) => {
  para();
  const voz = new SpeechSynthesisUtterance(String(texto || ''));
  voz.rate = Math.min(2, Math.max(0.5, Number(velocidad) || 1));
  if (idioma && idioma !== 'und') voz.lang = idioma;
  const fin = (error) => { if (hablando !== voz) return; hablando = null; pagina(`window.atrioSpoken && atrioSpoken(${JSON.stringify(ident)}, ${error ? 'true' : 'false'})`); };
  voz.onend = () => fin(false);
  voz.onerror = (e) => { if (e.error === 'canceled' || e.error === 'interrupted') return; fin(true); };
  hablando = voz;
  speechSynthesis.speak(voz);
};

const pregunta = (canal, ident, ...a) => ipcRenderer.invoke(`lector:${canal}`, id, ...a)
  .then((r) => pagina(`window.atrioResult && atrioResult(${JSON.stringify(ident)}, ${JSON.stringify(r)})`));

contextBridge.exposeInMainWorld('Atrio', {
  state: () => ipcRenderer.sendSync('lector:state', id),
  saveProgress: (cfi, fraction, label) => envia('progreso', cfi, fraction, label),
  saveSettings: (json) => envia('ajustes', json),
  saveAnnotations: (json) => envia('notas', json),
  saveBookmarks: (json) => envia('marcas', json),
  saveStats: (json) => envia('tiempo', json),
  speak: habla,
  stopSpeaking: para,
  // El brillo es el de la pantalla del ordenador: aquí no se toca
  setBrightness: () => {},
  brilloMovil: () => 'permiso',
  pedirPermisoBrillo: () => {},
  brilloActual: () => 0.5,
  volumeKeys: () => {},
  keepScreenOn: (on) => envia('pantalla', !!on),
  setTheme: (t) => envia('tema', String(t)),
  systemBars: () => {},
  copy: (t) => envia('copiar', String(t)),
  share: (t) => envia('copiar', String(t)),
  shareImage: (b64) => envia('imagen', String(b64)),
  openUrl: (u) => envia('abrir-url', String(u)),
  close: () => envia('cerrar'),
  buzz: () => {},
  lookup: (palabra, frase, ident) => { pregunta('buscar', ident, palabra, frase); },
  translate: (palabra, frase, idioma, ident) => { pregunta('traducir', ident, palabra, frase, idioma); },
});

// El lector se maneja en el móvil con toques en los lados; en el ordenador, además, con el teclado (lo manda la ventana)
ipcRenderer.on('lector:pagina', (_e, d) => pagina(`window.atrioPage && atrioPage(${Number(d) > 0 ? 1 : -1})`));
ipcRenderer.on('lector:atras', () => pagina('window.atrioBack && !atrioBack() && window.atrioMenu && atrioMenu()'));
