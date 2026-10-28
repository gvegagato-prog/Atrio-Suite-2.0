'use strict';
const fs = require('fs');
const path = require('path');
const { parseVdf, get, parseBinaryVdf } = require('../vdf');
const { IS_WIN, IS_MAC, HOME, expandHome, exists } = require('../paths');

// Windows: carpeta de Steam según el registro (HKCU\Software\Valve\Steam\SteamPath)
function winSteamPath() {
  try {
    const out = require('child_process').execFileSync('reg', ['query', 'HKCU\\Software\\Valve\\Steam', '/v', 'SteamPath'],
      { encoding: 'utf8', windowsHide: true, timeout: 5000 });
    const m = out.match(/SteamPath\s+REG_\w+\s+(.+)/i);
    return m ? path.normalize(m[1].trim()) : null;
  } catch { return null; }
}
const { sortKey } = require('../util');

// Herramientas que Steam instala como "juegos" y no queremos mostrar.
// Se filtra por nombre para no ocultar por error ningún juego real.
const TOOL_PATTERNS = [
  /^proton\b/i, /steam linux runtime/i, /steamworks common redistributables/i,
  /^steamvr$/i, /^steam audio/i, /proton easyanticheat runtime/i, /proton battleye runtime/i,
];

const FLATPAK_MARK = `${path.sep}.var${path.sep}app${path.sep}com.valvesoftware.Steam${path.sep}`;

// Carpeta steamapps de una biblioteca (las instalaciones antiguas usaban "SteamApps")
function appsDirOf(lib) {
  for (const n of ['steamapps', 'SteamApps']) {
    const p = path.join(lib, n);
    if (exists(p)) return p;
  }
  return null;
}

function candidateRoots(extra = []) {
  const list = IS_WIN ? [
    winSteamPath(),
    path.join(process.env['ProgramFiles(x86)'] || 'C:\\Program Files (x86)', 'Steam'),
    path.join(process.env.ProgramFiles || 'C:\\Program Files', 'Steam'),
    ...extra.map(expandHome),
  ].filter(Boolean) : IS_MAC ? [
    path.join(HOME, 'Library/Application Support/Steam'),
    ...extra.map(expandHome),
  ] : [
    path.join(HOME, '.local/share/Steam'),
    path.join(HOME, '.steam/steam'),
    path.join(HOME, '.steam/root'),
    path.join(HOME, '.steam/debian-installation'),
    path.join(HOME, '.var/app/com.valvesoftware.Steam/.local/share/Steam'),
    path.join(HOME, '.var/app/com.valvesoftware.Steam/data/Steam'),
    ...extra.map(expandHome),
  ];
  const seen = new Set();
  const roots = [];
  for (const p of list) {
    if (!appsDirOf(p)) continue;
    let real;
    try { real = fs.realpathSync(p); } catch { continue; }
    if (seen.has(real)) continue;
    seen.add(real);
    roots.push({ path: real, flatpak: (real + path.sep).includes(FLATPAK_MARK) || (p + path.sep).includes(FLATPAK_MARK) });
  }
  return roots;
}

function libraryFolders(root) {
  const folders = new Set([root]);
  const files = [
    path.join(appsDirOf(root) || path.join(root, 'steamapps'), 'libraryfolders.vdf'),
    path.join(root, 'config', 'libraryfolders.vdf'),
  ];
  for (const vdfPath of files) {
    let data;
    try { data = parseVdf(fs.readFileSync(vdfPath, 'utf8')); } catch { continue; }
    const lf = get(data, 'libraryfolders') || get(data, 'LibraryFolders') || {};
    for (const k of Object.keys(lf)) {
      const entry = lf[k];
      const p = typeof entry === 'string' ? entry : get(entry, 'path');
      if (!p || !appsDirOf(p)) continue;
      try { folders.add(fs.realpathSync(p)); } catch { folders.add(p); }
    }
  }
  return [...folders];
}

// Última partida por juego. Steam la guarda por usuario; en vez de depender de una ruta
// exacta, buscamos en localconfig.vdf cualquier bloque de apps con campo LastPlayed.
function lastPlayedMap(root) {
  const out = {};
  Object.defineProperty(out, 'playtime', { value: {}, enumerable: false });
  const userdata = path.join(root, 'userdata');
  let users = [];
  try { users = fs.readdirSync(userdata); } catch { return out; }
  for (const u of users) {
    const f = path.join(userdata, u, 'config', 'localconfig.vdf');
    let data;
    try { data = parseVdf(fs.readFileSync(f, 'utf8')); } catch { continue; }
    const walk = (obj, depth) => {
      if (!obj || typeof obj !== 'object' || depth > 8) return;
      for (const [k, v] of Object.entries(obj)) {
        if (!v || typeof v !== 'object') continue;
        if (/^\d+$/.test(k)) {
          const lp = get(v, 'LastPlayed');
          if (lp && /^\d+$/.test(lp)) out[k] = Math.max(out[k] || 0, parseInt(lp, 10) * 1000);
          // Tiempo total que Steam ha registrado (en minutos)
          const pt = get(v, 'Playtime');
          if (pt && /^\d+$/.test(pt)) out.playtime[k] = Math.max(out.playtime[k] || 0, parseInt(pt, 10) * 60000);
        } else {
          walk(v, depth + 1);
        }
      }
    };
    walk(data, 0);
  }
  return out;
}

