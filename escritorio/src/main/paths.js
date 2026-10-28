'use strict';
const os = require('os');
const path = require('path');
const fs = require('fs');

const IS_WIN = process.platform === 'win32';
const IS_MAC = process.platform === 'darwin';
const HOME = process.env.ATRIO_HOME || os.homedir();
// En Windows todo va en %LOCALAPPDATA%\Atrio-datos (separado de la carpeta que usa Chromium)
const WIN_BASE = IS_WIN ? path.join(process.env.LOCALAPPDATA || path.join(HOME, 'AppData', 'Local'), 'Atrio-datos') : null;
// En macOS, lo de siempre: ~/Library/Application Support/Atrio-datos (y la caché en ~/Library/Caches)
const MAC_BASE = IS_MAC ? path.join(HOME, 'Library', 'Application Support', 'Atrio-datos') : null;
const XDG_CONFIG = IS_WIN ? path.join(WIN_BASE, 'config') : IS_MAC ? path.join(MAC_BASE, 'config') : (process.env.XDG_CONFIG_HOME || path.join(HOME, '.config'));
const XDG_DATA = IS_WIN ? path.join(WIN_BASE, 'data') : IS_MAC ? path.join(MAC_BASE, 'data') : (process.env.XDG_DATA_HOME || path.join(HOME, '.local', 'share'));
const XDG_CACHE = IS_WIN ? path.join(WIN_BASE, 'cache') : IS_MAC ? path.join(HOME, 'Library', 'Caches', 'Atrio') : (process.env.XDG_CACHE_HOME || path.join(HOME, '.cache'));
// %APPDATA% (Roaming) de Windows: ahí guardan sus datos Heroic y otros programas
const WIN_APPDATA = IS_WIN ? (process.env.APPDATA || path.join(HOME, 'AppData', 'Roaming')) : null;

const APP_CONFIG_DIR = path.join(XDG_CONFIG, 'atrio');
const APP_CACHE_DIR = path.join(XDG_CACHE, 'atrio');
// El arte se guarda como datos propios (no como caché que el sistema pueda limpiar)
const ART_DIR = path.join(XDG_DATA, 'atrio', 'art');
const OLD_ART_DIR = path.join(APP_CACHE_DIR, 'art');
try {
  if (fs.existsSync(OLD_ART_DIR) && !fs.existsSync(ART_DIR)) {
    fs.mkdirSync(path.dirname(ART_DIR), { recursive: true });
    fs.renameSync(OLD_ART_DIR, ART_DIR);
  }
} catch { /* si no se puede mover, se volverá a descargar */ }

function expandHome(p) {
  if (!p) return p;
  if (p === '~') return HOME;
  if (p.startsWith('~/') || p.startsWith('~\\')) return path.join(HOME, p.slice(2));
  return p;
}

function exists(p) {
  try { fs.accessSync(p); return true; } catch { return false; }
}

function readJson(p, fallback = null) {
  try { return JSON.parse(fs.readFileSync(p, 'utf8')); } catch { return fallback; }
}

function mkdirp(p) {
  fs.mkdirSync(p, { recursive: true });
}

module.exports = { IS_WIN, IS_MAC, WIN_APPDATA, HOME, XDG_CONFIG, XDG_DATA, XDG_CACHE, APP_CONFIG_DIR, APP_CACHE_DIR, ART_DIR, expandHome, exists, readJson, mkdirp };
