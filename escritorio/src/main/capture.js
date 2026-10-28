'use strict';
// Captura de pantalla a la resolución real del monitor, sin pasar por la interfaz de Atrio.
// Linux: la herramienta del propio escritorio (KDE: Spectacle; Sway/Hyprland: grim; GNOME: gnome-screenshot),
// y en X11 o si no hay ninguna, la captura de Chromium. Windows: la captura de Chromium al tamaño físico.
const fs = require('fs');
const os = require('os');
const path = require('path');
const { execFile } = require('child_process');

const IS_WIN = process.platform === 'win32';
const IS_MAC = process.platform === 'darwin';

function cleanEnv() {
  try { return require('./launcher').cleanEnv(); } catch { return process.env; }
}

function which(cmd) {
  if (IS_WIN) return null;
  for (const d of String(cleanEnv().PATH || '/usr/local/bin:/usr/bin:/bin').split(':').concat(IS_MAC ? ['/usr/sbin', '/opt/homebrew/bin'] : [])) {
    const p = path.join(d, cmd);
    try { fs.accessSync(p, fs.constants.X_OK); return p; } catch { /* sigue */ }
  }
  return null;
}

function run(bin, args, timeout = 15000) {
  return new Promise((resolve, reject) => {
    execFile(bin, args, { env: cleanEnv(), timeout, windowsHide: true }, (err, _o, e) => (err ? reject(new Error(String(e || err.message).trim().slice(0, 200))) : resolve()));
  });
}

// Ancho y alto de un PNG o JPEG
function sizeOf(buf) {
  if (buf.length > 24 && buf.readUInt32BE(0) === 0x89504e47) return { w: buf.readUInt32BE(16), h: buf.readUInt32BE(20), mime: 'image/png' };
  if (buf.length > 4 && buf[0] === 0xff && buf[1] === 0xd8) {
    let i = 2;
    while (i + 9 < buf.length) {
      if (buf[i] !== 0xff) { i++; continue; }
      const m = buf[i + 1];
      const len = buf.readUInt16BE(i + 2);
      if (m >= 0xc0 && m <= 0xcf && ![0xc4, 0xc8, 0xcc].includes(m)) return { w: buf.readUInt16BE(i + 7), h: buf.readUInt16BE(i + 5), mime: 'image/jpeg' };
      i += 2 + len;
    }
    return { w: 0, h: 0, mime: 'image/jpeg' };
  }
  return { w: 0, h: 0, mime: 'application/octet-stream' };
}

function desktop() {
  return `${process.env.XDG_CURRENT_DESKTOP || ''}:${process.env.XDG_SESSION_DESKTOP || ''}:${process.env.DESKTOP_SESSION || ''}`.toLowerCase();
}
const wayland = () => !!process.env.WAYLAND_DISPLAY || process.env.XDG_SESSION_TYPE === 'wayland';

// Métodos disponibles en este equipo, en orden de preferencia
function methods() {
  const out = [];
  if (IS_WIN) return ['chromium'];
  // macOS: la herramienta del sistema (pide permiso de «Grabación de pantalla» la primera vez) y, si no, la de Chromium
  if (IS_MAC) return ['screencapture', 'chromium'];
  const d = desktop();
  if (/kde|plasma/.test(d) && which('spectacle')) out.push('spectacle');
  if (wayland() && which('grim') && !/kde|plasma|gnome/.test(d)) out.push('grim');
  if (/gnome|unity|cinnamon|budgie/.test(d) && which('gnome-screenshot')) out.push('gnome-screenshot');
  if (!wayland()) {
    out.push('chromium');
    if (which('import')) out.push('import');
  }
  if (which('spectacle') && !out.includes('spectacle')) out.push('spectacle');
  if (which('grim') && !out.includes('grim')) out.push('grim');
  if (!out.includes('chromium')) out.push('chromium');
  return out;
}

// Pantalla en la que se juega: la del puntero (en un juego a pantalla completa es la suya)
function targetDisplay() {
  const { screen } = require('electron');
  try { return screen.getDisplayNearestPoint(screen.getCursorScreenPoint()); } catch { return screen.getPrimaryDisplay(); }
}

async function viaChromium(scope) {
  const { desktopCapturer, screen } = require('electron');
  const disp = targetDisplay();
  const all = screen.getAllDisplays();
  const big = all.reduce((m, d) => Math.max(m, Math.round(d.size.width * d.scaleFactor), Math.round(d.size.height * d.scaleFactor)), 0);
  const want = { width: Math.round(disp.size.width * disp.scaleFactor), height: Math.round(disp.size.height * disp.scaleFactor) };
  const sources = await desktopCapturer.getSources({ types: ['screen'], thumbnailSize: scope === 'todas' ? { width: big, height: big } : want });
  if (!sources.length) throw new Error('el sistema no ha dado ninguna pantalla');
  const src = sources.find((s) => String(s.display_id) === String(disp.id)) || sources[0];
  const img = src.thumbnail;
  if (!img || img.isEmpty()) throw new Error('captura vacía');
  return img.toPNG();
}

async function viaTool(method, scope, multi) {
  const file = path.join(os.tmpdir(), `atrio-captura-${process.pid}-${Date.now()}.png`);
  try {
    if (method === 'screencapture') await run('/usr/sbin/screencapture', ['-x', '-t', 'png', file], 20000);
    else if (method === 'spectacle') await run('spectacle', ['-b', '-n', scope === 'todas' || !multi ? '-f' : '-m', '-o', file], 20000);
    else if (method === 'grim') await run('grim', [file]);
    else if (method === 'gnome-screenshot') await run('gnome-screenshot', ['-f', file]);
    else if (method === 'import') await run('import', ['-window', 'root', file]);
    else throw new Error('método desconocido');
    // Spectacle puede tardar un instante en escribir el archivo después de salir
    for (let i = 0; i < 20 && !(fs.existsSync(file) && fs.statSync(file).size > 0); i++) await new Promise((r) => setTimeout(r, 150));
    const buf = fs.readFileSync(file);
    if (buf.length < 100) throw new Error('archivo vacío');
    return buf;
  } finally {
    try { fs.unlinkSync(file); } catch { /* */ }
  }
}

// Hace la captura. opts: { method: 'auto'|nombre, scope: 'juego'|'todas', format: 'png'|'jpg' }
async function take(opts = {}) {
  let multi = false;
  try { multi = require('electron').screen.getAllDisplays().length > 1; } catch { /* */ }
  const list = opts.method && opts.method !== 'auto' ? [opts.method, ...methods().filter((m) => m !== opts.method)] : methods();
  const errors = [];
  for (const m of list) {
    try {
      let buf = m === 'chromium' ? await viaChromium(opts.scope) : await viaTool(m, opts.scope, multi);
      if (opts.format === 'jpg') {
        const { nativeImage } = require('electron');
        buf = nativeImage.createFromBuffer(buf).toJPEG(Number(opts.jpgQuality) || 92);
      }
      const s = sizeOf(buf);
      if (!s.w || !s.h) throw new Error('imagen no válida');
      return { buf, ...s, method: m };
    } catch (e) {
      errors.push(`${m}: ${e.message}`);
    }
  }
  throw new Error(`No se ha podido capturar la pantalla (${errors.join(' · ')})`);
}

module.exports = { take, methods, sizeOf };