// Busca arte que Steam ya tiene en disco (caché de la biblioteca y arte personalizado del usuario)
function findLocalArt(root, appid) {
  const art = {};
  const cache = path.join(root, 'appcache', 'librarycache');
  const flat = {
    cover: [`${appid}_library_600x900.jpg`, `${appid}_library_600x900_2x.jpg`],
    hero: [`${appid}_library_hero.jpg`],
    logo: [`${appid}_logo.png`],
    wide: [`${appid}_header.jpg`],
  };
  for (const [type, names] of Object.entries(flat)) {
    for (const n of names) {
      const p = path.join(cache, n);
      if (exists(p)) { art[type] = p; break; }
    }
  }
  // Formato por carpetas: librarycache/<appid>/... (se buscan nombres por prefijo)
  const dir = path.join(cache, String(appid));
  if (exists(dir)) {
    const walk = (d, depth) => {
      let entries = [];
      try { entries = fs.readdirSync(d, { withFileTypes: true }); } catch { return; }
      for (const e of entries) {
        const full = path.join(d, e.name);
        if (e.isDirectory()) { if (depth < 3) walk(full, depth + 1); continue; }
        const n = e.name.toLowerCase();
        if (!/\.(jpe?g|png|webp)$/.test(n)) continue;
        if (!art.cover && n.startsWith('library_600x900')) art.cover = full;
        else if (!art.hero && n.startsWith('library_hero') && !n.includes('blur')) art.hero = full;
        else if (!art.logo && n.startsWith('logo')) art.logo = full;
        else if (!art.wide && (n.startsWith('header') || n.startsWith('library_header'))) art.wide = full;
      }
    };
    walk(dir, 0);
  }
  // Arte personalizado del usuario en Steam (userdata/<id>/config/grid): tiene prioridad
  const userdata = path.join(root, 'userdata');
  let users = [];
  try { users = fs.readdirSync(userdata); } catch { /* nada */ }
  for (const u of users) {
    const grid = path.join(userdata, u, 'config', 'grid');
    if (!exists(grid)) continue;
    for (const ext of ['png', 'jpg', 'jpeg', 'webp']) {
      const c = path.join(grid, `${appid}p.${ext}`);
      const h = path.join(grid, `${appid}_hero.${ext}`);
      const l = path.join(grid, `${appid}_logo.${ext}`);
      const w = path.join(grid, `${appid}.${ext}`);
      if (exists(c)) art.cover = c;
      if (exists(h)) art.hero = h;
      if (exists(l)) art.logo = l;
      if (exists(w)) art.wide = w;
    }
  }
  return art;
}

// ---------- Juegos "que no son de Steam" añadidos a Steam (shortcuts.vdf) ----------
// Se abren con steam://rungameid/<id largo>, así Steam usa el Proton y la configuración
// de mando que tengan asignados en Steam.
const unquote = (s) => String(s || '').trim().replace(/^"(.*)"$/, '$1');
const fieldOf = (obj, name) => {
  const k = Object.keys(obj).find((x) => x.toLowerCase() === name.toLowerCase());
  return k === undefined ? undefined : obj[k];
};

function shortcutArt(root, shortId, iconField) {
  const art = {};
  const userdata = path.join(root, 'userdata');
  let users = [];
  try { users = fs.readdirSync(userdata); } catch { /* */ }
  for (const u of users) {
    const grid = path.join(userdata, u, 'config', 'grid');
    if (!exists(grid)) continue;
    for (const ext of ['png', 'jpg', 'jpeg', 'webp']) {
      const c = path.join(grid, `${shortId}p.${ext}`);
      const h = path.join(grid, `${shortId}_hero.${ext}`);
      const l = path.join(grid, `${shortId}_logo.${ext}`);
      const w = path.join(grid, `${shortId}.${ext}`);
      const i = path.join(grid, `${shortId}_icon.${ext}`);
      if (!art.cover && exists(c)) art.cover = c;
      if (!art.hero && exists(h)) art.hero = h;
      if (!art.logo && exists(l)) art.logo = l;
      if (!art.wide && exists(w)) art.wide = w;
      if (!art.icon && exists(i)) art.icon = i;
    }
  }
  const icon = unquote(iconField);
  if (!art.icon && icon && /\.(png|jpe?g|webp)$/i.test(icon) && exists(icon)) art.icon = icon;
  return art;
}

