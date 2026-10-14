'use strict';
const fs = require('fs');
const path = require('path');
const { APP_CONFIG_DIR, readJson, mkdirp } = require('./paths');

const CONFIG_PATH = path.join(APP_CONFIG_DIR, 'config.json');

const DEFAULTS = {
  version: 1,
  sgdbKey: '',
  theme: {
    layout: 'horizonte',   // horizonte | consola | rejilla | lista | carrusel | estante
    scheme: 'noche',       // noche | oled | grafito | papel | arena | niebla
    accent: 'auto',        // 'auto' toma el color del arte; o un color hex
    font: 'grotesca',      // grotesca | serif | mono | redonda
    density: 'normal',     // compacta | normal | amplia
    backdrop: 'arte',      // arte | difuso | liso
    motion: true,
    clock: true,
    labels: true,
    sharpen: true,         // ampliar con calidad los fondos pequeños (los de 4K nunca se tocan)
    heroFit: 'nitido',
    form: 'clasico',       // forma: cómo se dibujan notas, tarjetas y botones (las mismas diez del móvil)     // nitido (ajustado al ancho, sin ampliar de más) | llenar (cubre toda la pantalla)
  },
  sources: { steam: true, heroic: true, lutris: true, emulators: true, custom: true },
  extraSteamPaths: [],
  extraHeroicPaths: [],
  emulators: [],
  customGames: [],
  overrides: {},
  stats: {},
  // La suite (Ronrón en el móvil): qué puede hacer el móvil en este PC y cómo son las capturas
  suite: {
    remoteShot: true,      // capturas pedidas desde el móvil
    remoteLaunch: true,    // lanzar juegos desde el móvil
    remotePower: true,     // suspender, apagar o reiniciar desde el móvil (el móvil pide confirmación)
    shotFormat: 'png',     // png (calidad original, sin pérdida) | jpg
    shotScope: 'juego',    // juego (la pantalla donde se juega) | todas
    shotMethod: 'auto',    // auto | spectacle | grim | gnome-screenshot | import | chromium
    shotUpload: true,      // subir al móvil (si no, solo se guardan en este PC)
    shotDir: '',           // '' = Imágenes/Atrio
    autoSeg: true,         // al cerrar un juego que está en Ronrón, guardar la partida como parte para valorar
    segMin: 5,             // minutos mínimos para que una partida cuente como parte
  },
  // Streaming al móvil (Polaris en este PC + Nova en el móvil)
  stream: {
    user: '',              // usuario y contraseña del panel de Polaris (para emparejar el móvil desde la app)
    pass: '',
    apiKey: '',            // o la clave de API de Polaris (api_key en polaris.conf), en lugar del usuario
    app: 'Desktop',        // la aplicación de Polaris que abre Nova (el escritorio: el juego lo lanza Atrio)
    remote: true,          // dejar que el móvil prepare el streaming (arrancar Polaris, lanzar el juego, emparejar)
  },
  behavior: {
    onLaunch: 'minimize',   // minimize | none | quit
    returnOnExit: true,     // volver a Atrio al cerrar el juego
    hideSteamLaunch: true,  // KDE: esconder las ventanas de Steam mientras arranca el juego
    onSelect: 'page',       // page (abrir la página del juego) | play (jugar directamente)
    autostart: null,        // visible | hidden | off (null = aún sin decidir: se activa al instalar)
    viaSteam: true,         // si Atrio está añadido a Steam, abrirlo a través de Steam al iniciar sesión
    swapAB: false,
    padMaps: {},            // botones configurados a mano por mando (id -> mapa)
    fullscreen: true,
    sort: 'recent',         // recent | alpha | source
    filter: 'all',
  },
};

function deepMerge(base, extra) {
  if (Array.isArray(base)) return Array.isArray(extra) ? extra : base;
  if (base && typeof base === 'object') {
    const out = { ...base };
    if (extra && typeof extra === 'object' && !Array.isArray(extra)) {
      for (const k of Object.keys(extra)) {
        out[k] = k in base ? deepMerge(base[k], extra[k]) : extra[k];
      }
    }
    return out;
  }
  return extra === undefined ? base : extra;
}

let current = null;

function load() {
  const stored = readJson(CONFIG_PATH, {});
  current = deepMerge(DEFAULTS, stored || {});
  return current;
}

function get() {
  return current || load();
}

function save(next) {
  current = next;
  mkdirp(APP_CONFIG_DIR);
  const tmp = CONFIG_PATH + '.tmp';
  fs.writeFileSync(tmp, JSON.stringify(current, null, 2));
  fs.renameSync(tmp, CONFIG_PATH);
  return current;
}

// Aplica un parche parcial (mezcla profunda en objetos, reemplazo en arrays)
function patch(partial) {
  return save(deepMerge(get(), partial));
}

module.exports = { load, get, save, patch, DEFAULTS, CONFIG_PATH };
