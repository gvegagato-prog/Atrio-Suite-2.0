'use strict';
// Notas de Rotten Tomatoes (rottentomatoes.com) para películas y series: la de la crítica (Tomatometer) y la del público
// (Popcornmeter). No hay API pública: se busca el título original en su buscador (cada resultado trae el año), se elige el
// que coincide por título y año y, en su página, se leen las dos notas del JSON que la propia página lleva dentro. Es el
// mismo método que Rt.kt del móvil (las mismas expresiones), y se guarda una semana en este equipo.
const fs = require('fs');
const path = require('path');
const { XDG_DATA, mkdirp } = require('./paths');

const BASE = () => process.env.ATRIO_RT_BASE || 'https://www.rottentomatoes.com';
const UA = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36';
const SEMANA = 7 * 24 * 3600 * 1000;
const ARCHIVO = () => path.join(XDG_DATA, 'atrio', 'rt.json');

const norm = (s) => String(s || '').toLowerCase().normalize('NFD').replace(/\p{M}+/gu, '').replace(/&/g, 'and').replace(/[^a-z0-9]+/g, ' ').trim();
const entidades = (s) => String(s || '').replace(/&#(\d+);/g, (_, n) => String.fromCodePoint(+n)).replace(/&#x([0-9a-f]+);/gi, (_, n) => String.fromCodePoint(parseInt(n, 16)))
  .replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&');

/** La dirección de la ficha de Rotten Tomatoes que coincide con el título y el año (o null). html: la página de búsqueda. */
function buscaUrl(html, titulo, anio, tv) {
  const quiere = tv ? 'tvSeries' : 'movie';
  const sec = String(html).split('<search-page-result').slice(1).find((s) => new RegExp(`type="${quiere}"`).test(s.split('>')[0]));
  if (!sec) return null;
  const filas = [];
  for (const m of sec.matchAll(/<search-page-media-row([^>]*)>([\s\S]*?)<\/search-page-media-row>/g)) {
    const [, attrs, cuerpo] = m;
    const url = (/href="(https:\/\/www\.rottentomatoes\.com\/(?:m|tv)\/[^"]+)"/.exec(cuerpo) || [])[1];
    if (!url) continue;
    const nombre = (/data-qa="info-name"[^>]*>\s*([^<]+?)\s*<\/a>/.exec(cuerpo) || /alt="([^"]+)"/.exec(cuerpo) || [])[1] || '';
    const attr = (n) => { const x = new RegExp(`${n}="(\\d{4})"`).exec(attrs); return x ? Number(x[1]) : null; };
    filas.push({ url, nombre: entidades(nombre), anio: attr(tv ? 'start-year' : 'release-year') ?? attr('release-year') });
  }
  if (!filas.length) return null;
  const t = norm(titulo);
  const y = Number(anio) || null;
  const f = filas.find((x) => norm(x.nombre) === t && (y == null || x.anio == null || Math.abs(x.anio - y) <= 1))
    || filas.find((x) => norm(x.nombre) === t)
    || filas.find((x) => y != null && x.anio === y && (norm(x.nombre).startsWith(t) || t.startsWith(norm(x.nombre))));
  return f ? f.url : null;
}

/** Las dos notas de la página de un título (−1 = no hay); null si no hay ninguna. */
function notas(html, url) {
  const nota = (bloque) => { const m = new RegExp(`"${bloque}":\\{[^}]*?"score":"(\\d+)"`).exec(html); return m ? Number(m[1]) : -1; };
  const tm = nota('criticsScore');
  const pc = nota('audienceScore');
  if (tm < 0 && pc < 0) return null;
  return { tm, pc, u: url, cf: /"criticsScore":\{[^}]*?"certified":true/.test(html) };
}

function crear({ fetchImpl = (...a) => fetch(...a), log = () => {} } = {}) {
  let cache = null;
  const lee = () => { if (!cache) { try { cache = JSON.parse(fs.readFileSync(ARCHIVO(), 'utf8')); } catch { cache = {}; } } return cache; };
  const guarda = () => { try { mkdirp(path.dirname(ARCHIVO())); fs.writeFileSync(ARCHIVO(), JSON.stringify(cache)); } catch { /* */ } };
  const get = async (url, cab = {}) => {
    const ctl = new AbortController();
    const t = setTimeout(() => ctl.abort(), 15000);
    try {
      const r = await fetchImpl(url, { headers: { 'User-Agent': UA, 'Accept-Language': 'en-US,en;q=0.8', ...cab }, signal: ctl.signal, redirect: 'follow' });
      if (!r.ok) throw new Error(`${new URL(url).hostname}: ${r.status}`);
      return r;
    } finally { clearTimeout(t); }
  };

  // Título original y año: de TMDB (películas, si hay clave) o de TVmaze (series)
  async function original(tv, id, nombre, claveTmdb) {
    try {
      if (tv) {
        const o = await (await get(`https://api.tvmaze.com/shows/${id}`)).json();
        return [o.name || nombre, String(o.premiered || '').slice(0, 4)];
      }
      const k = String(claveTmdb || '').trim();
      if (k) {
        const token = k.startsWith('eyJ');
        const o = await (await get(`https://api.themoviedb.org/3/movie/${id}?language=en-US${token ? '' : `&api_key=${encodeURIComponent(k)}`}`, token ? { Authorization: `Bearer ${k}` } : {})).json();
        return [o.title || o.original_title || nombre, String(o.release_date || '').slice(0, 4)];
      }
    } catch { /* sin datos: se usa el nombre */ }
    return [nombre, ''];
  }

  /** { tm, pc, u, cf } o null (no hay en Rotten Tomatoes). Se guarda una semana; sin conexión se usa lo que hubiera. */
  async function puntuacion({ tv, id, nombre, claveTmdb }) {
    const k = `${tv ? 't' : 'm'}:${id}`;
    const c = lee()[k];
    if (c && Date.now() - c.at < SEMANA) return c.none ? null : c.r;
    let r;
    try {
      const [t, y] = await original(tv, id, nombre, claveTmdb);
      const busca = async (titulo) => buscaUrl(await (await get(`${BASE()}/search?search=${encodeURIComponent(titulo)}`)).text(), titulo, y, tv);
      let url = await busca(t);
      if (!url && norm(t) !== norm(nombre)) url = await busca(nombre);
      // (la base solo cambia en las pruebas: en la web de verdad es la misma dirección)
      r = url ? notas(await (await get(url.replace('https://www.rottentomatoes.com', BASE()))).text(), url) : null;
    } catch (e) {
      log('rt:', e.message);
      return c && !c.none ? c.r : null;
    }
    lee()[k] = r ? { at: Date.now(), r } : { at: Date.now(), none: true };
    guarda();
    return r;
  }
  return { puntuacion };
}

module.exports = { crear, buscaUrl, notas, norm };
