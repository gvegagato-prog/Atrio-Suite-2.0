'use strict';
const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');
const { HOME, mkdirp } = require('./paths');

// Variables que el AppImage inyecta y que no deben heredar los juegos
const APPIMAGE_VARS = ['APPDIR', 'APPIMAGE', 'ARGV0', 'OWD', 'CHROME_DESKTOP', 'ORIGINAL_XDG_CURRENT_DESKTOP'];

function cleanEnv(extra = {}) {
  const env = { ...process.env };
  const appdir = process.env.APPDIR;
  for (const k of APPIMAGE_VARS) delete env[k];
  // Quita las rutas del propio AppImage para que los juegos usen las del sistema
  if (appdir) {
    for (const k of ['LD_LIBRARY_PATH', 'PATH', 'XDG_DATA_DIRS', 'PYTHONPATH', 'PERLLIB', 'QT_PLUGIN_PATH', 'GSETTINGS_SCHEMA_DIR', 'GTK_PATH']) {
      if (!env[k]) continue;
      env[k] = env[k].split(':').filter((p) => p && !p.startsWith(appdir)).join(':');
      if (!env[k]) delete env[k];
    }
  }
  return { ...env, ...extra };
}

const IS_WIN = process.platform === 'win32';
const IS_MAC = process.platform === 'darwin';
// En Windows y macOS las direcciones (steam://, https://) las abre el sistema con el programa que las tenga registradas
const ABRE_EL_SISTEMA = IS_WIN || IS_MAC;

function which(cmd) {
  if (IS_WIN) return null;
  const dirs = (cleanEnv().PATH || '/usr/local/bin:/usr/bin:/bin').split(':').concat(IS_MAC ? ['/opt/homebrew/bin', '/usr/local/bin'] : []);
  for (const d of dirs) {
    const p = path.join(d, cmd);
    try { fs.accessSync(p, fs.constants.X_OK); return p; } catch { /* sigue */ }
  }
  return null;
}

function spawnDetached(cmd, args, opts = {}) {
  return new Promise((resolve, reject) => {
    let child;
    try {
      child = spawn(cmd, args, {
        cwd: opts.cwd && fs.existsSync(opts.cwd) ? opts.cwd : HOME,
        env: cleanEnv(opts.env),
        detached: true,
        stdio: 'ignore',
        windowsHide: true,
        shell: !!opts.shell,
      });
    } catch (e) { return reject(e); }
    let settled = false;
    child.once('error', (e) => {
      if (settled) return;
      settled = true;
      reject(e.code === 'ENOENT' ? new Error(`No se encuentra «${path.basename(cmd)}» en el sistema`) : e);
    });
    child.once('exit', (code) => {
      if (settled) return;
      settled = true;
      if (code === 0) return resolve(child);
      if (code === 127) return reject(new Error('Comando no encontrado: revisa la ruta o el programa'));
      if (code === 126) return reject(new Error('El archivo no se puede ejecutar: revisa sus permisos'));
      reject(new Error(`El juego se cerró nada más abrirse (código ${code})`));
    });
    // Si no hay error de arranque en 600 ms, lo damos por lanzado
    setTimeout(() => { if (!settled) { settled = true; child.unref(); resolve(child); } }, 600);
  });
}

// Windows: abre una URL (steam://, heroic://, https://) con el programa que la tenga registrada
async function openUrl(url) {
  const { shell } = require('electron');
  await shell.openExternal(url);
  return null;
}

async function launch(game) {
  const l = game.launch || {};
  if (l.ensureDirs) for (const d of l.ensureDirs) { try { mkdirp(d); } catch { /* */ } }
  if (l.makeExecutable && !IS_WIN) {
    try {
      const st = fs.statSync(l.makeExecutable);
      if (!(st.mode & 0o111) && !/\.(exe|bat|msi)$/i.test(l.makeExecutable)) fs.chmodSync(l.makeExecutable, st.mode | 0o755);
    } catch { /* */ }
  }
  switch (l.type) {
    case 'steam': {
      // Steam acepta la URL steam://rungameid/<id> tanto si ya está abierto como si no
      const url = `steam://rungameid/${l.appid}`;
      if (ABRE_EL_SISTEMA) return openUrl(url);
      if (l.flatpak && which('flatpak')) return spawnDetached('flatpak', ['run', 'com.valvesoftware.Steam', url]);
      const bin = which('steam');
      if (bin) return spawnDetached(bin, [url]);
      if (which('flatpak') && fs.existsSync(path.join(HOME, '.var/app/com.valvesoftware.Steam'))) {
        return spawnDetached('flatpak', ['run', 'com.valvesoftware.Steam', url]);
      }
      return spawnDetached('xdg-open', [url]);
    }
    case 'url':
      if (ABRE_EL_SISTEMA) return openUrl(l.url);
      return spawnDetached('xdg-open', [l.url]);
    case 'exec': {
      // macOS: una aplicación (Juego.app) es una carpeta; se abre su ejecutable de dentro, para que Atrio vea cuándo
      // termina (y cuente las horas), como con cualquier otro juego
      if (IS_MAC && /\.app\/?$/i.test(l.cmd || '')) {
        const bin = ejecutableDeApp(l.cmd);
        if (bin) return spawnDetached(bin, l.args || [], { cwd: l.cwd, env: l.env });
        return spawnDetached('/usr/bin/open', ['-W', '-a', l.cmd, ...(l.args && l.args.length ? ['--args', ...l.args] : [])], { cwd: l.cwd, env: l.env });
      }
      return spawnDetached(l.cmd, l.args || [], { cwd: l.cwd, env: l.env });
    }
    case 'shell':
      if (!l.command || !l.command.trim()) throw new Error('Este juego no tiene comando de lanzamiento');
      // Windows: lo interpreta cmd.exe (las rutas van entre comillas dobles)
      if (IS_WIN) return spawnDetached(l.command, [], { cwd: l.cwd, env: l.env, shell: true });
      return spawnDetached('/bin/sh', ['-c', l.command], { cwd: l.cwd, env: l.env });
    default:
      throw new Error('Tipo de lanzamiento desconocido');
  }
}

/** El ejecutable de dentro de una .app de macOS (CFBundleExecutable de su Info.plist), o null. */
function ejecutableDeApp(app) {
  const dir = path.join(app, 'Contents', 'MacOS');
  let nombre = null;
  try { nombre = require('child_process').execFileSync('/usr/bin/plutil', ['-extract', 'CFBundleExecutable', 'raw', '-o', '-', path.join(app, 'Contents', 'Info.plist')], { encoding: 'utf8', timeout: 4000 }).trim(); } catch { /* sin plutil o sin la clave */ }
  if (nombre && fs.existsSync(path.join(dir, nombre))) return path.join(dir, nombre);
  try { const l = fs.readdirSync(dir).filter((f) => !f.startsWith('.')); if (l.length === 1) return path.join(dir, l[0]); } catch { /* */ }
  return null;
}

module.exports = { launch, cleanEnv, which, spawnDetached, ejecutableDeApp };
