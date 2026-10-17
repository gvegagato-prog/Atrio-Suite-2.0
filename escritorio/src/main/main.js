'use strict';
const { app, BrowserWindow, ipcMain, protocol, net, dialog, shell, screen, powerMonitor, nativeTheme } = require('electron');
const fs = require('fs');
const path = require('path');
const { pathToFileURL } = require('url');

const config = require('./config');
const art = require('./art');
const meta = require('./meta');
const ronron = require('./ronron');
const suite = require('./suite');
const web = require('./web');
const os = require('os');
const { launch } = require('./launcher');
const { createTracker } = require('./playlog');
const steam = require('./scanners/steam');
const heroic = require('./scanners/heroic');
const lutris = require('./scanners/lutris');
const local = require('./scanners/local');
const { HOME, XDG_CONFIG, exists, mkdirp } = require('./paths');

protocol.registerSchemesAsPrivileged([
  { scheme: 'atrio', privileges: { standard: true, secure: true, supportFetchAPI: true, corsEnabled: true, stream: true } },
]);

// Sandbox de Chromium: se mantiene activo (usa espacios de nombres de usuario, que Arch y
// CachyOS permiten). Sin sandbox, en algunos kernels la interfaz no puede crear su memoria
// compartida y se cae. Solo se desactiva si no hay alternativa (root o sin espacios de nombres).
function userNamespacesAvailable() {
  try { if (fs.readFileSync('/proc/sys/kernel/unprivileged_userns_clone', 'utf8').trim() === '0') return false; } catch { /* no existe: permitido */ }
  try { if (fs.readFileSync('/proc/sys/user/max_user_namespaces', 'utf8').trim() === '0') return false; } catch { /* */ }
  return true;
}
const runningAsRoot = typeof process.getuid === 'function' && process.getuid() === 0;
if (process.env.ATRIO_NO_SANDBOX === '1' || runningAsRoot || !userNamespacesAvailable()) app.commandLine.appendSwitch('no-sandbox');
// En algunos sistemas Chromium no puede crear memoria compartida en /dev/shm y la
// interfaz se cae al arrancar (ventana vacía). Con esto usa la carpeta temporal.
app.commandLine.appendSwitch('disable-dev-shm-usage');
// Interfaz en español: así las fechas de los formularios salen como día/mes/año
app.commandLine.appendSwitch('lang', 'es-ES');

// ---------- Registro de diagnóstico ----------
// ~/.cache/atrio/atrio.log: lo justo para saber por qué algo falla en otro equipo
const LOG_PATH = path.join(require('./paths').APP_CACHE_DIR, 'atrio.log');
function log(...parts) {
  const line = `[${new Date().toISOString()}] ${parts.map((p) => (typeof p === 'string' ? p : JSON.stringify(p))).join(' ')}`;
  try { console.log(line); } catch { /* */ }
  try { fs.mkdirSync(path.dirname(LOG_PATH), { recursive: true }); fs.appendFileSync(LOG_PATH, line + '\n'); } catch { /* */ }
}
try { fs.mkdirSync(path.dirname(LOG_PATH), { recursive: true }); fs.writeFileSync(LOG_PATH, ''); } catch { /* */ }
log('sandbox', app.commandLine.hasSwitch('no-sandbox') ? 'desactivado' : 'activo');
log('Atrio', app.getVersion(), 'Electron', process.versions.electron, 'Chrome', process.versions.chrome,
  'sesión', process.env.XDG_SESSION_TYPE || '?', 'wayland', process.env.WAYLAND_DISPLAY || '-', 'args', process.argv.slice(1).join(' '));

const install = require('./install');
const steamShortcut = require('./steamshortcut');
// ¿Atrio está añadido a Steam? Si es así y se quiere, se abre a través de Steam (mando completo)
function steamVia() {
  if (config.get().behavior.viaSteam === false) return null;
  try { return steamShortcut.findAtrio(); } catch { return null; }
}
// La instancia única se decide al arrancar, después de instalar/actualizar la copia fija
let primary = true;

let win = null;
let library = new Map();   // key -> juego (con datos internos)
let scanInfo = {};
let scanning = null;
let lastScan = 0;
let tracker = null;
let quitting = false;
const background = process.argv.includes('--background');

// ---------- Versiones de Proton instaladas (para juegos añadidos con umu-run) ----------
function protonList() {
  const dirs = [
    '/usr/share/steam/compatibilitytools.d',
    '/usr/local/share/steam/compatibilitytools.d',
    path.join(HOME, '.steam/root/compatibilitytools.d'),
    path.join(HOME, '.local/share/Steam/compatibilitytools.d'),
    path.join(HOME, '.var/app/com.valvesoftware.Steam/data/Steam/compatibilitytools.d'),
  ];
  for (const r of steam.candidateRoots(config.get().extraSteamPaths || [])) dirs.push(path.join(r.path, 'steamapps', 'common'));
  const out = [];
  const seen = new Set();
  for (const d of dirs) {
    let entries = [];
    try { entries = fs.readdirSync(d, { withFileTypes: true }); } catch { continue; }
    for (const e of entries) {
      if (!e.isDirectory() && !e.isSymbolicLink()) continue;
      const full = path.join(d, e.name);
      if (!exists(path.join(full, 'proton'))) continue;
      let real = full;
      try { real = fs.realpathSync(full); } catch { /* */ }
      if (seen.has(real)) continue;
      seen.add(real);
      let name = e.name;
      try {
        const vdf = require('./vdf').parseVdf(fs.readFileSync(path.join(full, 'compatibilitytool.vdf'), 'utf8'));
        const tools = require('./vdf').get(vdf, 'compatibilitytools', 'compat_tools') || {};
        const first = Object.values(tools)[0];
        const dn = first && require('./vdf').get(first, 'display_name');
        if (dn) name = dn;
      } catch { /* sin nombre bonito */ }
      out.push({ name, path: full });
    }
  }
  // CachyOS primero, luego GE, luego el resto
  const rank = (n) => (/cachyos/i.test(n) ? 0 : /ge-proton|proton-ge/i.test(n) ? 1 : 2);
  return out.sort((a, b) => rank(a.name) - rank(b.name) || b.name.localeCompare(a.name, 'es', { numeric: true }));
}

// ---------- Biblioteca ----------
function artUrls(game) {
  const files = art.resolve(game);
  const out = {};
  for (const [t, p] of Object.entries(files)) {
    let v = 0;
    try { v = Math.round(fs.statSync(p).mtimeMs); } catch { /* */ }
    out[t] = `atrio://art/${encodeURIComponent(game.key)}/${t}?v=${v}`;
  }
  return out;
}

// ---------- Horas de la cuenta ----------
// Las partidas de este equipo + las de tus otros equipos (misma cuenta de Ronrón), apuntadas a los
// juegos de esta biblioteca: por clave, por id de SteamGridDB o por nombre.
const nameKey = (s) => String(s || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, '');
function sgdbOf(g) {
  const ov = config.get().overrides[g.key] || {};
  return ov.sgdbId || art.knownId(g.key) || null;
}
// ¿Hay algún juego abierto ahora? (entonces Atrio no se pone delante)
function gameRunning() { return !!(tracker && tracker.state().active.length); }
// Juegos lanzados desde el móvil para jugar en streaming
const streamedKeys = new Set();

