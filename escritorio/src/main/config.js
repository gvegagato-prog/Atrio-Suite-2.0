'use strict';
const fs = require('fs');
const path = require('path');
const { APP_CONFIG_DIR, readJson, mkdirp } = require('./paths');

const CONFIG_PATH = path.join(APP_CONFIG_DIR, 'config.json');

const DEFAULTS = {
  version: 1,
  sgdbKey: '',
  // Interfaz que se abre: 'revista' (la de escritorio, como la app del móvil) o 'lanzador' (pantalla grande y mando)
  ui: 'revista',
  // La revista: tema (claro | oscuro | auto) y color de acento (rojo | azul | verde | mostaza | morado | naranja | rosa | tinta)
  revista: {
    tema: 'auto',
    acento: 'rojo',
    fondo: '',            // un fondo de color (granate, bosque, marino, petroleo, berenjena, chocolate, pizarra, mostaza) o '' (papel)
  },
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
    heroFit: 'nitido',     // nitido (ajustado al ancho, sin ampliar de más) | llenar (cubre toda la pantalla)
    form: 'clasico',       // forma: cómo se dibujan portadas, tarjetas y botones (las mismas diez del móvil)
    gauge: '',             // notas aparte de la forma ('' = las de la forma)
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
  },
  // Tu nube (kDrive de Infomaniak): token y número de tu kDrive (solo en este PC), y si se suben las capturas
  nube: { token: '', drive: '', shots: true },
  // Streaming al móvil (Steam de este PC + Steam Link en el móvil)
  stream: {
    remote: true,          // dejar que el móvil prepare el streaming (abrir Steam y lanzar el juego)
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
  // Lo que se guardaba para Polaris (usuario, contraseña, clave de API): ya no se usa y no se deja en el disco
  if (current.stream && ['user', 'pass', 'apiKey', 'app'].some((k) => k in current.stream)) {
    const { user, pass, apiKey, app, ...resto } = current.stream;
    current.stream = resto;
    try { save(current); } catch { /* se limpia la próxima vez */ }
  }
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
