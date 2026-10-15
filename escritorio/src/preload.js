'use strict';
const { contextBridge, ipcRenderer } = require('electron');

const invoke = (ch, ...a) => ipcRenderer.invoke(ch, ...a);

contextBridge.exposeInMainWorld('atrio', {
  platform: process.platform,
  state: () => invoke('state'),
  rescan: () => invoke('rescan'),
  patchConfig: (p) => invoke('config:patch', p),
  override: (key, p) => invoke('override', key, p),
  launch: (key) => invoke('launch', key),
  // Cambiar entre la revista y el lanzador clásico ('revista' | 'lanzador'); se recuerda para la próxima vez
  switchUi: (mode) => invoke('ui:switch', mode),
  // La revista: buscar en Open Library, TMDB, TVmaze y RAWG (lo hace el proceso principal)
  netJson: (url, token) => invoke('net:json', url, token),
  sgdbSearch: (term) => invoke('sgdb:search', term),
  sgdbTest: (key) => invoke('sgdb:test', key),
  sgdbAssets: (key, type, id, page) => invoke('sgdb:assets', key, type, id, page),
  setArt: (key, type, src) => invoke('art:set', key, type, src),
  saveHd: (key, buf) => invoke('art:saveHd', key, buf),
  refetchArt: (key, replace) => invoke('art:refetch', key, replace),
  pickFile: (opts) => invoke('dialog:file', opts),
  toggleFullscreen: () => invoke('win:fullscreen'),
  minimize: () => invoke('win:minimize'),
  quit: () => invoke('app:quit'),
  power: (action) => invoke('power', action),
  openExternal: (url) => invoke('open:external', url),
  exists: (p) => invoke('fs:exists', p),
  playState: () => invoke('play:state'),
  protonList: () => invoke('proton:list'),
  meta: (key) => invoke('meta:get', key),
  autostart: () => invoke('autostart:get'),
  setAutostart: (mode) => invoke('autostart:set', mode),
  log: (msg) => invoke('log', msg),
  playForget: (key) => invoke('play:forget', key),
  ronron: () => invoke('ronron:state'),
  ronronLogin: (u, p, k) => invoke('ronron:login', u, p, k),
  ronronLogout: () => invoke('ronron:logout'),
  ronronSync: () => invoke('ronron:sync'),
  ronronEdit: (op) => invoke('ronron:edit', op),
  ronronArt: (id) => invoke('ronron:art', id),
  ronronAssets: (id, type, page) => invoke('ronron:assets', id, type, page),
  suite: () => invoke('suite:state'),
  suiteShot: () => invoke('suite:shot'),
  suiteShots: (key) => invoke('suite:shots', key),
  suiteShotDelete: (id) => invoke('suite:shotDelete', id),
  suiteShotShow: (id) => invoke('suite:shotShow', id),
  suiteMethods: () => invoke('suite:methods'),
  streamInfo: () => invoke('stream:info'),
  streamStart: () => invoke('stream:start'),
  streamCheck: () => invoke('stream:check'),
  streamWeb: () => invoke('stream:web'),
  webOpen: (url, bounds) => invoke('web:open', url, bounds),
  webBounds: (bounds) => invoke('web:bounds', bounds),
  webHide: () => invoke('web:hide'),
  webShow: (bounds) => invoke('web:show', bounds),
  webClose: () => invoke('web:close'),
  webNav: (action) => invoke('web:nav', action),
  webState: () => invoke('web:state'),
  on: (channel, cb) => {
    const allowed = ['art', 'art-idle', 'library', 'focus', 'game-ended', 'play', 'winfocus', 'notice', 'ronron',
      'suite', 'shot-start', 'shot-taken', 'seg', 'remote', 'web', 'web-key', 'web-error', 'stream'];
    if (!allowed.includes(channel)) return;
    ipcRenderer.on(channel, (_e, payload) => cb(payload));
  },
});