function playState() {
  if (!tracker) return { sessions: [], active: [] };
  const st = tracker.state();
  const uid = ronron.uid();
  const mine = st.sessions.filter((x) => !uid || !x[3] || x[3] === uid).map((x) => [x[0], x[1], x[2]]);
  const others = ronron.hoursOthers();
  if (!uid || !Object.keys(others).length) return { sessions: mine, active: st.active };
  const bySg = new Map();
  const byName = new Map();
  for (const g of library.values()) {
    const sg = sgdbOf(g);
    if (sg && !bySg.has(String(sg))) bySg.set(String(sg), g.key);
    const n = nameKey(g.name);
    if (n && !byName.has(n)) byName.set(n, g.key);
  }
  // Historiales borrados en cualquier equipo
  const forgot = { ...tracker.forgotten() };
  const mapKey = (k, info) => {
    if (library.has(k)) return k;
    if (info && info.sg && bySg.has(String(info.sg))) return bySg.get(String(info.sg));
    if (info && info.n && byName.has(nameKey(info.n))) return byName.get(nameKey(info.n));
    return null;
  };
  for (const p of Object.values(others)) {
    for (const [k, t] of Object.entries((p && p.forgot) || {})) {
      const lk = mapKey(k, p.games && p.games[k]);
      if (lk && Number(t) > (forgot[lk] || 0)) forgot[lk] = Number(t);
    }
  }
  const out = mine.filter((x) => !(forgot[x[0]] >= x[2]));
  for (const p of Object.values(others)) {
    for (const x of (p && p.s) || []) {
      const lk = mapKey(x[0], p.games && p.games[x[0]]);
      if (!lk || forgot[lk] >= x[2]) continue;
      out.push([lk, x[1], x[2]]);
    }
  }
  return { sessions: out, active: st.active };
}
// Lo que este equipo sube a la cuenta: sus partidas con nombre e id de SteamGridDB de cada juego
function hoursPayload(uid) {
  const st = tracker.state();
  const s = st.sessions.filter((x) => x[3] === uid).map((x) => [x[0], x[1], x[2]]);
  const games = {};
  for (const x of s) {
    if (games[x[0]]) continue;
    const g = library.get(x[0]);
    if (!g) continue;
    games[x[0]] = { n: g.name, sg: sgdbOf(g) || undefined };
  }
  return { name: require('os').hostname(), os: process.platform, games, s, forgot: tracker.forgotten() };
}
let hoursTimer = null;
function hoursSoon(delay = 8000) {
  clearTimeout(hoursTimer);
  hoursTimer = setTimeout(hoursSync, delay);
}
async function hoursSync() {
  const uid = ronron.uid();
  if (!uid || !tracker || !library.size) return;
  try {
    tracker.claim(uid);
    await ronron.hoursPush(hoursPayload(uid));
    if (await ronron.hoursPull()) send('play', playState());
  } catch (e) { log('horas de la cuenta', e.message); }
}

function publicGame(g) {
  const cfg = config.get();
  const ov = cfg.overrides[g.key] || {};
  const st = cfg.stats[g.key] || {};
  return {
    key: g.key,
    source: g.source,
    sourceLabel: g.sourceLabel,
    platform: g.platform || '',
    name: ov.name || g.name,
    originalName: g.name,
    sortName: ov.name ? ov.name.toLowerCase() : g.sortName,
    favorite: !!ov.favorite,
    hidden: !!ov.hidden,
    lastPlayed: Math.max(st.lastPlayed || 0, g.lastPlayedSource || 0),
    playCount: st.count || 0,
    art: artUrls(g),
    sgdbId: ov.sgdbId || null,
    sgdbKnown: ov.sgdbId || art.knownId(g.key) || null,
    aliasKeys: g.aliasKeys || [],
    custom: g.source === 'custom',
    customId: g.customId || null,
    pendingUpdate: !!g.pendingUpdate,
    steamPlaytime: g.steamPlaytime || 0,
    steamAppId: g.steamAppId || null,
    meta: meta.get(g),
    // Webs guardadas para este juego (guías, builds…) y si se trata como juego online
    links: Array.isArray(ov.links) ? ov.links : [],
    online: ov.online !== undefined ? !!ov.online : !!(meta.get(g) && meta.get(g).online),
  };
}

async function scanAll() {
  const cfg = config.get();
  const all = [];
  const info = { steam: null, heroic: null, lutris: null, errors: [] };
  const tryScan = async (name, fn) => {
    try { return await fn(); } catch (e) { info.errors.push(`${name}: ${e.message}`); return null; }
  };
  if (cfg.sources.steam) {
    const r = await tryScan('Steam', () => steam.scan(cfg));
    if (r) {
      all.push(...r.games);
      info.steam = { roots: r.roots, count: r.games.length, shortcuts: r.games.filter((g) => g.steamShortcut).length, flatpak: r.flatpak, problems: r.problems };
    }
  }
  if (cfg.sources.heroic) {
    const r = await tryScan('Heroic', () => heroic.scan(cfg));
    if (r) { all.push(...r.games); info.heroic = { count: r.games.length }; }
  }
  if (cfg.sources.lutris) {
    const r = await tryScan('Lutris', () => lutris.scan(cfg));
    if (r) { all.push(...r.games); info.lutris = { available: r.available, count: r.games.length }; }
  }
  if (cfg.sources.emulators) {
    const r = await tryScan('Emuladores', () => local.scanEmulators(cfg));
    if (r) all.push(...r);
  }
  if (cfg.sources.custom) {
    const r = await tryScan('Propios', () => local.scanCustom(cfg));
    if (r) all.push(...r);
  }
  // Si un juego de Heroic, Lutris, un emulador o uno añadido a mano también está añadido a Steam,
  // se muestra solo el de Steam: así se abre con el Proton y el mando configurados en Steam.
  const norm = (s) => String(s).normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, '');
  const inSteam = new Map();
  for (const g of all) if (g.steamShortcut) inSteam.set(norm(g.name), g);
  const merged = [];
  let dupes = 0;
  for (const g of all) {
    const twin = !g.steamShortcut && g.source !== 'steam' ? inSteam.get(norm(g.name)) : null;
    if (twin) {
      dupes++;
      // El acceso directo hereda el arte remoto (Heroic) y las rutas para contar horas
      twin.remoteArt = { ...(g.remoteArt || {}), ...(twin.remoteArt || {}) };
      if (!twin.installDir && g.installDir) twin.installDir = g.installDir;
      if (!twin.exePath && (g.exePath || g.romPath)) twin.exePath = g.exePath || g.romPath;
      twin.aliasKeys = [...(twin.aliasKeys || []), g.key];
      continue;
    }
    merged.push(g);
  }
  info.merged = dupes;
  library = new Map(merged.map((g) => [g.key, g]));
  scanInfo = info;
  lastScan = Date.now();
  return all;
}

