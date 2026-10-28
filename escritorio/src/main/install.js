'use strict';
// Instalación en el sistema: copia el AppImage a una carpeta fija, lo añade al menú de
// aplicaciones y gestiona el arranque al iniciar sesión (archivos .desktop estándar).
const fs = require('fs');
const path = require('path');
const { XDG_CONFIG, XDG_DATA, exists, mkdirp } = require('./paths');

const APP_DIR = path.join(XDG_DATA, 'atrio', 'app');
const INSTALLED = path.join(APP_DIR, 'Atrio.AppImage');
const ICON = path.join(APP_DIR, 'atrio.png');
const STAMP = path.join(APP_DIR, 'version.txt');
const MENU_ENTRY = path.join(XDG_DATA, 'applications', 'atrio.desktop');
const AUTOSTART = path.join(XDG_CONFIG, 'autostart', 'atrio.desktop');

const quote = (p) => `"${String(p).replace(/(["`$\\])/g, '\\$1')}"`;

function desktopFile(exec, extra = []) {
  return [
    '[Desktop Entry]', 'Type=Application', 'Name=Atrio', 'Comment=Tu biblioteca de juegos',
    `Exec=${exec}`, `Icon=${exists(ICON) ? ICON : 'atrio'}`, 'Terminal=false', 'Categories=Game;',
    'StartupWMClass=atrio', ...extra, '',
  ].join('\n');
}

// Ejecutable que deben usar el menú y el arranque automático
function launcherPath() {
  if (exists(INSTALLED)) return INSTALLED;
  return process.env.APPIMAGE || null;
}

// Copia este AppImage a la carpeta fija si es otra versión (o no estaba)
async function installSelf(version, iconSource) {
  const src = process.env.APPIMAGE;
  if (!src || !exists(src)) return { installed: false, reason: 'no-appimage' };
  if (path.resolve(src) === path.resolve(INSTALLED)) return { installed: true, updated: false };
  let size = 0;
  try { size = fs.statSync(src).size; } catch { /* */ }
  const stamp = `${version} ${size}`;
  let current = '';
  try { current = fs.readFileSync(STAMP, 'utf8').trim(); } catch { /* */ }
  const fresh = !exists(INSTALLED);
  if (current !== stamp || fresh) {
    mkdirp(APP_DIR);
    const tmp = INSTALLED + '.part';
    await fs.promises.copyFile(src, tmp);
    await fs.promises.chmod(tmp, 0o755);
    // rename sustituye el archivo aunque la copia anterior esté abierta
    await fs.promises.rename(tmp, INSTALLED);
    fs.writeFileSync(STAMP, stamp);
  }
  try { if (iconSource) fs.writeFileSync(ICON, fs.readFileSync(iconSource)); } catch { /* sin icono */ }
  return { installed: true, updated: current !== stamp, fresh };
}

function writeMenuEntry() {
  const exe = launcherPath();
  if (!exe) return false;
  mkdirp(path.dirname(MENU_ENTRY));
  fs.writeFileSync(MENU_ENTRY, desktopFile(`${quote(exe)} %U`));
  return true;
}

// mode: 'visible' (pantalla completa al iniciar sesión) | 'hidden' (en segundo plano) | 'off'
// via: acceso directo de Atrio en Steam ({ gameId, flatpak }) para abrirlo a través de Steam
function setAutostart(mode, via) {
  if (mode === 'off') { try { fs.unlinkSync(AUTOSTART); } catch { /* ya no estaba */ } return state(); }
  const exe = launcherPath();
  if (!exe) throw new Error('Solo disponible usando el AppImage');
  mkdirp(path.dirname(AUTOSTART));
  let exec = `${quote(exe)}${mode === 'hidden' ? ' --background' : ''}`;
  if (via && via.gameId && mode === 'visible') {
    exec = via.flatpak
      ? `flatpak run com.valvesoftware.Steam steam://rungameid/${via.gameId}`
      : `steam steam://rungameid/${via.gameId}`;
  }
  fs.writeFileSync(AUTOSTART, desktopFile(exec, ['X-GNOME-Autostart-enabled=true']));
  return state();
}

