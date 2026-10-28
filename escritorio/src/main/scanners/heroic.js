'use strict';
const path = require('path');
const { IS_WIN, IS_MAC, WIN_APPDATA, HOME, XDG_CONFIG, expandHome, exists, readJson } = require('../paths');
const { sortKey } = require('../util');

const RUNNER_LABEL = { legendary: 'Epic', gog: 'GOG', nile: 'Amazon', sideload: 'Heroic' };

function roots(extra = []) {
  // Heroic guarda su configuración en la carpeta appData de Electron: ~/.config en Linux, %APPDATA% en Windows
  return [
    ...(IS_WIN ? [path.join(WIN_APPDATA, 'heroic')] : IS_MAC ? [path.join(HOME, 'Library/Application Support/heroic')] : [
      path.join(XDG_CONFIG, 'heroic'),
      path.join(HOME, '.var/app/com.heroicgameslauncher.hgl/config/heroic'),
    ]),
    ...extra.map(expandHome),
  ].filter((p) => exists(p));
}

function remoteArt(g) {
  const art = {};
  const pick = (v) => (typeof v === 'string' && /^https?:\/\//.test(v) ? v : null);
  if (pick(g.art_square)) art.cover = g.art_square;
  if (pick(g.art_cover)) art.wide = g.art_cover;
  if (pick(g.art_background)) art.hero = g.art_background;
  if (pick(g.art_logo)) art.logo = g.art_logo;
  return art;
}

function make(runner, appName, title, extra = {}) {
  const name = title || appName;
  return {
    key: `heroic:${runner}:${appName}`,
    source: 'heroic',
    sourceLabel: RUNNER_LABEL[runner] || 'Heroic',
    name,
    sortName: sortKey(name),
    heroicRunner: runner,
    heroicAppName: appName,
    launch: {
      type: 'url',
      url: `heroic://launch?appName=${encodeURIComponent(appName)}&runner=${runner}`,
    },
    ...extra,
  };
}

function scan(cfg) {
  const games = [];
  const seen = new Set();
  const push = (g) => { if (!seen.has(g.key)) { seen.add(g.key); games.push(g); } };

  for (const root of roots(cfg.extraHeroicPaths || [])) {
    // Epic (Legendary)
    const legendaryFiles = [
      path.join(root, 'legendaryConfig', 'legendary', 'installed.json'),
      path.resolve(root, '..', 'legendary', 'installed.json'),
    ];
    for (const f of legendaryFiles) {
      const data = readJson(f);
      if (!data || typeof data !== 'object') continue;
      for (const [appName, g] of Object.entries(data)) {
        if (!g || g.is_dlc) continue;
        push(make('legendary', appName, g.title, { installDir: g.install_path }));
      }
    }

    // GOG
    const gogLib = readJson(path.join(root, 'gog_store', 'library.json'), {});
    const gogTitles = {};
    const gogArt = {};
    for (const g of (gogLib && gogLib.games) || []) {
      if (g && g.app_name) { gogTitles[g.app_name] = g.title; gogArt[g.app_name] = remoteArt(g); }
    }
    const gogInst = readJson(path.join(root, 'gog_store', 'installed.json'), {});
    for (const g of (gogInst && gogInst.installed) || []) {
      if (!g || !g.appName || g.is_dlc) continue;
      push(make('gog', g.appName, gogTitles[g.appName], { installDir: g.install_path, remoteArt: gogArt[g.appName] }));
    }

    // Amazon (Nile)
    const nile = readJson(path.join(root, 'store_cache', 'nile_library.json'), {});
    for (const g of (nile && (nile.library || nile.games)) || []) {
      if (!g || !g.app_name || !g.is_installed) continue;
      push(make('nile', g.app_name, g.title, { installDir: g.install && g.install.install_path, remoteArt: remoteArt(g) }));
    }

    // Apps añadidas a mano en Heroic
    const side = readJson(path.join(root, 'sideload_apps', 'library.json'), {});
    for (const g of (side && side.games) || []) {
      if (!g || !g.app_name || g.is_installed === false) continue;
      push(make('sideload', g.app_name, g.title, { remoteArt: remoteArt(g) }));
    }
  }
  return { games };
}

module.exports = { scan };