async function rescan() {
  if (!scanning) scanning = scanAll().finally(() => { scanning = null; });
  await scanning;
  autoArt();
  try { suite.libChanged(); } catch { /* aún no ha arrancado */ }
  return state();
}

function state() {
  return {
    config: config.get(),
    games: [...library.values()].map(publicGame),
    scanInfo,
    home: HOME,
    play: playState(),
  };
}

function send(channel, payload) {
  try {
    if (win && !win.isDestroyed() && !win.webContents.isDestroyed() && !win.webContents.isCrashed()) win.webContents.send(channel, payload);
  } catch { /* la interfaz se está recargando */ }
}

function autoArt(force = false, onlyKey = null) {
  const cfg = config.get();
  const wanted = ['cover', 'wide', 'hero', 'logo'];
  for (const g of library.values()) {
    if (onlyKey && g.key !== onlyKey) continue;
    const ov = cfg.overrides[g.key] || {};
    if (ov.hidden && !onlyKey) continue;
    art.enqueue({
      game: named(g), apiKey: cfg.sgdbKey, wanted, force,
      onUpdate: (key) => { const gg = library.get(key); if (gg) send('art', publicGame(gg)); },
      onIdle: () => send('art-idle', null),
    });
  }
}

// Datos de la tienda para toda la biblioteca, de uno en uno y sin prisa (la tienda limita peticiones)
let metaRunning = false;
async function prefetchMeta() {
  if (metaRunning) return;
  metaRunning = true;
  const cfg = config.get();
  const list = [...library.values()].filter((g) => !(cfg.overrides[g.key] || {}).hidden && g.source !== 'emulators');
  for (const g of list) {
    if (meta.get(g) && meta.get(g).t && Date.now() - meta.get(g).t < 7 * 24 * 3600 * 1000) continue;
    const before = JSON.stringify(meta.get(g));
    await meta.ensure(named(g)).catch(() => null);
    if (JSON.stringify(meta.get(g)) !== before) send('art', publicGame(g));
    await new Promise((r) => setTimeout(r, 2500));
  }
  metaRunning = false;
}

// El juego con el nombre que le has puesto en Atrio: es el que se usa para buscar en
// SteamGridDB y en la tienda de Steam (p. ej. «Alan Wake II» en vez de «AlanWake2.exe»)
function named(g) {
  const ov = config.get().overrides[g.key] || {};
  return ov.name ? { ...g, name: ov.name } : g;
}

// ---------- Suite: lo que el móvil ve del PC ----------
// El juego que se está jugando ahora (el último que ha empezado), con arte que el móvil puede cargar
function currentGame() {
  if (!tracker) return null;
  const act = [...(tracker.state().active || [])].sort((a, b) => b.s - a.s)[0];
  if (!act) return null;
  const g = library.get(act.k);
  if (!g) return null;
  const sg = Number(sgdbOf(g)) || 0;
  return {
    key: g.key, name: named(g).name, sg, s: act.s, src: g.source,
    cover: art.publicUrl(g, 'cover'), hero: art.publicUrl(g, 'hero'), logo: art.publicUrl(g, 'logo'),
  };
}
// La biblioteca resumida para el móvil
function suiteLibrary() {
  const cfg = config.get();
  const mins = new Map();
  if (tracker) for (const x of tracker.state().sessions) mins.set(x[0], (mins.get(x[0]) || 0) + (x[2] - x[1]));
  return [...library.values()].filter((g) => !(cfg.overrides[g.key] || {}).hidden).map((g) => {
    const st = cfg.stats[g.key] || {};
    return {
      key: g.key, name: named(g).name, cover: art.publicUrl(g, 'cover') || art.publicUrl(g, 'wide'),
      sg: Number(sgdbOf(g)) || 0, fav: !!(cfg.overrides[g.key] || {}).favorite,
      lp: Math.max(st.lastPlayed || 0, g.lastPlayedSource || 0), src: g.source,
      // Las dos en milisegundos: se pasan a minutos (antes Steam iba sin pasar y salían horas infladas)
      min: Math.round(Math.max(mins.get(g.key) || 0, g.steamPlaytime || 0) / 60000),
      links: Array.isArray((cfg.overrides[g.key] || {}).links) ? cfg.overrides[g.key].links : [],
    };
  });
}
// Al cerrar un juego: la partida pasa a Ronrón como «parte jugada» para valorarla (en el móvil o aquí)
async function segFromSession(key) {
  const cfg = config.get().suite || {};
  if (cfg.autoSeg === false || !tracker || !ronron.status().signed) return;
  const ses = [...tracker.state().sessions].reverse().find((x) => x[0] === key);
  if (!ses) return;
  const min = Math.round((ses[2] - ses[1]) / 60000);
  if (min < (Number(cfg.segMin) || 5)) return;
  const g = library.get(key);
  const id = g ? Number(sgdbOf(g)) : 0;
  if (!id) return;
  const p = ((ronron.data() || {}).plays || []).find((o) => Number(o.id) === id && o.status === 1);
  if (!p) return;
  try {
    // «Valorar por partes» apagado en esa ficha: la partida se guarda (horas) sin pedir nota
    const quiet = !!p.noParts;
    await ronron.edit({ op: 'addSeg', kind: 0, id, seg: { id: `a${ses[1]}`, s: ses[1], e: ses[2], min, src: 'atrio', pc: os.hostname(), ...(quiet ? { q: 1 } : {}) } });
    if (!quiet) send('seg', { id, name: p.name, min });
  } catch (e) { log('parte jugada', e.message); }
}

function setupSuiteIpc() {
  ipcMain.handle('suite:state', () => suite.state());
  ipcMain.handle('suite:shot', () => suite.takeAndUpload({}).then((r) => ({ id: r.id, w: r.w, h: r.h, method: r.method })));
  ipcMain.handle('suite:shots', (_e, key) => suite.localShots(key).map((x) => ({ ...x, url: `atrio://shot/${x.id}` })));
  ipcMain.handle('suite:shotDelete', (_e, id) => {
    const s0 = suite.forgetShot(id);
    if (s0 && s0.file) { try { require('fs').unlinkSync(s0.file); } catch { /* ya no estaba */ } }
    return true;
  });
  ipcMain.handle('suite:shotShow', (_e, id) => { const f = suite.shotFile(id); if (f) shell.showItemInFolder(f); return !!f; });
  ipcMain.handle('suite:methods', () => require('./capture').methods());
  // Streaming (Polaris)
  ipcMain.handle('stream:info', () => require('./polaris').refresh());
  ipcMain.handle('stream:start', () => require('./polaris').startService());
  ipcMain.handle('stream:check', async () => {
    const sun = require('./polaris');
    await sun.refresh();
    if (!sun.info().running) return { ok: false, msg: 'Polaris no está en marcha.' };
    try { const c = await sun.clients(); return { ok: true, msg: `Usuario correcto · ${c.length} ${c.length === 1 ? 'dispositivo emparejado' : 'dispositivos emparejados'}` }; } catch (e) { return { ok: false, msg: e.message }; }
  });
  ipcMain.handle('stream:web', () => shell.openExternal(`https://localhost:${(require('./polaris').info().port || 47989) + 1}`));
  // Webs dentro de la página del juego
  ipcMain.handle('web:open', (_e, url, bounds) => web.open(url, bounds));
  ipcMain.handle('web:bounds', (_e, bounds) => { web.setBounds(bounds); return true; });
  ipcMain.handle('web:hide', () => web.hide());
  ipcMain.handle('web:show', (_e, bounds) => web.show(bounds));
  ipcMain.handle('web:close', () => web.close());
  ipcMain.handle('web:nav', (_e, action) => web.nav(action));
  ipcMain.handle('web:state', () => web.state());
}

