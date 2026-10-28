'use strict';
// El PDF de una obra: la página que prepara escritos-export.js (htmlPdf) se abre en una ventana escondida y Chromium la
// imprime a PDF, con el tamaño de página, los márgenes y la numeración que dice su CSS. Todo en este PC.
const fs = require('fs');
const os = require('os');
const path = require('path');
const { pathToFileURL } = require('url');
const X = require('./escritos-export');

// Las letras de la revista (Fraunces, SIL OFL) para el estilo «libro»
function fuentes() {
  const dir = path.join(__dirname, '..', 'renderer', 'revista', 'fuentes');
  const f = (n, peso, estilo = 'normal') => `@font-face{font-family:'Libro';src:url('${pathToFileURL(path.join(dir, n)).href}');font-weight:${peso};font-style:${estilo}}`;
  return f('display_regular.ttf', 400) + f('display_italic.ttf', 400, 'italic') + f('display_semibold.ttf', 600) + f('display_semibold.ttf', 700);
}

/** El PDF (Buffer) de la obra pid con las opciones de exportar ({ estilo: 'libro' | 'manuscrito', interlineado, … }). */
async function pdf(escr, pid, opciones = {}) {
  const { BrowserWindow } = require('electron');
  const tmp = path.join(os.tmpdir(), `atrio-pdf-${process.pid}-${Date.now()}.html`);
  fs.writeFileSync(tmp, X.htmlPdf(escr, pid, opciones, fuentes()));
  const w = new BrowserWindow({ show: false, width: 800, height: 1100, webPreferences: { sandbox: true, contextIsolation: true, nodeIntegration: false, spellcheck: false } });
  try {
    await w.loadFile(tmp);
    // (que estén las letras antes de imprimir)
    await w.webContents.executeJavaScript('document.fonts.ready.then(() => document.fonts.size)', true).catch(() => 0);
    return await w.webContents.printToPDF({ preferCSSPageSize: true, printBackground: true, displayHeaderFooter: false, generateTaggedPDF: true, generateDocumentOutline: true });
  } finally {
    if (!w.isDestroyed()) w.destroy();
    fs.rmSync(tmp, { force: true });
  }
}

module.exports = { pdf, fuentes };