function state() {
  let mode = 'off';
  let viaSteam = false;
  try {
    const txt = fs.readFileSync(AUTOSTART, 'utf8');
    mode = /--background/.test(txt) ? 'hidden' : 'visible';
    viaSteam = /steam:\/\/rungameid/.test(txt);
  } catch { /* sin arranque automático */ }
  return {
    available: !!(process.env.APPIMAGE || exists(INSTALLED)),
    mode,
    installed: exists(INSTALLED),
    path: launcherPath(),
    menu: exists(MENU_ENTRY),
    viaSteam,
  };
}

// ---------- Windows ----------
// El instalador (.exe) ya copia Atrio y lo pone en el menú Inicio. El arranque al iniciar sesión
// se guarda en el registro con app.setLoginItemSettings.
const WIN_ITEM = 'Atrio';
function winSteamExe() {
  try {
    const out = require('child_process').execFileSync('reg', ['query', 'HKCU\\Software\\Valve\\Steam', '/v', 'SteamExe'],
      { encoding: 'utf8', windowsHide: true, timeout: 5000 });
    const m = out.match(/SteamExe\s+REG_\w+\s+(.+)/i);
    return m ? path.normalize(m[1].trim()) : null;
  } catch { return null; }
}
function winItem() {
  const { app } = require('electron');
  try {
    const st = app.getLoginItemSettings();
    return (st.launchItems || []).find((i) => i.name === WIN_ITEM) || null;
  } catch { return null; }
}
const win = {
  async installSelf() { return { installed: false, reason: 'windows' }; },
  writeMenuEntry() { return true; },
  setAutostart(mode, via) {
    const { app } = require('electron');
    if (!app.isPackaged) throw new Error('Solo disponible con Atrio instalado');
    const cur = winItem();
    if (cur) app.setLoginItemSettings({ openAtLogin: false, name: WIN_ITEM, path: cur.path, args: cur.args || [] });
    if (mode === 'off') return win.state();
    let exe = process.execPath;
    let args = mode === 'hidden' ? ['--background'] : [];
    const steamExe = via && via.gameId && mode === 'visible' ? winSteamExe() : null;
    if (steamExe) { exe = steamExe; args = [`steam://rungameid/${via.gameId}`]; }
    app.setLoginItemSettings({ openAtLogin: true, name: WIN_ITEM, path: exe, args });
    return win.state();
  },
  state() {
    const { app } = require('electron');
    const it = winItem();
    const args = (it && it.args) || [];
    const mode = !it || it.enabled === false ? 'off' : args.includes('--background') ? 'hidden' : 'visible';
    return {
      available: app.isPackaged,
      mode,
      installed: app.isPackaged,
      path: process.execPath,
      menu: app.isPackaged,
      viaSteam: args.some((a) => /steam:\/\/rungameid/.test(a)),
      windows: true,
    };
  },
};

// ---------- macOS ----------
// Atrio.app se arrastra a Aplicaciones (no hay nada que instalar). El arranque al iniciar sesión se guarda en los
// «Elementos de inicio de sesión» con app.setLoginItemSettings (lo que se pide en Ajustes → Comportamiento).
const mac = {
  async installSelf() { return { installed: false, reason: 'macos' }; },
  writeMenuEntry() { return true; },
  setAutostart(mode) {
    const { app } = require('electron');
    if (!app.isPackaged) throw new Error('Solo disponible con Atrio.app instalado');
    app.setLoginItemSettings({ openAtLogin: mode !== 'off', openAsHidden: mode === 'hidden', args: mode === 'hidden' ? ['--background'] : [] });
    return mac.state();
  },
  state() {
    const { app } = require('electron');
    let st = {};
    try { st = app.getLoginItemSettings(); } catch { /* */ }
    const mode = !st.openAtLogin ? 'off' : st.openAsHidden || (st.executableWillLaunchAtLogin && false) ? 'hidden' : 'visible';
    return { available: app.isPackaged, mode, installed: app.isPackaged, path: process.execPath, menu: app.isPackaged, viaSteam: false, mac: true };
  },
};

module.exports = process.platform === 'win32'
  ? { ...win, INSTALLED: process.execPath, AUTOSTART: null }
  : process.platform === 'darwin' ? { ...mac, INSTALLED: process.execPath, AUTOSTART: null }
    : { installSelf, writeMenuEntry, setAutostart, state, INSTALLED, AUTOSTART };