// ---------- Lanzar y energía (desde la interfaz o desde el móvil) ----------
async function launchGame(key) {
  const g = library.get(key);
  if (!g) throw new Error('Juego no encontrado');
  // KDE: que no salga la ventana de Steam «Preparando para iniciar…» por encima
  if (g.launch && g.launch.type === 'steam' && config.get().behavior.hideSteamLaunch !== false && !process.env.ATRIO_SHOTS) {
    const armed = await require('./kwinhide').arm(win);
    log('ocultar ventanas de Steam', armed ? 'activo' : 'no disponible');
  }
  const child = await launch(g);
  const cfg = config.get();
  const st = { ...(cfg.stats[key] || {}) };
  st.lastPlayed = Date.now();
  st.count = (st.count || 0) + 1;
  config.save({ ...cfg, stats: { ...cfg.stats, [key]: st } });
  const mode = cfg.behavior.onLaunch;
  if (win && mode === 'minimize') setTimeout(() => win && win.minimize(), 1200);
  if (mode === 'quit') setTimeout(() => app.quit(), 1500);
  // El registro de partidas vigila el juego; al cerrarlo, Atrio vuelve a primer plano
  if (tracker) tracker.expect(key, g.launch && g.launch.type === 'shell' && child ? child.pid : null);
  return publicGame(g);
}

// Apagar, reiniciar o suspender el equipo (systemd; con polkit no pide contraseña en tu sesión)
async function doPower(action) {
  const cmd = { apagar: 'poweroff', reiniciar: 'reboot', suspender: 'suspend' }[action];
  if (!cmd) throw new Error('Acción desconocida');
  log('energía', action);
  if (tracker) { if (cmd === 'suspend') tracker.pause(); else tracker.stop(); }
  if (process.env.ATRIO_POWER_DRY) return { ok: true, dry: cmd };
  const { execFile } = require('child_process');
  if (process.platform === 'win32') {
    // Windows: shutdown para apagar/reiniciar; para suspender, SetSuspendState (no hiberna)
    const [bin, args] = cmd === 'suspend'
      ? ['powershell.exe', ['-NoProfile', '-NonInteractive', '-Command',
        'Add-Type -AssemblyName System.Windows.Forms; if (-not [System.Windows.Forms.Application]::SetSuspendState([System.Windows.Forms.PowerState]::Suspend, $false, $false)) { exit 1 }']]
      : ['shutdown.exe', [cmd === 'reboot' ? '/r' : '/s', '/t', '0']];
    return new Promise((resolve, reject) => {
      execFile(bin, args, { windowsHide: true, timeout: 20000 }, (err, _out, errOut) => {
        if (err) {
          if (cmd !== 'suspend' && tracker) tracker.start();
          log('energía falló', String(errOut || err.message).slice(0, 300));
          reject(new Error(cmd === 'suspend' ? 'Windows no ha dejado suspender el equipo.' : `No se ha podido: ${String(errOut || err.message).trim().slice(0, 160)}`));
        } else resolve({ ok: true });
      });
    });
  }
  // Linux: se prueban varias vías, de la más directa a la de la sesión de escritorio, hasta que una funcione
  const env = require('./launcher').cleanEnv();
  const run = (bin, args) => new Promise((resolve) => {
    execFile(bin, args, { env, timeout: 15000 }, (err, out, errOut) => resolve({ ok: !err, out: String(out || ''), err: String(errOut || (err && err.message) || '').trim() }));
  });
  const method = { poweroff: 'PowerOff', reboot: 'Reboot', suspend: 'Suspend' }[cmd];
  const tries = [
    ['systemctl', [cmd]],
    // logind directamente (interactivo: si hace falta, el escritorio puede pedir permiso)
    ['busctl', ['call', 'org.freedesktop.login1', '/org/freedesktop/login1', 'org.freedesktop.login1.Manager', method, 'b', 'true']],
  ];
  if (cmd !== 'suspend') {
    // Plasma: cierra la sesión (y con ella lo que impedía apagar) y luego apaga, como el menú de inicio
    const kde = cmd === 'reboot' ? 'logoutAndReboot' : 'logoutAndShutdown';
    for (const q of ['qdbus6', 'qdbus-qt6', 'qdbus']) tries.push([q, ['org.kde.Shutdown', '/Shutdown', kde]]);
    tries.push(['gnome-session-quit', [cmd === 'reboot' ? '--reboot' : '--power-off', '--no-prompt']]);
  }
  const errors = [];
  for (const [bin, args] of tries) {
    const r = await run(bin, args);
    if (r.ok) { log('energía con', bin); return { ok: true, via: bin }; }
    if (!/ENOENT|not found|No such file|ServiceUnknown|was not provided|Cannot find/i.test(r.err)) errors.push(`${bin}: ${r.err}`);
    log('energía falló con', bin, r.err.slice(0, 200));
  }
  if (cmd !== 'suspend' && tracker) tracker.start();
  // Quién lo impide (programas que bloquean el apagado o la suspensión)
  let who = '';
  try {
    const r = await run('systemd-inhibit', ['--list', '--no-pager', '--no-legend']);
    const what = cmd === 'suspend' ? 'sleep' : 'shutdown';
    who = r.out.split('\n').filter((l) => l.includes(what) && /\bblock\b/.test(l)).map((l) => l.trim().split(/\s+/)[0]).filter(Boolean).join(', ');
  } catch { /* */ }
  const all = errors.join(' · ');
  throw new Error(who ? `No me deja: lo está bloqueando ${who}. Ciérralo y vuelve a probar.`
    : /authoriz|autoriz|polkit|Access denied|Interactive authentication/i.test(all) ? 'El sistema no me deja (falta permiso de polkit). Mira «Energía» en el README de Atrio.'
      : `No se ha podido: ${all.slice(0, 200) || 'ninguna vía ha funcionado'}`);
}