function scanShortcuts(root, played, skipName) {
  const games = [];
  const seen = new Set();
  const userdata = path.join(root.path, 'userdata');
  let users = [];
  try { users = fs.readdirSync(userdata); } catch { return games; }
  for (const u of users) {
    const f = path.join(userdata, u, 'config', 'shortcuts.vdf');
    let data;
    try { data = parseBinaryVdf(fs.readFileSync(f)); } catch { continue; }
    const list = fieldOf(data, 'shortcuts') || {};
    for (const sc of Object.values(list)) {
      if (!sc || typeof sc !== 'object') continue;
      const name = String(fieldOf(sc, 'AppName') || '').trim();
      const rawId = fieldOf(sc, 'appid');
      if (!name || rawId === undefined || typeof rawId === 'bigint') continue;
      if (skipName && skipName(name, unquote(fieldOf(sc, 'Exe')))) continue;
      if (Number(fieldOf(sc, 'IsHidden') || 0)) continue;
      const shortId = String(Number(BigInt.asUintN(32, BigInt(rawId))));
      if (seen.has(shortId)) continue;
      seen.add(shortId);
      const exe = unquote(fieldOf(sc, 'Exe'));
      const startDir = unquote(fieldOf(sc, 'StartDir')).replace(/[\\/]+$/, '');
      const longId = ((BigInt(shortId) << 32n) | 0x02000000n).toString();
      // Carpeta de trabajo como pista para el registro de horas, salvo si es demasiado genérica
      const deep = startDir && startDir.split(/[\\/]/).filter(Boolean).length >= 3 && startDir !== HOME;
      const lastPlayed = Number(fieldOf(sc, 'LastPlayTime') || 0) * 1000;
      games.push({
        key: `steam-sc:${shortId}`,
        source: 'steam',
        sourceLabel: 'Steam',
        platform: 'Añadido a Steam',
        name,
        sortName: sortKey(name),
        steamShortcut: true,
        steamTrackId: shortId,
        exePath: exe && path.isAbsolute(exe) && !/[\\/](steam|flatpak|env|bash|sh|steam\.exe|cmd\.exe|powershell\.exe)$/i.test(exe) ? exe : null,
        installDir: deep ? startDir : null,
        lastPlayedSource: Math.max(played[shortId] || 0, lastPlayed),
        steamPlaytime: played.playtime[shortId] || 0,
        localArt: shortcutArt(root.path, shortId, fieldOf(sc, 'icon')),
        launch: { type: 'steam', appid: longId, flatpak: root.flatpak },
      });
    }
  }
  return games;
}

function dirHasContent(p) {
  try { return fs.readdirSync(p).length > 0; } catch { return false; }
}

function scan(cfg) {
  const games = [];
  const seen = new Set();
  const roots = candidateRoots(cfg.extraSteamPaths || []);
  const problems = [];
  for (const root of roots) {
    const played = lastPlayedMap(root.path);
    for (const lib of libraryFolders(root.path)) {
      const appsDir = appsDirOf(lib);
      if (!appsDir) continue;
      let files = [];
      try { files = fs.readdirSync(appsDir).filter((f) => /^appmanifest_\d+\.acf$/i.test(f)); } catch (e) {
        problems.push(`${appsDir}: ${e.code || e.message}`);
        continue;
      }
      for (const f of files) {
        let st;
        try { st = get(parseVdf(fs.readFileSync(path.join(appsDir, f), 'utf8')), 'AppState'); } catch { continue; }
        if (!st) continue;
        const appid = String(get(st, 'appid') || f.match(/\d+/)[0]);
        const name = (get(st, 'name') || '').trim() || `Juego de Steam ${appid}`;
        if (seen.has(appid)) continue;
        if (TOOL_PATTERNS.some((r) => r.test(name))) continue;
        const flags = parseInt(get(st, 'StateFlags') || '4', 10) || 0;
        const installDir = path.join(appsDir, 'common', get(st, 'installdir') || '');
        // Bit 4 = instalado. Si Steam está actualizándolo puede faltar ese bit aunque
        // el juego esté en disco: en ese caso lo mostramos igualmente.
        const installed = (flags & 4) !== 0;
        if (!installed && !(get(st, 'installdir') && dirHasContent(installDir))) continue;
        seen.add(appid);
        games.push({
          key: `steam:${appid}`,
          source: 'steam',
          sourceLabel: 'Steam',
          name,
          sortName: sortKey(name),
          steamAppId: appid,
          steamTrackId: appid,
          installDir,
          pendingUpdate: !installed || (flags & 2) !== 0,
          lastPlayedSource: Math.max(played[appid] || 0, (parseInt(get(st, 'LastPlayed') || '0', 10) || 0) * 1000),
          steamPlaytime: played.playtime[appid] || 0,
          localArt: findLocalArt(root.path, appid),
          launch: { type: 'steam', appid, flatpak: root.flatpak },
        });
      }
    }
    // Accesos directos añadidos a Steam (Atrio no se lista a sí mismo)
    if (cfg.steamShortcuts !== false) {
      const isAtrio = (name, exe) => /^atrio$/i.test(name) || /Atrio[^\\/]*\.(AppImage|exe)$/i.test(exe || '');
      for (const g of scanShortcuts(root, played, isAtrio)) {
        if (seen.has(g.key)) continue;
        seen.add(g.key);
        games.push(g);
      }
    }
  }
  return { games, roots: roots.map((r) => r.path), flatpak: roots.some((r) => r.flatpak), problems };
}

module.exports = { scan, candidateRoots };