// ---------- IPC ----------
function setupIpc() {
  ipcMain.handle('state', () => state());
  ipcMain.handle('rescan', () => rescan());

  ipcMain.handle('config:patch', async (_e, partial) => {
    const before = config.get();
    const next = config.patch(partial);
    const needRescan = ['sources', 'emulators', 'customGames', 'extraSteamPaths', 'extraHeroicPaths']
      .some((k) => k in partial && JSON.stringify(before[k]) !== JSON.stringify(next[k]));
    if ('sgdbKey' in partial && partial.sgdbKey && partial.sgdbKey !== before.sgdbKey) {
      setImmediate(() => autoArt(true));
    }
    if (needRescan) return rescan();
    if ('behavior' in partial && 'fullscreen' in (partial.behavior || {}) && win && !process.env.ATRIO_SHOTS) {
      win.setFullScreen(!!next.behavior.fullscreen);
    }
    return state();
  });

  ipcMain.handle('override', (_e, key, patch) => {
    const cfg = config.get();
    const cur = { ...(cfg.overrides[key] || {}), ...patch };
    for (const k of Object.keys(cur)) if (cur[k] === null || cur[k] === '' || cur[k] === false) delete cur[k];
    const overrides = { ...cfg.overrides };
    if (Object.keys(cur).length) overrides[key] = cur; else delete overrides[key];
    config.save({ ...cfg, overrides });
    if ('sgdbId' in patch) art.forgetSgdbId(key);
    // Al renombrar, se vuelve a buscar con el nombre nuevo: arte que falte y datos de la tienda
    if ('name' in patch) {
      art.forgetSgdbId(key);
      setImmediate(() => autoArt(true, key));
    }
    const g = library.get(key);
    return g ? publicGame(g) : null;
  });

  ipcMain.handle('launch', (_e, key) => launchGame(key));
  // Cambiar de interfaz: se guarda la elección (salvo en las capturas automáticas) y se carga la otra página
  ipcMain.handle('ui:switch', (_e, mode) => {
    const m = mode === 'lanzador' ? 'lanzador' : 'revista';
    if (!process.env.ATRIO_SHOTS) config.patch({ ui: m });
    if (win && !win.isDestroyed()) {
      try { web.close(); } catch { /* no había ninguna web abierta */ }
      try { win.setBackgroundColor(uiBackground(m)); } catch { /* */ }
      // Después de responder, para que la interfaz que se va no se quede esperando la respuesta
      setImmediate(() => { if (win && !win.isDestroyed()) win.loadFile(uiFile(m)); });
    }
    return m;
  });
  ipcMain.handle('power', (_e, action) => doPower(action));
  // La revista busca libros, pelis, series y juegos (Descubrir y «Añadir»): solo en estas webs y solo JSON.
  // El token de TMDB («eyJ…») va en la cabecera, como en el móvil; las claves v3 ya van en la dirección.
  const NET_OK = new Set(['openlibrary.org', 'api.themoviedb.org', 'api.tvmaze.com', 'api.rawg.io']);
  ipcMain.handle('net:json', async (_e, url, token) => {
    let u;
    try { u = new URL(String(url)); } catch { throw new Error('Dirección no válida'); }
    if (u.protocol !== 'https:' || !NET_OK.has(u.hostname)) throw new Error('Esa web no está permitida');
    const headers = { 'User-Agent': 'Atrio/escritorio (app personal)', Accept: 'application/json' };
    if (token && u.hostname === 'api.themoviedb.org') headers.Authorization = `Bearer ${String(token)}`;
    const ctl = new AbortController();
    const t = setTimeout(() => ctl.abort(), 20000);
    try {
      const r = await fetch(u.href, { headers, signal: ctl.signal });
      if (r.status === 401) throw new Error('La clave no es válida (401): revísala en el móvil, en Ajustes → Servicios');
      if (r.status === 429) throw new Error('Demasiadas búsquedas seguidas: espera un momento');
      if (!r.ok) throw new Error(`El servidor respondió ${r.status}`);
      return await r.json();
    } catch (e) {
      if (e.name === 'AbortError') throw new Error('La web tarda demasiado en contestar');
      throw e;
    } finally { clearTimeout(t); }
  });
  setupSuiteIpc();


  ipcMain.handle('sgdb:search', (_e, term) => {
    const key = sgdbKeyAny();
    if (!key) throw new Error('Añade tu clave de SteamGridDB en Ajustes');
    return art.searchGames(key, term);
  });

  ipcMain.handle('sgdb:test', async (_e, key) => {
    await art.searchGames(key, 'Portal');
    return true;
  });

  ipcMain.handle('sgdb:assets', async (_e, gameKey, type, sgdbId, page) => {
    const key = sgdbKeyAny();
    if (!key) throw new Error('Añade tu clave de SteamGridDB en Ajustes');
    const g = library.get(gameKey);
    if (!g) throw new Error('Juego no encontrado');
    const ov = config.get().overrides[gameKey] || {};
    return art.listAssets(key, named(g), type, sgdbId || ov.sgdbId, page || 0);
  });

  ipcMain.handle('art:set', async (_e, gameKey, type, source) => {
    const g = library.get(gameKey);
    if (!g) throw new Error('Juego no encontrado');
    if (!source) art.removeType(gameKey, type);
    else if (/^https?:\/\//.test(source)) await art.download(source, gameKey, type);
    else art.setFromFile(gameKey, type, source);
    return publicGame(g);
  });

  // Fondo reescalado generado por la interfaz para pantallas grandes
  ipcMain.handle('art:saveHd', (_e, gameKey, buffer) => {
    const g = library.get(gameKey);
    if (!g || !buffer || buffer.byteLength < 1000 || buffer.byteLength > 40 * 1024 * 1024) return null;
    art.saveHd(gameKey, buffer);
    return publicGame(g);
  });
  ipcMain.handle('art:refetch', (_e, gameKey, replace) => {
    if (gameKey && replace) for (const t of art.TYPES) art.clearCached(gameKey, t);
    autoArt(true, gameKey || null);
    return true;
  });

  ipcMain.handle('dialog:file', async (_e, opts = {}) => {
    const r = await dialog.showOpenDialog(win, {
      title: opts.title || 'Elegir archivo',
      defaultPath: opts.defaultPath || HOME,
      properties: [opts.directory ? 'openDirectory' : 'openFile', 'showHiddenFiles'],
      filters: opts.filters,
    });
    return r.canceled ? null : r.filePaths[0];
  });

  ipcMain.handle('win:fullscreen', () => {
    if (!win) return false;
    win.setFullScreen(!win.isFullScreen());
    return win.isFullScreen();
  });
  ipcMain.handle('win:minimize', () => win && win.minimize());
  ipcMain.handle('app:quit', () => app.quit());
  ipcMain.handle('open:external', (_e, url) => {
    if (/^https:\/\/(www\.)?steamgriddb\.com\//.test(url)) shell.openExternal(url);
  });
  ipcMain.handle('fs:exists', (_e, p) => exists(p));
  ipcMain.handle('log:path', () => LOG_PATH);
  ipcMain.handle('autostart:get', () => ({ ...install.state(), steam: steamShortcut.findAtrio() }));
  ipcMain.handle('autostart:set', (_e, mode) => {
    const st = install.setAutostart(mode, steamVia());
    config.patch({ behavior: { autostart: mode } });
    return st;
  });
  ipcMain.handle('log', (_e, msg) => { log('interfaz:', String(msg).slice(0, 500)); });
  ipcMain.handle('proton:list', () => protonList());
  ipcMain.handle('meta:get', async (_e, key) => {
    const g = library.get(key);
    if (!g) return null;
    await meta.ensure(named(g)).catch(() => null);
    return publicGame(g);
  });
  ipcMain.handle('play:state', () => playState());
  ipcMain.handle('play:forget', (_e, key) => { if (tracker) tracker.forget(key); hoursSoon(1000); return playState(); });

  // ---------- Ronrón ----------
  const rrState = () => ({ status: ronron.status(), data: ronron.data() });
  ipcMain.handle('ronron:state', () => rrState());
  ipcMain.handle('ronron:login', async (_e, user, pass, webKey) => {
    await ronron.signIn(user, pass, webKey);
    hoursSync().catch(() => {});
    send('play', playState());
    return rrState();
  });
  ipcMain.handle('ronron:logout', () => { ronron.signOut(); send('play', playState()); return rrState(); });
  ipcMain.handle('ronron:sync', async () => { await ronron.sync(false); return rrState(); });
  ipcMain.handle('ronron:edit', async (_e, op) => { await ronron.edit(op); return rrState(); });
  // Portada, fondo y logo de SteamGridDB para una ficha nueva (URL, para que también se vean en el móvil)
  ipcMain.handle('ronron:art', (_e, sgdbId) => ronronArt(sgdbId));
  // Lista de arte de SteamGridDB para una ficha de Ronrón (para elegir portada, fondo o logo)
  ipcMain.handle('ronron:assets', async (_e, sgdbId, type, page) => {
    const key = sgdbKeyAny();
    if (!key) throw new Error('Añade tu clave de SteamGridDB en Ajustes');
    return art.listAssets(key, { key: `ronron:${sgdbId}`, name: '' }, type, sgdbId, page || 0);
  });
}

// La clave de SteamGridDB: la de este PC o, si no tiene, la de tu cuenta (la que pusiste en el móvil)
function sgdbKeyAny() {
  return config.get().sgdbKey || String(((ronron.data() || {}).keys || {}).sgdb || '').trim();
}
// Id de una ficha a mano: el mismo que customItemId() del móvil (FNV-1a de 64 bits sobre el nombre normalizado)
function customItemId(name) {
  const s = String(name || '').normalize('NFD').replace(/\p{M}+/gu, '').toLowerCase().replace(/[^\p{L}\p{Nd}]+/gu, '');
  if (!s) return 0;
  const M = (1n << 64n) - 1n;
  let x = 0xcbf29ce484222325n;
  for (let i = 0; i < s.length; i++) { x ^= BigInt(s.charCodeAt(i)); x = (x * 0x100000001b3n) & M; }
  return Number((x & 0x7fffffffffffffffn) % 9000000000000n + 100000000000n);
}

async function ronronArt(sgdbId) {
  const key = sgdbKeyAny();
  if (!key || !sgdbId) return { cover: '', hero: '', logo: '' };
  const fake = { key: `ronron:${sgdbId}`, name: '' };
  const pick = async (type) => {
    try {
      const r = await art.listAssets(key, fake, type, sgdbId, 0);
      const items = type === 'hero' ? [...r.items].sort((a, b) => (b.width || 0) - (a.width || 0)) : r.items;
      return items.length ? items[0].url : '';
    } catch { return ''; }
  };
  const [cover, hero, logo] = await Promise.all([pick('cover'), pick('hero'), pick('logo')]);
  return { cover, hero, logo };
}

// Lo que Atrio ve que juegas pasa a «Jugando» en tu cuenta (tras 2 minutos seguidos, para no contar
// lanzamientos de prueba). Se puede desactivar en Ajustes, Ronrón.
const ronronSeen = new Map(); // clave -> día en que ya se comprobó
async function ronronWatch() {
  if (!tracker || config.get().behavior.ronronAuto === false || !ronron.status().signed) return;
  const now = Date.now();
  const day = new Date().toDateString();
  const min = Number(process.env.ATRIO_RONRON_AUTO_MIN || 2) * 60 * 1000;
  for (const a of tracker.state().active || []) {
    if (now - a.s < min || ronronSeen.get(a.k) === day) continue;
    const g = library.get(a.k);
    if (!g) continue;
    const ng = named(g);
    let id = (config.get().overrides[g.key] || {}).sgdbId || art.knownId(g.key) || null;
    const key = sgdbKeyAny();
    let looked = !key;
    if (!id && key) { try { id = await art.gameId(key, ng); looked = true; } catch { id = null; } }
    // SteamGridDB no lo conoce (o no hay clave): se añade igual, como una ficha a mano con su nombre.
    // Si la búsqueda falló por la red, se reintenta en el siguiente minuto.
    if (!id && !looked) continue;
    const sg = !!id;
    if (!id) id = customItemId(ng.name);
    if (!id) { ronronSeen.set(a.k, day); continue; }
    if (ronron.knows(id)) { ronronSeen.set(a.k, day); continue; }
    const pics = sg ? await ronronArt(id).catch(() => ({})) : {};
    // Con las partes jugadas activadas, los minutos se suman al cerrar el juego (no se cuentan dos veces)
    const segsOn = (config.get().suite || {}).autoSeg !== false;
    const what = await ronron.autoPlay({ id, name: ng.name, ...pics, minutes: segsOn ? 0 : (now - a.s) / 60000 }).catch(() => undefined);
    // Solo se da por visto si se ha podido guardar (si falló, se reintenta)
    if (what !== undefined) ronronSeen.set(a.k, day);
    if (what) send('notice', what === 'nuevo' ? `${ng.name} está ahora en «Jugando»` : `${ng.name} vuelve a «Jugando»`);
  }
}

// ---------- Interfaz: la revista (escritorio) o el lanzador clásico (pantalla grande y mando) ----------
// Se puede forzar con ATRIO_UI=revista|lanzador o con --revista / --lanzador. Las capturas automáticas
// (ATRIO_SHOTS) usan el lanzador salvo que se pida otra cosa: su plan de pasos (window.__test) es de esa interfaz.
function uiMode() {
  const env = process.env.ATRIO_UI;
  if (env === 'revista' || env === 'lanzador') return env;
  if (process.argv.includes('--lanzador')) return 'lanzador';
  if (process.argv.includes('--revista')) return 'revista';
  if (process.env.ATRIO_SHOTS) return 'lanzador';
  // Atrio 2: siempre la revista (que empieza en su modo Juegos, el lanzador). El lanzador clásico solo
  // se abre a propósito (--lanzador o ATRIO_UI=lanzador) y lo usan las pruebas automáticas.
  return 'revista';
}
function uiFile(mode) {
  return mode === 'lanzador'
    ? path.join(__dirname, '..', 'renderer', 'index.html')
    : path.join(__dirname, '..', 'renderer', 'revista', 'index.html');
}
// Color de la ventana mientras carga (para que la revista en claro no parpadee en negro)
function uiBackground(mode) {
  if (mode === 'lanzador') return '#0d0d0f';
  const tema = (config.get().revista || {}).tema;
  const dark = tema === 'oscuro' || (tema !== 'claro' && nativeTheme.shouldUseDarkColors);
  return dark ? '#141210' : '#F3EEE4';
}

// ---------- Ventana ----------
function createWindow() {
  const cfg = config.get();
  const shots = !!process.env.ATRIO_SHOTS;
  const ui = uiMode();
  let size = { width: 1600, height: 900 };
  if (shots && process.env.ATRIO_SIZE) {
    const [w, h] = process.env.ATRIO_SIZE.split('x').map(Number);
    size = { width: w, height: h };
  } else {
    try { const wa = screen.getPrimaryDisplay().workAreaSize; size = { width: wa.width, height: wa.height }; } catch { /* */ }
  }
  win = new BrowserWindow({
    ...size,
    useContentSize: true,
    show: false,
    frame: false,
    fullscreen: false,
    backgroundColor: uiBackground(ui),
    title: 'Atrio',
    icon: path.join(__dirname, '..', 'renderer', 'icon.png'),
    webPreferences: {
      preload: path.join(__dirname, '..', 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      backgroundThrottling: false,
      offscreen: false,
    },
  });
  win.setMenuBarVisibility(false);
  web.attach(win, send);
  win.loadFile(uiFile(ui));
  // La pantalla completa se aplica después de mostrar la ventana: crearla ya a pantalla
  // completa da problemas en algunos compositores Wayland
  // Siempre a pantalla completa (la revista con su modo Juegos es el lanzador). --ventana para depurar
  const wantFull = !shots && !process.argv.includes('--ventana') && (ui === 'revista' || !!cfg.behavior.fullscreen);
  win.once('ready-to-show', () => {
    if (shots) { win.showInactive(); return; }
    if (background) return;
    win.show();
    // La revista es una app de escritorio: se abre ocupando la pantalla, pero en ventana
    if (ui === 'revista' && !process.argv.includes('--ventana')) win.maximize();
    if (wantFull) setTimeout(() => { if (win) win.setFullScreen(true); }, 250);
  });
  win.on('show', () => { if (wantFull && win && !win.isFullScreen()) setTimeout(() => { if (win) win.setFullScreen(true); }, 250); });

  // Diagnóstico y recuperación si la parte gráfica se cae
  let reloads = 0;
  const wc = win.webContents;
  wc.on('did-finish-load', () => log('interfaz cargada'));
  wc.on('did-fail-load', (_e, code, desc, url) => log('fallo al cargar', code, desc, url));
  wc.on('preload-error', (_e, p, err) => log('error en preload', p, String(err && err.stack || err)));
  wc.on('console-message', (...args) => {
    const ev = args[0] || {};
    const level = ev.level !== undefined ? ev.level : args[1];
    const msg = ev.message !== undefined ? ev.message : args[2];
    const isErr = level === 'error' || level === 'warning' || level === 3 || level === 2;
    if (isErr) log('consola', level, msg, ev.sourceId || args[4] || '', ev.lineNumber || args[3] || '');
  });
  wc.on('render-process-gone', (_e, details) => {
    log('la interfaz se cerró', details);
    if (quitting) return;
    if (reloads < 2) { reloads++; setTimeout(() => { if (win && !win.isDestroyed()) win.reload(); }, 600); return; }
    dialog.showErrorBox('Atrio no ha podido mostrar la interfaz',
      `La parte gráfica se ha cerrado varias veces (${details.reason}).\n\nHay un registro con los detalles en:\n${LOG_PATH}`);
  });
  // Con el arranque automático activo, cerrar la ventana la oculta y Atrio sigue contando horas
  win.on('close', (e) => {
    if (quitting || shots || install.state().mode !== 'hidden') return;
    e.preventDefault();
    win.hide();
  });
  win.on('focus', () => send('winfocus', true));
  win.on('blur', () => send('winfocus', false));
  win.on('closed', () => { win = null; });
  const onFocus = () => {
    send('focus', null);
    try {
      const st = install.state();
      const via = steamVia();
      if (st.mode === 'visible' && !!via !== st.viaSteam) install.setAutostart('visible', via);
    } catch { /* */ }
    if (Date.now() - lastScan < 45000 || scanning) return;
    const before = [...library.keys()].sort().join('|');
    rescan().then((s) => {
      if ([...library.keys()].sort().join('|') !== before) send('library', s);
    }).catch(() => {});
  };
  win.on('restore', onFocus);
  win.on('focus', onFocus);
  win.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  win.webContents.on('will-navigate', (e) => e.preventDefault());
  if (shots) runShots();
}

// ---------- Capturas automáticas (solo para pruebas de desarrollo) ----------
async function runShots() {
  const out = process.env.ATRIO_SHOTS;
  const plan = JSON.parse(fs.readFileSync(process.env.ATRIO_SHOT_PLAN, 'utf8'));
  fs.mkdirSync(out, { recursive: true });
  const wc = win.webContents;
  await new Promise((r) => wc.once('did-finish-load', r));
  const wait = (ms) => new Promise((r) => setTimeout(r, ms));
  await wait(2500);
  for (const step of plan) {
    try {
      const res = await wc.executeJavaScript(`window.__test ? window.__test(${JSON.stringify(step)}) : 'sin hook'`);
      if (res) console.log(step.name, '->', res);
    } catch (e) { console.log('error', step.name, e.message); }
    await wait(step.wait || 900);
    if (step.name) {
      // screen: toda la pantalla (incluye las webs de Guías, que van en una capa nativa aparte)
      if (step.screen) {
        try { require('child_process').execFileSync('import', ['-window', 'root', path.join(out, `${step.name}.png`)]); continue; } catch { /* sin ImageMagick */ }
      }
      const img = await wc.capturePage();
      fs.writeFileSync(path.join(out, `${step.name}.png`), img.toPNG());
    }
  }
  app.quit();
}

app.on('child-process-gone', (_e, d) => log('proceso secundario cerrado', d));

app.whenReady().then(async () => {
  let inst = null;
  if (!process.env.ATRIO_SHOTS) {
    try { inst = await install.installSelf(app.getVersion(), path.join(__dirname, '..', 'renderer', 'icon.png')); log('instalación', inst); }
    catch (e) { log('no se pudo instalar la copia fija', e.message); }
    primary = app.requestSingleInstanceLock({ version: app.getVersion(), updated: !!(inst && inst.updated) });
    if (!primary) { app.quit(); return; }
  }
  config.load();
  // Windows: el instalador ya hizo su parte; la primera vez se activa el arranque al iniciar sesión
  if (process.platform === 'win32' && app.isPackaged && !process.env.ATRIO_SHOTS) {
    try {
      const fresh = !config.get().behavior.autostart;
      const want = config.get().behavior.autostart || 'visible';
      if (fresh) config.patch({ behavior: { autostart: want } });
      if (fresh || install.state().mode !== want) install.setAutostart(want, steamVia());
      if (fresh) setTimeout(() => send('notice', 'Atrio se abrirá al iniciar sesión. Puedes cambiarlo en Ajustes, Comportamiento.'), 3500);
    } catch (e) { log('arranque automático', e.message); }
  }
  // Menú de aplicaciones y arranque automático apuntando siempre a la copia instalada
  if (inst && inst.installed) {
    try {
      install.writeMenuEntry();
      const want = config.get().behavior.autostart || 'visible';
      if (!config.get().behavior.autostart) config.patch({ behavior: { autostart: want } });
      install.setAutostart(want, steamVia());
      if (inst.fresh) setTimeout(() => send('notice', 'Atrio ya está en tu menú de aplicaciones y se abrirá al iniciar sesión. Puedes cambiarlo en Ajustes, Comportamiento.'), 3500);
    } catch (e) { log('menú o arranque automático', e.message); }
  }
  try { log('GPU', app.getGPUFeatureStatus()); } catch { /* */ }
  art.setFetch((...a) => net.fetch(...a));
  meta.setFetch((...a) => net.fetch(...a));
  ronron.setFetch((...a) => net.fetch(...a));
  ronron.start(() => send('ronron', { status: ronron.status(), data: ronron.data() }));
  protocol.handle('atrio', async (req) => {
    const u = new URL(req.url);
    const parts = u.pathname.split('/').filter(Boolean);
    if (u.host === 'art' && parts.length === 2) {
      const key = decodeURIComponent(parts[0]);
      const g = library.get(key);
      const p = g && art.resolve(g)[parts[1]];
      if (p) {
        const r = await net.fetch(pathToFileURL(p).toString());
        return new Response(r.body, {
          status: r.status,
          headers: {
            'content-type': r.headers.get('content-type') || 'image/*',
            'access-control-allow-origin': '*',
            'cache-control': 'max-age=31536000, immutable',
          },
        });
      }
    }
    if (u.host === 'shot' && parts.length === 1) {
      const f = suite.shotFile(decodeURIComponent(parts[0]));
      if (f) {
        const r = await net.fetch(pathToFileURL(f).toString());
        return new Response(r.body, { status: r.status, headers: { 'content-type': /\.jpe?g$/i.test(f) ? 'image/jpeg' : 'image/png', 'cache-control': 'max-age=31536000, immutable' } });
      }
    }
    return new Response('no encontrado', { status: 404 });
  });
  tracker = createTracker({
    getGames: () => [...library.values()],
    getUid: () => ronron.uid(),
    onChange: () => { send('play', playState()); ronronWatch().catch(() => {}); hoursSoon(); suite.liveChanged(); },
    onEnd: (key, counted, launchedHere) => {
      if (counted) segFromSession(key).catch(() => {});
      if (!launchedHere || !win || config.get().behavior.returnOnExit === false) return;
      // Lanzado para jugar en streaming: quien juega está en el móvil; Atrio no se pone delante
      if (streamedKeys.has(key)) { streamedKeys.delete(key); return; }
      // Algunos juegos (Deadlock, los que se reinician al actualizar o pasan por un lanzador) desaparecen unos
      // segundos y vuelven: Atrio solo vuelve a primer plano si de verdad no hay ningún juego abierto un rato después
      setTimeout(() => {
        if (!win || win.isDestroyed() || gameRunning()) return;
        if (win.isMinimized()) win.restore();
        win.show();
        win.focus();
        send('game-ended', key);
      }, 15 * 1000);
    },
  });
  setupIpc();
  createWindow();
  suite.start({
    log, send, fetch: (...a) => net.fetch(...a), version: () => app.getVersion(), config: () => config.get(),
    current: currentGame, library: suiteLibrary,
    // Minutos jugados hoy en este PC (contando la partida en curso)
    today: () => {
      if (!tracker) return 0;
      const d0 = new Date(); d0.setHours(0, 0, 0, 0);
      const st = tracker.state();
      let ms = 0;
      for (const x of st.sessions) if (x[2] > d0.getTime()) ms += x[2] - Math.max(x[1], d0.getTime());
      for (const a of st.active || []) ms += Date.now() - Math.max(a.s, d0.getTime());
      return Math.round(ms / 60000);
    }, launch: launchGame, power: doPower, rescan,
    // Con un juego abierto, Atrio no le quita el foco (en streaming, un clic acabaría en Atrio en vez de en el juego)
    focus: () => { if (win && !gameRunning()) { if (win.isMinimized()) win.restore(); win.show(); win.focus(); } },
    // Jugar en streaming: Atrio se aparta (minimizado) para que en el móvil solo se vea y se toque el juego
    streaming: (key) => { if (!key) return; streamedKeys.add(key); if (win && !win.isDestroyed() && !process.env.ATRIO_SHOTS) win.minimize(); },
    // Una web adjuntada desde el móvil a un juego de esta biblioteca
    addLink: (key, url, name) => {
      if (!library.has(key)) throw new Error('Ese juego no está en la biblioteca de este PC');
      if (!web.okUrl(url)) throw new Error('La dirección tiene que empezar por http:// o https://');
      const cfg = config.get();
      const ov = { ...(cfg.overrides[key] || {}) };
      const links = (Array.isArray(ov.links) ? ov.links : []).filter((l) => l.u !== url);
      links.push({ u: url, n: String(name || '').slice(0, 60) || new URL(url).hostname.replace(/^www\./, '') });
      ov.links = links.slice(-20);
      config.save({ ...cfg, overrides: { ...cfg.overrides, [key]: ov } });
      const g = library.get(key);
      send('art', publicGame(g));
      suite.libChanged();
      return named(g).name;
    },
  }, ronron);
  // Si el equipo se suspende por otro lado (botón, tapa, inactividad), tampoco cuenta el rato dormido
  try { powerMonitor.on('suspend', () => { log('suspendido'); if (tracker) tracker.pause(); }); } catch { /* */ }
  rescan().then(() => { send('library', state()); suite.libChanged(); tracker.start(); setTimeout(prefetchMeta, 8000); hoursSoon(4000); });
  // Horas de tus otros equipos
  setInterval(() => hoursSync().catch(() => {}), Number(process.env.ATRIO_HOURS_EVERY || 60000));
  setInterval(() => ronronWatch().catch(() => {}), 60 * 1000);
});

app.on('second-instance', (_e, _argv, _cwd, data) => {
  // Se ha abierto una versión nueva de Atrio: reiniciar con la copia instalada
  if (data && data.updated && data.version && data.version !== app.getVersion() && fs.existsSync(install.INSTALLED)) {
    log('nueva versión', data.version, 'reiniciando');
    quitting = true;
    if (tracker) tracker.stop();
    app.relaunch({ execPath: install.INSTALLED, args: [] });
    app.exit(0);
    return;
  }
  if (win) { if (win.isMinimized()) win.restore(); win.show(); win.focus(); }
});
let suiteStopped = false;
app.on('before-quit', (e) => {
  quitting = true;
  if (tracker) tracker.stop();
  // Antes de cerrar, el móvil se entera de que este PC se desconecta
  if (!suiteStopped) {
    suiteStopped = true;
    e.preventDefault();
    Promise.race([suite.stop(), new Promise((r) => setTimeout(r, 2500))]).finally(() => app.quit());
  }
});
app.on('window-all-closed', () => app.quit());
